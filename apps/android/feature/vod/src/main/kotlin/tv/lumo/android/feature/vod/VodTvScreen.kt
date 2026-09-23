package tv.lumo.android.feature.vod

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
import tv.lumo.android.core.data.model.VodItem
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
 * The films, on a television (US-13).
 *
 * The focus map for this screen — arrival focus and every direction from every
 * zone — is `docs/design/tv-focus-map.md`, and it is a deliverable of this task
 * in its own right. The commonest defect in television applications is not a
 * logic error: it is a control no sequence of key presses reaches.
 *
 * <h2>`LiveTvScreen`'s structure, taken as it stands</h2>
 *
 * Category strip across the top, horizontal grid below, arrival focus on the
 * first card, `UP` to the strip and `DOWN` back. **Divergence would be the
 * defect**: a viewer who has learnt the channel screen has learnt this one, and
 * two sets of rules on one remote is two sets to get wrong.
 *
 * <h2>One row, and the arithmetic is the argument</h2>
 *
 * The channel grid has two rows because a channel card is a wide, short strip. A
 * poster is 2:3 in portrait, and that changes the sum entirely.
 *
 * On a 1080p panel at the density Android TV reports, the usable height after
 * overscan is around 480 dp; the title and the category strip take about 150 of
 * it. Two rows of posters in what is left puts each one near 100 dp wide, which
 * is below the size at which somebody recognises a film from three metres — and a
 * poster nobody recognises is a card that has stopped doing its job. One row, and
 * the cards stay legible.
 *
 * That is exactly the trade this task names: **the number of columns is fixed on
 * legibility at three metres, not on what fits.**
 */
@Composable
fun VodTvScreen(
    onOpenFilm: (filmId: String) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    returnedFilmId: String? = null,
    onReturnHandled: () -> Unit = {},
    viewModel: VodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val films = viewModel.films.collectAsLazyPagingItems()

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
            VodStep.Loading -> TvCentered {
                Text(
                    text = stringResource(R.string.feature_vod_tv_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            VodStep.NoSource -> TvMessage(
                title = stringResource(R.string.feature_vod_no_source_title),
                body = stringResource(R.string.feature_vod_no_source_body),
            )

            VodStep.NeedsChoice -> TvMessage(
                title = stringResource(R.string.feature_vod_needs_choice_title),
                body = stringResource(R.string.feature_vod_needs_choice_body),
            )

            // The two faces of a first import, which used to share one "not
            // ready" sentence. Each has a button — "My sources" — so that `RIGHT`
            // from the rail lands somewhere while there is no grid to land on.
            VodStep.Importing -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_first_import_title),
                detail = stringResource(
                    (state.notice as? SourceNotice.Refreshing)?.step.labelRes(),
                ),
                body = stringResource(DataR.string.core_data_first_import_body),
                actionLabel = stringResource(DataR.string.core_data_notice_open_sources),
                onAction = onOpenSources,
            )

            VodStep.ImportFailed -> LumoTvStateMessage(
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
            VodStep.Unreachable -> LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_unreached_title),
                body = stringResource(DataR.string.core_data_unreached_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = viewModel::refreshSource,
                secondaryActionLabel = stringResource(DataR.string.core_data_notice_change_source),
                onSecondaryAction = onOpenSources,
            )

            VodStep.Browsing -> Browsing(
                state = state,
                films = films,
                onOpenSources = onOpenSources,
                onRetry = viewModel::refresh,
                onRefreshSource = viewModel::refreshSource,
                onSelectCategory = viewModel::onCategorySelected,
                onSelectResume = viewModel::onResumeSelected,
                onOpenFilm = onOpenFilm,
                returnedFilmId = returnedFilmId,
                onReturnHandled = onReturnHandled,
            )
        }
    }
}

@Composable
private fun Browsing(
    state: VodState,
    films: LazyPagingItems<VodItem>,
    onOpenSources: () -> Unit,
    onRetry: () -> Unit,
    onRefreshSource: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectResume: () -> Unit,
    onOpenFilm: (String) -> Unit,
    returnedFilmId: String?,
    onReturnHandled: () -> Unit,
) {
    // Arrival focus: the first card, not the category strip. Somebody who opened
    // the films wants a film, and the shelf they are already on is the right one
    // — the same reasoning, word for word, as the channel grid.
    val focusTarget = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var focusIndex by remember { mutableIntStateOf(0) }

    // Coming back from a film's own screen. US-10's rule, and this task restates
    // it: BACK returns to **the film that was being looked at**, not to the head
    // of the grid. A catalogue of thirty thousand that comes back at the top has
    // lost the viewer's place, and the film they just left is the hardest of all
    // to find again.
    LaunchedEffect(returnedFilmId, films.itemCount) {
        val target = returnedFilmId ?: return@LaunchedEffect
        val index = films.itemSnapshotList.items.indexOfFirst { it.id == target }

        // Not among the windows Paging holds — the grid was rebuilt, or the film
        // was dropped by a re-synchronisation. The first card keeps the focus,
        // which is where an arrival would have put it anyway.
        if (index < 0) return@LaunchedEffect

        focusIndex = index
        gridState.scrollToItem(index)
        onReturnHandled()
    }

    LaunchedEffect(focusIndex, films.itemCount) {
        if (films.itemCount == 0) return@LaunchedEffect
        // The card may not be composed on the frame this runs. Failing to focus
        // is recoverable — the D-pad still works — and throwing would take the
        // screen down.
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
                text = stringResource(R.string.feature_vod_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
            )
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_vod_offline),
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
            // Only when there is something in it. A chip that filters onto
            // nothing leaves a blank grid after an OK, which at three metres
            // reads as a breakage rather than as an empty shelf.
            hasResumable = state.continueWatching.isNotEmpty(),
            resumeSelected = state.resumeSelected,
            onSelect = onSelectCategory,
            onSelectResume = onSelectResume,
        )

        // Failed to load is not empty (US-024), and the button is what gives this
        // state a focus target below the strip.
        if (emptyGridOf(films.itemCount, state.refreshing, state.refreshFailed) == EmptyGrid.Unavailable) {
            LumoTvStateMessage(
                title = stringResource(DataR.string.core_data_catalogue_unavailable_title),
                body = stringResource(DataR.string.core_data_catalogue_unavailable_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = onRetry,
            )
            return@Column
        }

        if (films.itemCount == 0 && !state.refreshing) {
            TvMessage(
                title = stringResource(R.string.feature_vod_empty_title),
                body = stringResource(R.string.feature_vod_empty_body),
            )
            return@Column
        }

        LazyHorizontalGrid(
            // One row. See the class documentation: two would put a poster near
            // 100 dp wide, and a poster nobody recognises at three metres is a
            // card that has stopped being a card.
            rows = GridCells.Fixed(1),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            state = gridState,
            contentPadding = PaddingValues(LumoSpacing.sm),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = films.itemCount,
                key = films.itemKey { it.id },
            ) { index ->
                FilmCard(
                    film = films[index],
                    onOpen = onOpenFilm,
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
 * "All" is first because it is what the screen opens on. It is `LiveTvScreen`'s
 * strip with the favourite groups taken out — a group holds channels (US-12), and
 * offering a chip here that filtered onto nothing would be the empty shelf that
 * screen's own rules already refuse.
 *
 * **"Continue watching" is a chip and not a rail** (S5-11), in second position,
 * and that is `S4-08`'s ruling applied unchanged: this strip has no height for a
 * second mechanism, and a chip costs no new focus zone. The phone, which has the
 * room, gets the rail instead.
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
                label = stringResource(R.string.feature_vod_all_categories),
                selected = selectedId == null && !resumeSelected,
                onClick = { onSelect(null) },
            )
        }
        // Second, and only when there is something in it — the position "Repris"
        // holds on the channel strip, for the same reason: it is what somebody
        // turning the television on reaches for most often, and the one shelf
        // they did not have to build.
        if (hasResumable) {
            item {
                TvCategoryChip(
                    label = stringResource(R.string.feature_vod_continue_watching),
                    selected = resumeSelected,
                    onClick = onSelectResume,
                )
            }
        }
        items(categories, key = { it.id }) { category ->
            TvCategoryChip(
                label = category.channelCount
                    ?.let { stringResource(R.string.feature_vod_category_count, category.name, it) }
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
    val interactionSource = remember { MutableInteractionSource() }

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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/**
 * One film, as a card the remote can land on.
 *
 * A null card is a window Paging has not loaded yet: drawn at full size so the
 * grid keeps its shape, and **not focusable** — a card the D-pad can stop on with
 * nothing behind it is a dead end that appears and disappears as the grid scrolls.
 *
 * `OK` opens the film's own screen and does not play it. That is the one place
 * this screen departs from the channel grid, and it is what a film is: a channel
 * is played, a film is *chosen*, and the choosing needs a year, a running time and
 * a synopsis that no card has room for.
 *
 * There is no long press. The channel grid uses one to file a favourite; a film
 * has no favourite in v1, and a gesture that does nothing is worse than no gesture.
 */
@Composable
private fun FilmCard(
    film: VodItem?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .width(CARD_WIDTH)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .clickable(
                enabled = film != null,
                interactionSource = interactionSource,
                indication = null,
            ) { film?.let { onOpen(it.id) } }
            .padding(LumoSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(
            posterUrl = film?.posterUrl,
            title = film?.name.orEmpty(),
            modifier = Modifier.width(CARD_WIDTH),
        )

        Text(
            text = film?.name.orEmpty(),
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

/**
 * Poster width, chosen for three metres.
 *
 * At 2:3 this is a 300 dp poster, which is what a single row can afford under the
 * title and the category strip — and comfortably above the size at which a film
 * is recognisable from a sofa.
 */
private val CARD_WIDTH = 200.dp
