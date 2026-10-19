package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
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
 *
 * @param footer what the shell pins to the foot of the rail, under the
 * destinations and inside the same overscan — the source switcher (US-018). A
 * slot rather than a parameter per thing, because what goes there is a feature's
 * and this module may not know features. It stays in the rail's single column on
 * purpose: `DOWN` from the last destination reaches it and `RIGHT` leaves for the
 * content, so it opens no focus zone of its own.
 */
@Composable
fun LumoTvNavRail(
    destinations: List<LumoDestination>,
    selectedRoute: String?,
    onSelect: (LumoDestination) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val selectedIndex = destinations.indexOfFirst { it.route == selectedRoute }
    val requesters = remember(destinations) { destinations.map { FocusRequester() } }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            .background(LumoColors.Surface)
            .tvOverscanEdges(start = true, top = true, bottom = true)
            .padding(end = LumoSpacing.sm)
            // `LEFT` from the content comes back to the entry that is open, not
            // to whichever entry happens to be level with the card the remote
            // left (BUG-S11-06-07): the rail is one zone, and re-entering a zone
            // lands on its current entry (docs/design/tv-focus-map.md). With no
            // entry open — a detail screen — the geometric search applies.
            .focusProperties {
                onEnter = {
                    val target = requesters.getOrNull(selectedIndex)
                    if (requestedFocusDirection == FocusDirection.Left && target != null) {
                        target.requestFocus()
                    }
                }
            }
            .focusGroup(),
        // `xs` between entries, not `sm`: eight destinations at `sm` filled a
        // 1080p panel and pushed the footer — the active source's name — off
        // the bottom, clipped to "Banc Xtr…" or to an empty outline
        // (BUG-S11-06-10). See [RAIL_ITEM_VERTICAL_PADDING] for the budget.
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        destinations.forEachIndexed { index, destination ->
            LumoTvNavRailItem(
                destination = destination,
                selected = index == selectedIndex,
                focusRequester = requesters[index],
                onSelect = { onSelect(destination) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        footer()
    }
}

@Composable
private fun LumoTvNavRailItem(
    destination: LumoDestination,
    selected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // Focus is the cyan outline and the raised surface; selection is cyan ink.
    // Neither is ever a cyan fill: the charter keeps the accent out of
    // backgrounds, and a rail whose focused item is a cyan block gives the same
    // look to "the remote is here" and to "this is open".
    Text(
        text = stringResource(destination.titleRes),
        style = LumoTypeScale.tv.label.copy(letterSpacing = RAIL_LETTER_SPACING),
        color = when {
            selected -> LumoColors.Accent
            focused -> LumoColors.OnDark
            else -> LumoColors.OnDarkMuted
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = LumoSpacing.sm, vertical = RAIL_ITEM_VERTICAL_PADDING),
    )
}

/**
 * The vertical padding of a destination, and the reason it is not `sm`.
 *
 * A 1080p panel is 540 dp tall, 486 dp inside the 5 % overscan. Eight entries of
 * a 29 sp line with `sm` above and below, `sm` apart, took 416 dp and left the
 * footer 54 dp: not enough for the "Source" heading and the source's name, which
 * were clipped (BUG-S11-06-10). At 6 dp and `xs` apart they take 356 dp, which
 * leaves the footer room for the heading and a name on two lines.
 */
private val RAIL_ITEM_VERTICAL_PADDING = 6.dp

// Wide enough that the start overscan margin does not eat the label, and no
// wider: the canvas has no rail at all, so every dp given to this column is a
// dp taken from screens drawn for the full panel. 244 dp, with `sm` at its end and inside each
// entry and the tighter tracking below, is the narrowest that keeps the longest
// label, « Ma bibliothèque », on one line (S15-05); at 220 dp it broke inside
// the word. It still leaves room for a sub-menu and its panel side by side.
private val RAIL_WIDTH = 244.dp

// The label style's 0.1 em tracking is drawn for short labels; in a column this
// narrow it is what pushed French names onto a second line (S15-05).
private val RAIL_LETTER_SPACING = 0.04.em
