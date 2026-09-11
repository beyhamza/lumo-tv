package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTypeScale
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscan

/**
 * The television placeholder: same content as [LumoMobilePlaceholder], a
 * genuinely different screen.
 *
 * Larger type, overscan margins, and something the remote can actually land on.
 *
 * @param detail an optional line for observed state, matching
 * [LumoMobilePlaceholder]'s parameter of the same name.
 * @param focusable when true the card takes D-pad focus, so every TV screen has
 * at least one focus target. A screen with nothing focusable traps the user —
 * `BACK` becomes the only key that does anything, which is exactly the
 * "élément interactif inatteignable au D-pad" US-10 forbids.
 */
@Composable
fun LumoTvPlaceholder(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    focusable: Boolean = true,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            // Everything inside stays clear of the 5 % a television crops.
            .tvOverscan(),
        contentAlignment = Alignment.CenterStart,
    ) {
        var focused by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }

        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .then(
                    if (focusable) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .lumoTvFocus(focused)
                            .clip(LumoTvShapes.large)
                            .background(LumoColors.SurfaceRaised)
                            // `clickable` both makes this focusable and binds
                            // the D-pad centre key. Adding `focusable()` as well
                            // would create two focus targets on one card.
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick,
                            )
                    } else {
                        Modifier.clip(LumoTvShapes.large).background(LumoColors.SurfaceRaised)
                    },
                )
                .padding(LumoSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Text(
                text = title,
                style = LumoTypeScale.tv.display,
                color = LumoColors.OnDark,
            )
            Text(
                text = body,
                style = LumoTypeScale.tv.body,
                color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = LumoTypeScale.tv.label,
                    color = LumoColors.Accent,
                )
            }
        }
    }
}
