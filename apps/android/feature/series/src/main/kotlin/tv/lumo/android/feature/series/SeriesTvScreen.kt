package tv.lumo.android.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.data.EmptyGrid
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.emptyGridOf
import tv.lumo.android.core.data.labelRes
import tv.lumo.android.core.data.messageRes
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.wording
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.component.LumoTvSourceNotice
import tv.lumo.android.core.designsystem.component.LumoTvStateMessage
import tv.lumo.android.core.designsystem.effect.PollWhile
import tv.lumo.android.core.designsystem.effect.SOURCE_NOTICE_POLL_MILLIS
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * The series, on a television (US-15).
 *
 * **`VodTvScreen`, and deliberately not a line of difference in shape**: category
 * strip across the top, one horizontal row of posters below, arrival focus on the
 * first card, `UP` to the strip and `DOWN` back. The arithmetic that fixed one row
 * there — a poster at 2:3 in what is left after overscan, the title and the strip —
 * has not changed, and a poster nobody recognises from three metres is a card that
 * has stopped being a card.
 *
 * The focus map for this screen is `docs/design/tv-focus-map.md`, and it is a
 * deliverable of this task rather than paperwork: the commonest defect in a
 * television application is not a logic error, it is a control no sequence of key
 * presses reaches.
 *
 * <h2>What it does *not* have, and both absences are decisions</h2>
 *
 * **A "continue watching" chip, and it is a chip rather than a rail** (S6-08). The
 * ruling is `S4-08`'s, for the third time: a rail above this grid is a second focus
 * zone, and this strip has no height for a second mechanism. The phone has the room
 * and gets the rail.
 *
 * It filters the grid to the series somebody has started; `OK` on one of those
 * cards opens the series, where the focus now lands on the episode to resume. Two
 * presses, and **no new zone in the focus map** — which is the point.
 *
 * Shown only when there is something in it, which is the film strip's rule and the
 * reason for it: a chip that filters onto nothing leaves a blank grid after an `OK`,
 * and at three metres that reads as a breakage rather than as an empty shelf.
 *
 * **No search.** The phone has a field because it has a keyboard; a television has
 * a D-pad, and an on-screen keyboard is `feature:search`'s problem rather than a
 * second one built here.
 *
 * <h2>An empty catalogue says which kind of empty it is</h2>
 *
 * The same three sentences as the phone. An M3U playlist *cannot* carry series
 * (`adr/0010`) and a panel that offers none simply does not — telling somebody
 * with an Xtream panel that their format cannot do something it can is the worse
 * of the two mistakes, and on a television there is nowhere else to go and check.
 */
@Composable
fun SeriesTvScreen(
    onOpenSeries: (seriesId: String) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    returnedSeriesId: String? = null,
    onReturnHandled: () -> Unit = {},
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val series = viewModel.items.collectAsLazyPagingItems()

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
            // Not on the leading edge: the rail is there and has already paid for
            // that margin.
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        when (state.step) {
            SeriesStep.Loading -> TvCentered {
                Text(
                    text = stringResource(R.string.feature_series_tv_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            SeriesStep.NoSource -> TvMessage(
                title = stringResource(R.string.feature_series_no_source_title),
                body = stringResource(R.string.feature_series_no_source_body),
            )

            SeriesStep.NeedsChoice -> TvMessage(
                title = stringResource(R.string.feature_series_needs_choice_title),
                body = stringResource(R.string.feature_series_needs_choice_body),
            )

            // The two faces of a first import, which used to share one "not
            // ready" sentence. Each has a button — "My sources" — so that `RIGHT`
            // from the rail lands somewhere while there is no grid to land on.
            SeriesStep.Importing -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_first_import_title),
                detail = stringResource(
                    (state.notice as? SourceNotice.Refreshing)?.step.labelRes(),
                ),
                body = stringResource(DataR.string.core_data_first_import_body),
                actionLabel = stringResource(DataR.string.core_data_notice_open_sources),
                onAction = onOpenSources,
            )

            SeriesStep.ImportFailed -> LumoTvStateMessage(
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
            SeriesStep.Unreachable -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_unreached_title),
                body = stringResource(DataR.string.core_data_unreached_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = viewModel::refreshSource,
                secondaryActionLabel = stringResource(DataR.string.core_data_notice_change_source),
                onSecondaryAction = onOpenSources,
            )

            SeriesStep.Browsing -> Browsing(
                state = state,
                series = series,
                onOpenSources = onOpenSources,
                onRetry = viewModel::refresh,
                onRefreshSource = viewModel::refreshSource,
                onSelectCategory = viewModel::onCategorySelected,
                onSelectResume = viewModel::onResumeSelected,
                onOpenSeries = onOpenSeries,
                returnedSeriesId = returnedSeriesId,
                onReturnHandled = onReturnHandled,
            )
        }
    }
}

@Composable
private fun Browsing(
    state: SeriesState,
    series: LazyPagingItems<Series>,
    onOpenSources: () -> Unit,
    onRetry: () -> Unit,
    onRefreshSource: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectResume: () -> Unit,
    onOpenSeries: (String) -> Unit,
    returnedSeriesId: String?,
    onReturnHandled: () -> Unit,
) {
    // Arrival focus: the first card, not the category strip. Somebody who opened
    // the series wants a series, and the shelf they are already on is the right
    // one — the same reasoning, word for word, as the two grids before it.
    val focusTarget = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var focusIndex by remember { mutableIntStateOf(0) }

    // Coming back from a series' own screen. US-10's rule, restated: BACK returns
    // to **the series that was being looked at**, not to the head of the grid.
    LaunchedEffect(returnedSeriesId, series.itemCount) {
        val target = returnedSeriesId ?: return@LaunchedEffect
        val index = series.itemSnapshotList.items.indexOfFirst { it.id == target }

        // Not among the windows Paging holds — the grid was rebuilt, or the series
        // was dropped by a re-synchronisation. The first card keeps the focus,
        // which is where an arrival would have put it anyway.
        if (index < 0) return@LaunchedEffect

        focusIndex = index
        gridState.scrollToItem(index)
        onReturnHandled()
    }

    LaunchedEffect(focusIndex, series.itemCount) {
        if (series.itemCount == 0) return@LaunchedEffect
        // The card may not be composed on the frame this runs. Failing to focus is
        // recoverable — the D-pad still works — and throwing would take the screen
        // down.
        runCatching { focusTarget.requestFocus() }
    }

    Column(
        modifier = Modifier.padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_series_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
            )
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_series_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            // In the header line, compact: a notice of its own height would take
            // it from the one row of posters. A refresh is text; a failure adds
            // the one stop this line has — "My sources", reached by `UP` from
            // the category strip.
            state.notice?.let { notice ->
                val wording = notice.wording()
                LumoTvSourceNotice(
                    title = stringResource(wording.title),
                    message = stringResource(wording.message),
                    hint = wording.hint?.let { stringResource(it) },
                    isError = wording.failed,
                    actionLabel = stringResource(wording.action).takeIf { wording.actionable },
                    onAction = onOpenSources,
                    // An outage adds "try again" before it, on the same line.
                    retryLabel = wording.retry?.let { stringResource(it) },
                    onRetry = onRefreshSource,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Categories(
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            // Only when there is something in it. See the class documentation.
            hasResumable = state.continueWatching.isNotEmpty(),
            resumeSelected = state.resumeSelected,
            onSelect = onSelectCategory,
            onSelectResume = onSelectResume,
        )

        // Failed to load is not empty (US-024) — except under a playlist, which
        // cannot carry series whatever became of the request (adr/0010). The
        // button is what gives this state a focus target below the strip.
        if (
            !state.isPlaylist &&
            emptyGridOf(series.itemCount, state.refreshing, state.refreshFailed) == EmptyGrid.Unavailable
        ) {
            LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_catalogue_unavailable_title),
                body = stringResource(DataR.string.core_data_catalogue_unavailable_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = onRetry,
            )
            return@Column
        }

        if (series.itemCount == 0 && !state.refreshing) {
            TvMessage(
                title = stringResource(R.string.feature_series_empty_title),
                // The phone's three sentences, unchanged. Which of the two
                // absences this is matters more here, not less: a television
                // viewer has no second screen to go and check on.
                body = if (state.isPlaylist) {
                    stringResource(R.string.feature_series_empty_playlist)
                } else {
                    stringResource(R.string.feature_series_empty_panel)
                },
            )
            return@Column
        }

        LazyHorizontalGrid(
            // One row, for `VodTvScreen`'s arithmetic: two would put a poster near
            // 100 dp wide, and a poster nobody recognises at three metres is a card
            // that has stopped being a card.
            rows = GridCells.Fixed(1),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            state = gridState,
            contentPadding = PaddingValues(LumoSpacing.sm),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = series.itemCount,
                key = series.itemKey { it.id },
            ) { index ->
                SeriesCard(
                    series = series[index],
                    onOpen = onOpenSeries,
                    // One requester, moved to whichever card is the target: the
                    // first on arrival, the one just looked at on the way back.
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

/**
 * The strip above the grid.
 *
 * `UP` from the grid lands here, `DOWN` goes back, `LEFT`/`RIGHT` walk it, and
 * "All" is first because it is what the screen opens on. The film strip's, chip
 * for chip: **All · [Continue watching] · [the source's categories]**.
 */
@Composable
private fun Categories(
    categories: List<Category>,
    selectedId: String?,
    hasResumable: Boolean,
    resumeSelected: Boolean,
    onSelect: (String?) -> Unit,
    onSelectResume: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        contentPadding = PaddingValues(LumoSpacing.xs),
    ) {
        item {
            TvCategoryChip(
                label = stringResource(R.string.feature_series_all_categories),
                selected = selectedId == null && !resumeSelected,
                onClick = { onSelect(null) },
            )
        }
        // Second, and only when there is something in it — the position it holds
        // on the channel and film strips, for the same reason: it is what somebody
        // turning the television on reaches for most often, and the one shelf they
        // did not have to build.
        if (hasResumable) {
            item {
                TvCategoryChip(
                    label = stringResource(R.string.feature_series_continue_watching),
                    selected = resumeSelected,
                    onClick = onSelectResume,
                )
            }
        }
        items(categories, key = { it.id }) { category ->
            TvCategoryChip(
                label = category.channelCount
                    ?.let {
                        stringResource(R.string.feature_series_category_count, category.name, it)
                    }
                    ?: category.name,
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
internal fun TvCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            focused -> LumoColors.OnAccent
            selected -> LumoColors.Accent
            else -> LumoColors.OnDarkMuted
        },
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(
                when {
                    focused -> LumoColors.Accent
                    selected -> LumoColors.SurfaceRaised
                    else -> LumoColors.Surface
                },
            )
            // `clickable` makes it focusable and binds the centre key at once.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/**
 * One series, as a card the remote can land on.
 *
 * A null card is a window Paging has not loaded yet: drawn at full size so the grid
 * keeps its shape, and **not focusable** — a card the D-pad can stop on with
 * nothing behind it is a dead end that appears and disappears as the grid scrolls.
 *
 * `OK` opens the series' own screen, which is the only thing it could do: a series
 * is not played, an episode is, and choosing one needs a season and a list no card
 * has room for.
 */
@Composable
private fun SeriesCard(
    series: Series?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(CARD_WIDTH)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .clickable(
                enabled = series != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { series?.let { onOpen(it.id) } }
            .padding(LumoSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(
            posterUrl = series?.posterUrl,
            title = series?.name.orEmpty(),
            modifier = Modifier.width(CARD_WIDTH),
        )

        Text(
            text = series?.name.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TvMessage(title: String, body: String) {
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
internal fun TvCentered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** The film grid's width, for the film grid's reason: 2:3 at three metres. */
private val CARD_WIDTH = 200.dp
