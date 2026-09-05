package tv.lumo.android.core.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The one ordering that matters, and the plain mappings around it.
 *
 * ExoPlayer follows `onPlayerError` with `onPlaybackStateChanged(STATE_IDLE)`.
 * Seen on the television emulator: a channel whose panel answered 403 left a
 * black screen with no message and the focus on the video surface, because the
 * idle report had overwritten the failure a few milliseconds after it was set.
 */
class PlaybackStateTransitionsTest {

    @Test
    fun `idle after a failure keeps the failure`() {
        val failed = PlaybackState.Failed(PlaybackError.REFUSED)

        val next = failed.afterExoPlayerState(Player.STATE_IDLE, playWhenReady = true, title = "x")

        assertThat(next).isEqualTo(failed)
    }

    @Test
    fun `idle after anything else is idle`() {
        listOf(
            PlaybackState.Idle,
            PlaybackState.Buffering,
            PlaybackState.Playing("x"),
            PlaybackState.Paused,
            PlaybackState.Ended,
        ).forEach { current ->
            assertThat(current.afterExoPlayerState(Player.STATE_IDLE, playWhenReady = false, title = null))
                .isEqualTo(PlaybackState.Idle)
        }
    }

    @Test
    fun `a failure is replaced once the player moves again`() {
        // A retry calls play(), which sets Buffering itself; but if the engine
        // recovers on its own, its own progress must still be reflected.
        val failed = PlaybackState.Failed(PlaybackError.UNREACHABLE)

        assertThat(failed.afterExoPlayerState(Player.STATE_BUFFERING, playWhenReady = true, title = "x"))
            .isEqualTo(PlaybackState.Buffering)
        assertThat(failed.afterExoPlayerState(Player.STATE_READY, playWhenReady = true, title = "x"))
            .isEqualTo(PlaybackState.Playing("x"))
    }

    @Test
    fun `ready is playing or paused depending on playWhenReady`() {
        assertThat(PlaybackState.Buffering.afterExoPlayerState(Player.STATE_READY, playWhenReady = true, title = "x"))
            .isEqualTo(PlaybackState.Playing("x"))
        assertThat(PlaybackState.Buffering.afterExoPlayerState(Player.STATE_READY, playWhenReady = false, title = "x"))
            .isEqualTo(PlaybackState.Paused)
    }

    @Test
    fun `ended is ended`() {
        assertThat(PlaybackState.Playing("x").afterExoPlayerState(Player.STATE_ENDED, playWhenReady = true, title = "x"))
            .isEqualTo(PlaybackState.Ended)
    }
}
