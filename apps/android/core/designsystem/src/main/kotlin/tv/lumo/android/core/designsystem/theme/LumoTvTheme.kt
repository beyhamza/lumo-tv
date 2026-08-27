package tv.lumo.android.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * The Android TV theme (`tv.lumo.androidtv`).
 *
 * `androidx.tv.material3`, not `androidx.compose.material3`: the TV library's
 * components carry focus states, the phone library's do not. A Material 3
 * `Card` on a television is focusable by accident at best and invisible when
 * focused at worst.
 *
 * There is no light variant, and that is a decision rather than an omission.
 * Televisions have no system dark-mode setting to follow, the room is usually
 * dark, and a bright 55-inch panel at night is unpleasant.
 */
@Composable
fun LumoTvTheme(content: @Composable () -> Unit) {
    val type = LumoTypeScale.tv.withDefaultFamily()

    MaterialTheme(
        colorScheme = darkColorScheme(
            // Same reasoning as the phone: cyan is what "the remote is here"
            // looks like, so it stays out of anything filled and merely present.
            // On a television that matters more, not less — a grid of cyan tiles
            // makes the focused one impossible to find.
            primary = LumoColors.OnDark,
            onPrimary = LumoColors.Ink,
            background = LumoColors.Ink,
            onBackground = LumoColors.OnDark,
            surface = LumoColors.Surface,
            onSurface = LumoColors.OnDark,
            surfaceVariant = LumoColors.SurfaceRaised,
            onSurfaceVariant = LumoColors.OnDarkMuted,
            border = LumoColors.Outline,
            error = LumoColors.Error,
            onError = LumoColors.OnError,
        ),
        typography = Typography(
            displayMedium = type.display,
            titleLarge = type.title,
            bodyLarge = type.body,
            labelLarge = type.label,
        ),
        content = content,
    )
}
