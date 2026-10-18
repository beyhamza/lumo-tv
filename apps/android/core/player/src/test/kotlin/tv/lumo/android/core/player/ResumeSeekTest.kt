package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * US-019 / S12-05: a resume asked for right after `play` waits for the stream to say
 * it can be moved through, instead of being dropped while that is still unknown.
 */
class ResumeSeekTest {

    private class FakePlayer : LumoPlayer {
        val seeks = mutableListOf<Long>()
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val progress = MutableStateFlow(PlaybackProgress())
        override val audioTracks: StateFlow<List<AudioTrack>> = MutableStateFlow(emptyList())
        override fun play(request: PlaybackRequest) {}
        override fun selectAudioTrack(id: String) {}
        override fun seekTo(positionMs: Long) { seeks += positionMs }
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun release() {}
    }

    @Test
    fun `the seek waits for the stream to turn out seekable, then lands`() = runTest {
        val player = FakePlayer()

        val applied = async { player.seekWhenSeekable(3_300L) }
        runCurrent()
        assertThat(player.seeks).isEmpty()

        player.progress.value = PlaybackProgress(positionMs = 400L, durationMs = 6_000L, seek = SeekAvailability.AVAILABLE)

        assertThat(applied.await()).isTrue()
        assertThat(player.seeks).containsExactly(3_300L)
    }

    @Test
    fun `a stream that refuses seeking starts where it starts`() = runTest {
        val player = FakePlayer()
        player.progress.value = PlaybackProgress(seek = SeekAvailability.REFUSED)

        assertThat(player.seekWhenSeekable(3_300L)).isFalse()
        assertThat(player.seeks).isEmpty()
    }

    @Test
    fun `zero is the beginning and asks for nothing`() = runTest {
        val player = FakePlayer()

        assertThat(player.seekWhenSeekable(0L)).isFalse()
        assertThat(player.seeks).isEmpty()
    }
}
