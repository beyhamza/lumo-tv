package tv.lumo.android.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var currentTitle: String? = null

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
        _state.value = PlaybackState.Buffering

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
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun resume() {
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentTitle = null
        _state.value = PlaybackState.Idle
    }

    override fun release() {
        exoPlayer.release()
        _state.value = PlaybackState.Idle
    }

    private inner class StateListener : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
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

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_state.value is PlaybackState.Failed) return
            _state.value = if (isPlaying) PlaybackState.Playing(currentTitle) else PlaybackState.Paused
        }

        override fun onPlayerError(error: PlaybackException) {
            // The exception message can contain the stream URL. It is mapped to
            // a code and dropped; it is never logged and never shown.
            _state.value = PlaybackState.Failed(error.toLumoError())
        }
    }

    private companion object {
        const val LIVE_TARGET_OFFSET_MS = 5_000L
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
