package tv.lumo.android.feature.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.labelRes
import tv.lumo.android.core.data.messageRes
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.component.LumoTvStateMessage
import tv.lumo.android.core.designsystem.effect.PollWhile
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.network.generated.model.SourceKind

/**
 * "My sources" on the television (US-024, design S8-E09).
 *
 * <h2>Two things can be done here, and the screen says where the rest is done</h2>
 *
 * A television chooses the source it browses and asks for a refresh. It never
 * types (docs/architecture.md §5), so adding, renaming, deleting and the automatic
 * refresh are done **from the phone application or lumo.tv**, and a block under
 * the list says so in those words. That block is not the activation wording and
 * must not drift towards it: pairing a television is about the *account*, and
 * somebody reading this screen is already signed in. The `[mock]` "show the
 * pairing code" button the placeholder carried is gone for that reason — it made
 * exactly that confusion.
 *
 * <h2>Focus</h2>
 *
 * The targets are the **buttons**, never the cards: a focusable card with a
 * button inside it is two stops for one thing (AGENTS.md §6).
 *
 * - On arrival the focus is on the **active source** — its *Refresh*, the one
 *   control it has, since *Use this source* is not offered on the source already
 *   in use.
 * - `UP`/`DOWN` walk the sources, `LEFT`/`RIGHT` the one or two buttons of a
 *   source; `LEFT` from the first button reaches the rail when the rail is what
 *   opened this screen.
 * - **A refreshing source is not a dead end.** Its button reads "Refreshing…" and
 *   does nothing, but it **stays focusable**: disabling it would take it out of
 *   the focus search, and with a single source that is refreshing the screen would
 *   be left with no target at all — `BACK` the only key that works (rule 1 of
 *   docs/design/tv-focus-map.md).
 * - **Pressing *Use this source* removes that button** — the source is now the
 *   active one — so the focus is handed to the same source's *Refresh* rather than
 *   left on a control that no longer exists.
 * - `BACK` is the application's: Settings when pushed from there, Home when the
 *   switcher at the foot of the rail opened it.
 */
@Composable
fun SourceTvScreen(
    modifier: Modifier = Modifier,
    viewModel: MySourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // A synchronisation on display has to move, and to end. From the composition,
    // so that it stops with the screen.
    PollWhile(
        active = state.anyRefreshing,
        everyMillis = SOURCE_LIST_POLL_MILLIS,
        onTick = viewModel::reload,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscan(),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (state.phase) {
            // Lasts as long as one request, and the rail beside it is focusable
            // throughout.
            MySourcesPhase.Loading -> Text(
                text = stringResource(R.string.feature_source_tv_loading),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDarkMuted,
            )

            // A television never adds a source: it says where one is added, and
            // the one thing it *can* do is look again — which is its button.
            MySourcesPhase.Empty -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_source_tv_none_title),
                body = stringResource(DataR.string.core_data_source_tv_none_body),
                actionLabel = stringResource(R.string.feature_source_tv_check_again),
                onAction = viewModel::reload,
            )

            MySourcesPhase.Unavailable -> LumoTvStateMessage(
                title = stringResource(R.string.feature_source_list_unavailable_title),
                body = stringResource(R.string.feature_source_list_unavailable_body),
                actionLabel = stringResource(R.string.feature_source_retry),
                onAction = viewModel::reload,
            )

            MySourcesPhase.Listed -> Listed(
                rows = state.rows,
                onUse = viewModel::use,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun Listed(
    rows: List<SourceRow>,
    onUse: (String) -> Unit,
    onRefresh: (String) -> Unit,
) {
    // The active source, or the first when none is chosen yet. Decided once per
    // arrival: the list is re-read every few seconds during a refresh, and a
    // focus pulled back at each poll would fight the remote.
    val arrivalId = remember { (rows.firstOrNull { it.active } ?: rows.firstOrNull())?.id }

    Column(
        modifier = Modifier
            .widthIn(max = 1_100.dp)
            // A column that scrolls rather than a lazy one: sources are counted in
            // ones and twos, and every card composed means `UP` and `DOWN` always
            // find a button. Focusing one scrolls it into view by itself.
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.feature_source_list_title),
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
        )

        rows.forEach { row ->
            SourceCard(
                row = row,
                onUse = { onUse(row.id) },
                onRefresh = { onRefresh(row.id) },
                takesArrivalFocus = row.id == arrivalId,
            )
        }

        Guidance()
    }
}

/**
 * One source: what it is, what it is doing, what it holds, and its one or two
 * buttons. The card is **drawn**, never focusable.
 */
@Composable
private fun SourceCard(
    row: SourceRow,
    onUse: () -> Unit,
    onRefresh: () -> Unit,
    takesArrivalFocus: Boolean,
) {
    val failed = row.state is SourceRowState.Failed

    val useFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    // A plain holder and not state: written by a press, read by the effect that
    // press causes, and never drawn.
    val handOver = remember { booleanArrayOf(false) }

    LaunchedEffect(Unit) {
        if (!takesArrivalFocus) return@LaunchedEffect
        // Failing to focus is recoverable — the D-pad still works — and throwing
        // would take the screen down.
        runCatching { (if (row.active) refreshFocus else useFocus).requestFocus() }
    }

    // *Use this source* was pressed and has just left the composition with the
    // focus on it. Without this the screen is left with no focused target until
    // the next key press, which on some sets lands nowhere.
    LaunchedEffect(row.active) {
        if (row.active && handOver[0]) {
            handOver[0] = false
            runCatching { refreshFocus.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoTvShapes.medium)
            .background(LumoColors.Surface)
            .then(
                if (failed) {
                    Modifier.border(2.dp, LumoColors.Error.copy(alpha = 0.5f), LumoTvShapes.medium)
                } else {
                    Modifier
                },
            )
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindMark(row.kind)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDark,
                )
                Text(
                    text = summaryOf(row),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
                StateLine(row.state)
            }

            // Selection, drawn — and said in words, because a colour at three
            // metres on a badly calibrated panel is not a label. Never the focus
            // signature: focus is where the remote is, this is what is browsed.
            if (row.active) {
                Text(
                    text = stringResource(R.string.feature_source_list_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.Accent,
                    modifier = Modifier
                        .background(LumoColors.Accent.copy(alpha = 0.12f), LumoTvShapes.pill)
                        .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
            if (!row.active) {
                LumoTvButton(
                    text = stringResource(R.string.feature_source_list_use),
                    onClick = {
                        handOver[0] = true
                        onUse()
                    },
                    primary = true,
                    focusRequester = useFocus,
                )
            }

            LumoTvButton(
                text = stringResource(
                    when {
                        row.refreshing -> R.string.feature_source_list_refreshing
                        (row.state as? SourceRowState.Failed)?.exit == SourceExit.Retry ->
                            R.string.feature_source_retry
                        else -> R.string.feature_source_list_refresh
                    },
                ),
                // Guarded in the view model, and **not** disabled here: a disabled
                // button leaves the focus search, and a single refreshing source
                // would leave this screen with no target. See the class doc.
                onClick = onRefresh,
                focusRequester = refreshFocus,
            )
        }

        RefreshOutcome(row.refresh)
    }
}

/** `m3u` / `xtr` in a gradient square — the canvas's mark for the kind. Decoration. */
@Composable
private fun KindMark(kind: SourceKind) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(LumoTvShapes.small)
            .background(
                Brush.linearGradient(
                    listOf(
                        LumoColors.Accent.copy(alpha = 0.25f),
                        LumoColors.AccentViolet.copy(alpha = 0.25f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(kind.shortNameRes()).take(KIND_MARK_LENGTH).lowercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = LumoColors.Accent,
        )
    }
}

/**
 * `Xtream · 1 248 channels · 312 films · 40 series · refreshed 2 hours ago`, with
 * every part that is not known left out — **never a zero in its place** — and no
 * series for a playlist (`adr/0010`).
 */
@Composable
private fun summaryOf(row: SourceRow): String = listOfNotNull(
    stringResource(row.kind.shortNameRes()),
    row.channels?.let { pluralStringResource(R.plurals.feature_source_list_channels, it, it) },
    row.films?.let { pluralStringResource(R.plurals.feature_source_list_films, it, it) },
    row.series?.takeIf { row.showsSeries }
        ?.let { pluralStringResource(R.plurals.feature_source_list_series, it, it) },
    row.lastSyncedAt
        ?.let { stringResource(R.string.feature_source_list_last_synced, relative(it)) }
        ?: stringResource(R.string.feature_source_list_never_synced),
).joinToString(separator = " · ")

/** Ready, the **real step** of a refresh, or the reason of a failure and its age. */
@Composable
private fun StateLine(state: SourceRowState) {
    when (state) {
        SourceRowState.Ready -> Text(
            text = stringResource(R.string.feature_source_switcher_status_ready),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDark,
        )

        is SourceRowState.Refreshing -> Text(
            text = stringResource(state.step.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.Accent,
        )

        is SourceRowState.Failed -> {
            Text(
                text = listOfNotNull(
                    stringResource(state.reason.messageRes()),
                    state.at?.let { stringResource(R.string.feature_source_list_failed_at, relative(it)) },
                ).joinToString(separator = " "),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.Error,
            )
            // Credentials and addresses are corrected where they can be typed.
            if (state.exit == SourceExit.FixCredentials || state.exit == SourceExit.FixAddress) {
                Text(
                    text = stringResource(R.string.feature_source_tv_error_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
        }

        SourceRowState.Unknown -> Unit
    }
}

/** The phone's sentences, for the same refusals — see `RefreshControl`. */
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
        style = MaterialTheme.typography.labelLarge,
        color = if (control is RefreshControl.Failed) LumoColors.Error else LumoColors.OnDarkMuted,
    )
}

/**
 * Where the rest is done. Text, and **not a focus stop**: there is nothing to
 * press, and a stop with nothing behind it is a dead end under the last source.
 */
@Composable
private fun Guidance() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoTvShapes.medium)
            .border(1.dp, LumoColors.OnDarkMuted.copy(alpha = 0.3f), LumoTvShapes.medium)
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.feature_source_tv_guidance_title),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
        )
        Text(
            text = stringResource(R.string.feature_source_tv_guidance_body),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
    }
}

/** `m3u`, `xtr`: three letters fit the square at the label size. */
private const val KIND_MARK_LENGTH = 3
