package tv.lumo.android.core.designsystem.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import tv.lumo.android.core.designsystem.theme.LUMO_TV_OVERSCAN_FRACTION
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoFocus
import tv.lumo.android.core.designsystem.theme.LumoShapes

/**
 * The focus signature every focusable element on the television wears.
 *
 * Scale **and** border **and** elevation, together, as
 * docs/architecture.md §3 requires. Any one of them alone fails on some real
 * setup: colour and border wash out on a badly calibrated panel, scale is easy
 * to miss in a dense grid, elevation disappears over bright artwork. Applying
 * all three from one modifier is also what stops each screen from inventing its
 * own idea of what "focused" looks like.
 *
 * Test it with a remote control on a real device, never with a mouse on the
 * emulator (backlog, Definition of Done): a mouse produces hover, not focus, and
 * hover hides exactly the bugs this modifier exists to prevent.
 */
@Composable
fun Modifier.lumoTvFocus(
    focused: Boolean,
    shape: Shape = LumoShapes.medium,
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
        .shadow(
            elevation = if (focused) LumoFocus.Elevation else 0.dp,
            shape = shape,
            clip = false,
        )
        .border(
            width = if (focused) LumoFocus.BorderWidth else 0.dp,
            color = if (focused) LumoColors.Accent else LumoColors.Outline.copy(alpha = 0f),
            shape = shape,
        )
}

/**
 * The 5 % margin a television crops.
 *
 * Measured against the actual screen rather than hard-coded in dp, because a
 * 1080p set and a 4K set report different sizes and both crop proportionally.
 * Apply it to the outermost container of every TV screen: anything outside it
 * may be physically invisible on a viewer's set.
 */
@Composable
fun Modifier.tvOverscan(): Modifier = tvOverscanEdges(
    start = true,
    top = true,
    end = true,
    bottom = true,
)

/**
 * Overscan on chosen edges only.
 *
 * The side rail needs it on its outer three edges but not on the one that faces
 * the content, where the gap would just be wasted width. A full-bleed video
 * surface needs it on none at all — the picture is supposed to reach the edge of
 * the panel; only the controls drawn over it have to stay inside the safe area.
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
