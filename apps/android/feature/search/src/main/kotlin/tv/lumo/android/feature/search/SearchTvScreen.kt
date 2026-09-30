package tv.lumo.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * The unified search, on a television (US-021, S10-02, design S10-E01/S10-E02/S10-E06).
 *
 * Same engine as the phone — [SearchViewModel] and `core:data` own every rule,
 * and this file draws and moves the focus. What differs is the surface: overscan
 * on the outer edges (never the leading one, where the rail already paid for the
 * margin), the television type scale, and a D-pad journey.
 *
 * <h2>The focus rules, which are most of the work here</h2>
 *
 * 1. The field takes the focus on arrival, and nothing else does. OK on it opens
 *    the platform's own keyboard; the product builds no on-screen keyboard in
 *    S10 (Q9).
 * 2. **No arrival steals the focus.** A response that lands while somebody is
 *    typing or reading a result leaves the focus exactly where it was — there is
 *    no `LaunchedEffect` on the results here, deliberately.
 * 3. The one explicit move is `Voir les résultats`: it puts the focus on the first
 *    card, and while a request is in flight it does nothing and keeps the focus,
 *    rather than jumping to a card that does not exist yet (Q9, SR-13).
 *
 * <h2>Absent, empty and loading are three different things</h2>
 *
 * The filters come from [SearchState.filters], the source's real presence: a type
 * it does not carry has no tab. A type it carries but that matched nothing keeps
 * its tab and shows an empty [SearchSection.Loaded]. And a request in flight shows
 * a line of its own, never an empty row.
 */
@Composable
fun SearchTvScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.query))
    }
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    // Arrival focus, once. It is the whole of the arrival choreography: nothing
    // below ever asks for the focus again as a side effect of data arriving.
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
    }

    val query = state.query.trim()
    val firstTarget = state.firstResultTarget()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            // Not on the leading edge: the rail is there and has already paid for
            // that margin.
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        ) {
            TvSearchField(
                value = field,
                onValueChange = {
                    field = it
                    viewModel.onQueryChanged(it.text, composing = it.composition != null)
                },
                onSubmit = viewModel::onSearchSubmitted,
                onClear = {
                    field = TextFieldValue("")
                    viewModel.onQueryChanged("", composing = false)
                },
                focusRequester = fieldFocus,
            )

            if (state.tooLong) {
                Text(
                    text = stringResource(R.string.feature_search_too_long),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LumoColors.Error,
                )
            }

            // Only the filters the source can answer, plus All (SR-11).
            FilterTabs(
                filters = state.filters,
                selected = state.filter,
                onSelect = viewModel::onFilterSelected,
            )

            if (state.invitation) {
                Invitation()
            } else {
                // The explicit way into the results (Q9). It is a control, not an
                // automatic move: while the answer is in flight it keeps the
                // focus, and a second press once the cards are there lands on the
                // first of them.
                LumoTvButton(
                    text = stringResource(R.string.feature_search_see_results),
                    onClick = { runCatching { firstResultFocus.requestFocus() } },
                    primary = true,
                )

                when (state.filter) {
                    SearchFilter.All -> GroupedResults(
                        state = state,
                        query = query,
                        firstTarget = firstTarget,
                        firstResultFocus = firstResultFocus,
                        viewModel = viewModel,
                    )

                    SearchFilter.Channels -> TvTypeResults(
                        filter = SearchFilter.Channels,
                        section = state.channels,
                        query = query,
                        firstTarget = firstTarget,
                        firstResultFocus = firstResultFocus,
                        onRetry = { viewModel.onRetry(SearchFilter.Channels) },
                        onLoadMore = { viewModel.onLoadMore(SearchFilter.Channels) },
                    ) { channel, requester -> TvChannelCard(channel, requester) }

                    SearchFilter.Films -> TvTypeResults(
                        filter = SearchFilter.Films,
                        section = state.films,
                        query = query,
                        firstTarget = firstTarget,
                        firstResultFocus = firstResultFocus,
                        onRetry = { viewModel.onRetry(SearchFilter.Films) },
                        onLoadMore = { viewModel.onLoadMore(SearchFilter.Films) },
                    ) { film, requester -> TvMediaCard(film.name, film.posterUrl, requester) }

                    SearchFilter.Series -> TvTypeResults(
                        filter = SearchFilter.Series,
                        section = state.series,
                        query = query,
                        firstTarget = firstTarget,
                        firstResultFocus = firstResultFocus,
                        onRetry = { viewModel.onRetry(SearchFilter.Series) },
                        onLoadMore = { viewModel.onLoadMore(SearchFilter.Series) },
                    ) { series, requester -> TvMediaCard(series.name, series.posterUrl, requester) }
                }
            }
        }
    }
}

/**
 * The single field, a pill, with the composition passed through untouched.
 *
 * The focus requester is on the text field, which is the focusable node, so the
 * pill ring around it follows the field rather than the row.
 */
@Composable
private fun TvSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.feature_search_hint)
    val clearDescription = stringResource(R.string.feature_search_clear)
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
            text = stringResource(R.string.feature_search_glyph_search),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDarkMuted,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (value.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_search_hint),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = description },
            )
        }

        if (value.text.isNotEmpty()) {
            Text(
                text = stringResource(R.string.feature_search_glyph_clear),
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

/** The filters, in the order [SearchState.filters] gives them. */
@Composable
private fun FilterTabs(
    filters: List<SearchFilter>,
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        filters.forEach { filter ->
            LumoTvButton(
                text = filter.label(),
                onClick = { onSelect(filter) },
                primary = filter == selected,
            )
        }
    }
}

/** The empty field: an invitation, and nothing loaded (Q9, SR-04). */
@Composable
private fun Invitation() {
    Text(
        text = stringResource(R.string.feature_search_invitation),
        style = MaterialTheme.typography.bodyLarge,
        color = LumoColors.OnDarkMuted,
    )
}

/**
 * The grouped view: one row per present type, four hits at most, "Voir tous" when
 * the type has more (Q9, SR-06).
 */
@Composable
private fun GroupedResults(
    state: SearchState,
    query: String,
    firstTarget: ResultKey?,
    firstResultFocus: FocusRequester,
    viewModel: SearchViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xl)) {
        TvSection(
            title = stringResource(R.string.feature_search_filter_channels),
            filter = SearchFilter.Channels,
            section = state.channels,
            query = query,
            firstTarget = firstTarget,
            firstResultFocus = firstResultFocus,
            onSeeAll = { viewModel.onFilterSelected(SearchFilter.Channels) },
            onRetry = { viewModel.onRetry(SearchFilter.Channels) },
        ) { channel, requester -> TvChannelCard(channel, requester) }

        TvSection(
            title = stringResource(R.string.feature_search_filter_films),
            filter = SearchFilter.Films,
            section = state.films,
            query = query,
            firstTarget = firstTarget,
            firstResultFocus = firstResultFocus,
            onSeeAll = { viewModel.onFilterSelected(SearchFilter.Films) },
            onRetry = { viewModel.onRetry(SearchFilter.Films) },
        ) { film, requester -> TvMediaCard(film.name, film.posterUrl, requester) }

        TvSection(
            title = stringResource(R.string.feature_search_filter_series),
            filter = SearchFilter.Series,
            section = state.series,
            query = query,
            firstTarget = firstTarget,
            firstResultFocus = firstResultFocus,
            onSeeAll = { viewModel.onFilterSelected(SearchFilter.Series) },
            onRetry = { viewModel.onRetry(SearchFilter.Series) },
        ) { series, requester -> TvMediaCard(series.name, series.posterUrl, requester) }
    }
}

/**
 * One section of the grouped view.
 *
 * An [SearchSection.Idle] section is one this source does not carry, or that the
 * filter did not ask for: it draws nothing rather than a title over emptiness.
 */
@Composable
private fun <T> TvSection(
    title: String,
    filter: SearchFilter,
    section: SearchSection<T>,
    query: String,
    firstTarget: ResultKey?,
    firstResultFocus: FocusRequester,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
    card: @Composable (T, FocusRequester?) -> Unit,
) {
    if (section is SearchSection.Idle) return

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
            )
            if (section is SearchSection.Loaded && section.hasMore) {
                LumoTvButton(
                    text = stringResource(R.string.feature_search_see_all),
                    onClick = onSeeAll,
                )
            }
        }

        when (section) {
            SearchSection.Idle -> Unit
            SearchSection.Loading -> TvLoadingLine()
            is SearchSection.Failed -> TvErrorLine(onRetry)
            is SearchSection.Loaded ->
                if (section.items.isEmpty()) {
                    TvEmptyLine(query)
                } else {
                    TvCardRow(section.items, filter, firstTarget, firstResultFocus, card)
                }
        }
    }
}

/** The list of one type: twenty a page, "Afficher plus" on demand (Q9, SR-07). */
@Composable
private fun <T> TvTypeResults(
    filter: SearchFilter,
    section: SearchSection<T>,
    query: String,
    firstTarget: ResultKey?,
    firstResultFocus: FocusRequester,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    card: @Composable (T, FocusRequester?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
        when (section) {
            SearchSection.Idle, SearchSection.Loading -> TvLoadingLine()
            is SearchSection.Failed -> TvErrorLine(onRetry)
            is SearchSection.Loaded -> {
                if (section.items.isEmpty()) {
                    TvEmptyLine(query)
                } else {
                    TvCardRow(section.items, filter, firstTarget, firstResultFocus, card)
                }

                when {
                    // A next page in flight: what is on screen stays, nothing moves.
                    section.loadingMore -> TvLoadingLine()
                    // The next page failed: the pages already here stay, and the
                    // retry concerns that page alone (SR-07).
                    section.pageError != null -> TvPageErrorLine(onRetry)
                    section.hasMore -> LumoTvButton(
                        text = stringResource(R.string.feature_search_show_more),
                        onClick = onLoadMore,
                    )
                }
            }
        }
    }
}

/** A scrolling row of cards; the first of the first non-empty section takes the focus. */
@Composable
private fun <T> TvCardRow(
    items: List<T>,
    filter: SearchFilter,
    firstTarget: ResultKey?,
    firstResultFocus: FocusRequester,
    card: @Composable (T, FocusRequester?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        items.forEachIndexed { index, item ->
            val requester = if (firstTarget == ResultKey(filter, index)) firstResultFocus else null
            card(item, requester)
        }
    }
}

@Composable
private fun TvChannelCard(channel: Channel, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }

    Text(
        text = channel.name,
        style = MaterialTheme.typography.bodyLarge,
        color = LumoColors.OnDark,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(220.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .focusable()
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

@Composable
private fun TvMediaCard(title: String, posterUrl: String?, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(180.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .focusable()
            .padding(LumoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(
            posterUrl = posterUrl,
            title = title,
            modifier = Modifier.fillMaxWidth(),
            shape = LumoTvShapes.small,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = LumoColors.OnDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvLoadingLine() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.feature_search_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = LumoColors.OnDarkMuted,
        )
    }
}

/** A present type that matched nothing — its filter stays (SR-11). */
@Composable
private fun TvEmptyLine(query: String) {
    Text(
        text = stringResource(R.string.feature_search_empty, query),
        style = MaterialTheme.typography.bodyLarge,
        color = LumoColors.OnDarkMuted,
    )
}

@Composable
private fun TvErrorLine(onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_search_error),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.Error,
        )
        LumoTvButton(
            text = stringResource(R.string.feature_search_retry),
            onClick = onRetry,
        )
    }
}

@Composable
private fun TvPageErrorLine(onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_search_page_error),
            style = MaterialTheme.typography.bodyMedium,
            color = LumoColors.Error,
        )
        LumoTvButton(
            text = stringResource(R.string.feature_search_retry),
            onClick = onRetry,
        )
    }
}

/** The four labels, from the filter itself so the call site cannot drift. */
@Composable
private fun SearchFilter.label(): String = stringResource(
    when (this) {
        SearchFilter.All -> R.string.feature_search_filter_all
        SearchFilter.Channels -> R.string.feature_search_filter_channels
        SearchFilter.Films -> R.string.feature_search_filter_films
        SearchFilter.Series -> R.string.feature_search_filter_series
    },
)

/**
 * Where the first real result is, for the one explicit focus move.
 *
 * A pair of (filter, index) so a card can recognise itself as the target without
 * the screen having to hold a mutable "have I attached it yet" flag — which would
 * fire on recomposition and produce a moving focus, the exact thing SR-13
 * forbids.
 */
private data class ResultKey(val filter: SearchFilter, val index: Int)

private fun SearchState.firstResultTarget(): ResultKey? = when (filter) {
    SearchFilter.All -> listOf(
        SearchFilter.Channels to channels,
        SearchFilter.Films to films,
        SearchFilter.Series to series,
    ).firstOrNull { (_, section) -> section.hasAnyItem() }
        ?.let { (type, _) -> ResultKey(type, 0) }

    else -> if (section(filter).hasAnyItem()) ResultKey(filter, 0) else null
}

private fun SearchSection<*>?.hasAnyItem(): Boolean =
    this is SearchSection.Loaded && items.isNotEmpty()
