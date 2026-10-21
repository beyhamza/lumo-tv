package tv.lumo.android.core.player.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumo.android.core.player.AudioTrack
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackRequest
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.core.player.TrackKind
import tv.lumo.android.core.player.TrackOptions
import tv.lumo.android.core.player.WatchedSegmentTracker
import tv.lumo.android.core.player.WatchedTime
import tv.lumo.android.core.player.seekWhenSeekable

/**
 * The phone's player, or the Chromecast it sends to (S16-01/03/04/05, ADR 0013).
 *
 * <h2>Why the screens do not know</h2>
 *
 * The film, episode and channel view models drive one [LumoPlayer]: play, pause,
 * seek, save the position every thirty seconds, take the watched time, chain the
 * next episode when the state says `Ended`. All of that has to keep working while
 * the picture is on the television (US-027), so this class answers those same
 * calls from whichever end is playing. The screens only add what is new — the Cast
 * button, the remote-control panel, the refusal sentence — through [CastControl].
 *
 * <h2>Three modes</h2>
 *
 * - **local**: everything goes to the phone's player, unchanged;
 * - **remote**: the receiver plays. The phone's player was stopped **before** the
 *   `LOAD` (decision 5: a one-connection subscription would refuse the receiver
 *   otherwise); state and position are the receiver's; the time watched is counted
 *   only while the receiver says it plays (ADR 0012);
 * - **detached**: the cast was stopped. Nothing plays; the position reached is
 *   kept, saved like a pause, and offered back to the phone (S16-03).
 *
 * A refusal (S16-05) goes straight back to local at the position reached, and says
 * so once. Nothing here ever relays or rewrites a stream (ADR 0007): the receiver is
 * handed the URL the phone was given.
 *
 * Pure Kotlin over [RemotePlayback], so every path is a JVM test. Call on the main
 * thread.
 */
class CastingLumoPlayer(
    /** The phone's own player. The video surface draws this one. */
    val local: LumoPlayer,
    /** Null on the television, which ships no Cast SDK. */
    private val remote: RemotePlayback?,
    private val scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
    /** Never handed a URL: codes and labels only (AGENTS.md §5). */
    private val log: (String) -> Unit = {},
) : LumoPlayer, CastControl {

    private enum class Mode { LOCAL, REMOTE, DETACHED }

    private val mode = MutableStateFlow(Mode.LOCAL)

    /** What plays, URL included. Dropped by [stop], as the phone's player drops its own. */
    private var request: PlaybackRequest? = null
    private val stream = MutableStateFlow<StreamInfo?>(null)

    private val remoteState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val remoteProgress = MutableStateFlow(PlaybackProgress())
    private val remoteWatched = WatchedSegmentTracker(clock)

    /** The receiver has played this stream: from now on an end is an end, and silence is not a refusal. */
    private var remoteStarted = false

    /** The receiver accepted the `LOAD`: its statuses are about this stream from now on. */
    private var remoteLoaded = false

    /**
     * The receiver has reported this stream loading or playing. Until then an `IDLE`
     * is the previous stream's last word — the end of the episode before, still in
     * the status when the next `LOAD` is answered — and not about this one. A
     * receiver that fails this stream before ever starting it answers the `LOAD`
     * with an error, sends a detailed error, or stays silent: signals 1, 2 and 4.
     */
    private var remoteActive = false
    private var startPositionMs = 0L

    /** What the phone knew about seeking when it handed over; a refused `Range` stays refused. */
    private var seekHint = SeekAvailability.UNKNOWN

    private var silence: Job? = null
    private var loading: Job? = null
    private var localResume: Job? = null

    /** Time watched on one end and not yet taken by a save, kept across a switch. */
    private var carried = WatchedTime.NONE

    private val detachedProgress = MutableStateFlow(PlaybackProgress())
    private val notice = MutableStateFlow<CastNotice?>(null)
    private val resumeOffer = MutableStateFlow<CastResumeOffer?>(null)
    private var sessionConnected = false

    override val isRemote: Boolean get() = mode.value == Mode.REMOTE

    override val state: StateFlow<PlaybackState> =
        combine(mode, local.state, remoteState) { mode, local, remote ->
            when (mode) {
                Mode.LOCAL -> local
                Mode.REMOTE -> remote
                Mode.DETACHED -> PlaybackState.Paused
            }
        }.stateIn(scope, SharingStarted.Eagerly, local.state.value)

    override val progress: StateFlow<PlaybackProgress> =
        combine(mode, local.progress, remoteProgress, detachedProgress) { mode, local, remote, detached ->
            when (mode) {
                Mode.LOCAL -> local
                Mode.REMOTE -> remote
                Mode.DETACHED -> detached
            }
        }.stateIn(scope, SharingStarted.Eagerly, local.progress.value)

    override val audioTracks: StateFlow<List<AudioTrack>> =
        combine(mode, local.audioTracks) { mode, tracks -> if (mode == Mode.LOCAL) tracks else emptyList() }
            .stateIn(scope, SharingStarted.Eagerly, local.audioTracks.value)

    /** The receiver's tracks are its own business: the menu says "not known yet" while casting. */
    override val trackOptions: StateFlow<TrackOptions> =
        combine(mode, local.trackOptions) { mode, options -> if (mode == Mode.LOCAL) options else TrackOptions() }
            .stateIn(scope, SharingStarted.Eagerly, local.trackOptions.value)

    override val trackChangeFailures: Flow<TrackKind> = local.trackChangeFailures

    override val castUi: StateFlow<CastUi> = if (remote == null) {
        MutableStateFlow(CastUi()).asStateFlow()
    } else {
        combine(
            combine(remote.supported, remote.devicesAvailable, remote.session, ::Triple),
            combine(mode, stream, remote.status, ::Triple),
            notice,
            resumeOffer,
        ) { (supported, devices, session), (mode, stream, status), notice, offer ->
            CastUi(
                buttonVisible = CastRules.showButton(
                    castSupported = supported,
                    devicesAvailable = devices,
                    sessionConnected = session is RemoteSession.Connected,
                    streamKnown = stream != null,
                    needsUserAgent = stream?.needsUserAgent == true,
                ),
                casting = mode == Mode.REMOTE,
                deviceName = (session as? RemoteSession.Connected)?.deviceName,
                title = stream?.title,
                artworkUrl = stream?.artworkUrl,
                volume = status?.volume.takeIf { mode == Mode.REMOTE },
                notice = notice,
                resumeOffer = offer,
            )
        }.stateIn(scope, SharingStarted.Eagerly, CastUi())
    }

    init {
        if (remote != null) {
            scope.launch { remote.session.collect(::onSession) }
            scope.launch { remote.status.collect { it?.let(::onStatus) } }
            scope.launch { remote.signals.collect { handle(CastSignals.verdict(it, remoteStarted)) } }
        }
    }

    // ---- LumoPlayer ---------------------------------------------------------

    override fun play(request: PlaybackRequest) {
        localResume?.cancel()
        this.request = request
        stream.value = StreamInfo(request.needsUserAgent, request.title, request.artworkUrl)
        notice.value = null
        resumeOffer.value = null
        // A new stream is a new session: the previous one's time was taken by its last save.
        carried = WatchedTime.NONE

        if (canCast(request)) {
            local.stop()
            startRemote(request, from = null)
        } else {
            leaveRemote()
            local.play(request)
        }
    }

    override fun selectAudioTrack(id: String) {
        if (mode.value == Mode.LOCAL) local.selectAudioTrack(id)
    }

    override fun selectSubtitleTrack(id: String?) {
        if (mode.value == Mode.LOCAL) local.selectSubtitleTrack(id)
    }

    override fun selectVideoQuality(id: String?) {
        if (mode.value == Mode.LOCAL) local.selectVideoQuality(id)
    }

    override fun settleTrackChange(kind: TrackKind) = local.settleTrackChange(kind)

    override fun revertTrackChange(kind: TrackKind) = local.revertTrackChange(kind)

    override fun seekTo(positionMs: Long) {
        when (mode.value) {
            Mode.LOCAL -> local.seekTo(positionMs)
            Mode.REMOTE -> {
                if (remoteProgress.value.seek != SeekAvailability.AVAILABLE) return
                // The jump is not watching (CW-04).
                remoteWatched.onSeek()
                remote?.seekTo(positionMs.coerceAtLeast(0L))
                remoteProgress.value = remoteProgress.value.copy(positionMs = positionMs)
            }
            Mode.DETACHED -> if (detachedProgress.value.seek == SeekAvailability.AVAILABLE) {
                detachedProgress.value = detachedProgress.value.copy(positionMs = positionMs.coerceAtLeast(0L))
            }
        }
    }

    override fun pause() {
        when (mode.value) {
            Mode.LOCAL -> local.pause()
            Mode.REMOTE -> remote?.pause()
            Mode.DETACHED -> Unit
        }
    }

    override fun resume() {
        when (mode.value) {
            Mode.LOCAL -> local.resume()
            Mode.REMOTE -> remote?.play()
            // Play, after a cast was stopped, is "resume here".
            Mode.DETACHED -> resumeHere()
        }
    }

    override fun resumeAtLiveEdge() {
        when (mode.value) {
            Mode.LOCAL -> local.resumeAtLiveEdge()
            Mode.REMOTE -> remote?.play()
            Mode.DETACHED -> resumeHere()
        }
    }

    override fun takeWatched(): WatchedTime {
        val current = when (mode.value) {
            Mode.LOCAL -> local.takeWatched()
            Mode.REMOTE -> remoteWatched.take()
            Mode.DETACHED -> WatchedTime.NONE
        }
        val all = merge(carried, current)
        val max = WatchedSegmentTracker.MAX_PER_SAVE
        carried = WatchedTime(all.segments.drop(max), all.sessionStartedAtMs)
        return WatchedTime(all.segments.take(max), all.sessionStartedAtMs)
    }

    override fun stop() {
        localResume?.cancel()
        leaveRemote()
        local.stop()
        request = null
        stream.value = null
        carried = WatchedTime.NONE
        notice.value = null
        resumeOffer.value = null
    }

    override fun release() = local.release()

    // ---- CastControl --------------------------------------------------------

    override fun setVolume(level: Float) {
        if (mode.value == Mode.REMOTE) remote?.setVolume(level.coerceIn(0f, 1f))
    }

    override fun stopCasting() {
        if (mode.value != Mode.REMOTE) return
        detach()
        remote?.endSession()
    }

    override fun resumeHere() {
        if (mode.value != Mode.DETACHED) return
        val request = request ?: return
        val reached = detachedProgress.value
        resumeOffer.value = null
        mode.value = Mode.LOCAL
        playLocally(request, if (reached.seek == SeekAvailability.AVAILABLE) reached.positionMs else 0L)
    }

    override fun dismissNotice() {
        notice.value = null
    }

    override fun dismissResumeOffer() {
        resumeOffer.value = null
    }

    // ---- switching ----------------------------------------------------------

    private fun canCast(request: PlaybackRequest): Boolean =
        remote != null && remote.supported.value && sessionConnected && !request.needsUserAgent

    private fun onSession(session: RemoteSession) {
        val connected = session is RemoteSession.Connected
        val wasConnected = sessionConnected
        sessionConnected = connected
        if (connected && !wasConnected) {
            // A device was chosen during a playback: what plays goes there, from here.
            val request = request ?: return
            if (!canCast(request)) return
            when (mode.value) {
                Mode.LOCAL -> {
                    val from = local.progress.value
                    carried = merge(carried, local.takeWatched())
                    // Before the LOAD, never after (decision 5).
                    local.stop()
                    startRemote(request, from)
                }
                Mode.DETACHED -> startRemote(request, detachedProgress.value)
                Mode.REMOTE -> Unit
            }
        } else if (session is RemoteSession.None && wasConnected && mode.value == Mode.REMOTE) {
            // Left from the Cast dialog, or the device went away.
            detach()
        }
    }

    private fun startRemote(request: PlaybackRequest, from: PlaybackProgress?) {
        val remote = remote ?: return
        silence?.cancel()
        loading?.cancel()
        resumeOffer.value = null
        mode.value = Mode.REMOTE

        seekHint = if (request.isLive) SeekAvailability.LIVE else from?.seek ?: SeekAvailability.UNKNOWN
        startPositionMs = if (!request.isLive && from?.seek == SeekAvailability.AVAILABLE) from.positionMs else 0L
        // Decision 7: a server that ignores Range cannot be resumed on the receiver
        // either; it starts at zero, and the sentence says so.
        if (!request.isLive && from?.seek == SeekAvailability.REFUSED && from.positionMs > 0L) {
            notice.value = CastNotice.STARTED_FROM_BEGINNING
        }
        remoteStarted = false
        remoteLoaded = false
        remoteActive = false
        remoteWatched.reset()
        remoteState.value = PlaybackState.Buffering
        remoteProgress.value = PlaybackProgress(
            positionMs = startPositionMs,
            seek = if (request.isLive) SeekAvailability.LIVE else SeekAvailability.UNKNOWN,
        )

        val media = RemoteMedia(
            url = request.streamUrl,
            container = CastRules.containerOf(request.streamUrl, request.isLive),
            live = request.isLive,
            title = request.title,
            artworkUrl = request.artworkUrl,
        )
        silence = scope.launch {
            delay(CastSignals.SILENCE_TIMEOUT_MS)
            handle(CastSignals.verdict(CastSignal.Silence, remoteStarted))
        }
        loading = scope.launch {
            val code = remote.load(media, startPositionMs)
            if (mode.value != Mode.REMOTE) return@launch
            if (code == CastSignals.SUCCESS) {
                remoteLoaded = true
                remote.status.value?.let(::onStatus)
            } else {
                handle(CastSignals.verdict(CastSignal.LoadAnswered(code), remoteStarted))
            }
        }
    }

    private fun onStatus(status: RemoteStatus) {
        if (mode.value != Mode.REMOTE || !remoteLoaded) return
        val live = request?.isLive == true

        if (status.playerState == RemotePlayerState.IDLE) {
            if (remoteActive) handle(CastSignals.verdict(CastSignal.WentIdle(status.idleReason), remoteStarted))
            return
        }
        remoteActive = true
        if (status.playerState == RemotePlayerState.PLAYING || status.playerState == RemotePlayerState.PAUSED) {
            remoteStarted = true
            silence?.cancel()
        }
        remoteWatched.onPlaying(status.playerState == RemotePlayerState.PLAYING)
        remoteState.value = when (status.playerState) {
            RemotePlayerState.PLAYING -> PlaybackState.Playing(request?.title)
            RemotePlayerState.PAUSED -> PlaybackState.Paused
            else -> PlaybackState.Buffering
        }
        remoteProgress.value = PlaybackProgress(
            positionMs = if (live) 0L else status.positionMs,
            durationMs = if (live) null else status.durationMs,
            seek = remoteSeek(status, live),
        )
    }

    private fun remoteSeek(status: RemoteStatus, live: Boolean): SeekAvailability = when {
        live -> SeekAvailability.LIVE
        seekHint == SeekAvailability.REFUSED -> SeekAvailability.REFUSED
        !remoteStarted -> SeekAvailability.UNKNOWN
        status.seekable -> SeekAvailability.AVAILABLE
        else -> SeekAvailability.REFUSED
    }

    private fun handle(verdict: CastVerdict) {
        if (mode.value != Mode.REMOTE) return
        when (verdict) {
            CastVerdict.Ignore -> Unit
            CastVerdict.Finished -> {
                silence?.cancel()
                remoteWatched.onPlaying(false)
                remoteProgress.value = remoteProgress.value.let { it.copy(positionMs = it.durationMs ?: it.positionMs) }
                // The episode player chains the next one from here, on the receiver (S13-03).
                remoteState.value = PlaybackState.Ended
            }
            CastVerdict.StoppedElsewhere -> detach()
            is CastVerdict.Refused -> refuse(verdict.code)
        }
    }

    /** S16-05: one sentence, back to the phone at the position reached, the device left. */
    private fun refuse(code: String) {
        log("cast refused: $code")
        val reached = if (remoteStarted) remoteProgress.value.positionMs else startPositionMs
        val seekable = remoteStarted && remoteProgress.value.seek == SeekAvailability.AVAILABLE ||
            !remoteStarted && seekHint == SeekAvailability.AVAILABLE
        silence?.cancel()
        loading?.cancel()
        carried = merge(carried, remoteWatched.take())
        remoteWatched.reset()
        mode.value = Mode.LOCAL
        notice.value = CastNotice.REFUSED
        remote?.endSession()
        val request = request ?: return
        playLocally(request, if (seekable) reached else 0L)
    }

    /** The cast ended without a refusal: keep the position, offer it back to the phone. */
    private fun detach() {
        silence?.cancel()
        loading?.cancel()
        carried = merge(carried, remoteWatched.take())
        remoteWatched.reset()
        val live = request?.isLive == true
        val reached = remoteProgress.value
        detachedProgress.value = if (live) {
            PlaybackProgress(seek = SeekAvailability.LIVE)
        } else {
            reached.copy(seek = if (reached.seek == SeekAvailability.UNKNOWN) seekHint else reached.seek)
        }
        mode.value = Mode.DETACHED
        resumeOffer.value = CastResumeOffer(positionMs = if (live) null else reached.positionMs)
    }

    private fun leaveRemote() {
        silence?.cancel()
        loading?.cancel()
        if (mode.value == Mode.REMOTE) remote?.stopMedia()
        remoteWatched.reset()
        remoteState.value = PlaybackState.Idle
        remoteProgress.value = PlaybackProgress()
        mode.value = Mode.LOCAL
    }

    private fun playLocally(request: PlaybackRequest, positionMs: Long) {
        localResume?.cancel()
        local.play(request)
        if (!request.isLive && positionMs > 0L) {
            localResume = scope.launch { local.seekWhenSeekable(positionMs) }
        }
    }

    private fun merge(a: WatchedTime, b: WatchedTime): WatchedTime = WatchedTime(
        segments = a.segments + b.segments,
        sessionStartedAtMs = listOfNotNull(a.sessionStartedAtMs, b.sessionStartedAtMs).minOrNull(),
    )

    private data class StreamInfo(val needsUserAgent: Boolean, val title: String?, val artworkUrl: String?)
}

/** What the player screens add for Cast: the button, the remote-control panel, the sentences. */
interface CastControl {

    val castUi: StateFlow<CastUi>

    /** 0..1. */
    fun setVolume(level: Float)

    /** *Stop casting*: the receiver stops and the phone offers to take over (S16-03). */
    fun stopCasting()

    /** *Resume on this phone*, at the position the receiver reached. */
    fun resumeHere()

    fun dismissNotice()

    fun dismissResumeOffer()
}

data class CastUi(
    val buttonVisible: Boolean = false,
    /** The receiver plays: the screen draws the remote-control panel instead of the picture. */
    val casting: Boolean = false,
    val deviceName: String? = null,
    val title: String? = null,
    val artworkUrl: String? = null,
    /** 0..1, while casting, when the device says. */
    val volume: Float? = null,
    val notice: CastNotice? = null,
    val resumeOffer: CastResumeOffer? = null,
)

enum class CastNotice {
    /** "Ce flux ne peut pas être envoyé sur ce Chromecast" (S16-05). */
    REFUSED,

    /** The server ignores Range: the receiver starts at the beginning (decision 7). */
    STARTED_FROM_BEGINNING,
}

/** The cast was stopped; [positionMs] is where to resume here, null for a channel. */
data class CastResumeOffer(val positionMs: Long?)
