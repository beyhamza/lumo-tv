package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

/**
 * S10B-03: leaving the app pauses what plays; coming back resumes a channel at
 * its edge and leaves a film paused where it was.
 */
class BackgroundPlaybackTest {

    private class RecordingPlayer(initial: PlaybackState) : LumoPlayer {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow(initial)
        override val progress: StateFlow<PlaybackProgress> = MutableStateFlow(PlaybackProgress())
        override val audioTracks: StateFlow<List<AudioTrack>> = MutableStateFlow(emptyList())
        override fun play(request: PlaybackRequest) { calls += "play" }
        override fun selectAudioTrack(id: String) {}
        override fun seekTo(positionMs: Long) { calls += "seek" }
        override fun pause() { calls += "pause"; state.value = PlaybackState.Paused }
        override fun resume() { calls += "resume" }
        override fun resumeAtLiveEdge() { calls += "resumeAtLiveEdge" }
        override fun stop() { calls += "stop" }
        override fun release() {}
    }

    @Test
    fun `a playing channel is paused on leaving and resumed at the edge on return`() {
        val player = RecordingPlayer(PlaybackState.Playing(title = null))
        val background = BackgroundPlayback(player)

        assertThat(background.onBackground()).isTrue()
        background.onForeground(live = true)

        assertThat(player.calls).containsExactly("pause", "resumeAtLiveEdge").inOrder()
    }

    @Test
    fun `a playing film is paused on leaving and stays paused on return`() {
        val player = RecordingPlayer(PlaybackState.Playing(title = "Film"))
        val background = BackgroundPlayback(player)

        background.onBackground()
        background.onForeground(live = false)

        assertThat(player.calls).containsExactly("pause")
    }

    @Test
    fun `a stream still loading is paused too, or it would start behind the launcher`() {
        val player = RecordingPlayer(PlaybackState.Buffering)

        assertThat(BackgroundPlayback(player).onBackground()).isTrue()
        assertThat(player.calls).containsExactly("pause")
    }

    @Test
    fun `what the viewer paused is not resumed by coming back`() {
        val player = RecordingPlayer(PlaybackState.Paused)
        val background = BackgroundPlayback(player)

        assertThat(background.onBackground()).isFalse()
        background.onForeground(live = true)

        assertThat(player.calls).isEmpty()
    }

    @Test
    fun `opening the screen is not a return`() {
        val player = RecordingPlayer(PlaybackState.Idle)

        BackgroundPlayback(player).onForeground(live = true)

        assertThat(player.calls).isEmpty()
    }

    @Test
    fun `a stream replaced while away is not resumed by the old pause`() {
        val player = RecordingPlayer(PlaybackState.Playing(title = null))
        val background = BackgroundPlayback(player)

        background.onBackground()
        background.reset()
        background.onForeground(live = true)

        assertThat(player.calls).containsExactly("pause")
    }
}
