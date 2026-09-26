package tv.lumo.android.feature.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.SubcomposeAsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.DirectView
import tv.lumo.android.core.data.EpgDayWindow
import tv.lumo.android.core.data.EmptyGrid
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.emptyGridOf
import tv.lumo.android.core.data.labelRes
import tv.lumo.android.core.data.messageRes
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.wording
import tv.lumo.android.core.designsystem.component.LumoFavoriteGroupChoice
import tv.lumo.android.core.designsystem.component.LumoFavoriteGroupSheet
import tv.lumo.android.core.designsystem.component.LumoSourceNotice
import tv.lumo.android.core.designsystem.component.LumoStateMessage
import tv.lumo.android.core.designsystem.effect.PollWhile
import tv.lumo.android.core.designsystem.effect.SOURCE_NOTICE_POLL_MILLIS
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The channel list (US-08), laid out as the M4 mock-up draws it
 * (docs/design/canvas, mobile artboard 15): a heading with a round search
 * button, a strip of pill-shaped category chips, and one card per channel —
 * logo on the left, name over "category · quality", chevron on the right.
 *
 * <h2>Fifteen thousand channels is an ordinary source</h2>
 *
 * Which is why the list is a `LazyColumn` fed by Paging 3 reading windows
 * straight out of SQLite, and never a `List<Channel>`. Materialising a catalogue
 * that size allocates on every emission and drops frames on a phone; the story
 * asks for it to stay smooth past five hundred, and the way to be sure of that is
 * never to hold more than a screenful.
 *
 * <h2>The offline indicator is small on purpose</h2>
 *
 * US-08 asks for a *discreet* one, and it is right to: the list works offline, so
 * the state is normal rather than a failure. A banner would say "something is
 * wrong" about a screen that is doing exactly what it was built to do. What would
 * be wrong is saying nothing — a catalogue silently a week old is the failure a
 * user cannot diagnose.
 *
 * <h2>Logos are the user's own, or nothing</h2>
 *
 * `tvg-logo` from their playlist. Lumo ships no bundled artwork and no fallback
 * image (AGENTS.md §1), so a channel with no logo gets its initial — never a
 * picture of ours standing in for one of theirs.
 *
 * <h2>What the mock-up does not show, and is kept anyway</h2>
 *
 * The favourite heart (US-12) is smaller and muted, but still its own 48 dp
 * target: the mock-up wins on looks, not on taking a gesture away. A long press
 * on the whole card opens the group picker too. The refresh button sits beside
 * the search button because the list has one and the mock-up simply did not draw
 * it.
 *
 * <h2>Two views, one screen (S9-04-02)</h2>
 *
 * A pill pair switches this screen between **Chaînes** and **Guide** without
 * leaving the destination. The filter strip and the name search sit above both,
 * so the two views genuinely share them: picking a category and stepping into
 * the Guide keeps the category, and a search typed in one is there in the other
 * (GD-01). Which view a source opens on is remembered by the repository, per
 * device and per source; a change of source drops the search and the filter and
 * opens the new source's own view (GD-02, GD-03). A search that finds nothing
 * says so and offers the two ways out the design names.
 */
@Composable
fun LiveMobileScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    requestedView: DirectView? = null,
    onRequestHandled: () -> Unit = {},
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    // The clock the programme sheet reads (S9-06-01). Kept here, beside the
    // overlays, so the panel can be re-read when a programme ends without the
    // Guide's own `now` being involved. Reset whenever a new programme opens.
    var sheetNow by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state.programmeSheet?.programme?.id) { sheetNow = Instant.now() }

    // The home screen's explicit entry (S9-04-04): "All channels" or "TV guide"
    // asked for a view, and it beats what the source remembers for this open. The
    // request is consumed here so that the effect can fire again for the *same*
    // view on a later press — `requestedView` goes back to null in between, which
    // is what a `LaunchedEffect` keyed on the value alone would not give us.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state.step) {
            LiveStep.Loading -> Centered { CircularProgressIndicator() }

            LiveStep.NoSource -> Message(
                title = stringResource(R.string.feature_live_no_source_title),
                body = stringResource(R.string.feature_live_no_source_body),
            )

            LiveStep.NeedsChoice -> Message(
                title = stringResource(R.string.feature_live_needs_choice_title),
                body = stringResource(R.string.feature_live_needs_choice_body),
            )

            // The two faces of a first import, which used to share one "not
            // ready" sentence: one says wait and shows the real step, the other
            // says why and where it is fixed (US-024).
            LiveStep.Importing -> LumoStateMessage(
                title = stringResource(DataR.string.core_data_first_import_title),
                detail = stringResource(
                    (state.notice as? SourceNotice.Refreshing)?.step.labelRes(),
                ),
                body = stringResource(DataR.string.core_data_first_import_body),
                actionLabel = stringResource(DataR.string.core_data_notice_open_sources),
                onAction = onOpenSources,
            )

            LiveStep.ImportFailed -> LumoStateMessage(
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
            // ligne"): not "no source", and the two ways on the story names.
            LiveStep.Unreachable -> LumoStateMessage(
                title = stringResource(DataR.string.core_data_unreached_title),
                body = stringResource(DataR.string.core_data_unreached_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = viewModel::refreshSource,
                secondaryActionLabel = stringResource(DataR.string.core_data_notice_change_source),
                onSecondaryAction = onOpenSources,
            )

            LiveStep.Browsing -> {
                val guideList = rememberLazyListState()
                val dayList = rememberLazyListState()
                val scope = rememberCoroutineScope()
                var now by remember { mutableStateOf(Instant.now()) }
                var searchOpen by remember { mutableStateOf(false) }
                // The day the mobile Guide's channel view is on. Null is "today",
                // which Maintenant resets to; the five days are derived below.
                var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

                // GD-13: Android's own Back climbs out of a channel's day the
                // same way the header's ‹ does — one level, back to "En ce
                // moment" — and only leaves the Direct destination when no day
                // is open. Enabled on the level rule itself, so the gesture and
                // the drawn level can never disagree.
                val closeChannelDay = {
                    viewModel.onChannelDayClosed()
                    selectedDay = null
                }
                BackHandler(enabled = state.guideChannelDayOpen() && state.programmeSheet == null) { closeChannelDay() }

                DirectHeader(
                    state = state,
                    searchOpen = searchOpen || state.search.isNotEmpty(),
                    onSearchOpen = { searchOpen = it },
                    onSelectView = viewModel::onDirectViewSelected,
                    onSearchChanged = viewModel::onSearchChanged,
                    onRefresh = viewModel::refresh,
                )

                // Over the list and never instead of it: the catalogue stays
                // browsable while the source refreshes and after a failed attempt.
                state.notice?.let { notice ->
                    val wording = notice.wording()
                    LumoSourceNotice(
                        title = stringResource(wording.title),
                        message = stringResource(wording.message),
                        hint = wording.hint?.let { stringResource(it) },
                        isError = wording.failed,
                        actionLabel = stringResource(wording.action),
                        onAction = onOpenSources,
                        // Only an outage has one: it reads the list again.
                        retryLabel = wording.retry?.let { stringResource(it) },
                        onRetry = viewModel::refreshSource,
                        modifier = Modifier.padding(
                            horizontal = LumoSpacing.lg,
                            vertical = LumoSpacing.xs,
                        ),
                    )
                }

                Filters(
                    categories = state.categories,
                    groups = state.groupsWithChannels,
                    filter = state.filter,
                    onSelectCategory = viewModel::onCategorySelected,
                    onSelectGroup = viewModel::onGroupSelected,
                )

                // A favourite write needs the network, and nothing is queued for
                // later: US-12 asks for an offline change to be refused out loud
                // rather than silently lost. Said here, next to the list the
                // change was meant for, and dismissed by tapping it.
                state.favoriteError?.let {
                    Text(
                        text = stringResource(R.string.feature_live_favorite_failed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = viewModel::onFavoriteErrorShown)
                            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.xs),
                    )
                }

                val nothingFound = state.searchFoundNothing(
                    itemCount = channels.itemCount,
                    loading = channels.loadState.refresh is LoadState.Loading,
                )

                when {
                    // A search that found nothing is not an empty catalogue: say
                    // which it is, and give the two ways out the design names —
                    // clear the query, or widen the filter to Toutes (S9-04-02).
                    nothingFound -> EmptySearch(
                        query = state.search,
                        onClear = viewModel::onSearchCleared,
                        onAll = { viewModel.onCategorySelected(null) },
                    )

                    // A channel's day (S9-05-04): the second level of the mobile
                    // Guide, opened from a row below. One grouped read for the
                    // channel and the day shown; Back returns to "En ce moment"
                    // with the list's position kept (GD-13).
                    state.view == DirectView.Guide && state.dayChannel != null -> {
                        val zone = remember { ZoneId.systemDefault() }
                        val days = remember(now, zone) { EpgDayWindow.around(now, zone) }
                        val today = days[EpgDayWindow.DAYS_BEFORE.toInt()]
                        val activeDay = days.firstOrNull { it.date == selectedDay } ?: today
                        GuideDayMobile(
                            state = state,
                            channel = requireNotNull(state.dayChannel),
                            days = days,
                            activeDay = activeDay,
                            today = today.date,
                            now = now,
                            onBack = closeChannelDay,
                            onSelectDay = { selectedDay = it.date },
                            onNow = {
                                now = Instant.now()
                                selectedDay = null
                            },
                            onDayVisible = viewModel::onDayVisible,
                            onOpenProgramme = viewModel::onProgrammeOpened,
                            listState = dayList,
                        )
                    }

                    // "En ce moment", one line per channel of the filtered result,
                    // current then next (S9-04-05). The list reports its page and
                    // the tracker asks once for what it does not hold — never one
                    // request per card. A tap opens the channel's day (S9-05-04),
                    // it does not play: the design keeps launch to the Chaînes view.
                    state.view == DirectView.Guide -> GuideNowList(
                        state = state,
                        channels = channels,
                        now = now,
                        onNow = {
                            now = Instant.now()
                            scope.launch { guideList.scrollToItem(0) }
                        },
                        onOpenChannel = viewModel::onChannelDayOpened,
                        onPageVisible = viewModel::onGuideVisible,
                        listState = guideList,
                    )

                    else -> {
                        // A list that failed to load is not an empty list (US-024).
                        if (
                            state.filter == CatalogueFilter.All &&
                            emptyGridOf(channels.itemCount, state.refreshing, state.refreshFailed) ==
                            EmptyGrid.Unavailable
                        ) {
                            LumoStateMessage(
                                title = stringResource(DataR.string.core_data_catalogue_unavailable_title),
                                body = stringResource(DataR.string.core_data_catalogue_unavailable_body),
                                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                                onAction = viewModel::refresh,
                            )
                        } else LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = LumoSpacing.lg,
                                vertical = LumoSpacing.md,
                            ),
                            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                        ) {
                            items(
                                count = channels.itemCount,
                                key = channels.itemKey { it.id },
                            ) { index ->
                                // Null is a placeholder Paging has not loaded yet.
                                // It is drawn as a row of the right height so the
                                // scrollbar keeps its size on a fifteen-thousand-
                                // channel list.
                                val channel = channels[index]
                                ChannelRow(
                                    channel = channel,
                                    categoryName = channel?.categoryId?.let { id ->
                                        state.categories.firstOrNull { it.id == id }?.name
                                    },
                                    favorited = channel != null && state.isFavorited(channel.id),
                                    onPlay = onPlay,
                                    onFavorite = viewModel::onFavoriteClicked,
                                    onFavoriteLongPress = viewModel::onFavoriteLongPressed,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.sheetChannel?.let { channel ->
        val inGroups = state.groupsOf(channel.id)
        LumoFavoriteGroupSheet(
            groups = state.groups.map { group ->
                LumoFavoriteGroupChoice(
                    id = group.id,
                    label = group.displayName(),
                    checked = group.id in inGroups,
                )
            },
            createLabel = stringResource(R.string.feature_live_favorite_new_group),
            createPlaceholder = stringResource(R.string.feature_live_favorite_group_name),
            confirmLabel = stringResource(R.string.feature_live_favorite_group_create),
            onToggle = { groupId, checked ->
                viewModel.onGroupToggled(channel.id, groupId, checked)
            },
            onCreate = { name -> viewModel.onGroupCreated(channel.id, name) },
            onDismiss = viewModel::onGroupSheetDismissed,
        )
    }

    // The programme sheet (S9-06-01): a panel from the bottom, over the guide.
    // Back closes it first (the handler below), then the guide's own level.
    BackHandler(enabled = state.programmeSheet != null) { viewModel.onProgrammeClosed() }
    state.programmeSheet?.let { sheet ->
        ProgrammeSheetMobile(
            sheet = sheet,
            now = sheetNow,
            onClose = viewModel::onProgrammeClosed,
            onWatch = { open ->
                // GD-08: the moment is read again at the press.
                if (watchAllowed(open.programme, Instant.now())) {
                    // GD-09: playback leaves the sheet behind (review D2).
                    viewModel.onProgrammeClosed()
                    onPlay(open.channelId, open.channelName)
                }
            },
            onTimePassed = { sheetNow = Instant.now() },
        )
    }
}

/**
 * The Direct header (S9-04-02): the Chaînes/Guide switch, the two round buttons,
 * the name search and the two discreet notices.
 *
 * The switch is two pills rather than a Material tab row — there are exactly two
 * views, and the phone mock-up draws them side by side. The search opens under
 * the buttons and stays open while it holds text, so a rotation does not hide a
 * query the user is still reading.
 */
@Composable
private fun DirectHeader(
    state: LiveState,
    searchOpen: Boolean,
    onSearchOpen: (Boolean) -> Unit,
    onSelectView: (DirectView) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                CategoryChip(
                    label = stringResource(R.string.feature_live_view_channels),
                    selected = state.view == DirectView.Channels,
                    onClick = { onSelectView(DirectView.Channels) },
                )
                CategoryChip(
                    label = stringResource(R.string.feature_live_view_guide),
                    selected = state.view == DirectView.Guide,
                    onClick = { onSelectView(DirectView.Guide) },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.refreshing) {
                    Box(modifier = Modifier.size(ROUND_BUTTON), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(LumoSpacing.md),
                        )
                    }
                } else {
                    RoundButton(
                        glyph = stringResource(R.string.feature_live_glyph_refresh),
                        description = stringResource(R.string.feature_live_refresh),
                        onClick = onRefresh,
                    )
                }

                RoundButton(
                    glyph = stringResource(R.string.feature_live_glyph_search),
                    description = stringResource(R.string.feature_live_search),
                    onClick = { onSearchOpen(!searchOpen) },
                )
            }
        }

        if (searchOpen) {
            SearchField(
                value = state.search,
                onValueChange = onSearchChanged,
                onClear = { onSearchChanged("") },
            )
        }

        // Discreet, and only when it is true: the muted label rather than a
        // banner, because serving the cache is what this screen does well.
        if (state.origin == DataOrigin.Cache) {
            Notice(
                text = stringResource(R.string.feature_live_offline),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.refreshFailed) {
            Notice(
                text = stringResource(R.string.feature_live_refresh_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The name search (S9-04-02). It filters the list by channel name and leaves the
 * active filter chip alone (GD-01); it is asked of the local cache, so it still
 * answers with no network.
 */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onClear: () -> Unit) {
    val description = stringResource(R.string.feature_live_search)
    val clearDescription = stringResource(R.string.feature_live_search_clear)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_live_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description },
            )
        }

        if (value.isNotEmpty()) {
            Text(
                text = stringResource(R.string.feature_live_glyph_clear),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(LumoSpacing.xs)
                    .semantics { contentDescription = clearDescription },
            )
        }
    }
}

/**
 * A search that found nothing (S9-04-02).
 *
 * The two ways out are the design's: clear the query, or widen the filter back to
 * Toutes — one of the two is what narrowed the list to nothing.
 */
@Composable
private fun EmptySearch(query: String, onClear: () -> Unit, onAll: () -> Unit) {
    LumoStateMessage(
        title = stringResource(R.string.feature_live_search_empty_title),
        body = stringResource(R.string.feature_live_search_empty_body, query),
        actionLabel = stringResource(R.string.feature_live_search_clear),
        onAction = onClear,
        secondaryActionLabel = stringResource(R.string.feature_live_all_categories),
        onSecondaryAction = onAll,
    )
}

@Composable
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = LumoSpacing.xs),
    )
}

/** A 48 dp disc on the first surface level, carrying one glyph. */
@Composable
private fun RoundButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(ROUND_BUTTON)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The shared filters: Toutes, the account's groups that hold something, then the
 * source's categories (S9-04-02).
 *
 * A horizontal row rather than a side list: a phone is narrow, the names are
 * long, and the first thing someone does here is scan them. "All" comes first and
 * is what the screen opens on — a catalogue that opens on somebody's first
 * category is a catalogue that hides the rest. The same strip is drawn above both
 * views, so the filter is genuinely shared and not re-picked on each switch.
 */
@Composable
private fun Filters(
    categories: List<Category>,
    groups: List<FavoriteGroup>,
    filter: CatalogueFilter,
    onSelectCategory: (String?) -> Unit,
    onSelectGroup: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumoSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        modifier = Modifier.padding(bottom = LumoSpacing.xs),
    ) {
        item {
            CategoryChip(
                label = stringResource(R.string.feature_live_all_categories),
                selected = filter is CatalogueFilter.All,
                onClick = { onSelectCategory(null) },
            )
        }
        items(groups, key = { "group-" + it.id }) { group ->
            CategoryChip(
                label = group.displayName(),
                selected = (filter as? CatalogueFilter.Group)?.id == group.id,
                onClick = { onSelectGroup(group.id) },
            )
        }
        if (groups.isNotEmpty()) {
            item { Spacer(modifier = Modifier.size(LumoSpacing.lg)) }
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                // The count comes from the server and is null when it did not
                // count. An absent count is a chip without a number, not a zero.
                label = category.channelCount
                    ?.let { stringResource(R.string.feature_live_category_count, category.name, it) }
                    ?: category.name,
                selected = (filter as? CatalogueFilter.Category)?.id == category.id,
                onClick = { onSelectCategory(category.id) },
            )
        }
    }
}

/**
 * A pill: light ink on the selected one, the first surface level on the rest.
 * Never cyan — that colour means "the remote is here", not "this is open".
 */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = LumoSpacing.md)
            .wrapContentSize(Alignment.Center),
    )
}

/**
 * One channel, as a card.
 *
 * The second line is "category · quality", either half dropped when unknown.
 * `quality` is echoed exactly as the source wrote it: a badge reading `fhd` in
 * lower case is a source that wrote `fhd`, and normalising it here would be this
 * layer deciding what the provider meant. The category name is looked up from the
 * list the chips already hold — nothing new is asked of the view model.
 *
 * The provider's channel number is no longer a column of its own: the mock-up
 * has none, and the number is still the key a remote control types on the
 * television, where it is drawn.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: Channel?,
    categoryName: String?,
    favorited: Boolean,
    onPlay: (channelId: String, name: String?) -> Unit,
    onFavorite: (Channel) -> Unit,
    onFavoriteLongPress: (Channel) -> Unit,
) {
    val subtitle = subtitleOf(categoryName, channel?.quality)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            // Null is a row Paging has not loaded yet: it draws, and it does
            // nothing when tapped, rather than opening a player for no channel.
            .combinedClickable(
                enabled = channel != null,
                onClick = { channel?.let { onPlay(it.id, it.name) } },
                onLongClick = { channel?.let(onFavoriteLongPress) },
            )
            .padding(LumoSpacing.sm + LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm + LumoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(channel)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (channel != null) {
            Heart(
                favorited = favorited,
                onClick = { onFavorite(channel) },
                onLongClick = { onFavoriteLongPress(channel) },
            )
        }

        Text(
            text = stringResource(R.string.feature_live_glyph_chevron),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Category · HD", "Category", "HD", or nothing — never a stray separator. */
@Composable
private fun subtitleOf(categoryName: String?, quality: String?): String? = when {
    categoryName != null && quality != null ->
        stringResource(R.string.feature_live_subtitle_join, categoryName, quality)
    else -> categoryName ?: quality
}

/**
 * The favourite control (US-12).
 *
 * <h2>A glyph, because the product has no icon set</h2>
 *
 * The same reason `LumoMobileNavBar` is a row of labels rather than a Material
 * bar: a placeholder icon is a design decision made by accident. A filled and an
 * outlined heart are two characters that carry the state honestly until there is
 * a real icon to replace them with.
 *
 * <h2>Discreet, and still its own target</h2>
 *
 * The mock-up draws no heart on the card, so this one is small and muted — but a
 * gesture that exists is not taken away for a picture. The row opens the player;
 * this opens nothing. Long-pressing it, or the row, opens the group picker.
 *
 * `contentDescription` says what a tap *does*, not what is drawn: TalkBack
 * reading "heart" tells somebody nothing about whether the channel is starred.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Heart(favorited: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val description = if (favorited) {
        stringResource(R.string.feature_live_favorite_remove)
    } else {
        stringResource(R.string.feature_live_favorite_add)
    }

    Text(
        text = if (favorited) "♥" else "♡",
        style = MaterialTheme.typography.bodyLarge,
        color = if (favorited) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = description,
            )
            // A 48dp target: the glyph is small and the row it sits in is a
            // scrolling list, which is where a near-miss becomes a channel
            // launching instead.
            .sizeIn(minWidth = 40.dp, minHeight = 48.dp)
            .wrapContentSize()
            .semantics { contentDescription = description },
    )
}

/**
 * The logo the user's own playlist advertises, or their channel's initial, on a
 * 76 × 48 plate as the mock-up sizes it.
 *
 * **Lumo ships no fallback artwork** (AGENTS.md §1). A channel with no logo gets a
 * letter, never a bundled image of ours standing in for one of theirs.
 *
 * <h2>A logo that does not load looks like a channel with no logo</h2>
 *
 * And that is deliberate, because it will happen often. Most panels advertise
 * their logos over `http`, and this application does not permit cleartext — so on
 * a real source many of these will fail. An empty grey square for each would read
 * as a broken list; the initial reads as a channel whose provider gave no picture,
 * which is the same thing from the user's side and true from ours.
 */
@Composable
private fun Logo(channel: Channel?) {
    val shape = LumoShapes.small

    if (channel?.logoUrl == null) {
        Initial(channel, shape)
        return
    }

    SubcomposeAsyncImage(
        model = channel.logoUrl,
        // Decorative: the name is right next to it, and a screen reader reading
        // "logo of X" before "X" is noise.
        contentDescription = null,
        // Fit, not crop: a logo is a mark, and cropping one is cutting somebody's
        // brand in half. The plate colour fills what the logo does not.
        contentScale = ContentScale.Fit,
        loading = { Initial(channel, shape) },
        error = { Initial(channel, shape) },
        modifier = Modifier
            .width(LOGO_WIDTH)
            .height(LOGO_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(LumoSpacing.xs),
    )
}

@Composable
private fun Initial(channel: Channel?, shape: Shape) {
    Box(
        modifier = Modifier
            .width(LOGO_WIDTH)
            .height(LOGO_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel?.name?.take(1)?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private val ROUND_BUTTON = 48.dp
private val LOGO_WIDTH = 76.dp
private val LOGO_HEIGHT = 48.dp
