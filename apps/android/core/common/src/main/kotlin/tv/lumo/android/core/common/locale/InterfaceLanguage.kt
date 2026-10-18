package tv.lumo.android.core.common.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The interface language of this installation: French or English (US-025, S13-04, Q6).
 *
 * <h2>Per installation, never the account's</h2>
 *
 * Q6: the choice belongs to this installation, survives a sign-out, and does not
 * call `PATCH /me` — the account's locale stays what it is. It is also distinct
 * from the playback languages, which were seeded once and do not follow it (PS-01).
 *
 * <h2>The platform's per-app language, which persists itself</h2>
 *
 * From Android 13 the choice is handed to `LocaleManager`: the system stores it,
 * shows it in the system's app-language settings (`locales_config.xml`) and
 * recreates the activity in the new language. The project has no AppCompat, so
 * below 13 the same contract is kept by hand: the choice is written to a private
 * preference, applied to each activity's base context ([wrap]) and the activity
 * is recreated.
 *
 * <h2>Without an explicit choice</h2>
 *
 * The system language when the product speaks it, English otherwise (Q6) —
 * [effective] is that rule, pure and tested.
 */
object InterfaceLanguage {

    /** The two languages the product ships, in the order Settings lists them. */
    val SUPPORTED: List<String> = listOf("fr", "en")

    /** The language a screen is shown in: the explicit choice, else a supported system language, else English. */
    fun effective(explicit: String?, system: String?): String {
        val chosen = explicit?.let(::supportedOrNull)
        return chosen ?: system?.let(::supportedOrNull) ?: "en"
    }

    /** `fr-CA` → `fr`; anything the product does not ship → null. */
    fun supportedOrNull(tag: String): String? =
        tag.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_').takeIf { it in SUPPORTED }

    /** A language named in itself — "Français", "English" — so somebody who cannot read the current one finds theirs. */
    fun nativeName(language: String): String {
        val locale = Locale.forLanguageTag(language)
        return locale.getDisplayLanguage(locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** The explicit choice made in Settings, or null when the system language applies. */
    fun explicit(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) null else supportedOrNull(locales[0].toLanguageTag())
        } else {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        }

    /**
     * Switches the interface to [language] and redraws [activity] in it.
     *
     * On Android 13+ the system recreates the activity itself; below, it is
     * recreated here after the choice is written.
     */
    fun choose(activity: Activity, language: String) {
        val supported = supportedOrNull(language) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(supported)
        } else {
            activity.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY, supported).apply()
            activity.recreate()
        }
    }

    /**
     * An activity's base context in the chosen language, below Android 13.
     * From 13 the platform does it and [base] is returned untouched.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val chosen = base.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(chosen)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(configuration)
    }

    private const val FILE = "interface_language"
    private const val KEY = "language"
}
