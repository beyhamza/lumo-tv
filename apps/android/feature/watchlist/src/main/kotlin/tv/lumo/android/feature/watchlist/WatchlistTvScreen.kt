package tv.lumo.android.feature.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.component.LumoTvStateMessage
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges
import tv.lumo.android.feature.watchlist.navigation.WatchlistActions

/**
 * "My list" on a television (US-022, S11-E05).
 *
 * <h2>Focus map</h2>
 *
 * - **Arrival**: the first card — or, back from a sheet, the card that was opened
 *   (US-10's rule). With an empty list, "Browse films".
 * - The filters, when there are any, sit above the grid: `UP` from the first row.
 * - **The compact panel** of an unavailable card is a dialog: the remote cannot
 *   wander onto the grid behind it, `BACK` closes it, and the focus arrives on
 *   "Remove from my list". When it closes, the focus returns to **the card that
 *   opened it**; after a removal, to the **neighbour** that took its place — or to
 *   the section title when the list is now empty (WL-03).
 * - The title is a focus target only while the list is empty, so that a removal
 *   of the last card has somewhere visible to land and nothing is pressed past
 *   otherwise.
 */
@Composable
fun WatchlistTvScreen(
    actions: WatchlistActions,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Each `ON_START` — arrival, return from a sheet, return from the background
    // (S11-05) — re-reads in place: the grid is keyed by item, so the focused card
    // keeps the focus if it is still there.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    WatchlistTvContent(
        state = state,
        actions = actions,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onSelectFilter = viewModel::selectFilter,
        onOpenPanel = viewModel::openPanel,
        onClosePanel = viewModel::closePanel,
        onRemove = viewModel::remove,
        onRemovalShown = viewModel::onRemovalShown,
        modifier = modifier,
    )
}

/** The screen over a plain state, so a focus test can drive it without Hilt. */
@Composable
internal fun WatchlistTvContent(
    state: WatchlistState,
    actions: WatchlistActions,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectFilter: (WatchlistFilter) -> Unit,
    onOpenPanel: (WatchlistEntry) -> Unit,
    onClosePanel: () -> Unit,
    onRemove: (WatchlistEntry) -> Unit,
    onRemovalShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()

    // The card last selected, saved with the back stack entry: back from a sheet,
    // the remote lands where it left.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // The card whose panel is open, and where it stood, to put the focus back.
    var panelOrigin by remember { mutableStateOf<Pair<String, Int>?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        Column(
            modifier = Modifier.padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            var titleFocused by remember { mutableStateOf(false) }
            val emptyNow = state.phase == WatchlistPhase.Ready && state.items.isEmpty()
            Text(
                text = stringResource(R.string.feature_watchlist_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
                modifier = Modifier
                    .focusRequester(titleFocus)
                    .onFocusChanged { titleFocused = it.isFocused }
                    .then(if (emptyNow) Modifier.focusable() else Modifier)
                    .lumoTvFocus(titleFocused && emptyNow)
                    .testTag(TITLE_TAG),
            )

            Notices(state = state, onRetry = onRetry)

            if (state.filters.isNotEmpty()) {
                Filters(state.filters, state.filter, onSelectFilter)
            }

            // The only control on a failed load: it takes the focus.
            val retryFocus = remember { FocusRequester() }
            LaunchedEffect(state.phase) {
                if (state.phase is WatchlistPhase.LoadFailed) runCatching { retryFocus.requestFocus() }
            }

            when (val phase = state.phase) {
                WatchlistPhase.Loading -> Text(
                    text = stringResource(R.string.feature_watchlist_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )

                is WatchlistPhase.LoadFailed -> LumoTvStateMessage(
                    title = stringResource(
                        if (phase.offline) {
                            R.string.feature_watchlist_offline_title
                        } else {
                            R.string.feature_watchlist_error_title
                        },
                    ),
                    body = stringResource(
                        if (phase.offline) {
                            R.string.feature_watchlist_offline_body
                        } else {
                            R.string.feature_watchlist_error_body
                        },
                    ),
                    isError = !phase.offline,
                    actionLabel = stringResource(R.string.feature_watchlist_retry),
                    onAction = onRetry,
                    actionFocusRequester = retryFocus,
                )

                WatchlistPhase.NoSource -> Text(
                    text = stringResource(R.string.feature_watchlist_no_source),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )

                WatchlistPhase.Ready -> when {
                    state.confirmedEmpty -> Empty(actions = actions, focusArrival = state.removedId == null)

                    state.filterEmpty -> FilterEmpty(state.filter) { onSelectFilter(WatchlistFilter.All) }

                    else -> Grid(
                        state = state,
                        gridState = gridState,
                        requesters = requesters,
                        arrivalId = selectedId,
                        onSelect = { index, entry ->
                            selectedId = entry.id
                            if (entry.selection() == CardSelection.OpenPanel) panelOrigin = entry.id to index
                            select(entry, actions, onOpenPanel)
                        },
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }

        state.panel?.let { entry ->
            UnavailablePanel(
                entry = entry,
                state = state,
                onRemove = { onRemove(entry) },
                onClose = onClosePanel,
            )
        }
    }

    // The panel closed: back to the card that opened it, or, if it was removed, to
    // the neighbour that took its place — or the title when nothing is left.
    LaunchedEffect(state.panel, state.items) {
        val origin = panelOrigin ?: return@LaunchedEffect
        if (state.panel != null) return@LaunchedEffect
        panelOrigin = null

        val (id, index) = origin
        val stillThere = state.items.indexOfFirst { it.id == id }
        val target = when {
            stillThere >= 0 -> stillThere
            state.items.isEmpty() -> -1
            else -> index.coerceAtMost(state.items.lastIndex)
        }
        // One frame for the grid (or the empty title) to be composed.
        withFrameNanos { }
        if (target < 0) {
            runCatching { titleFocus.requestFocus() }
        } else {
            gridState.scrollToItem(target)
            withFrameNanos { }
            val targetId = state.items[target].id
            selectedId = targetId
            runCatching { requesters[targetId]?.requestFocus() }
        }
        if (state.removedId != null) onRemovalShown()
    }
}

@Composable
private fun Notices(state: WatchlistState, onRetry: () -> Unit) {
    if (state.phase != WatchlistPhase.Ready) return
    val text = when {
        state.offline -> stringResource(R.string.feature_watchlist_offline_notice)
        state.refreshFailed -> stringResource(R.string.feature_watchlist_refresh_failed)
        else -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.offline) LumoColors.OnDarkMuted else LumoColors.Error,
            modifier = Modifier
                .widthIn(max = NOTICE_WIDTH)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (!state.rereading) {
            LumoTvButton(text = stringResource(R.string.feature_watchlist_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun Filters(
    filters: List<WatchlistFilter>,
    selected: WatchlistFilter,
    onSelect: (WatchlistFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        filters.forEach { filter ->
            var focused by remember { mutableStateOf(false) }
            val isSelected = filter == selected
            Text(
                text = filterLabel(filter),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    focused -> LumoColors.OnAccent
                    isSelected -> LumoColors.Accent
                    else -> LumoColors.OnDarkMuted
                },
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .lumoTvFocus(focused, shape = LumoTvShapes.pill)
                    .clip(LumoTvShapes.pill)
                    .background(
                        when {
                            focused -> LumoColors.Accent
                            isSelected -> LumoColors.SurfaceRaised
                            else -> LumoColors.Surface
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(filter) }
                    .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.sm),
            )
        }
    }
}

@Composable
private fun Grid(
    state: WatchlistState,
    gridState: LazyGridState,
    requesters: MutableMap<String, FocusRequester>,
    arrivalId: String?,
    onSelect: (index: Int, entry: WatchlistEntry) -> Unit,
    onLoadMore: () -> Unit,
) {
    // Once per arrival, as on the library: an item removed from a phone meanwhile
    // must not yank the focus across the grid.
    LaunchedEffect(Unit) {
        val index = state.items.indexOfFirst { it.id == arrivalId }.coerceAtLeast(0)
        gridState.scrollToItem(index)
        withFrameNanos { }
        runCatching { requesters[state.items[index].id]?.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        // Room for the focus scale: a lazy grid clips to its bounds.
        contentPadding = PaddingValues(LumoSpacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(state.items, key = { _, entry -> entry.id }) { index, entry ->
            // One requester per composed card, registered by id while it is on
            // screen: what the focus return looks up after a scroll.
            val requester = remember { FocusRequester() }
            DisposableEffect(entry.id, requester) {
                requesters[entry.id] = requester
                onDispose { if (requesters[entry.id] === requester) requesters.remove(entry.id) }
            }
            Card(
                entry = entry,
                focusRequester = requester,
                onSelect = { onSelect(index, entry) },
            )
        }

        if (state.hasMore || state.loadMoreFailed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                    if (state.loadMoreFailed) {
                        Text(
                            text = stringResource(R.string.feature_watchlist_load_more_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = LumoColors.Error,
                        )
                    }
                    LumoTvButton(
                        text = stringResource(
                            when {
                                state.loadingMore -> R.string.feature_watchlist_loading
                                state.loadMoreFailed -> R.string.feature_watchlist_retry
                                else -> R.string.feature_watchlist_load_more
                            },
                        ),
                        onClick = onLoadMore,
                        enabled = !state.loadingMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(
    entry: WatchlistEntry,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val description = cardDescription(entry)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            // `clickable` alone: focusable and bound to the centre key, one target.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .semantics { contentDescription = description }
            .padding(LumoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(posterUrl = entry.cardPoster, title = entry.title)
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = cardStatusLabel(entry),
            style = MaterialTheme.typography.labelMedium,
            color = if (entry.available) LumoColors.OnDarkMuted else LumoColors.Error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Empty(actions: WatchlistActions, focusArrival: Boolean) {
    val films = remember { FocusRequester() }
    // Not after the last removal: the focus goes to the title then (WL-03).
    LaunchedEffect(Unit) { if (focusArrival) runCatching { films.requestFocus() } }

    Column(
        modifier = Modifier.widthIn(max = MESSAGE_WIDTH),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_watchlist_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
        )
        Text(
            text = stringResource(R.string.feature_watchlist_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
            LumoTvButton(
                text = stringResource(R.string.feature_watchlist_open_films),
                onClick = actions.onOpenFilms,
                primary = true,
                focusRequester = films,
            )
            LumoTvButton(
                text = stringResource(R.string.feature_watchlist_open_series),
                onClick = actions.onOpenSeriesCatalogue,
            )
        }
    }
}

@Composable
private fun FilterEmpty(filter: WatchlistFilter, onShowAll: () -> Unit) {
    val showAll = remember { FocusRequester() }
    LaunchedEffect(filter) { runCatching { showAll.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
        Text(
            text = filterEmptyLabel(filter),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
        )
        LumoTvButton(
            text = stringResource(R.string.feature_watchlist_show_all),
            onClick = onShowAll,
            primary = true,
            focusRequester = showAll,
        )
    }
}

/**
 * The compact panel (WL-03), in a dialog window: the remote is held inside it, and
 * `BACK` closes it. Arrival on the removal; "Close" one `RIGHT` away.
 */
@Composable
private fun UnavailablePanel(
    entry: WatchlistEntry,
    state: WatchlistState,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val removal = state.removal?.takeIf { it.itemId == entry.id }
    val remove = remember { FocusRequester() }
    val close = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { remove.requestFocus() }
        }

        Column(
            modifier = Modifier
                .width(PANEL_WIDTH)
                .clip(LumoTvShapes.large)
                .background(LumoColors.Surface)
                .padding(LumoSpacing.lg)
                .testTag(PANEL_TAG),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                color = LumoColors.OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = cardStatusLabel(entry),
                style = MaterialTheme.typography.titleMedium,
                color = LumoColors.Error,
            )
            Text(
                text = stringResource(R.string.feature_watchlist_panel_body),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
            )
            panelNote(state, removal)?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumoColors.Error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
                LumoTvButton(
                    text = stringResource(
                        when {
                            removal?.busy == true -> R.string.feature_watchlist_removing
                            removal?.unconfirmed == true -> R.string.feature_watchlist_retry
                            else -> R.string.feature_watchlist_remove
                        },
                    ),
                    // Kept focusable when it cannot act (offline, re-reading): the
                    // note above says why, and the remote is not thrown elsewhere.
                    onClick = { if (state.canRemove) onRemove() },
                    primary = true,
                    focusRequester = remove,
                )
                LumoTvButton(
                    text = stringResource(R.string.feature_watchlist_close),
                    onClick = { if (removal?.busy != true) onClose() },
                    focusRequester = close,
                )
            }
        }
    }
}

internal const val TITLE_TAG = "watchlist-title"
internal const val PANEL_TAG = "watchlist-panel"

/** Four posters across a 1080p panel beside the rail, readable at three metres. */
private const val GRID_COLUMNS = 4
private val NOTICE_WIDTH = 900.dp
private val MESSAGE_WIDTH = 900.dp
private val PANEL_WIDTH = 640.dp
