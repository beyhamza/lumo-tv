package tv.lumo.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The phone and tablet theme (`tv.lumo.android`).
 *
 * Material 3, with the Lumo palette mapped onto its roles. Dynamic colour is
 * deliberately not used: a video player's chrome has to stay legible over
 * arbitrary content, and a wallpaper-derived scheme can put a pale accent on a
 * pale frame.
 */
@Composable
fun LumoMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = LumoColors.Accent,
            onPrimary = LumoColors.OnAccent,
            background = LumoColors.Ink,
            onBackground = LumoColors.OnDark,
            surface = LumoColors.Surface,
            onSurface = LumoColors.OnDark,
            surfaceVariant = LumoColors.SurfaceRaised,
            onSurfaceVariant = LumoColors.OnDarkMuted,
            outline = LumoColors.Outline,
            error = LumoColors.Error,
            onError = LumoColors.OnError,
        )
    } else {
        lightColorScheme(
            primary = LumoColors.AccentPressed,
            onPrimary = LumoColors.OnAccent,
            background = LumoColors.LightSurface,
            onBackground = LumoColors.OnLight,
            surface = LumoColors.LightSurfaceRaised,
            onSurface = LumoColors.OnLight,
            surfaceVariant = LumoColors.LightSurface,
            onSurfaceVariant = LumoColors.OnLightMuted,
            outline = LumoColors.LightOutline,
            error = LumoColors.Error,
            onError = LumoColors.OnError,
        )
    }

    val type = LumoTypeScale.mobile.withDefaultFamily()

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            headlineLarge = type.display,
            titleLarge = type.title,
            bodyLarge = type.body,
            labelLarge = type.label,
        ),
        content = content,
    )
}
