package tv.lumo.android.feature.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.lumo.android.core.data.DirectView
import tv.lumo.android.core.data.EmptyGrid
import tv.lumo.android.core.data.EpgDay
import tv.lumo.android.core.data.EpgDayWindow
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.emptyGridOf
import tv.lumo.android.core.data.labelRes
import tv.lumo.android.core.data.messageRes
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.wording
import tv.lumo.android.core.designsystem.component.LumoFavoriteGroupChoice
import tv.lumo.android.core.designsystem.component.LumoTvFavoriteGroupSheet
import tv.lumo.android.core.designsystem.component.LumoTvSourceNotice
import tv.lumo.android.core.designsystem.component.LumoTvStateMessage
import tv.lumo.android.core.designsystem.effect.PollWhile
import tv.lumo.android.core.designsystem.effect.SOURCE_NOTICE_POLL_MILLIS
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * Direct on a television (US-16, S9-03): a scrolling column of categories on the
 * left, the channel grid on the right, one line of key hints at the bottom.
 *
 * <h2>The band became a column (S9-04-03)</h2>
 *
 * The filters used to be a strip above the grid. The 19 September review put
 * them in a **scrolling column** at the left of the cards, on web and on
 * television, and that is the layout here: `Toutes`, `Repris`, the favourite
 * groups that hold something, then the source's categories. The column is
 * outside the content area, so **it is there even when the search finds
 * nothing** — an empty result swaps the grid for a sentence and two ways out,
 * and never removes the shelf somebody might want next.
 *
 * `Repris` stays second, where S4-08 put it: it is what somebody turning the
 * television on reaches for most often, and the one shelf they did not have to
 * build.
 *
 * <h2>Two views, one screen (S9-04), one set of filters</h2>
 *
 * The `Chaînes`/`Guide` pills sit in the header, and the search field above the
 * content is shared by both, exactly as on the phone. Stepping into the Guide
 * keeps the category and the query (GD-01); a change of source drops both and
 * opens the new source on the view it was left on (GD-02, GD-03), which is the
 * shared [tv.lumo.android.core.data.repository.DirectViewRepository]'s job and
 * not this screen's. The Guide draws [GuideGridTv], the hour grid with the
 * reference-hour D-pad rule (S9-05-03).
 *
 * <h2>What is on, under each card and in the grid</h2>
 *
 * The guide arrived with C1. Each card carries the title of the programme on
 * air; the Guide's hour grid reads its **displayed day** whole, one grouped
 * window per page of channels. Both come from **one request per page**: the
 * screen watches which indices are visible, rounds them to a page of
 * [EPG_PAGE_SIZE] with [epgPageIds] — the same function the Guide uses — and
 * hands the page's channel ids to the view model, which asks once for what it
 * does not hold. Never a request per card, and never per cell — that is the trap
 * S7-04 names.
 *
 * Where the guide has nothing — no `tvg_id`, no guide on the source, not loaded
 * — a card shows its name and number and **nothing else**: no placeholder, no
 * "unavailable". The `[mock] données manquantes` badge that stood here while
 * the server's `epg` package was empty is gone with it (S7-03).
 *
 * <h2>Focus</h2>
 *
 * Arrival lands on the first channel, a return from the player on the channel
 * that was being watched (US-10). **A skeleton is never a target**: Paging draws
 * a window it has not loaded yet at the size of a card, and [arrivalFocusIndex]
 * skips it rather than stopping the remote on a dead end (S9-04-03). `LEFT` from
 * the grid reaches the category column and, beyond it, the rail; `UP` from the
 * first row reaches the search field and the view pills.
 */
@Composable
fun LiveTvScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    returnedChannelId: String? = null,
    onReturnHandled: () -> Unit = {},
    requestedView: DirectView? = null,
    onRequestHandled: () -> Unit = {},
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    // The home screen's explicit entry (S9-04-04): "All channels" or "TV guide"
    // asked for a view, and it beats what the source remembers for this open. The
    // request is consumed here so that the effect can fire again for the *same*
    // view on a later press — `requestedView` goes back to null in between.
    LaunchedEffect(requestedView) {
        if (requestedView != null) {
            viewModel.onExplicitEntry(requestedView)
            onRequestHandled()
        }
    }

    // A refresh on display has to move, and to go away when it ends (US-024).
    PollWhile(
        active = state.notice is SourceNotice.Refreshing,
        everyMillis = SOURCE_NOTICE_POLL_MILLIS,
        onTick = viewModel::refreshSource,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        when (state.step) {
            LiveStep.Loading -> Centered {
                Text(
                    text = stringResource(R.string.feature_live_tv_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            LiveStep.NoSource -> Message(
                title = stringResource(R.string.feature_live_no_source_title),
                body = stringResource(R.string.feature_live_no_source_body),
            )

            LiveStep.NeedsChoice -> Message(
                title = stringResource(R.string.feature_live_needs_choice_title),
                body = stringResource(R.string.feature_live_needs_choice_body),
            )

            // The two faces of a first import, which used to share one "not
            // ready" sentence. Each has a button — "My sources" — so that `RIGHT`
            // from the rail lands somewhere while there is no grid to land on.
            LiveStep.Importing -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_first_import_title),
                detail = stringResource(
                    (state.notice as? SourceNotice.Refreshing)?.step.labelRes(),
                ),
                body = stringResource(DataR.string.core_data_first_import_body),
                actionLabel = stringResource(DataR.string.core_data_notice_open_sources),
                onAction = onOpenSources,
            )

            LiveStep.ImportFailed -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_first_import_failed_title),
                detail = stringResource(
                    (state.notice as? SourceNotice.Failed)?.code.messageRes(),
                ),
                isError = true,
                body = stringResource(DataR.string.core_data_first_import_failed_body),
                actionLabel = stringResource(DataR.string.core_data_notice_open_sources),
                onAction = onOpenSources,
            )

            // An outage with nothing cached (US-024, "Indisponibilité et hors
            // ligne"): not "no source", and two targets so that `RIGHT` from
            // the rail lands somewhere — "try again" first, then "change source".
            LiveStep.Unreachable -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_unreached_title),
                body = stringResource(DataR.string.core_data_unreached_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = viewModel::refreshSource,
                secondaryActionLabel = stringResource(DataR.string.core_data_notice_change_source),
                onSecondaryAction = onOpenSources,
            )

            LiveStep.Browsing -> Browsing(
                state = state,
                channels = channels,
                onChannelsVisible = viewModel::onChannelsVisible,
                onDayVisible = viewModel::onDayVisible,
                onOpenSources = onOpenSources,
                onRetry = viewModel::refresh,
                onRefreshSource = viewModel::refreshSource,
                onSelectView = viewModel::onDirectViewSelected,
                onSearchChanged = viewModel::onSearchChanged,
                onSearchCleared = viewModel::onSearchCleared,
                onSelectCategory = viewModel::onCategorySelected,
                onSelectGroup = viewModel::onGroupSelected,
                onSelectRecent = viewModel::onRecentSelected,
                onFavorite = viewModel::onFavoriteLongPressed,
                onPlay = onPlay,
                returnedChannelId = returnedChannelId,
                onReturnHandled = onReturnHandled,
            )
        }

        state.sheetChannel?.let { channel ->
            val inGroups = state.groupsOf(channel.id)
            LumoTvFavoriteGroupSheet(
                title = channel.name,
                groups = state.groups.map { group ->
                    LumoFavoriteGroupChoice(
                        id = group.id,
                        label = group.displayName(),
                        checked = group.id in inGroups,
                    )
                },
                onToggle = { groupId, checked ->
                    viewModel.onGroupToggled(channel.id, groupId, checked)
                },
                onDismiss = viewModel::onGroupSheetDismissed,
            )
        }
    }
}

@Composable
private fun Browsing(
    state: LiveState,
    channels: LazyPagingItems<Channel>,
    onChannelsVisible: (List<String>) -> Unit,
    onDayVisible: (EpgDay, List<String>) -> Unit,
    onOpenSources: () -> Unit,
    onRetry: () -> Unit,
    onRefreshSource: () -> Unit,
    onSelectView: (DirectView) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSearchCleared: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectRecent: () -> Unit,
    onFavorite: (Channel) -> Unit,
    onPlay: (channelId: String, name: String?) -> Unit,
    returnedChannelId: String?,
    onReturnHandled: () -> Unit,
) {
    val focusTarget = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var focusIndex by remember { mutableIntStateOf(0) }
    var focusSettled by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    // The day tab the Guide is on. Null means "today", which Maintenant resets
    // to; the five days themselves are derived from the clock below.
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    // Coming back from the player (US-10): the channel that was being watched,
    // not the head of the grid. A return owns the focus, so the arrival pass
    // below stands down and does not move it.
    LaunchedEffect(returnedChannelId, channels.itemCount) {
        val target = returnedChannelId ?: return@LaunchedEffect
        val index = channels.itemSnapshotList.items.indexOfFirst { it.id == target }

        if (index < 0) return@LaunchedEffect

        focusIndex = index
        focusSettled = true
        gridState.scrollToItem(index)
        onReturnHandled()
    }

    // Arrival focus, and the rule that a skeleton never takes it (S9-04-03).
    // Read from the Paging snapshot so it re-runs when a page actually loads in,
    // it settles once and then leaves the grid to the viewer.
    LaunchedEffect(channels, returnedChannelId) {
        if (returnedChannelId != null) return@LaunchedEffect
        snapshotFlow {
            arrivalFocusIndex(
                count = channels.itemCount,
                isLoaded = { index -> channels.itemSnapshotList.getOrNull(index) != null },
                current = 0,
            )
        }
            .distinctUntilChanged()
            .collect { index -> if (!focusSettled) focusIndex = index }
    }

    LaunchedEffect(focusIndex, channels.itemCount, state.view) {
        if (channels.itemCount == 0) return@LaunchedEffect
        if (channels.itemSnapshotList.getOrNull(focusIndex) == null) return@LaunchedEffect
        if (runCatching { focusTarget.requestFocus() }.getOrDefault(false)) {
            focusSettled = true
        }
    }

    // The page on display, for the guide. Read from the grid's layout and from
    // Paging's snapshot — both are state, so this re-runs when a page loads in
    // or the grid scrolls, and `distinctUntilChanged` keeps a scroll within a
    // page from asking anything. One grouped request per page, never per card.
    LaunchedEffect(gridState, channels) {
        snapshotFlow {
            epgPageIds(
                visible = gridState.layoutInfo.visibleItemsInfo.map { it.index },
                itemCount = channels.itemCount,
                idAt = { index -> channels.itemSnapshotList.getOrNull(index)?.id },
            )
        }
            .distinctUntilChanged()
            .collect(onChannelsVisible)
    }

    Column(
        modifier = Modifier.padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_live_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
            )
            ViewToggle(view = state.view, onSelectView = onSelectView)
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_live_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            // In the header line, compact: the grid below has the height of two
            // rows of cards, and a notice of its own height would push the second
            // off the panel. A refresh is text; a failure adds the one stop this
            // line has — "My sources", reached by `UP` from the search field — and
            // an outage two, "try again" then "change source", on the same line.
            state.notice?.let { notice ->
                val wording = notice.wording()
                LumoTvSourceNotice(
                    title = stringResource(wording.title),
                    message = stringResource(wording.message),
                    hint = wording.hint?.let { stringResource(it) },
                    isError = wording.failed,
                    actionLabel = stringResource(wording.action).takeIf { wording.actionable },
                    onAction = onOpenSources,
                    retryLabel = wording.retry?.let { stringResource(it) },
                    onRetry = onRefreshSource,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // A grid that failed to load is not an empty grid (US-024), and on a
        // television it needs a target: without one `RIGHT` from the rail is dead.
        if (
            state.filter == CatalogueFilter.All &&
            emptyGridOf(channels.itemCount, state.refreshing, state.refreshFailed) ==
            EmptyGrid.Unavailable
        ) {
            LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_catalogue_unavailable_title),
                body = stringResource(DataR.string.core_data_catalogue_unavailable_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = onRetry,
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xl),
        ) {
            // The column is a sibling of the content, so a search that finds
            // nothing takes the grid away and leaves the shelf in place: it stays
            // reachable, which is the whole point of the move (S9-04-03).
            FiltersColumn(
                groups = state.groupsWithChannels,
                categories = state.categories,
                filter = state.filter,
                hasRecent = state.recent.isNotEmpty(),
                onSelectCategory = onSelectCategory,
                onSelectGroup = onSelectGroup,
                onSelectRecent = onSelectRecent,
                modifier = Modifier
                    .width(FILTER_COLUMN_WIDTH)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            ) {
                TvSearchField(
                    value = state.search,
                    onValueChange = onSearchChanged,
                    onClear = onSearchCleared,
                )

                val nothingFound = state.searchFoundNothing(
                    itemCount = channels.itemCount,
                    loading = channels.loadState.refresh is LoadState.Loading,
                )

                when {
                    // A search that found nothing is not an empty catalogue: say
                    // which it is, and give the two ways out the design names —
                    // clear the query, or widen the filter to Toutes. The category
                    // column on the left is still there to be reached.
                    nothingFound -> TvEmptySearch(
                        query = state.search,
                        onClear = onSearchCleared,
                        onAll = { onSelectCategory(null) },
                    )

                    // The hour grid (S9-05-03): channels as rows, hours as
                    // columns, five day tabs J−1→J+3 and Maintenant. It reports
                    // its page for the day it draws, and the view model asks
                    // once for what it does not hold — never one request per
                    // cell.
                    state.view == DirectView.Guide -> {
                        val zone = remember { ZoneId.systemDefault() }
                        val days = remember(now, zone) { EpgDayWindow.around(now, zone) }
                        val today = days[EpgDayWindow.DAYS_BEFORE.toInt()]
                        val activeDay = days.firstOrNull { it.date == selectedDay } ?: today

                        GuideGridTv(
                            state = state,
                            channels = channels,
                            days = days,
                            activeDay = activeDay,
                            today = today.date,
                            now = now,
                            onNow = {
                                now = Instant.now()
                                selectedDay = null
                            },
                            onSelectDay = { selectedDay = it.date },
                            onPlay = onPlay,
                            onDayVisible = onDayVisible,
                            onSeeChannels = { onSelectView(DirectView.Channels) },
                        )
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(GRID_COLUMNS),
                        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                        state = gridState,
                        contentPadding = PaddingValues(LumoSpacing.sm),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                        ) { index ->
                            val channel = channels[index]
                            ChannelCard(
                                channel = channel,
                                onAir = channel?.let { state.onAir[it.id] },
                                favorited = channel != null && state.isFavorited(channel.id),
                                onPlay = onPlay,
                                onFavorite = onFavorite,
                                // One requester, moved to whichever card is the
                                // target: the first on arrival, the one just watched
                                // on the way back.
                                modifier = if (index == focusIndex) {
                                    Modifier.focusRequester(focusTarget)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.feature_live_tv_grid_hints),
            style = MaterialTheme.typography.labelLarge,
            color = LumoColors.OnDarkMuted,
        )
    }
}

/**
 * The `Chaînes`/`Guide` pills (S9-04). Two values, so two pills and not a tab
 * row: the phone draws the same pair, and a single vocabulary across surfaces is
 * what lets the shared search and filter mean the same thing on each of them.
 */
@Composable
private fun ViewToggle(view: DirectView, onSelectView: (DirectView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        CategoryChip(
            label = stringResource(R.string.feature_live_view_channels),
            selected = view == DirectView.Channels,
            onClick = { onSelectView(DirectView.Channels) },
        )
        CategoryChip(
            label = stringResource(R.string.feature_live_view_guide),
            selected = view == DirectView.Guide,
            onClick = { onSelectView(DirectView.Guide) },
        )
    }
}

/**
 * The name search (S9-04), shared by Chaînes and Guide and kept across the
 * switch (GD-01). It filters by channel name over the local cache — it answers
 * offline — and it leaves the active filter chip alone.
 *
 * A single-line field focused from the D-pad; the centre key opens the
 * television's own keyboard. Up and down leave the field for the view pills and
 * the content, because a single line has no vertical cursor to move.
 */
@Composable
private fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.feature_live_search)
    val clearDescription = stringResource(R.string.feature_live_search_clear)
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .lumoTvFocus(focused, shape = LumoTvShapes.pill)
            .clip(LumoTvShapes.pill)
            .background(LumoColors.Surface)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.feature_live_glyph_search),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDarkMuted,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_live_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LumoColors.OnDark),
                cursorBrush = SolidColor(LumoColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = description },
            )
        }

        if (value.isNotEmpty()) {
            Text(
                text = stringResource(R.string.feature_live_glyph_clear),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDarkMuted,
                modifier = Modifier
                    .clip(LumoTvShapes.pill)
                    .clickable(onClick = onClear)
                    .padding(LumoSpacing.xs)
                    .semantics { contentDescription = clearDescription },
            )
        }
    }
}

/**
 * A search that found nothing (S9-04), versus one still loading.
 *
 * The same two ways out as the phone: clear the query, or widen the filter back
 * to Toutes. The category column beside this message is untouched, so the shelf
 * that narrowed the result is still one `LEFT` away.
 */
@Composable
private fun TvEmptySearch(query: String, onClear: () -> Unit, onAll: () -> Unit) {
    LumoTvStateMessage(
        title = stringResource(R.string.feature_live_search_empty_title),
        body = stringResource(R.string.feature_live_search_empty_body, query),
        actionLabel = stringResource(R.string.feature_live_search_clear),
        onAction = onClear,
        secondaryActionLabel = stringResource(R.string.feature_live_all_categories),
        onSecondaryAction = onAll,
    )
}

/**
 * The scrolling category column (S9-04-03).
 *
 * `Toutes` first and the state the screen opens on, then `Repris` in second
 * position — only when something was watched, and never a chip that filters
 * onto nothing — then the account's groups that hold something, then the
 * source's categories. It is the phone's strip laid vertically; a column rather
 * than a strip because a television has the height for one and the width for
 * four readable cards, and the two do not fit side by side any other way.
 */
@Composable
private fun FiltersColumn(
    groups: List<FavoriteGroup>,
    categories: List<Category>,
    filter: CatalogueFilter,
    hasRecent: Boolean,
    onSelectCategory: (String?) -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        contentPadding = PaddingValues(LumoSpacing.xs),
    ) {
        item(key = "all") {
            CategoryChip(
                label = stringResource(R.string.feature_live_all_categories),
                selected = filter is CatalogueFilter.All,
                onClick = { onSelectCategory(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (hasRecent) {
            item(key = "recent") {
                CategoryChip(
                    label = stringResource(R.string.feature_live_recent),
                    selected = filter is CatalogueFilter.Recent,
                    onClick = onSelectRecent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(groups, key = { "group-" + it.id }) { group ->
            CategoryChip(
                label = group.displayName(),
                selected = (filter as? CatalogueFilter.Group)?.id == group.id,
                onClick = { onSelectGroup(group.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // The two blocks were separated by an interval and not a label on the
        // strip; a column keeps the same, with a gap in place of a heading line.
        if (groups.isNotEmpty() && categories.isNotEmpty()) {
            item(key = "gap") { Spacer(modifier = Modifier.height(LumoSpacing.md)) }
        }
        items(categories, key = { "category-" + it.id }) { category ->
            CategoryChip(
                label = category.channelCount
                    ?.let { stringResource(R.string.feature_live_category_count, category.name, it) }
                    ?: category.name,
                selected = (filter as? CatalogueFilter.Category)?.id == category.id,
                onClick = { onSelectCategory(category.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A chip is selected or focused, and the two are drawn apart: selection is the
 * light pill of the canvas (`Toutes`), focus is the shared outline. Neither is
 * a cyan fill.
 */
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            selected -> LumoColors.OnAccent
            focused -> LumoColors.OnDark
            else -> LumoColors.OnDarkMuted
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.pill)
            .clip(LumoTvShapes.pill)
            .background(
                when {
                    selected -> LumoColors.OnDark
                    focused -> LumoColors.SurfaceRaised
                    else -> LumoColors.Surface
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/**
 * One cell of the grid, as the canvas draws it: the name, the number under it
 * in a monospaced face, and the programme on air under that when the guide has
 * one (S7-04). The logo lives in the panel — at four columns a card is read by
 * its name, and a logo that small is a smudge.
 *
 * A null card is a window Paging has not loaded yet: drawn at full size so the
 * grid keeps its shape, and **not focusable** — `combinedClickable` is disabled
 * on it, so the D-pad never stops on a card with no channel behind it (S9-04-03).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: Channel?,
    onAir: EpgProgramme?,
    favorited: Boolean,
    onPlay: (channelId: String, name: String?) -> Unit,
    onFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .combinedClickable(
                enabled = channel != null,
                interactionSource = interactionSource,
                indication = null,
                onLongClick = { channel?.let(onFavorite) },
                onLongClickLabel = stringResource(R.string.feature_live_tv_favorite_hint),
            ) { channel?.let { onPlay(it.id, it.name) } }
            .padding(LumoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs, Alignment.CenterVertically),
    ) {
        // Smaller than body, and two lines: the canvas names its channels in
        // nine characters, real playlists in twenty-five.
        Text(
            text = channel?.name.orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            channel?.number?.let { number ->
                Text(
                    text = "%03d".format(number),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = if (focused) LumoColors.Accent else LumoColors.OnDarkMuted,
                )
            }
            if (favorited) {
                Text(
                    text = "♥",
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.Accent,
                )
            }
        }
        // Nothing under a card without a guide (S7-03): the line is absent,
        // not blank, and the card is a little shorter on the inside.
        onAir?.let { programme ->
            Text(
                text = programme.title,
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier.padding(LumoSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// Four columns, as the canvas: three metres away a name is still readable at
// this width on a 1080p panel, and a fifth column would not be.
private const val GRID_COLUMNS = 4

/** Two lines of name, the number, and one line of programme (S7-04). Two rows still fit a 1080p panel. */
private val CARD_HEIGHT = 140.dp

/**
 * The category column's width.
 *
 * Wide enough for a category name and its count at three metres, narrow enough
 * to leave the grid its four readable columns beside the rail — the arithmetic
 * the screen's documentation lays out. It is a fixed width and not a share:
 * a category name does not grow with the panel.
 */
private val FILTER_COLUMN_WIDTH = 200.dp
