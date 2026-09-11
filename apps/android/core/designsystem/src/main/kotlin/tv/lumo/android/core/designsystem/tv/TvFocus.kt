package tv.lumo.android.core.designsystem.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import tv.lumo.android.core.designsystem.theme.LUMO_TV_OVERSCAN_FRACTION
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoFocus
import tv.lumo.android.core.designsystem.theme.LumoTvShapes

/**
 * The television focus signature (docs/architecture.md §3): scale, a cyan
 * outline, and — the caller's part — a lighter surface. Three cues, so that a
 * washed-out panel, a colour-blind viewer and a dense grid each still leave two.
 *
 * No shadow. The charter's elevation is layered transparency, never a drop
 * shadow (`elevation.$principle`), and the third cue is the surface step the
 * caller draws on focus: `LumoColors.Surface` at rest, `LumoColors.SurfaceRaised`
 * when focused. Every TV component does exactly that.
 *
 * The outline sits [LumoFocus.BorderOffset] outside the content, as the charter's
 * `outlineOffset` says: a border drawn flush against a card's edge reads as part
 * of the card, one drawn a few pixels out reads as a cursor around it.
 */
@Composable
fun Modifier.lumoTvFocus(
    focused: Boolean,
    shape: Shape = LumoTvShapes.medium,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) LumoFocus.Scale else 1f,
        animationSpec = tween(LumoFocus.AnimationMillis),
        label = "lumo-tv-focus-scale",
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(
            width = LumoFocus.BorderWidth,
            color = if (focused) LumoColors.Accent else LumoColors.Outline.copy(alpha = 0f),
            shape = shape,
        )
        .padding(LumoFocus.BorderOffset)
}

@Composable
fun Modifier.tvOverscan(): Modifier = tvOverscanEdges(
    start = true,
    top = true,
    end = true,
    bottom = true,
)

/**
 * Overscan on the chosen edges only.
 *
 * A screen applies it on its outer edges, and a rail or a panel on the edges
 * that touch the bezel — never on the edge facing another element, where the
 * margin would be wasted width.
 */
@Composable
fun Modifier.tvOverscanEdges(
    start: Boolean = false,
    top: Boolean = false,
    end: Boolean = false,
    bottom: Boolean = false,
): Modifier {
    val configuration = LocalConfiguration.current
    val horizontal = (configuration.screenWidthDp * LUMO_TV_OVERSCAN_FRACTION).dp
    val vertical = (configuration.screenHeightDp * LUMO_TV_OVERSCAN_FRACTION).dp

    return padding(
        start = if (start) horizontal else 0.dp,
        top = if (top) vertical else 0.dp,
        end = if (end) horizontal else 0.dp,
        bottom = if (bottom) vertical else 0.dp,
    )
}
