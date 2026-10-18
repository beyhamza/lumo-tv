package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.common.playback.TrackPreferences

/**
 * S13-02: what a choice in the options menu does to the player and to the
 * device's preference — including the write that fails (PS-07) and the quality
 * that is never remembered.
 */
class TrackChoicesTest {

    private class FakePlayer(options: TrackOptions) : LumoPlayer {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val progress = MutableStateFlow(PlaybackProgress())
        override val audioTracks: StateFlow<List<AudioTrack>> = MutableStateFlow(options.audio)
        override val trackOptions = MutableStateFlow(options)
        override fun play(request: PlaybackRequest) {}
        override fun selectAudioTrack(id: String) { calls += "audio:$id" }
        override fun selectSubtitleTrack(id: String?) { calls += "text:$id" }
        override fun selectVideoQuality(id: String?) { calls += "video:$id" }
        override fun seekTo(positionMs: Long) {}
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun release() {}
    }

    private val options = TrackOptions(
        known = true,
        audio = listOf(audio("0:0", "en"), audio("0:1", "fr"), audio("0:2", null)),
        subtitles = listOf(text("2:0", "fr"), text("2:1", null)),
        qualities = listOf(quality("1:0"), quality("1:1")),
    )

    @Test
    fun `an audio choice plays now and is remembered as a language`() = runTest {
        val saved = mutableListOf<TrackPreferences>()
        val (player, choices) = setUp(this) { saved += it; true }
        choices.opened(TrackPreferences(audioLanguage = "en"))

        choices.selectAudio("0:1")
        runCurrent()

        assertThat(player.calls).containsExactly("audio:0:1")
        assertThat(saved.single().audioLanguage).isEqualTo("fra")
        assertThat(choices.notSaved.value).isFalse()
    }

    @Test
    fun `a track without a language changes the stream and nothing else`() = runTest {
        val saved = mutableListOf<TrackPreferences>()
        val (player, choices) = setUp(this) { saved += it; true }
        choices.opened(TrackPreferences(audioLanguage = "en"))

        choices.selectAudio("0:2")
        choices.selectSubtitle("2:1")
        runCurrent()

        assertThat(player.calls).containsExactly("audio:0:2", "text:2:1")
        assertThat(saved).isEmpty()
    }

    @Test
    fun `subtitles off are remembered as off, keeping the language`() = runTest {
        val saved = mutableListOf<TrackPreferences>()
        val (player, choices) = setUp(this) { saved += it; true }
        choices.opened(TrackPreferences(subtitlesEnabled = true, subtitleLanguage = "fra"))

        choices.selectSubtitle(null)
        runCurrent()

        assertThat(player.calls).containsExactly("text:null")
        assertThat(saved.single()).isEqualTo(TrackPreferences(subtitlesEnabled = false, subtitleLanguage = "fra"))
    }

    @Test
    fun `a quality is for this stream only and never remembered`() = runTest {
        val saved = mutableListOf<TrackPreferences>()
        val (player, choices) = setUp(this) { saved += it; true }
        choices.opened(TrackPreferences.NONE)

        choices.selectQuality("1:1")
        choices.selectQuality(null)
        runCurrent()

        assertThat(player.calls).containsExactly("video:1:1", "video:null")
        assertThat(saved).isEmpty()
    }

    @Test
    fun `a write that fails is applied and said not saved (PS-07)`() = runTest {
        val (player, choices) = setUp(this) { false }
        choices.opened(TrackPreferences(audioLanguage = "en"))

        choices.selectAudio("0:1")
        runCurrent()

        assertThat(player.calls).containsExactly("audio:0:1")
        assertThat(choices.notSaved.value).isTrue()

        // The next stream starts clean.
        choices.opened(TrackPreferences(audioLanguage = "en"))
        assertThat(choices.notSaved.value).isFalse()
    }

    @Test
    fun `an id the stream does not carry does nothing`() = runTest {
        val saved = mutableListOf<TrackPreferences>()
        val (player, choices) = setUp(this) { saved += it; true }
        choices.opened(TrackPreferences.NONE)

        // An id from another media (PS-06): it resolves to nothing here.
        choices.selectAudio("7:3")
        choices.selectSubtitle("7:3")
        runCurrent()

        assertThat(player.calls).isEmpty()
        assertThat(saved).isEmpty()
    }

    private fun setUp(scope: TestScope, save: suspend (TrackPreferences) -> Boolean): Pair<FakePlayer, TrackChoices> {
        val player = FakePlayer(options)
        return player to TrackChoices(player, scope, save)
    }

    private fun audio(id: String, language: String?) = AudioTrack(
        id = id, language = language, label = null, mimeType = null, channelCount = null,
        selected = false, playable = true,
    )

    private fun text(id: String, language: String?) = SubtitleTrack(
        id = id, language = language, label = null, mimeType = null, selected = false, playable = true,
    )

    private fun quality(id: String) = VideoQuality(
        id = id, width = null, height = 720, bitrate = null, selected = false, playable = true,
    )
}
