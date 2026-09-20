package tv.lumo.android.feature.source.switcher

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.feature.source.R

/**
 * The source switcher, on a television (US-018, design `S8-E04`).
 *
 * <h2>Where it sits, and why that adds no stop to anybody's journey</h2>
 *
 * At the foot of the rail, placed there by the shell. The rail is already the
 * zone `LEFT` reaches from every screen, so the switcher needs no focus zone of
 * its own; and at the foot, it is one `DOWN` past the last destination rather
 * than one more stop between a viewer and the channels. With a single source it
 * is plain text and not a stop at all — a focus target that opens onto one entry,
 * already ticked, is a press that teaches somebody the remote does nothing.
 *
 * <h2>The list is a dialog window, and that is a focus decision</h2>
 *
 * A layer drawn inside the screen has to stop the focus from escaping sideways
 * onto what is underneath, direction by direction. A window cannot leak it at
 * all: while it is up, its rows are the only targets that exist. So `UP` and
 * `DOWN` walk the sources and stop at the ends — a list that loops has no end,
 * and nobody can tell they have seen everything — `LEFT` and `RIGHT` do nothing,
 * and `BACK` closes, changing nothing, and gives the focus back to the trigger
 * (docs/design/tv-focus-map.md).
 *
 * <h2>Focus is not selection, here least of all</h2>
 *
 * The focus arrives on the source being browsed. When a choice is **required** —
 * several sources, none chosen on this device — it arrives on the first row,
 * because a surface must have a focus target, but no row carries the tick:
 * where the remote is and which source is active are two different facts, and
 * the second one is nobody's to decide but the viewer's.
 *
 * @param onSwitched the device now browses another source. Navigation is the
 * application's business: a feature does not know what is on the back stack.
 * @param onLastSourceLost the account's last source is gone: the way forward is
 * adding one (US-018).
 * @param onOpenSources "My sources" (US-024), this module's own destination.
 */
@Composable
fun SourceSwitcherTv(
    onSwitched: () -> Unit,
    onLastSourceLost: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceSwitcherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var listOpen by rememberSaveable { mutableStateOf(false) }
    val trigger = remember { FocusRequester() }

    val currentOnSwitched by rememberUpdatedState(onSwitched)
    val currentOnLastSourceLost by rememberUpdatedState(onLastSourceLost)
    LaunchedEffect(viewModel) {
        viewModel.switches.collect { currentOnSwitched() }
    }
    LaunchedEffect(viewModel) {
        viewModel.lastSourceLost.collect { currentOnLastSourceLost() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    // Closing a dialog gives the focus back to what opened it. The window
    // underneath usually restores it by itself; asking is what makes it a rule
    // rather than a habit. Asked from an effect, so that it runs once the dialog
    // window is actually gone — and failing is harmless: the trigger itself
    // disappears when the last other source has just been deleted.
    var returnFocus by remember { mutableStateOf(false) }
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            runCatching { trigger.requestFocus() }
            returnFocus = false
        }
    }

    fun close() {
        listOpen = false
        returnFocus = true
    }

    state.activeLabel?.let { label ->
        ActiveSource(
            label = label,
            canSwitch = state.canSwitch,
            trigger = trigger,
            onOpen = {
                viewModel.onListOpened()
                listOpen = true
            },
            modifier = modifier,
        )
    }

    val openedFromTrigger = listOpen && state.canSwitch
    if (state.mustChoose || openedFromTrigger) {
        SourceListDialog(
            state = state,
            onChoose = { sourceId ->
                viewModel.onSourceChosen(sourceId)
                if (openedFromTrigger) close()
            },
            onDismiss = ::close,
            onOpenSources = {
                listOpen = false
                onOpenSources()
            },
        )
    }
}

@Composable
private fun ActiveSource(
    label: String,
    canSwitch: Boolean,
    trigger: FocusRequester,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.feature_source_switcher_label),
            style = MaterialTheme.typography.labelLarge,
            color = LumoColors.OnDarkMuted,
            modifier = Modifier.padding(horizontal = LumoSpacing.md),
        )

        if (canSwitch) {
            ActiveSourceTrigger(label = label, trigger = trigger, onOpen = onOpen)
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = LumoSpacing.md),
            )
        }
    }
}

@Composable
private fun ActiveSourceTrigger(
    label: String,
    trigger: FocusRequester,
    onOpen: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val change = stringResource(R.string.feature_source_switcher_change)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(trigger)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            // `clickable` alone: it makes the row focusable and binds the centre
            // key. A `focusable()` on top would be a second target on one control
            // (AGENTS.md §6).
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = change,
                role = Role.Button,
                onClick = onOpen,
            )
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = LumoColors.OnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Decoration: the row already announces itself as a button that changes
        // the source, so the glyph stays out of the accessibility tree.
        Text(
            text = "▾",
            style = MaterialTheme.typography.labelLarge,
            color = LumoColors.OnDarkMuted,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun SourceListDialog(
    state: SourceSwitcherState,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenSources: () -> Unit,
) {
    val required = state.mustChoose
    val initial = remember { FocusRequester() }

    // The source being browsed, or the first row when nothing is: a surface with
    // no focus target leaves `BACK` as the only key that does anything, and in
    // the required case `BACK` does nothing either.
    val initialId = (state.choices.firstOrNull { it.active } ?: state.choices.firstOrNull())?.id

    LaunchedEffect(initialId) {
        // Failing to focus is recoverable — the remote still works — and
        // throwing would take the dialog down under the viewer.
        if (initialId != null) runCatching { initial.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (!required) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !required,
            dismissOnClickOutside = false,
            // The platform's default is a phone's dialog width. This one draws
            // its own scrim and its own card, sized for three metres.
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LumoColors.Scrim),
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
                    text = stringResource(
                        if (required) {
                            R.string.feature_source_switcher_choose_title
                        } else {
                            R.string.feature_source_switcher_change
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDark,
                )
                Text(
                    text = stringResource(
                        if (required) {
                            R.string.feature_source_switcher_choose_body
                        } else {
                            R.string.feature_source_switcher_device_only
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumoColors.OnDarkMuted,
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
                ) {
                    state.choices.forEach { choice ->
                        SourceChoiceRow(
                            choice = choice,
                            onChoose = { onChoose(choice.id) },
                            modifier = if (choice.id == initialId) {
                                Modifier.focusRequester(initial)
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                // Not while a choice is required: it would navigate underneath a
                // dialog that cannot close.
                if (!required) {
                    LumoTvButton(
                        text = stringResource(R.string.feature_source_switcher_manage),
                        onClick = onOpenSources,
                    )
                }
            }
        }
    }
}

/**
 * One source: name, state, and a tick when it is the one being browsed.
 *
 * The whole row is the target and `OK` on it is the choice — applied at once,
 * with no confirmation (US-018). The tick is a **drawn state**, never a second
 * target: two per row would double the journey through a list somebody is
 * trying to leave.
 *
 * Focus is the shared signature — scale, cyan outline, one surface lighter — and
 * selection is cyan ink on the tick. Never the same cue for both: a list that
 * lights the focused row like the active one reads as having switched source
 * while somebody is only walking it.
 */
@Composable
private fun SourceChoiceRow(
    choice: SourceChoice,
    onChoose: () -> Unit,
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
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            // `selectable` is `clickable` with a selected state: focusable, bound
            // to the centre key, and read as "selected" by a screen reader.
            .selectable(
                selected = choice.active,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onChoose,
            )
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = choice.label,
                style = MaterialTheme.typography.titleMedium,
                color = LumoColors.OnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            choice.status.labelRes?.let { status ->
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (choice.status == SourceChoiceStatus.Failed) {
                        LumoColors.Error
                    } else {
                        LumoColors.OnDarkMuted
                    },
                )
            }
        }
        if (choice.active) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.Accent,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * The favourite-group sheet's width, for the same reason: wide enough for a name
 * at three metres, narrow enough that the screen stays visible around it — which
 * is what says this is a layer and not a new screen.
 */
private val SHEET_WIDTH = 560.dp
