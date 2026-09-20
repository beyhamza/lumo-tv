package tv.lumo.android.feature.source

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.labelRes
import tv.lumo.android.core.data.messageRes
import tv.lumo.android.core.designsystem.component.LumoStateMessage
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind

/**
 * "My sources" on the phone: the list, and everything that can be done to a source
 * (US-024, design S8-E05, E07, E08).
 *
 * <h2>Every action is visible on the source it acts on</h2>
 *
 * No overflow menu. A source is looked after rarely, by somebody who came here on
 * purpose, often because something is wrong with it — and a menu hides exactly the
 * buttons that person is looking for. The cost is a taller card; the list is
 * counted in ones and twos, so the height is there to spend.
 *
 * <h2>Numbers that are known, and only those</h2>
 *
 * Channels come with the source; films and series are asked for separately and may
 * not have answered, or may never be able to. **An unknown number is a line that is
 * not drawn** — never a zero, which would be a statement about somebody's
 * subscription made from nothing. A playlist has no series line at all (`adr/0010`).
 *
 * <h2>Deleting says what it takes, and Cancel is the default</h2>
 *
 * The dialog names the source and lists what goes with it, what stays, what it
 * does *not* do — the subscription with the provider is untouched — and that
 * adding the source again will not bring anything back. The destructive button is
 * the quiet one.
 */
@Composable
internal fun MySourcesList(
    state: MySourcesState,
    actions: MySourcesActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_source_list_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.feature_source_list_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.rows.forEach { row ->
            SourceCard(
                row = row,
                autoSyncFailed = row.id in state.autoSyncFailed,
                actions = actions,
                source = state.sources.firstOrNull { it.id.toString() == row.id },
            )
        }

        // At the foot and not in the header: the list is what somebody came to
        // read, and adding is what they do once they have read it. The plan's
        // ceiling is checked when the form opens — see `SourceViewModel`.
        Button(onClick = actions.onAdd, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_source_add_title))
        }
    }

    when (val dialog = state.dialog) {
        is SourceDialog.Rename -> RenameDialog(dialog, actions)
        is SourceDialog.Delete -> DeleteDialog(dialog, actions)
        null -> Unit
    }
}

/** The wires of the list, gathered so the card does not take twelve parameters. */
internal class MySourcesActions(
    val onAdd: () -> Unit,
    val onUse: (sourceId: String) -> Unit,
    val onRefresh: (sourceId: String) -> Unit,
    val onFix: (Source) -> Unit,
    val onAutoSync: (sourceId: String, enabled: Boolean) -> Unit,
    val onAskRename: (sourceId: String) -> Unit,
    val onRenameChange: (String) -> Unit,
    val onConfirmRename: () -> Unit,
    val onAskDelete: (sourceId: String) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDialog: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(
    row: SourceRow,
    autoSyncFailed: Boolean,
    actions: MySourcesActions,
    source: Source?,
) {
    val failed = row.state is SourceRowState.Failed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.medium)
            .border(
                width = if (row.active) 2.dp else 1.dp,
                color = when {
                    failed -> MaterialTheme.colorScheme.error
                    row.active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                shape = LumoShapes.medium,
            )
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        // ---- what it is ----------------------------------------------------
        Text(
            text = row.label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = listOfNotNull(
                stringResource(row.kind.shortNameRes()),
                // In words and not only in the border: a colour is not a label.
                stringResource(R.string.feature_source_list_active).takeIf { row.active },
            ).joinToString(separator = " · "),
            style = MaterialTheme.typography.labelLarge,
            color = if (row.active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // ---- what it is doing ----------------------------------------------
        State(row.state)

        Text(
            text = row.lastSyncedAt
                ?.let { stringResource(R.string.feature_source_list_last_synced, relative(it)) }
                ?: stringResource(R.string.feature_source_list_never_synced),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- what it holds, when known -------------------------------------
        val counts = listOfNotNull(
            row.channels?.let { pluralStringResource(R.plurals.feature_source_list_channels, it, it) },
            row.films?.let { pluralStringResource(R.plurals.feature_source_list_films, it, it) },
            // Never for a playlist — see `SourceRow.series`.
            row.series?.takeIf { row.showsSeries }
                ?.let { pluralStringResource(R.plurals.feature_source_list_series, it, it) },
        )
        if (counts.isNotEmpty()) {
            Text(
                text = counts.joinToString(separator = " · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // ---- what can be done ----------------------------------------------
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        ) {
            if (!row.active) {
                Button(onClick = { actions.onUse(row.id) }) {
                    Text(stringResource(R.string.feature_source_list_use))
                }
            }

            // After a failure the button that can help comes first, and it is the
            // one the failure calls for — never a retry for a refused password.
            val exit = (row.state as? SourceRowState.Failed)?.exit
            if (source != null && (exit == SourceExit.FixCredentials || exit == SourceExit.FixAddress)) {
                Button(onClick = { actions.onFix(source) }) {
                    Text(
                        stringResource(
                            if (exit == SourceExit.FixCredentials) {
                                R.string.feature_source_fix_credentials
                            } else {
                                R.string.feature_source_fix_address
                            },
                        ),
                    )
                }
            }

            OutlinedButton(
                onClick = { actions.onRefresh(row.id) },
                // One operation at a time: while a synchronisation runs the control
                // says so and takes no press (US-024).
                enabled = !row.refreshing,
            ) {
                Text(
                    stringResource(
                        when {
                            row.refreshing -> R.string.feature_source_list_refreshing
                            exit == SourceExit.Retry -> R.string.feature_source_retry
                            else -> R.string.feature_source_list_refresh
                        },
                    ),
                )
            }

            OutlinedButton(onClick = { actions.onAskRename(row.id) }) {
                Text(stringResource(R.string.feature_source_list_rename))
            }

            TextButton(onClick = { actions.onAskDelete(row.id) }) {
                Text(
                    text = stringResource(R.string.feature_source_list_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        RefreshOutcome(row.refresh)

        // ---- automatic refresh ---------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // One node for a screen reader: the label, the sentence and the
                // switch, read together under the source's heading. Three bare
                // switches called "on" are no switch at all.
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feature_source_list_auto_sync),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Said here because it is the surprise: the switch is on a phone,
                // and what it changes is the website's and the television's too.
                Text(
                    text = stringResource(R.string.feature_source_list_auto_sync_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = row.autoSync,
                onCheckedChange = { actions.onAutoSync(row.id, it) },
                // A write in flight: the switch shows where it is going and waits.
                enabled = !row.autoSyncPending,
            )
        }
        if (autoSyncFailed) {
            Text(
                text = stringResource(R.string.feature_source_list_auto_sync_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Ready, refreshing with its **real step**, or failed with its reason and its age.
 *
 * Never a percentage: the server reports a step, and a bar drawn from four steps
 * would be a number invented on the device (US-024).
 */
@Composable
private fun State(state: SourceRowState) {
    when (state) {
        SourceRowState.Ready -> Text(
            text = stringResource(R.string.feature_source_switcher_status_ready),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        is SourceRowState.Refreshing -> Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(LumoSpacing.md))
            Text(
                text = stringResource(state.step.labelRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        is SourceRowState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
            Text(
                text = stringResource(state.reason.messageRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            state.at?.let {
                Text(
                    text = stringResource(R.string.feature_source_list_failed_at, relative(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // A status newer than this build: nothing is said rather than something false.
        SourceRowState.Unknown -> Unit
    }
}

/**
 * What became of this device's own request for a refresh.
 *
 * A rate limit is a **wait**, worded with the server's delay when it gave one and
 * without a number when it did not. Already-running is not here at all: the row
 * above says "refreshing", which is the whole truth of it.
 */
@Composable
private fun RefreshOutcome(control: RefreshControl) {
    val text = when (control) {
        RefreshControl.Idle, RefreshControl.Requesting -> return
        is RefreshControl.Wait -> waitMessage(control.minutes)
        is RefreshControl.Failed -> stringResource(
            if (control.offline) {
                R.string.feature_source_list_refresh_offline
            } else {
                R.string.feature_source_list_refresh_failed
            },
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (control is RefreshControl.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Shared with the television: one sentence for one refusal. */
@Composable
internal fun waitMessage(minutes: Int?): String = if (minutes == null) {
    stringResource(R.string.feature_source_list_refresh_wait)
} else {
    pluralStringResource(R.plurals.feature_source_list_refresh_wait_minutes, minutes, minutes)
}

/** `M3U` or `Xtream`: the type, as the list and the television name it. */
@StringRes
internal fun SourceKind.shortNameRes(): Int = if (this == SourceKind.XTREAM) {
    R.string.feature_source_kind_xtream_short
} else {
    R.string.feature_source_kind_m3u_short
}

/** "2 hours ago", in the device's language. Minutes are as fine as this needs to be. */
@Composable
internal fun relative(moment: OffsetDateTime): String = DateUtils.getRelativeTimeSpanString(
    moment.toInstant().toEpochMilli(),
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()

// ---- dialogs ----------------------------------------------------------------

@Composable
private fun RenameDialog(dialog: SourceDialog.Rename, actions: MySourcesActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(stringResource(R.string.feature_source_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                OutlinedTextField(
                    value = dialog.value,
                    onValueChange = actions.onRenameChange,
                    label = { Text(stringResource(R.string.feature_source_label_label)) },
                    singleLine = true,
                    enabled = !dialog.saving,
                    isError = dialog.failed,
                    supportingText = {
                        Text(
                            stringResource(
                                if (dialog.failed) {
                                    R.string.feature_source_rename_failed
                                } else {
                                    R.string.feature_source_rename_hint
                                },
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmRename, enabled = dialog.canSave) {
                Text(stringResource(R.string.feature_source_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDialog, enabled = !dialog.saving) {
                Text(stringResource(R.string.feature_source_dialog_cancel))
            }
        },
    )
}

/**
 * The confirmation US-024 asks for, sentence by sentence.
 *
 * **Cancel is the default action**: it is the filled button, and it is what Back
 * and a tap outside do. The deletion is the quiet text button, in the error
 * colour — somebody who presses the prominent thing without reading keeps their
 * catalogue. The watchlist joins the first sentence with its own lot (sprint 11).
 */
@Composable
private fun DeleteDialog(dialog: SourceDialog.Delete, actions: MySourcesActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(stringResource(R.string.feature_source_delete_title, dialog.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                Text(stringResource(R.string.feature_source_delete_removed))
                Text(stringResource(R.string.feature_source_delete_kept))
                Text(stringResource(R.string.feature_source_delete_subscription))
                Text(stringResource(R.string.feature_source_delete_no_restore))

                dialog.failure?.let { failure ->
                    Text(
                        text = stringResource(
                            if (failure is LumoError.Offline) {
                                R.string.feature_source_delete_offline
                            } else {
                                R.string.feature_source_delete_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
        },
        // Material puts the confirm button last, where the thumb lands. That slot
        // goes to Cancel on purpose.
        confirmButton = {
            Button(onClick = actions.onDismissDialog, enabled = !dialog.deleting) {
                Text(stringResource(R.string.feature_source_dialog_cancel))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onConfirmDelete, enabled = !dialog.deleting) {
                Text(
                    text = stringResource(
                        if (dialog.deleting) {
                            R.string.feature_source_delete_deleting
                        } else {
                            R.string.feature_source_delete_confirm
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/** The list could not be read and nothing is known. Not "no source": worth retrying. */
@Composable
internal fun SourcesUnavailable(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    LumoStateMessage(
        title = stringResource(R.string.feature_source_list_unavailable_title),
        body = stringResource(R.string.feature_source_list_unavailable_body),
        actionLabel = stringResource(R.string.feature_source_retry),
        onAction = onRetry,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    )
}
