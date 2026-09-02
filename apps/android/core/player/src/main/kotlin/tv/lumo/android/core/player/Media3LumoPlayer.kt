package tv.lumo.android.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Media3 behind [LumoPlayer].
 *
 * Three things are configured here that every IPTV player needs and that are
 * easy to leave out until a user complains:
 *
 * - **Audio focus** is delegated to ExoPlayer, so an incoming call pauses
 *   playback and a navigation prompt ducks it (US-09).
 * - **A wake lock** tied to the player keeps the CPU and the Wi-Fi radio alive
 *   while playing. Without it a phone with the screen off stops mid-stream, and
 *   some TV boxes drop the connection after their idle timeout.
 * - **`stop()` clears the media item.** ExoPlayer keeps the last item otherwise,
 *   and the URL is credential-bearing (AGENTS.md §5): it should not linger in
 *   memory after the user leaves the channel.
 */
@Singleton
internal class Media3LumoPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : Media3Player {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    override val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    override val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()

    /**
     * Where each published [AudioTrack] actually lives in the current stream.
     *
     * An override has to name a `TrackGroup` and an index inside it; an
     * identifier a screen can pass around cannot be either of those. So the map
     * is rebuilt on every `onTracksChanged` and holds only what is loaded right
     * now — which is also what makes a stale id from the previous episode
     * resolve to nothing instead of to whatever occupies that slot today.
     */
    private var trackSlots: Map<String, TrackSlot> = emptyMap()

    private data class TrackSlot(val group: TrackGroup, val index: Int, val playable: Boolean)

    private var currentTitle: String? = null
    private var isLive: Boolean = true

    /**
     * A seek asked for and not yet answered.
     *
     * The one piece of state that makes a refused `Range` request tellable from
     * an outage: both arrive as the same player error, and only this says which
     * of the two the user was doing at the time.
     */
    private var seekInFlight: Boolean = false

    /**
     * The position to come back to if that seek turns out to be refused.
     *
     * Without it, a refusal costs the viewer their place in the film as well as
     * the ability to move — two losses for one failure.
     */
    private var positionBeforeSeek: Long = 0L

    /**
     * Where the ticker runs.
     *
     * Its own scope rather than a caller's: this object outlives every screen in
     * the process, and a progress loop tied to whichever screen started playback
     * would stop the moment that screen went away, on a stream that is still
     * playing.
     */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var ticker: Job? = null

    override val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(StateListener())
        }

    override fun play(request: PlaybackRequest) {
        currentTitle = request.title
        isLive = request.isLive
        seekInFlight = false
        positionBeforeSeek = 0L
        // Both cleared before the new stream, not after. An override belongs to
        // the container it was chosen in: carried over, it would pick "the second
        // audio track" of the next episode, which is a different language or does
        // not exist. Choosing a language once and having it stick across episodes
        // is a real want, and a real feature — it is a preference expressed in
        // languages, not an index, and it is not this.
        clearAudioSelection()
        _state.value = PlaybackState.Buffering
        _progress.value = PlaybackProgress(
            seek = if (request.isLive) SeekAvailability.LIVE else SeekAvailability.UNKNOWN,
        )

        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(request.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(request.title).build(),
                )
                // Live streams get a shorter target offset so the player joins
                // the edge rather than the start of the buffer.
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(if (request.isLive) LIVE_TARGET_OFFSET_MS else C.TIME_UNSET)
                        .build(),
                )
                .build(),
        )
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        startTicker()
    }

    /**
     * Moves within the stream, refusing what the stream cannot do.
     *
     * Guarded here rather than only in the screens: two surfaces call this, a
     * remote control sends `LEFT` and `RIGHT` at it, and a seek on a live
     * stream is an ExoPlayer exception rather than a no-op.
     */
    override fun seekTo(positionMs: Long) {
        if (_progress.value.seek != SeekAvailability.AVAILABLE) return

        positionBeforeSeek = exoPlayer.currentPosition
        seekInFlight = true
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        // Optimistic, so the scrubber lands under the thumb rather than snapping
        // back for the frame before ExoPlayer reports its new position.
        _progress.update { it.copy(positionMs = positionMs) }
    }

    /**
     * Plays another audio track of the current stream.
     *
     * <p><b>An unplayable track is refused rather than attempted</b>, and that is
     * the one judgement call in here. An explicit override is honoured by the
     * track selector even when the device has no decoder for it, and the result
     * is not silence — it is a renderer failure that ends the film. Somebody who
     * asked to hear the French track would lose the picture as well.
     *
     * <p>Refusing is not the player arguing, because nothing is hidden: the track
     * is in [audioTracks] with `playable = false`, the screen draws it and says
     * why, and "this device has no decoder for that track" is an answer somebody
     * can act on — which is more than the silence that started all of this.
     */
    override fun selectAudioTrack(id: String) {
        val slot = trackSlots[id] ?: return
        if (!slot.playable) return

        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(slot.group, slot.index))
            .build()
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun resume() {
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        stopTicker()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentTitle = null
        seekInFlight = false
        clearAudioSelection()
        _state.value = PlaybackState.Idle
        _progress.value = PlaybackProgress()
    }

    /**
     * Forgets the tracks of a stream that is no longer loaded, and the choice
     * made among them.
     *
     * Both halves matter. An empty list is what stops a picker drawing the
     * previous episode's languages over the next one's picture; dropping the
     * override is what stops "the second track" following somebody from a film
     * into a channel.
     */
    private fun clearAudioSelection() {
        trackSlots = emptyMap()
        _audioTracks.value = emptyList()
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .build()
    }

    override fun release() {
        stopTicker()
        exoPlayer.release()
        _state.value = PlaybackState.Idle
        _progress.value = PlaybackProgress()
    }

    private inner class StateListener : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) onReady()

            _state.value = when (playbackState) {
                Player.STATE_BUFFERING -> PlaybackState.Buffering
                Player.STATE_READY ->
                    if (exoPlayer.playWhenReady) {
                        PlaybackState.Playing(currentTitle)
                    } else {
                        PlaybackState.Paused
                    }

                Player.STATE_ENDED -> PlaybackState.Ended
                else -> PlaybackState.Idle
            }
        }

        /**
         * The audio tracks of the stream, each time the answer changes.
         *
         * <p>Fires when the container's header has been read, and again after an
         * override — which is why `selected` is taken from Media3 rather than
         * remembered here: the selector has the last word, and a screen showing a
         * tick beside a track the player did not take would be lying.
         */
        override fun onTracksChanged(tracks: Tracks) {
            val slots = LinkedHashMap<String, TrackSlot>()
            val published = ArrayList<AudioTrack>()

            for ((groupIndex, group) in tracks.groups.withIndex()) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (index in 0 until group.length) {
                    // Position in the container, not a language: it is stable for
                    // this stream and meaningless for the next one, which is
                    // exactly what `AudioTrack.id` promises.
                    val id = "$groupIndex:$index"
                    val format = group.getTrackFormat(index)
                    val playable = group.isTrackSupported(index)

                    slots[id] = TrackSlot(group.mediaTrackGroup, index, playable)
                    published += AudioTrack(
                        id = id,
                        language = format.language,
                        label = format.label,
                        mimeType = format.sampleMimeType,
                        channelCount = format.channelCount.takeIf { it > 0 },
                        selected = group.isTrackSelected(index),
                        playable = playable,
                    )
                }
            }

            trackSlots = slots
            _audioTracks.value = published
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_state.value is PlaybackState.Failed) return
            _state.value = if (isPlaying) PlaybackState.Playing(currentTitle) else PlaybackState.Paused
        }

        /**
         * A failure, or a server that will not serve part of a file.
         *
         * The distinction is the whole of S5-08's seeking requirement and it
         * cannot be made from the error alone: a refused `Range` request and a
         * connection that dropped arrive as the same `ERROR_CODE_IO_*`. What
         * separates them is that the viewer had just asked to move.
         *
         * So a seek that ends in an IO error is not a failed film. Playback goes
         * back to where it was, the scrubber becomes `REFUSED` and says why, and
         * the picture stays up — which is the truthful outcome, because the film
         * itself was playing perfectly a second ago.
         */
        override fun onPlayerError(error: PlaybackException) {
            val failure = error.toLumoError()

            if (seekInFlight && failure != PlaybackError.UNPLAYABLE) {
                seekInFlight = false
                _progress.update { it.copy(seek = SeekAvailability.REFUSED) }
                exoPlayer.seekTo(positionBeforeSeek)
                exoPlayer.prepare()
                return
            }

            // The exception message can contain the stream URL. It is mapped to
            // a code and dropped; it is never logged and never shown.
            seekInFlight = false
            _state.value = PlaybackState.Failed(failure)
        }
    }

    /**
     * What the stream turns out to allow, once there is enough of it to say.
     *
     * `isCurrentMediaItemSeekable` is Media3's answer and it is honest about the
     * common case: a panel that streams MPEG-TS under a film's name produces no
     * seek map at all, and this is false. It is **not** the whole answer — an MP4
     * with a valid index reports true whatever the server does about `Range` —
     * which is why the refusal above exists as well.
     */
    private fun onReady() {
        seekInFlight = false

        if (isLive) {
            _progress.update { it.copy(seek = SeekAvailability.LIVE) }
            return
        }

        _progress.update {
            // Once refused, it stays refused for this stream. Asking again would
            // be another request against a server that has already answered.
            if (it.seek == SeekAvailability.REFUSED) {
                it
            } else if (exoPlayer.isCurrentMediaItemSeekable) {
                it.copy(seek = SeekAvailability.AVAILABLE)
            } else {
                it.copy(seek = SeekAvailability.REFUSED)
            }
        }
    }

    /**
     * The progress loop.
     *
     * Polled rather than pushed, because Media3 has no position callback — the
     * position advances with the clock and nothing fires. Twice a second: fast
     * enough that a scrubber does not visibly step, slow enough to be nothing on
     * a TV box's main thread.
     *
     * It runs while a stream is loaded, including while paused: a paused film
     * still has a position, and a bar that froze at the wrong value on pause
     * would be worse than one that does not move.
     */
    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive) {
                val duration = exoPlayer.duration
                _progress.update {
                    it.copy(
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                        // `C.TIME_UNSET` is what a live stream and a not-yet-loaded
                        // file both report. Null rather than zero: a bar that draws
                        // itself full because the end is unknown is a bar that lies.
                        durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0L },
                    )
                }
                delay(TICK_MILLIS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val LIVE_TARGET_OFFSET_MS = 5_000L

        /** Twice a second. See [startTicker]. */
        const val TICK_MILLIS = 500L
    }
}

/** Media3-aware view of [LumoPlayer], visible only inside `core:player`. */
internal interface Media3Player : LumoPlayer {
    val exoPlayer: ExoPlayer
}

private fun PlaybackException.toLumoError(): PlaybackError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> PlaybackError.UNREACHABLE

    // A panel that has run out of allowed simultaneous connections answers with
    // an HTTP error, not with a network failure — which is why US-09 wants this
    // case worded as a subscription limit rather than as an outage.
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
    -> PlaybackError.REFUSED

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    -> PlaybackError.UNPLAYABLE

    else -> PlaybackError.UNKNOWN
}
