package tv.lumo.android.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.PlaybackSourceGuard
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.EpisodePlaybackTarget
import tv.lumo.android.core.data.repository.PlaybackRepository
import tv.lumo.android.core.data.repository.ProgressRepository
import tv.lumo.android.core.data.repository.SeriesRepository
import tv.lumo.android.core.player.AudioTrack
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.PlaybackError
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackRequest
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Playing one episode, and then the one after it (US-15).
 *
 * <h2>`VodPlayerViewModel`, one table over</h2>
 *
 * Everything written there applies here unchanged: the URL is fetched per
 * playback from the one operation family that emits one, handed straight to the
 * player, and never written to Room, to a log or to saved state (AGENTS.md §5).
 * An episode is a progressive file exactly as a film is, so it gets the same
 * scrubber and the same four seek states.
 *
 * <h2>Why it is a copy rather than a shared class</h2>
 *
 * A feature module never depends on another feature module
 * (`settings.gradle.kts`), and the two failure lists genuinely differ — an
 * episode cannot be `VOD_ITEM_NOT_FOUND` and a film cannot be
 * `EPISODE_NOT_FOUND`. The day a third surface needs both, the shared half moves
 * down into `core:`; duplicating two enums is a smaller debt than a dependency
 * the architecture forbids.
 *
 * <h2>The next episode lives here and not in either screen (S6-06)</h2>
 *
 * The television counts ten seconds and the phone counts fewer, and that is the
 * *only* difference between them: the offer, the cancellation, the chaining across
 * a season boundary and the end of a series are one implementation, because two
 * would drift and "the television skipped an episode" is a bug nobody reports.
 * The duration arrives as an argument to [start] rather than being read from a
 * constant, so the difference stays a number a surface passes in.
 *
 * **Advancing does not navigate.** The next episode replaces the current one in
 * this same screen, so watching six of them leaves one back stack entry and `BACK`
 * returns to the series rather than walking backwards through an evening.
 *
 * **The countdown stops at the first key press, and the card stays.** Somebody
 * pressing a key is somebody watching, and starting an episode under their thumb
 * is the kind of thing that is not forgiven. What they lose is the automatic part;
 * the offer is still there to accept.
 *
 * <h2>The position is saved, and what reads it is a series (S6-08)</h2>
 *
 * The film player's thirty-second loop, unchanged in shape, writing `EPISODE`
 * rows instead of `VOD` ones. The last save happens **before** the player is
 * stopped, because stopping resets the position to zero — saving after it would
 * write every viewer back to the beginning of everything they leave.
 *
 * What is written is an episode; what reads it is a rail of **series**
 * (`SeriesRepository.resumable`). Nothing here knows about that, and it must not:
 * this file records where somebody is, and deciding what to offer them tomorrow —
 * this episode again, or the next one — is a rule that needs the tree.
 *
 * **A position is saved for an episode reached by advancing, too.** The offer is
 * a way of watching a series, not a way around the bookkeeping.
 */
@HiltViewModel
class EpisodePlayerViewModel @Inject constructor(
    private val playback: PlaybackRepository,
    private val series: SeriesRepository,
    private val progress: ProgressRepository,
    private val sourceGuard: PlaybackSourceGuard,
    /** Exposed for the video surface, which needs the instance rather than its state. */
    val player: LumoPlayer,
) : ViewModel() {

    private val _failure = MutableStateFlow<EpisodePlayerFailure?>(null)
    private val _target = MutableStateFlow<EpisodePlaybackTarget?>(null)

    /**
     * The episode on screen and the one being offered, in one value.
     *
     * Together rather than in two flows because they change together — opening an
     * episode clears the offer — and because a screen reading two would render one
     * against the other for exactly one frame.
     */
    private val _playing = MutableStateFlow(Playing())

    private var episodeId: String? = null
    private var currentTitle: String? = null
    private var autoAdvanceSeconds: Int = 0
    private var countdown: Job? = null
    private var saver: Job? = null
    private var resumeFromMs: Long = 0L

    val state: StateFlow<EpisodePlayerUiState> =
        combine(
            player.state,
            player.progress,
            _failure,
            _target,
            _playing,
        ) { playbackState, progress, failure, target, playing ->
            EpisodePlayerUiState(
                playback = playbackState,
                progress = progress,
                // A failure this screen produced outranks the player's own: it
                // carries a contract code, and the player's is UNKNOWN whenever
                // the stream never started at all.
                failure = failure
                    ?: (playbackState as? PlaybackState.Failed)?.error?.asEpisodeFailure(target),
                episode = playing.episode,
                upNext = playing.upNext,
                // Ended with nothing to follow. The screen leaves; there is no
                // card to draw and a picture frozen on the last frame of a series
                // is an application that has stopped answering.
                //
                // `nextChecked` is what keeps this from firing on **every**
                // episode: the lookup is a suspend call, so there is a window
                // after `Ended` in which no offer exists yet and none has been
                // ruled out. Without the third term the screen would leave in
                // that window, and the last-episode behaviour would be the only
                // behaviour.
                finished = playbackState is PlaybackState.Ended &&
                    playing.upNext == null &&
                    playing.nextChecked,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EpisodePlayerUiState(),
        )

    init {
        // The end of an episode is the one player event this screen acts on by
        // itself. Collected here rather than watched by the screen, so it fires
        // once for the process rather than once per recomposition.
        viewModelScope.launch {
            player.state.collect { if (it is PlaybackState.Ended) onEnded() }
        }
    }

    /**
     * Called once, with the episode the screen was opened for.
     *
     * @param title what the previous screen already knew to call it, so the
     *   player has a name to show before any request answers. Null is honest —
     *   a panel that numbers an episode without naming it exists, and the screen
     *   says "Episode 4" rather than inventing a title.
     * @param autoAdvanceSeconds how long the offer counts down before it plays by
     *   itself. Ten on a television, fewer on a phone; zero would mean "offer,
     *   never start", which no surface asks for today but costs nothing to allow.
     * @param resumeFromMs where the viewer chose to start. Zero is the beginning,
     *   and it is a choice somebody made on the previous screen — never a default
     *   this player applied on their behalf. Episodes reached by advancing start
     *   at zero because that is where they start, not because it is a fallback.
     */
    fun start(
        episodeId: String,
        title: String?,
        autoAdvanceSeconds: Int,
        resumeFromMs: Long = 0L,
    ) {
        if (this.episodeId == episodeId) return
        this.autoAdvanceSeconds = autoAdvanceSeconds
        this.resumeFromMs = resumeFromMs
        open(episodeId, title)
    }

    fun retry() {
        val episodeId = episodeId ?: return
        open(episodeId, currentTitle)
    }

    /**
     * The audio tracks of what is playing, and the way to change which one plays.
     *
     * Passed straight through rather than folded into the screen's state: the
     * list changes when a container header is read and when a choice is made,
     * which is a handful of times per stream, while the state above changes
     * several times a second. One flow would recompose the picker at the tick
     * rate of a progress bar.
     */
    val audioTracks: StateFlow<List<AudioTrack>> = player.audioTracks

    fun selectAudioTrack(id: String) = player.selectAudioTrack(id)
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun togglePlayPause() {
        if (player.state.value is PlaybackState.Playing) {
            player.pause()
            // A tap on pause. The moment a position is most likely to matter:
            // pausing is the single most likely thing somebody does before walking
            // away.
            saveNow()
        } else {
            player.resume()
        }
    }

    /**
     * A key was pressed while the offer was counting down.
     *
     * The countdown stops; **the card stays.** Somebody who touched the remote is
     * watching, and taking the offer away as well would punish them for it.
     */
    fun keepWatching() {
        countdown?.cancel()
        countdown = null
        _playing.update { it.copy(upNext = it.upNext?.copy(secondsLeft = null)) }
    }

    /** `OK` on the card, or the countdown reaching zero. */
    fun playNext() {
        val next = _playing.value.upNext?.episode ?: return
        countdown?.cancel()
        countdown = null
        // From its beginning: the offer is for an episode nobody has seen. Carrying
        // the previous one's position forward would drop somebody forty minutes into
        // an episode that has not started.
        resumeFromMs = 0L
        open(next.id, next.name)
    }

    private fun open(episodeId: String, title: String?) {
        this.episodeId = episodeId
        this.currentTitle = title
        _failure.value = null
        _playing.value = Playing()

        viewModelScope.launch {
            // From the cache, and before the stream: the label this screen shows
            // is known long before the panel answers, and it costs no request.
            val episode = series.episodesByIds(listOf(episodeId)).firstOrNull()
            _playing.update { it.copy(episode = episode) }

            // An evening of episodes is hours without a word to the API — the
            // streams come from the user's own server — so "does this source
            // still exist" is asked on a clock (US-024). Idempotent per source:
            // advancing to the next episode does not restart the sixty seconds.
            episode?.sourceId?.let(::watchSource)

            when (val result = playback.episodePlaybackTarget(episodeId)) {
                is LumoResult.Success -> {
                    val target = result.value
                    _target.value = target
                    player.play(
                        PlaybackRequest(
                            streamUrl = target.streamUrl,
                            title = title,
                            // The whole reason an episode gets a scrubber and a
                            // channel does not.
                            isLive = false,
                        ),
                    )
                    // After `play`, because the player has no timeline before it.
                    // Ignored by `LumoPlayer` until the stream turns out to be
                    // seekable, which is the honest outcome: a server that will not
                    // serve part of a file cannot resume one either.
                    if (resumeFromMs > 0L) player.seekTo(resumeFromMs)
                    startSaving()
                }

                is LumoResult.Failure -> _failure.value = result.error.asEpisodeFailure()
            }
        }
    }

    /**
     * The credits started.
     *
     * The next episode is looked up **now** rather than held from the start: an
     * hour has passed, the tree may have been refetched under this screen, and the
     * answer that matters is the one true at the moment the offer is made.
     */
    private fun onEnded() {
        val episodeId = episodeId ?: return
        // Asked once per episode. `Ended` can be re-emitted to a new collector.
        if (_playing.value.nextChecked) return

        viewModelScope.launch {
            val next = series.nextEpisode(episodeId)
            // Stamped whether or not there is one: "asked and there is none" is
            // what tells the screen to leave, and it is a different state from
            // "not asked yet".
            _playing.update { playing ->
                playing.copy(
                    upNext = next?.let { UpNext(it, autoAdvanceSeconds) },
                    nextChecked = true,
                )
            }
            if (next != null) startCountdown()
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var remaining = autoAdvanceSeconds
            while (remaining > 0) {
                delay(ONE_SECOND_MILLIS)
                remaining--
                // A key press cancels this job, so reaching here means nobody has
                // touched the remote. The guard is for the offer being cleared by
                // an episode opening underneath.
                val offer = _playing.value.upNext ?: return@launch
                _playing.update { it.copy(upNext = offer.copy(secondsLeft = remaining)) }
            }
            playNext()
        }
    }
    /**
     * The thirty-second loop.
     *
     * A loop rather than a listener, because nothing in the player fires as time
     * passes — the position advances with the clock. It runs while a stream is
     * loaded, **including while paused**: pausing is the single most likely moment
     * for somebody to walk away, and a paused position is the one most worth having.
     */
    private fun startSaving() {
        saver?.cancel()
        saver = viewModelScope.launch {
            while (isActive) {
                delay(SAVE_EVERY_MILLIS)
                saveNow()
            }
        }
    }

    private fun saveNow() {
        val episode = _playing.value.episode ?: return
        val snapshot = player.progress.value
        if (!snapshot.savable()) return

        viewModelScope.launch {
            progress.saveEpisode(
                // The episode's own source, from the cache, rather than one carried
                // in the route: an episode reached by advancing was never in a
                // route, and a player that only saved the first one would lose every
                // episode of an evening but the first.
                sourceId = episode.sourceId,
                episodeId = episode.id,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
            )
        }
    }

    private fun watchSource(sourceId: String) = sourceGuard.watch(viewModelScope, sourceId) {
        // No last save: the progress rows went with the source, and the server
        // would answer `404` to a write that names it. No next episode either —
        // nothing is started on the viewer's behalf.
        saver?.cancel()
        saver = null
        countdown?.cancel()
        countdown = null
        player.stop()
        _target.value = null
        _playing.update { it.copy(upNext = null) }
    }

    // ---- a source deleted elsewhere while this plays (US-024, C4 D5) --------

    /**
     * True once the server has **proven** that the source of what is playing was
     * deleted — `404 SOURCE_NOT_FOUND`, never a network error. Playback has been
     * stopped by then; the screen says so and offers *Continue*.
     */
    val sourceDeleted: StateFlow<Boolean> = sourceGuard.deleted

    /** The application is back in front of somebody: a reason to ask the server early. */
    fun onForeground() = sourceGuard.onForeground()

    /**
     * *Continue*. The active source is re-decided first — the one left, a question
     * when several are, "add a source" when none is — and only then does the
     * screen leave, onto a shell that already knows what it browses. Nothing else
     * is started on the viewer's behalf.
     */
    fun onSourceDeletedAcknowledged(leave: () -> Unit) {
        // Once: a second press while the list is being read would pop a second
        // entry off the back stack, and take the viewer out of where they were.
        if (acknowledging) return
        acknowledging = true

        viewModelScope.launch {
            sourceGuard.acknowledge()
            leave()
        }
    }

    private var acknowledging = false

    /** Leaving the screen. The player survives; the stream and its URL do not. */
    fun stop() {
        sourceGuard.stop()
        // Before `player.stop()`, which resets the position to zero. Saving after it
        // would write a viewer back to the beginning of every episode they leave.
        // Nothing to save once the source is gone: its progress went with it.
        if (!sourceGuard.deleted.value) saveNow()
        saver?.cancel()
        saver = null
        countdown?.cancel()
        countdown = null
        player.stop()
        _target.value = null
        _playing.value = Playing()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val ONE_SECOND_MILLIS = 1_000L

        /** An upsert, not a stream. The film player's interval, for its reason. */
        const val SAVE_EVERY_MILLIS = 30_000L
    }
}

/**
 * What is on screen, in one value.
 *
 * Together rather than in separate flows because they change together — opening an
 * episode clears all three — and because a screen reading three would render one
 * against another for exactly one frame.
 *
 * @param nextChecked whether the "what comes after this" question has been asked
 *   and answered for the current episode. Distinct from `upNext == null`, which is
 *   also true before anything has been asked; the difference is a whole behaviour,
 *   because "there is nothing after this" is what makes the screen leave.
 */
private data class Playing(
    val episode: Episode? = null,
    val upNext: UpNext? = null,
    val nextChecked: Boolean = false,
)

/**
 * The offer at the end of an episode.
 *
 * @param secondsLeft null once a key press has stopped the countdown. The card is
 *   still there and `OK` still works; what is gone is the part that would have
 *   started an episode without being asked.
 */
data class UpNext(val episode: Episode, val secondsLeft: Int?)

data class EpisodePlayerUiState(
    val playback: PlaybackState = PlaybackState.Idle,
    val progress: PlaybackProgress = PlaybackProgress(),
    val failure: EpisodePlayerFailure? = null,
    /** The episode being played, once the cache has been read. Carries its number. */
    val episode: Episode? = null,
    val upNext: UpNext? = null,
    /** The last episode of the series has ended. The screen leaves. */
    val finished: Boolean = false,
)

/**
 * What this screen can say.
 *
 * Its own type rather than `feature:vod`'s, and not out of preference: a feature
 * module never depends on another feature module (`settings.gradle.kts`), and the
 * two lists genuinely differ — an episode cannot be `VOD_ITEM_NOT_FOUND` and a
 * film cannot be `EPISODE_NOT_FOUND`. The day a third surface needs them both,
 * the shared half moves down into `core:`; duplicating two enums is a smaller
 * debt than a dependency the architecture forbids.
 */
sealed interface EpisodePlayerFailure {

    /** Worth trying again unchanged: the server did not answer, or we are offline. */
    data class Unreachable(val retryable: Boolean = true) : EpisodePlayerFailure

    /**
     * The subscription is already streaming as much as it allows.
     *
     * An episode counts against that ceiling exactly as a channel does, and the
     * sentence has to name **the user's own** provider's rule rather than imply
     * ours (US-09).
     */
    data class TooManyStreams(val allowed: Int?) : EpisodePlayerFailure

    /**
     * `SOURCE_NOT_READY`: the source is being refreshed, or was never imported.
     * Since lot C4 the catalogue stays browsable during a refresh and **playback
     * waits for its end**. Worth trying again: the refresh ends.
     */
    data object SourceNotReady : EpisodePlayerFailure

    /**
     * `SOURCE_AUTH_FAILED`: the provider refused the credentials at the last
     * synchronisation, and the server will not hand out a stream that would be
     * refused too (C4, decision D2). Retrying cannot help; "My sources" can.
     */
    data object CredentialsRefused : EpisodePlayerFailure

    /** The user's subscription with their provider has expired. */
    data object SubscriptionExpired : EpisodePlayerFailure

    /** The episode is gone — the tree was refetched and it is no longer in it. */
    data object EpisodeGone : EpisodePlayerFailure

    /** Reached, and not decodable on this device. Retrying will not help. */
    data object Unplayable : EpisodePlayerFailure

    data object Unexpected : EpisodePlayerFailure
}

internal fun LumoError.asEpisodeFailure(): EpisodePlayerFailure = when (this) {
    is LumoError.Offline -> EpisodePlayerFailure.Unreachable()
    is LumoError.Api -> when (code) {
        ErrorCode.SOURCE_MAX_CONNECTIONS -> EpisodePlayerFailure.TooManyStreams(null)
        ErrorCode.SOURCE_NOT_READY -> EpisodePlayerFailure.SourceNotReady
        ErrorCode.SOURCE_AUTH_FAILED -> EpisodePlayerFailure.CredentialsRefused
        ErrorCode.SOURCE_EXPIRED -> EpisodePlayerFailure.SubscriptionExpired
        ErrorCode.EPISODE_NOT_FOUND -> EpisodePlayerFailure.EpisodeGone
        else -> EpisodePlayerFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> EpisodePlayerFailure.Unexpected
}

internal fun PlaybackError.asEpisodeFailure(
    target: EpisodePlaybackTarget?,
): EpisodePlayerFailure = when (this) {
    PlaybackError.UNREACHABLE -> EpisodePlayerFailure.Unreachable()
    PlaybackError.REFUSED -> EpisodePlayerFailure.TooManyStreams(target?.maxConnections)
    PlaybackError.UNPLAYABLE -> EpisodePlayerFailure.Unplayable
    PlaybackError.UNKNOWN -> EpisodePlayerFailure.Unexpected
}

/** Whether trying the same thing again can help. Same answers as the film player. */
internal fun EpisodePlayerFailure.isRetryable(): Boolean = when (this) {
    is EpisodePlayerFailure.Unreachable -> retryable
    is EpisodePlayerFailure.TooManyStreams -> true
    EpisodePlayerFailure.SourceNotReady -> true
    EpisodePlayerFailure.CredentialsRefused -> false
    EpisodePlayerFailure.SubscriptionExpired -> false
    EpisodePlayerFailure.EpisodeGone -> false
    EpisodePlayerFailure.Unplayable -> false
    EpisodePlayerFailure.Unexpected -> true
}

/**
 * Whether this position is worth sending to the server.
 *
 * <h2>Two guards, and only one of them is an optimisation</h2>
 *
 * **A live stream is never saved.** `ProgressItemType` has no `LIVE` value, so the
 * contract cannot express it — but a type system does not stop a shared player
 * from being handed a channel and this view model from writing what it reports.
 *
 * **A position of zero is not saved either**, and that one is not thrift: a save at
 * zero overwrites a real position with the beginning of the episode. It happens on
 * every open — the player reports zero for the frames before the first one decodes
 * — so without this guard, opening an episode and closing it immediately would lose
 * where somebody was.
 *
 * `UNKNOWN` is refused with the same reasoning: nothing has loaded, so whatever the
 * position says is not a position.
 */
internal fun PlaybackProgress.savable(): Boolean = when (seek) {
    SeekAvailability.LIVE, SeekAvailability.UNKNOWN -> false
    SeekAvailability.AVAILABLE, SeekAvailability.REFUSED -> positionMs > 0L
}
