package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.common.playback.TrackPreferences

/**
 * S13-02: which tracks a stream starts with, by the rules of
 * `docs/design/0.2.0/player-settings-cases.md` (PS-04, PS-05, PS-06).
 */
class TrackRulesTest {

    // ---- audio ----------------------------------------------------------------

    @Test
    fun `the preferred audio language is chosen when the stream has it`() {
        val tracks = listOf(audio("0:0", "en", isDefault = true, selected = true), audio("0:1", "fr"))

        val initial = chooseInitialTracks(tracks, emptyList(), TrackPreferences(audioLanguage = "fr"))

        assertThat(initial.audioId).isEqualTo("0:1")
    }

    @Test
    fun `an absent audio language falls back to the default track, and the preference is untouched`() {
        val tracks = listOf(audio("0:0", "de"), audio("0:1", "en", isDefault = true))
        val preferences = TrackPreferences(audioLanguage = "fr")

        val initial = chooseInitialTracks(tracks, emptyList(), preferences)

        assertThat(initial.audioId).isEqualTo("0:1")
        // Nothing here writes: the fallback is for this stream only (PS-04).
        assertThat(preferences.audioLanguage).isEqualTo("fr")
    }

    @Test
    fun `without a default flag the fallback is what the player had selected, then the first`() {
        val selected = listOf(audio("0:0", "de"), audio("0:1", "en", selected = true))
        val none = listOf(audio("0:0", "de"), audio("0:1", "en"))

        assertThat(chooseInitialTracks(selected, emptyList(), TrackPreferences(audioLanguage = "fr")).audioId)
            .isEqualTo("0:1")
        assertThat(chooseInitialTracks(none, emptyList(), TrackPreferences(audioLanguage = "fr")).audioId)
            .isEqualTo("0:0")
    }

    @Test
    fun `a matching track this device cannot decode is skipped for a playable one`() {
        val tracks = listOf(audio("0:0", "fr", playable = false), audio("0:1", "en", isDefault = true))

        val initial = chooseInitialTracks(tracks, emptyList(), TrackPreferences(audioLanguage = "fr"))

        assertThat(initial.audioId).isEqualTo("0:1")
    }

    @Test
    fun `no playable audio track is no choice at all, not an invented one`() {
        val tracks = listOf(audio("0:0", "fr", playable = false))

        assertThat(chooseInitialTracks(tracks, emptyList(), TrackPreferences(audioLanguage = "fr")).audioId).isNull()
    }

    @Test
    fun `two tracks of one language prefer the default, then the container's order`() {
        val flagged = listOf(audio("0:0", "fr"), audio("0:1", "fr", isDefault = true))
        val plain = listOf(audio("0:0", "fr"), audio("0:1", "fr"))

        assertThat(chooseInitialTracks(flagged, emptyList(), TrackPreferences(audioLanguage = "fr")).audioId)
            .isEqualTo("0:1")
        assertThat(chooseInitialTracks(plain, emptyList(), TrackPreferences(audioLanguage = "fr")).audioId)
            .isEqualTo("0:0")
    }

    @Test
    fun `fr, fra, fre and fr-CA are one language`() {
        assertThat(sameLanguage("fr", "fra")).isTrue()
        assertThat(sameLanguage("fre", "fr-CA")).isTrue()
        assertThat(sameLanguage("ger", "de")).isTrue()
        assertThat(sameLanguage("fr", "en")).isFalse()
        // "No language" matches nothing, not even itself.
        assertThat(sameLanguage("und", "und")).isFalse()
        assertThat(sameLanguage(null, "fr")).isFalse()
    }

    // ---- subtitles ------------------------------------------------------------

    @Test
    fun `subtitles off selects no text track, a forced one included (PS-05)`() {
        val subtitles = listOf(text("2:0", "fr", forced = true, isDefault = true), text("2:1", "fr"))

        val initial = chooseInitialTracks(emptyList(), subtitles, TrackPreferences(subtitleLanguage = "fr"))

        assertThat(initial.subtitleId).isNull()
        assertThat(initial.subtitleLanguageUnavailable).isFalse()
    }

    @Test
    fun `subtitles on prefer a complete track over a forced one of the same language`() {
        val subtitles = listOf(text("2:0", "fr", forced = true), text("2:1", "fr"))

        val initial = chooseInitialTracks(emptyList(), subtitles, on("fr"))

        assertThat(initial.subtitleId).isEqualTo("2:1")
    }

    @Test
    fun `a forced track is used when it is the only one in the language`() {
        val subtitles = listOf(text("2:0", "fr", forced = true), text("2:1", "en"))

        assertThat(chooseInitialTracks(emptyList(), subtitles, on("fr")).subtitleId).isEqualTo("2:0")
    }

    @Test
    fun `an absent subtitle language turns on nothing else, and says so (PS-04)`() {
        val subtitles = listOf(text("2:0", "en", isDefault = true), text("2:1", "de"))

        val initial = chooseInitialTracks(emptyList(), subtitles, on("fr"))

        assertThat(initial.subtitleId).isNull()
        assertThat(initial.subtitleLanguageUnavailable).isTrue()
    }

    @Test
    fun `a subtitle in the language that cannot be shown is not a match`() {
        val subtitles = listOf(text("2:0", "fr", playable = false))

        val initial = chooseInitialTracks(emptyList(), subtitles, on("fr"))

        assertThat(initial.subtitleId).isNull()
        assertThat(initial.subtitleLanguageUnavailable).isTrue()
    }

    @Test
    fun `the language comes back on the next stream that has it`() {
        val preferences = on("fr")
        val without = chooseInitialTracks(emptyList(), listOf(text("2:0", "en")), preferences)
        val with = chooseInitialTracks(emptyList(), listOf(text("5:0", "en"), text("5:1", "fra")), preferences)

        assertThat(without.subtitleId).isNull()
        assertThat(with.subtitleId).isEqualTo("5:1")
    }

    // ---- what a choice remembers ---------------------------------------------

    @Test
    fun `an audio choice with a language becomes the preference`() {
        val updated = TrackPreferences(audioLanguage = "en").afterAudioChoice(audio("0:1", "fre"))

        assertThat(updated.audioLanguage).isEqualTo("fra")
    }

    @Test
    fun `a track without a language is a choice for this stream only (PS-06)`() {
        val preferences = TrackPreferences(audioLanguage = "en", subtitlesEnabled = false, subtitleLanguage = "en")

        assertThat(preferences.afterAudioChoice(audio("0:1", null))).isEqualTo(preferences)
        assertThat(preferences.afterAudioChoice(audio("0:1", "und"))).isEqualTo(preferences)
        assertThat(preferences.afterSubtitleChoice(text("2:0", null))).isEqualTo(preferences)
    }

    @Test
    fun `turning subtitles off keeps their language for next time`() {
        val updated = on("fr").afterSubtitleChoice(null)

        assertThat(updated.subtitlesEnabled).isFalse()
        assertThat(updated.subtitleLanguage).isEqualTo("fr")
    }

    @Test
    fun `choosing a subtitle turns them on, in its language`() {
        val updated = TrackPreferences(subtitleLanguage = "fr").afterSubtitleChoice(text("2:0", "en"))

        assertThat(updated.subtitlesEnabled).isTrue()
        assertThat(updated.subtitleLanguage).isEqualTo("eng")
    }

    private fun on(language: String) = TrackPreferences(subtitlesEnabled = true, subtitleLanguage = language)

    private fun audio(
        id: String,
        language: String?,
        playable: Boolean = true,
        isDefault: Boolean = false,
        selected: Boolean = false,
    ) = AudioTrack(
        id = id,
        language = language,
        label = null,
        mimeType = null,
        channelCount = null,
        selected = selected,
        playable = playable,
        isDefault = isDefault,
    )

    private fun text(
        id: String,
        language: String?,
        forced: Boolean = false,
        isDefault: Boolean = false,
        playable: Boolean = true,
    ) = SubtitleTrack(
        id = id,
        language = language,
        label = null,
        mimeType = "text/vtt",
        selected = false,
        playable = playable,
        forced = forced,
        isDefault = isDefault,
    )
}
