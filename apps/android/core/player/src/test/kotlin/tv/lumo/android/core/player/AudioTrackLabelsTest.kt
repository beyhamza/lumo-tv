package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import tv.lumo.android.core.designsystem.component.LumoAudioTrackChoice
import tv.lumo.android.core.player.ui.asChoices

/**
 * How an audio track gets its name on screen.
 *
 * <h2>What is actually being guarded</h2>
 *
 * Not arithmetic — there is none. The input is whatever a stranger's muxer
 * wrote, and every case below is one that occurs in the wild: a three-letter
 * language code the platform cannot resolve, a label where the language should
 * be, a track that declares nothing at all. None of them may produce a blank
 * row, and none of them may produce an invented language.
 */
class AudioTrackLabelsTest {

    @Test
    fun `a declared language is written in the reader's own language`() {
        assertThat(track(language = "fr").spokenName(Locale.FRENCH)).isEqualTo("français")
        assertThat(track(language = "fr").spokenName(Locale.ENGLISH)).isEqualTo("French")
    }

    @Test
    fun `a code the platform cannot resolve is shown as it was written`() {
        // `qaa` is the reserved code for "a language with no code", and panels do
        // send it. Returning it as it stands tells somebody more than nothing
        // does, and inventing a language for it would be worse than either.
        assertThat(track(language = "qaa").spokenName(Locale.FRENCH)).isEqualTo("qaa")
    }

    @Test
    fun `the container's own label wins when there is no language`() {
        // The more useful of the two in practice: muxers write "VF", "VOSTFR",
        // "Commentary" there, and none of that is expressible as a language code.
        assertThat(track(language = null, label = "VOSTFR").spokenName()).isEqualTo("VOSTFR")
    }

    @Test
    fun `a track that declares nothing has no name of its own`() {
        // Null rather than a made-up one. What to call it is the caller's
        // sentence, because "Track 2" is a position on a screen and this file
        // does not know about screens.
        assertThat(track(language = null, label = null).spokenName()).isNull()
        assertThat(track(language = "  ", label = "").spokenName()).isNull()
    }

    @Test
    fun `a codec is named by its trade name, and an unknown one is not named at all`() {
        assertThat(track(mimeType = "audio/ac3").codecName()).isEqualTo("Dolby Digital")
        assertThat(track(mimeType = "audio/eac3").codecName()).isEqualTo("Dolby Digital Plus")
        assertThat(track(mimeType = "audio/mp4a-latm").codecName()).isEqualTo("AAC")
        // Nothing rather than a raw mime type: `audio/x-whatever` beside a
        // language is noise to everyone who did not write it.
        assertThat(track(mimeType = "audio/x-whatever").codecName()).isNull()
        assertThat(track(mimeType = null).codecName()).isNull()
    }

    @Test
    fun `only the layouts people have a word for are named`() {
        assertThat(track(channelCount = 6).channelLayout()).isEqualTo("5.1")
        assertThat(track(channelCount = 8).channelLayout()).isEqualTo("7.1")
        // Two channels is "stereo", which is the caller's word and not this
        // module's; eleven is not anything, and "11 channels" is a line nobody
        // has ever wanted to read.
        assertThat(track(channelCount = 2).channelLayout()).isNull()
        assertThat(track(channelCount = 11).channelLayout()).isNull()
    }

    @Test
    fun `a row carries the language and the codec`() {
        val choices = listOf(track(language = "fr", mimeType = "audio/ac3", channelCount = 6))
            .asChoices(unnamed = { "Piste $it" }, unsupported = "×", locale = Locale.FRENCH)

        assertThat(choices.single()).isEqualTo(
            LumoAudioTrackChoice(
                id = "0:0",
                label = "français",
                detail = "Dolby Digital 5.1",
                selected = false,
                enabled = true,
            ),
        )
    }

    @Test
    fun `a nameless track is numbered by its position, counted from one`() {
        val choices = listOf(
            track(id = "0:0", language = "en"),
            track(id = "0:1", language = null, label = null),
        ).asChoices(unnamed = { "Piste $it" }, unsupported = "×")

        assertThat(choices[1].label).isEqualTo("Piste 2")
    }

    @Test
    fun `an undecodable track says so instead of naming its codec`() {
        // The row replaces the codec rather than joining it. "Dolby Digital ·
        // this device cannot decode it" reads as a contradiction, and the second
        // half is the half somebody needs.
        val choices = listOf(track(mimeType = "audio/ac3", channelCount = 6, playable = false))
            .asChoices(unnamed = { "Piste $it" }, unsupported = "Non décodable ici")

        assertThat(choices.single().detail).isEqualTo("Non décodable ici")
        // Still a row, and still not selectable. Hiding it would be the more
        // comfortable choice and the wrong one: a track that is simply absent is
        // the mystery this feature exists to end.
        assertThat(choices.single().enabled).isFalse()
    }

    @Test
    fun `a track nothing is known about draws no second line`() {
        val choices = listOf(track(language = "fr", mimeType = null, channelCount = null))
            .asChoices(unnamed = { "Piste $it" }, unsupported = "×")

        assertThat(choices.single().detail).isNull()
    }

    private fun track(
        id: String = "0:0",
        language: String? = null,
        label: String? = null,
        mimeType: String? = null,
        channelCount: Int? = null,
        selected: Boolean = false,
        playable: Boolean = true,
    ) = AudioTrack(
        id = id,
        language = language,
        label = label,
        mimeType = mimeType,
        channelCount = channelCount,
        selected = selected,
        playable = playable,
    )
}
