package tv.lumo.android.feature.source.switcher

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.feature.source.R

/**
 * The source switcher, on a phone (US-018, design `S8-E04`).
 *
 * A strip the shell places above its content: the word "Source" and the name of
 * the one being browsed, always. With several sources the strip is a button that
 * opens the list; with one it is plain text, because a control that opens onto a
 * single entry already ticked teaches somebody that it does nothing.
 *
 * <h2>Nothing is drawn when there is nothing to name</h2>
 *
 * No source, a session still being read, a choice remembered offline whose name
 * only the server knows: the strip is absent rather than empty. The catalogue
 * underneath already says what the situation is.
 *
 * <h2>The required chooser is the same list, without a way out</h2>
 *
 * Several sources and no choice on this device — a second device, or the active
 * source deleted elsewhere — and the list opens by itself, cannot be dismissed,
 * and ticks nothing. Picking one among several on somebody's behalf is the one
 * thing US-018 rules out.
 *
 * @param onSwitched the device now browses another source. Navigation is the
 * application's business: a feature does not know what else is on the back stack.
 * @param onLastSourceLost the account's last source is gone: the way forward is
 * adding one (US-018).
 * @param onOpenSources "My sources". The full screen is `S8-05`'s.
 */
@Composable
fun SourceSwitcherMobile(
    onSwitched: () -> Unit,
    onLastSourceLost: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceSwitcherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var listOpen by rememberSaveable { mutableStateOf(false) }

    // The effect outlives any one composition, and the shell's lambda closes over
    // a navigation state that does not: the latest one is what has to be called.
    val currentOnSwitched by rememberUpdatedState(onSwitched)
    val currentOnLastSourceLost by rememberUpdatedState(onLastSourceLost)
    LaunchedEffect(viewModel) {
        viewModel.switches.collect { currentOnSwitched() }
    }
    LaunchedEffect(viewModel) {
        viewModel.lastSourceLost.collect { currentOnLastSourceLost() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    state.activeLabel?.let { label ->
        ActiveSourceStrip(
            label = label,
            canSwitch = state.canSwitch,
            onOpen = {
                viewModel.onListOpened()
                listOpen = true
            },
            modifier = modifier,
        )
    }

    if (state.mustChoose || (listOpen && state.canSwitch)) {
        SourceListDialog(
            state = state,
            onChoose = { sourceId ->
                listOpen = false
                viewModel.onSourceChosen(sourceId)
            },
            onDismiss = { listOpen = false },
            onOpenSources = {
                listOpen = false
                onOpenSources()
            },
        )
    }
}

@Composable
private fun ActiveSourceStrip(
    label: String,
    canSwitch: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = stringResource(R.string.feature_source_switcher_change)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canSwitch) {
                    Modifier.clickable(onClickLabel = change, role = Role.Button, onClick = onOpen)
                } else {
                    Modifier
                },
            )
            .heightIn(min = MIN_TOUCH_TARGET)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_source_switcher_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (canSwitch) {
            // A glyph, as everywhere else: the product ships no icon set. It is
            // decoration — the row already announces itself as a button that
            // changes the source — so it is kept out of the accessibility tree.
            Text(
                text = "▾",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
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

    AlertDialog(
        // Required means required: no back, no tap outside. There is a source to
        // pick on every row, so nobody is trapped.
        onDismissRequest = { if (!required) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !required,
            dismissOnClickOutside = !required,
        ),
        title = {
            Text(
                text = stringResource(
                    if (required) {
                        R.string.feature_source_switcher_choose_title
                    } else {
                        R.string.feature_source_switcher_change
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                Text(
                    text = stringResource(
                        if (required) {
                            R.string.feature_source_switcher_choose_body
                        } else {
                            R.string.feature_source_switcher_device_only
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.choices.forEach { choice ->
                        SourceChoiceRow(choice = choice, onChoose = { onChoose(choice.id) })
                    }
                }
            }
        },
        confirmButton = {
            // Not while a choice is required: it would navigate underneath a
            // dialog that cannot close, to a screen nobody could reach.
            if (!required) {
                TextButton(onClick = onOpenSources) {
                    Text(stringResource(R.string.feature_source_switcher_manage))
                }
            }
        },
    )
}

/**
 * One source: its name, its state, and a tick when it is the one being browsed.
 *
 * `selectable` with the radio role, so TalkBack reads "selected" from the state
 * rather than from a glyph — the tick is drawn for the eye and hidden from the
 * tree, which would otherwise announce it a second time as the word "check mark".
 */
@Composable
private fun SourceChoiceRow(choice: SourceChoice, onChoose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = choice.active, role = Role.RadioButton, onClick = onChoose)
            .heightIn(min = MIN_TOUCH_TARGET)
            .padding(vertical = LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = choice.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            choice.status.labelRes?.let { status ->
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (choice.status == SourceChoiceStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (choice.active) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** The words for a state, shared by the two surfaces. Null says nothing at all. */
@get:StringRes
internal val SourceChoiceStatus.labelRes: Int?
    get() = when (this) {
        SourceChoiceStatus.Ready -> R.string.feature_source_switcher_status_ready
        SourceChoiceStatus.Refreshing -> R.string.feature_source_switcher_status_refreshing
        SourceChoiceStatus.Failed -> R.string.feature_source_switcher_status_error
        SourceChoiceStatus.Unknown -> null
    }

/** Material's floor for something a thumb has to hit. */
private val MIN_TOUCH_TARGET = 48.dp
