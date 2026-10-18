package tv.lumo.android.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import tv.lumo.android.core.common.locale.InterfaceLanguage

/**
 * The language the interface is drawn in right now, as the product names it:
 * `fr` or `en`.
 *
 * Read from the configuration and nowhere else: `Locale.getDefault()` is not
 * observable, and a composable that reads it does not recompose when the
 * language changes (lint `NonObservableLocale`). The configuration already
 * carries the per-app choice, so the explicit choice and the system language
 * resolve to the same answer here.
 */
@Composable
internal fun currentInterfaceLanguage(): String {
    val locales = LocalConfiguration.current.locales
    return InterfaceLanguage.effective(explicit = null, system = if (locales.isEmpty) null else locales[0].language)
}

/** A playback language named in the interface's language — "Anglais" in French. Null code: not set. */
@Composable
internal fun playbackLanguageName(code: String?): String? {
    if (code.isNullOrBlank()) return null
    val locales = LocalConfiguration.current.locales
    val ui = if (locales.isEmpty) Locale.ENGLISH else locales[0]
    val name = Locale.forLanguageTag(code).getDisplayLanguage(ui).ifBlank { code }
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ui) else it.toString() }
}

/** The activity behind a composable's context — the one a language change recreates. */
internal fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
