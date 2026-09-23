package tv.lumo.android.feature.settings

/**
 * The web pages the settings screens point at, derived from one variable.
 *
 * <h2>Why the guides come from the activation URL</h2>
 *
 * `LUMO_ACTIVATION_URL` is the one address of the website this build knows —
 * `https://lumo.tv/activate` in production, a LAN host on a developer's stack —
 * and the guides live on that same site under a locale prefix
 * (`apps/web/src/i18n/routing.ts`: `localePrefix: "always"`, so `/fr/guides` and
 * `/en/guides` and never a bare `/guides`). Deriving the second address from the
 * first is what stops a local build from pairing a television against one host
 * and opening guides on another.
 *
 * <h2>Only the two languages the product ships</h2>
 *
 * A German phone reads the English interface (AGENTS.md §4), so it is sent to
 * the English guides: the website would answer a `/de/guides` request with a
 * 404, which on a phone looks like the guides do not exist.
 *
 * Pure and testable, because it is the sort of string surgery that is right
 * in production and wrong in the first test that passes a trailing slash.
 */
object SettingsLinks {

    /**
     * `https://lumo.tv/fr/guides` from `https://lumo.tv/activate` and `fr`.
     *
     * @param language the interface language's ISO code, as `Locale.language`
     * spells it. Anything but `fr` is English.
     * @return the guides page, or null when [activationUrl] carries no origin
     * to build on — a blank build value, or one with no scheme. A row that leads
     * nowhere is not drawn (US-025: nothing that is not delivered is shown).
     */
    fun guidesUrlOf(activationUrl: String, language: String): String? {
        val origin = ORIGIN.find(activationUrl.trim())?.value ?: return null
        val locale = if (language.lowercase().startsWith("fr")) "fr" else "en"
        return "$origin/$locale/guides"
    }

    /** `lumo.tv/fr/guides`, as a row prints it — the address without its scheme. */
    fun label(url: String): String =
        url.removePrefix("https://").removePrefix("http://").trimEnd('/')

    /** Scheme, host and optional port; the path is the activation page's own. */
    private val ORIGIN = Regex("^https?://[^/\\s]+")
}
