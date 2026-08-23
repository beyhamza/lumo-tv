package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTypeScale
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * The television's side rail (docs/architecture.md §3).
 *
 * Two properties matter more than how it looks:
 *
 * - **Every item is reachable with the D-pad.** They are laid out in a single
 *   column, so `UP`/`DOWN` walks them and `RIGHT` leaves for the content. There
 *   is no item that only a pointer can reach (US-10).
 * - **Focus and selection are drawn differently.** Focus is where the remote is
 *   right now; selection is which screen is open. Collapsing them into one
 *   colour makes the rail read as though it has moved when the user is only
 *   browsing it.
 *
 * The rail carries overscan on its own outer edges — not on the one facing the
 * content, where the margin would be wasted width. Skipping it would put the
 * first and last rail items in the strip a television crops, which is the worst
 * possible place for an interactive element to be.
 */
@Composable
fun LumoTvNavRail(
    destinations: List<LumoDestination>,
    selectedRoute: String?,
    onSelect: (LumoDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            .background(LumoColors.Surface)
            .tvOverscanEdges(start = true, top = true, bottom = true)
            .padding(end = LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        destinations.forEach { destination ->
            LumoTvNavRailItem(
                destination = destination,
                selected = destination.route == selectedRoute,
                onSelect = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun LumoTvNavRailItem(
    destination: LumoDestination,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = stringResource(destination.titleRes),
        style = LumoTypeScale.tv.label,
        color = when {
            focused -> LumoColors.OnAccent
            selected -> LumoColors.Accent
            else -> LumoColors.OnDarkMuted
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoShapes.small)
            .clip(LumoShapes.small)
            .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

// Wide enough that the start overscan margin does not eat the label.
private val RAIL_WIDTH = 320.dp
