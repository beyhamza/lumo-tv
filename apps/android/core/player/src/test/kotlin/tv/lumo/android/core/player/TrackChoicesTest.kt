package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
        override val trackChangeFailures = MutableSharedFlow<TrackKind>(extraBufferCapacity = 1)
        override fun settleTrackChange(kind: TrackKind) { calls += "settle:$kind" }
        override fun revertTrackChange(kind: TrackKind) { calls += "revert:$kind" }
        override fun seekTo(positionMs: Long) {}
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun release() {}
    }

    private val options = TrackOptions(
        known = true,
        audio = listOf(audio("0:0", "en", selected = true), audio("0:1", "fr"), audio("0:2", null)),
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

    // ---- PS-07: changing, confirmed, failed -------------------------------------

    @Test
    fun `a choice is pending until the player shows it, then settled`() = runTest {
        val (player, choices) = setUp(this) { true }
        choices.opened(TrackPreferences.NONE)

        choices.selectAudio("0:1")
        runCurrent()
        assertThat(choices.changes.value[TrackKind.AUDIO])
            .isEqualTo(TrackChange.Pending(TrackKind.AUDIO, "0:1", "0:0", testScheduler.currentTime))

        advanceTimeBy(2_000)
        player.trackOptions.value = options.copy(
            audio = listOf(audio("0:0", "en", selected = false), audio("0:1", "fr", selected = true), audio("0:2", null)),
        )
        runCurrent()

        assertThat(choices.changes.value).isEmpty()
        assertThat(player.calls).containsExactly("audio:0:1", "settle:AUDIO").inOrder()
    }

    @Test
    fun `no answer in time fails, the player puts the previous selection back, and nothing loops`() = runTest {
        val (player, choices) = setUp(this) { true }
        choices.opened(TrackPreferences.NONE)

        choices.selectSubtitle("2:0")
        advanceTimeBy(TrackChangeRules.TIMEOUT_MS - 1)
        runCurrent()
        assertThat(choices.changes.value[TrackKind.SUBTITLES]).isInstanceOf(TrackChange.Pending::class.java)

        advanceTimeBy(2)
        runCurrent()
        assertThat(choices.changes.value[TrackKind.SUBTITLES])
            .isEqualTo(TrackChange.Failed(TrackKind.SUBTITLES, target = "2:0", previous = null))
        assertThat(player.calls).containsExactly("text:2:0", "revert:SUBTITLES").inOrder()

        // An hour later: still one failure, no second attempt on its own.
        advanceTimeBy(3_600_000)
        runCurrent()
        assertThat(player.calls).containsExactly("text:2:0", "revert:SUBTITLES").inOrder()
    }

    @Test
    fun `a player error during the change fails it, and Retry asks once more`() = runTest {
        val (player, choices) = setUp(this) { true }
        choices.opened(TrackPreferences.NONE)

        choices.selectQuality("1:1")
        runCurrent()
        player.trackChangeFailures.emit(TrackKind.QUALITY)
        runCurrent()
        assertThat(choices.changes.value[TrackKind.QUALITY])
            .isEqualTo(TrackChange.Failed(TrackKind.QUALITY, target = "1:1", previous = null))
        // The playback itself was never failed by this.
        assertThat(player.state.value).isEqualTo(PlaybackState.Idle)

        choices.retry(TrackKind.QUALITY)
        runCurrent()
        assertThat(choices.changes.value[TrackKind.QUALITY]).isInstanceOf(TrackChange.Pending::class.java)
        assertThat(player.calls.filter { it.startsWith("video:") }).containsExactly("video:1:1", "video:1:1")
    }

    @Test
    fun `a new stream forgets a pending change`() = runTest {
        val (_, choices) = setUp(this) { true }
        choices.opened(TrackPreferences.NONE)
        choices.selectAudio("0:1")
        runCurrent()

        choices.opened(TrackPreferences.NONE)
        assertThat(choices.changes.value).isEmpty()
    }

    private fun setUp(scope: TestScope, save: suspend (TrackPreferences) -> Boolean): Pair<FakePlayer, TrackChoices> {
        val player = FakePlayer(options)
        return player to TrackChoices(player, scope, clock = { scope.testScheduler.currentTime }, save = save)
    }

    private fun audio(id: String, language: String?, selected: Boolean = false) = AudioTrack(
        id = id, language = language, label = null, mimeType = null, channelCount = null,
        selected = selected, playable = true,
    )

    private fun text(id: String, language: String?) = SubtitleTrack(
        id = id, language = language, label = null, mimeType = null, selected = false, playable = true,
    )

    private fun quality(id: String) = VideoQuality(
        id = id, width = null, height = 720, bitrate = null, selected = false, playable = true,
    )
}
