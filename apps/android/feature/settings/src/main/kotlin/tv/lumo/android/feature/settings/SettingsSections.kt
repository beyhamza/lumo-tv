package tv.lumo.android.feature.settings

import androidx.annotation.StringRes

/**
 * The five sections of Settings, in US-025's validated order, and what opening
 * each one does on each surface (S13-04, docs/design/0.2.0/settings.md).
 *
 * The same order as the website's `SETTINGS_ENTRIES`: Account and devices, My
 * sources, Playback, Application, Help and information.
 *
 * Pure, so the routing is pinned by `SettingsSectionsTest` without a screen.
 */
enum class SettingsSection(val key: String, @StringRes val titleRes: Int) {
    Account("account", R.string.feature_settings_menu_account),
    Sources("sources", R.string.feature_settings_menu_sources),
    Playback("playback", R.string.feature_settings_menu_playback),
    Application("application", R.string.feature_settings_menu_application),
    About("about", R.string.feature_settings_menu_about),
}

enum class SettingsSurface { Mobile, Tv }

/** What choosing a section does. */
sealed interface SettingsTarget {

    /** The section is drawn inside Settings. */
    data class Panel(val section: SettingsSection) : SettingsTarget

    /**
     * "My sources" is not a second sources screen: it opens US-024's, as on the
     * website. Only the phone leaves Settings for it — the television keeps its
     * read-only list with the way in as a row, so the menu can be browsed by focus.
     */
    data object OpenSources : SettingsTarget
}

/** One row of the Playback section. Which ones exist depends on the surface. */
enum class PlaybackRow { AudioLanguage, Subtitles, SubtitleLanguage, AutoplayNext, MobileData }

object SettingsSections {

    /** Where a television's Settings opens, and the phone's first row. */
    val DEFAULT: SettingsSection = SettingsSection.Account

    fun targetOf(section: SettingsSection, surface: SettingsSurface): SettingsTarget =
        if (section == SettingsSection.Sources && surface == SettingsSurface.Mobile) {
            SettingsTarget.OpenSources
        } else {
            SettingsTarget.Panel(section)
        }

    /**
     * A saved key back to its section — after a recreation (a language change
     * recreates the activity) or a return from "My sources". An unknown key is
     * nothing: the phone shows its list, the television its default.
     */
    fun fromKey(key: String?): SettingsSection? = SettingsSection.entries.firstOrNull { it.key == key }

    /**
     * Playback on mobile data exists on the phone only: a television is never
     * gated by the network (S13-05). Everything else is the same on both.
     */
    fun playbackRows(surface: SettingsSurface): List<PlaybackRow> =
        if (surface == SettingsSurface.Mobile) {
            PlaybackRow.entries
        } else {
            PlaybackRow.entries - PlaybackRow.MobileData
        }

    /**
     * The languages offered for audio and subtitles — the website's list
     * (`PREFERENCE_LANGUAGES`), so both surfaces offer the same choices. A stored
     * language outside it (written by a track picked in the player) stays first,
     * so the current value is always one of the choices.
     */
    val PREFERENCE_LANGUAGES: List<String> =
        listOf("fr", "en", "ar", "de", "es", "it", "nl", "pl", "pt", "ro", "ru", "tr")

    fun languageChoices(current: String?): List<String> =
        if (current == null || PREFERENCE_LANGUAGES.any { sameCode(it, current) }) {
            PREFERENCE_LANGUAGES
        } else {
            listOf(current) + PREFERENCE_LANGUAGES
        }

    /** Whether [choice] is the stored [current] — `fr` and `fra` are one language. */
    fun isCurrentLanguage(choice: String, current: String?): Boolean = current != null && sameCode(choice, current)

    private fun sameCode(a: String, b: String): Boolean = iso3(a) == iso3(b)

    private fun iso3(code: String): String {
        val locale = java.util.Locale.forLanguageTag(code.trim())
        return try {
            locale.isO3Language.ifEmpty { code.lowercase() }
        } catch (_: java.util.MissingResourceException) {
            code.lowercase()
        }
    }
}
