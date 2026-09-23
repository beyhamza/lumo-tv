package tv.lumo.android.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The guides link, derived from the pairing page (US-025, S8-06).
 *
 * One variable names the website, and the guides live under a locale prefix
 * there (`apps/web/src/i18n/routing.ts`, `localePrefix: "always"`): a bare
 * `/guides` is a redirect at best. What is pinned is that the derivation keeps
 * the host — a LAN stack included — and never sends a language the site does
 * not serve.
 */
class SettingsLinksTest {

    @Test
    fun `the guides live on the site the pairing page names, under the language`() {
        assertThat(SettingsLinks.guidesUrlOf("https://lumo.tv/activate", "fr"))
            .isEqualTo("https://lumo.tv/fr/guides")
        assertThat(SettingsLinks.guidesUrlOf("https://lumo.tv/activate", "en"))
            .isEqualTo("https://lumo.tv/en/guides")
    }

    @Test
    fun `a local stack keeps its host and port, whatever the pairing path`() {
        assertThat(SettingsLinks.guidesUrlOf("http://10.0.2.2:3000/activate/", "fr"))
            .isEqualTo("http://10.0.2.2:3000/fr/guides")
        assertThat(SettingsLinks.guidesUrlOf("  https://lumo.tv  ", "fr"))
            .isEqualTo("https://lumo.tv/fr/guides")
    }

    @Test
    fun `a language the site does not serve reads the English guides`() {
        // A German phone reads the English interface (AGENTS.md §4); the site
        // would answer `/de/guides` with a 404, which looks like no guides.
        assertThat(SettingsLinks.guidesUrlOf("https://lumo.tv/activate", "de"))
            .isEqualTo("https://lumo.tv/en/guides")
        assertThat(SettingsLinks.guidesUrlOf("https://lumo.tv/activate", "fr-CA"))
            .isEqualTo("https://lumo.tv/fr/guides")
        assertThat(SettingsLinks.guidesUrlOf("https://lumo.tv/activate", ""))
            .isEqualTo("https://lumo.tv/en/guides")
    }

    @Test
    fun `no origin, no link — the row is not drawn rather than dead`() {
        assertThat(SettingsLinks.guidesUrlOf("", "fr")).isNull()
        assertThat(SettingsLinks.guidesUrlOf("lumo.tv/activate", "fr")).isNull()
    }

    @Test
    fun `a row prints the address without its scheme`() {
        assertThat(SettingsLinks.label("https://lumo.tv/fr/guides/")).isEqualTo("lumo.tv/fr/guides")
        assertThat(SettingsLinks.label("http://10.0.2.2:3000/activate")).isEqualTo("10.0.2.2:3000/activate")
    }
}
