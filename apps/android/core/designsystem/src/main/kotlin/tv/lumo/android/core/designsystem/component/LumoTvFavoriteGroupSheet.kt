package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus

/**
 * The group picker, on a television (S4-07).
 *
 * <h2>Why it is not the phone's sheet with bigger text</h2>
 *
 * The phone's is a row per group with a checkbox, aimed at with a thumb. Here
 * there is no thumb: every row is a focus target the remote walks through with
 * `UP` and `DOWN`, and the checkbox is not a second target — pressing `OK` on the
 * row is what toggles it. Two targets per row would double the length of the
 * journey through a list somebody is trying to leave.
 *
 * <h2>What this adds to the focus map</h2>
 *
 * A new surface, and it is documented in `docs/design/tv-focus-map.md` rather than
 * only here. `UP` and `DOWN` walk the groups and stop at the ends — no wrapping,
 * because a list that loops has no ends and a viewer cannot tell they have seen
 * everything. `LEFT` and `RIGHT` do nothing at all, deliberately: there is nothing
 * beside this list, and a focus that escaped sideways would land on the grid
 * underneath while this is still open. `BACK` closes and changes nothing.
 *
 * <h2>The first row takes the focus on arrival</h2>
 *
 * Every screen has to have one — a surface with no focus target leaves `BACK` as
 * the only key that does anything, which is the defect
 * `docs/design/tv-focus-map.md` exists to prevent.
 *
 * Stateless: every toggle is reported upwards at once, so the caller's optimistic
 * state is the only source of what is ticked.
 */
@Composable
fun LumoTvFavoriteGroupSheet(
    title: String,
    groups: List<LumoFavoriteGroupChoice>,
    onToggle: (groupId: String, checked: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }

    // Nothing composes on the frame this runs if the list is empty; failing to
    // focus is recoverable — the remote still works — and throwing would take the
    // screen down under the viewer.
    LaunchedEffect(groups.size) {
        if (groups.isNotEmpty()) runCatching { first.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // A scrim rather than a panel floating over a live grid: at three
            // metres the eye needs to be told which of the two layers is the one
            // accepting keys.
            .background(LumoColors.Scrim)
            .onKeyEvent { event ->
                val isBack = event.key == Key.Back || event.key == Key.Escape
                if (isBack && event.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(SHEET_WIDTH)
                .clip(LumoTvShapes.large)
                .background(LumoColors.Surface)
                .padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
                items(groups, key = { it.id }) { group ->
                    GroupRow(
                        group = group,
                        onToggle = onToggle,
                        modifier = if (group.id == groups.first().id) {
                            Modifier.focusRequester(first)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: LumoFavoriteGroupChoice,
    onToggle: (groupId: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
            // `clickable` makes the row focusable and binds the centre key at
            // once. A checkbox with its own handler would be a second target on a
            // row that has one thing to do.
            .clickable(interactionSource = interactionSource, indication = null) {
                onToggle(group.id, !group.checked)
            }
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A glyph rather than a Material checkbox: the product has no icon set,
        // and the phone's control draws at a size nobody reads at three metres.
        Text(
            text = if (group.checked) "✓" else "·",
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) LumoColors.OnAccent else LumoColors.OnDarkMuted,
        )
        Text(
            text = group.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) LumoColors.OnAccent else LumoColors.OnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Wide enough for a group name at three metres, narrow enough that the grid stays
 * visible around it — which is what says the sheet is a layer and not a new screen.
 */
private val SHEET_WIDTH = 560.dp
