package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.theme.LumoTypeScale
import tv.lumo.android.core.designsystem.tv.lumoTvFocus

/**
 * A pill button for the television, as the canvas draws every control that is
 * pressed with OK: `Réessayer`, `Chaîne suivante`, `Pause`, `Sous-titres`.
 *
 * One look for every state the D-pad can put it in, and no other:
 *
 * - at rest, a raised pill with primary ink, or a plain one when [primary] is
 *   false — the primary is the answer the screen expects;
 * - focused, the shared signature: scale, cyan outline, one surface lighter.
 *   Never a cyan fill, for the reason written on `LumoFocus`.
 *
 * `clickable` alone: it makes the pill focusable and binds the centre key. A
 * second `focusable()` would put two targets on one button (AGENTS.md §6).
 */
@Composable
fun LumoTvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = text,
        style = LumoTypeScale.tv.label,
        color = when {
            !enabled -> LumoColors.OnDarkMuted
            focused || primary -> LumoColors.OnDark
            else -> LumoColors.OnDarkMuted
        },
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.pill)
            .clip(LumoTvShapes.pill)
            .background(
                when {
                    focused -> LumoColors.SurfaceRaised
                    primary -> LumoColors.SurfaceRaised
                    else -> LumoColors.Surface
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.sm + LumoSpacing.xs),
    )
}
