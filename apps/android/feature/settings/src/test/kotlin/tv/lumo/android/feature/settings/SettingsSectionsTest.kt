package tv.lumo.android.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** S13-04: the five sections, their order, and where each one leads on each surface. */
class SettingsSectionsTest {

    @Test
    fun `five sections in the website's order`() {
        assertThat(SettingsSection.entries.map { it.key })
            .containsExactly("account", "sources", "playback", "application", "about")
            .inOrder()
        assertThat(SettingsSections.DEFAULT).isEqualTo(SettingsSection.Account)
    }

    @Test
    fun `my sources opens US-024's screen on the phone and a panel on the television`() {
        assertThat(SettingsSections.targetOf(SettingsSection.Sources, SettingsSurface.Mobile))
            .isEqualTo(SettingsTarget.OpenSources)
        assertThat(SettingsSections.targetOf(SettingsSection.Sources, SettingsSurface.Tv))
            .isEqualTo(SettingsTarget.Panel(SettingsSection.Sources))
    }

    @Test
    fun `every other section is drawn inside Settings on both surfaces`() {
        val others = SettingsSection.entries - SettingsSection.Sources
        for (surface in SettingsSurface.entries) {
            for (section in others) {
                assertThat(SettingsSections.targetOf(section, surface)).isEqualTo(SettingsTarget.Panel(section))
            }
        }
    }

    @Test
    fun `a saved key comes back to its section, an unknown one to nothing`() {
        SettingsSection.entries.forEach { assertThat(SettingsSections.fromKey(it.key)).isEqualTo(it) }
        assertThat(SettingsSections.fromKey("billing")).isNull()
        assertThat(SettingsSections.fromKey("Account")).isNull()
        assertThat(SettingsSections.fromKey(null)).isNull()
    }

    @Test
    fun `mobile data is a phone row only`() {
        assertThat(SettingsSections.playbackRows(SettingsSurface.Mobile)).contains(PlaybackRow.MobileData)
        assertThat(SettingsSections.playbackRows(SettingsSurface.Tv)).containsExactly(
            PlaybackRow.AudioLanguage,
            PlaybackRow.Subtitles,
            PlaybackRow.SubtitleLanguage,
            PlaybackRow.AutoplayNext,
        ).inOrder()
    }

    @Test
    fun `the stored language is always among the choices, never twice`() {
        assertThat(SettingsSections.languageChoices("fr")).isEqualTo(SettingsSections.PREFERENCE_LANGUAGES)
        // Written as ISO 639-2 by a track picked in the player: the same French.
        assertThat(SettingsSections.languageChoices("fra")).isEqualTo(SettingsSections.PREFERENCE_LANGUAGES)
        assertThat(SettingsSections.isCurrentLanguage("fr", "fra")).isTrue()
        assertThat(SettingsSections.isCurrentLanguage("en", "fra")).isFalse()
        // A language outside the list comes first, so it can be seen ticked.
        assertThat(SettingsSections.languageChoices("ja").first()).isEqualTo("ja")
        assertThat(SettingsSections.languageChoices(null)).isEqualTo(SettingsSections.PREFERENCE_LANGUAGES)
        assertThat(SettingsSections.isCurrentLanguage("fr", null)).isFalse()
    }
}
