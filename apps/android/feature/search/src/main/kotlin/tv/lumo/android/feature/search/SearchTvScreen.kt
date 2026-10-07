package tv.lumo.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.VodItem
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
 *    typing or reading a result leaves the focus exactly where it was — the one
 *    effect keyed on the state below waits for a pending return, never for data
 *    (Q9, SR-13).
 * 3. The one explicit move is `Voir les résultats`: it puts the focus on the first
 *    card, and while a request is in flight it does nothing and keeps the focus,
 *    rather than jumping to a card that does not exist yet (Q9, SR-13).
 * 4. **A return restores the card it left from** (S10-03). Choosing a channel, a
 *    film or a series records its key, and coming back from the player or the
 *    fiche puts the focus back on that card — once, and only while a return is
 *    pending, so a late answer still cannot move the focus (SR-12, SR-13).
 *
 * <h2>Opening a result</h2>
 *
 * A card says "this channel/film/series was chosen" and the application decides
 * whether that is playback or a fiche — this file draws and moves the focus, it
 * owns no route (S10-03).
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
    onPlayChannel: (channelId: String, name: String?) -> Unit,
    onOpenFilm: (filmId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.query))
    }
    // The card chosen before leaving for a player or a fiche. Saved with the
    // entry, so a return finds it; consumed the moment the focus is back on it.
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }
    // The first filter tab, so DOWN can leave the field: a `BasicTextField`
    // consumes the D-pad for its cursor, so the focus would otherwise never
    // reach the controls drawn below it (S10-05, SR-13).
    val filtersFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }

    // Arrival focus, once, and not when a return is restoring a card.
    LaunchedEffect(Unit) {
        if (chosen == null) runCatching { fieldFocus.requestFocus() }
    }

    // The return's own move. It runs only while a return is pending, so an answer
    // that lands later still cannot take the focus (SR-13).
    LaunchedEffect(chosen, state) {
        if (chosen == null) return@LaunchedEffect
        if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
            chosen = null
        }
    }

    val query = state.query.trim()
    val firstTargetKey = state.firstResultTarget()

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
                onNavigateDown = { runCatching { filtersFocus.requestFocus() } },
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
                firstFocus = filtersFocus,
            )

            // A partial failure that fell back to the cache: say the data may be
            // old rather than pass it off as the server's answer (S10-04).
            if (state.someFromCache) {
                TvNotice(stringResource(R.string.feature_search_stale))
            }

            // Nothing answered and there is no cache either: the search could not
            // run, which is not the same as "nothing matched" (S10-04).
            if (state.offline) {
                TvNotice(stringResource(R.string.feature_search_offline))
            }

            if (state.invitation) {
                Invitation()
            } else if (state.noResults) {
                // A completed search that matched nothing: name the text and the
                // source, and offer to clear rather than leave a blank screen
                // (S10-04, S10-E04). The action clears and puts the focus back on
                // the field, so the D-pad can type again without a detour.
                TvNoResults(
                    query = query,
                    source = state.sourceLabel,
                    onClear = {
                        field = TextFieldValue("")
                        viewModel.onQueryChanged("", composing = false)
                        runCatching { fieldFocus.requestFocus() }
                    },
                )
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
                        firstTargetKey = firstTargetKey,
                        restoreKey = chosen,
                        firstResultFocus = firstResultFocus,
                        restoreFocus = restoreFocus,
                        onSeeAll = viewModel::onFilterSelected,
                        onRetry = viewModel::onRetry,
                        channelCard = { channel, requester ->
                            TvChannelCard(channel, requester) {
                                chosen = resultKey(SearchFilter.Channels, channel.id)
                                onPlayChannel(channel.id, channel.name)
                            }
                        },
                        filmCard = { film, requester ->
                            TvMediaCard(film.name, film.posterUrl, requester) {
                                chosen = resultKey(SearchFilter.Films, film.id)
                                onOpenFilm(film.id)
                            }
                        },
                        seriesCard = { series, requester ->
                            TvMediaCard(series.name, series.posterUrl, requester) {
                                chosen = resultKey(SearchFilter.Series, series.id)
                                onOpenSeries(series.id)
                            }
                        },
                    )

                    SearchFilter.Channels -> TvTypeResults(
                        section = state.channels,
                        query = query,
                        firstTargetKey = firstTargetKey,
                        restoreKey = chosen,
                        firstResultFocus = firstResultFocus,
                        restoreFocus = restoreFocus,
                        keyOf = { resultKey(SearchFilter.Channels, it.id) },
                        onRetry = { viewModel.onRetry(SearchFilter.Channels) },
                        onLoadMore = { viewModel.onLoadMore(SearchFilter.Channels) },
                    ) { channel, requester ->
                        TvChannelCard(channel, requester) {
                            chosen = resultKey(SearchFilter.Channels, channel.id)
                            onPlayChannel(channel.id, channel.name)
                        }
                    }

                    SearchFilter.Films -> TvTypeResults(
                        section = state.films,
                        query = query,
                        firstTargetKey = firstTargetKey,
                        restoreKey = chosen,
                        firstResultFocus = firstResultFocus,
                        restoreFocus = restoreFocus,
                        keyOf = { resultKey(SearchFilter.Films, it.id) },
                        onRetry = { viewModel.onRetry(SearchFilter.Films) },
                        onLoadMore = { viewModel.onLoadMore(SearchFilter.Films) },
                    ) { film, requester ->
                        TvMediaCard(film.name, film.posterUrl, requester) {
                            chosen = resultKey(SearchFilter.Films, film.id)
                            onOpenFilm(film.id)
                        }
                    }

                    SearchFilter.Series -> TvTypeResults(
                        section = state.series,
                        query = query,
                        firstTargetKey = firstTargetKey,
                        restoreKey = chosen,
                        firstResultFocus = firstResultFocus,
                        restoreFocus = restoreFocus,
                        keyOf = { resultKey(SearchFilter.Series, it.id) },
                        onRetry = { viewModel.onRetry(SearchFilter.Series) },
                        onLoadMore = { viewModel.onLoadMore(SearchFilter.Series) },
                    ) { series, requester ->
                        TvMediaCard(series.name, series.posterUrl, requester) {
                            chosen = resultKey(SearchFilter.Series, series.id)
                            onOpenSeries(series.id)
                        }
                    }
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
    onNavigateDown: () -> Unit,
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

        // DOWN is read on the wrapper that owns the field, not on the field's own
        // modifier: a `BasicTextField` creates its key handler deeper than the
        // modifier passed to it, so an `onPreviewKeyEvent` put on the field itself
        // is not guaranteed to sit before the field's handler on the root-to-focus
        // path — this wrapper always is. LEFT and RIGHT are deliberately left to
        // the field: they still move the cursor (S10-05, SR-13).
        Box(
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key != Key.DirectionDown) return@onPreviewKeyEvent false
                    onNavigateDown()
                    true
                },
        ) {
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
    firstFocus: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        filters.forEachIndexed { index, filter ->
            LumoTvButton(
                text = filter.label(),
                onClick = { onSelect(filter) },
                primary = filter == selected,
                // Only the first tab carries the requester: DOWN from the field
                // always lands on the first filter, and the rest of the row is
                // the D-pad's own to walk.
                focusRequester = if (index == 0) firstFocus else null,
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
 * A completed search that matched nothing anywhere (S10-04, S10-E04).
 *
 * It recalls the searched text and the source it was searched in, and offers to
 * clear — never a blank screen that could be read as a source with no content.
 * When the source's name could not be read it falls back to the per-query line
 * rather than inventing a name.
 */
@Composable
private fun TvNoResults(query: String, source: String?, onClear: () -> Unit) {
    val text = if (source != null) {
        stringResource(R.string.feature_search_no_result, query, source)
    } else {
        stringResource(R.string.feature_search_empty, query)
    }

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
        LumoTvButton(
            text = stringResource(R.string.feature_search_clear),
            onClick = onClear,
            primary = true,
        )
    }
}

/** A quiet status line: stale cache, or a search that could not run (S10-04). */
@Composable
private fun TvNotice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
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
    firstTargetKey: String?,
    restoreKey: String?,
    firstResultFocus: FocusRequester,
    restoreFocus: FocusRequester,
    onSeeAll: (SearchFilter) -> Unit,
    onRetry: (SearchFilter) -> Unit,
    channelCard: @Composable (Channel, FocusRequester?) -> Unit,
    filmCard: @Composable (VodItem, FocusRequester?) -> Unit,
    seriesCard: @Composable (Series, FocusRequester?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xl)) {
        TvSection(
            title = stringResource(R.string.feature_search_filter_channels),
            section = state.channels,
            query = query,
            firstTargetKey = firstTargetKey,
            restoreKey = restoreKey,
            firstResultFocus = firstResultFocus,
            restoreFocus = restoreFocus,
            keyOf = { resultKey(SearchFilter.Channels, it.id) },
            onSeeAll = { onSeeAll(SearchFilter.Channels) },
            onRetry = { onRetry(SearchFilter.Channels) },
            card = channelCard,
        )

        TvSection(
            title = stringResource(R.string.feature_search_filter_films),
            section = state.films,
            query = query,
            firstTargetKey = firstTargetKey,
            restoreKey = restoreKey,
            firstResultFocus = firstResultFocus,
            restoreFocus = restoreFocus,
            keyOf = { resultKey(SearchFilter.Films, it.id) },
            onSeeAll = { onSeeAll(SearchFilter.Films) },
            onRetry = { onRetry(SearchFilter.Films) },
            card = filmCard,
        )

        TvSection(
            title = stringResource(R.string.feature_search_filter_series),
            section = state.series,
            query = query,
            firstTargetKey = firstTargetKey,
            restoreKey = restoreKey,
            firstResultFocus = firstResultFocus,
            restoreFocus = restoreFocus,
            keyOf = { resultKey(SearchFilter.Series, it.id) },
            onSeeAll = { onSeeAll(SearchFilter.Series) },
            onRetry = { onRetry(SearchFilter.Series) },
            card = seriesCard,
        )
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
    section: SearchSection<T>,
    query: String,
    firstTargetKey: String?,
    restoreKey: String?,
    firstResultFocus: FocusRequester,
    restoreFocus: FocusRequester,
    keyOf: (T) -> String,
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
                    TvCardRow(
                        items = section.items,
                        keyOf = keyOf,
                        firstTargetKey = firstTargetKey,
                        restoreKey = restoreKey,
                        firstResultFocus = firstResultFocus,
                        restoreFocus = restoreFocus,
                        card = card,
                    )
                }
        }
    }
}

/** The list of one type: twenty a page, "Afficher plus" on demand (Q9, SR-07). */
@Composable
private fun <T> TvTypeResults(
    section: SearchSection<T>,
    query: String,
    firstTargetKey: String?,
    restoreKey: String?,
    firstResultFocus: FocusRequester,
    restoreFocus: FocusRequester,
    keyOf: (T) -> String,
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
                    TvCardRow(
                        items = section.items,
                        keyOf = keyOf,
                        firstTargetKey = firstTargetKey,
                        restoreKey = restoreKey,
                        firstResultFocus = firstResultFocus,
                        restoreFocus = restoreFocus,
                        card = card,
                    )
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

/**
 * A scrolling row of cards.
 *
 * The card that was chosen before leaving carries [restoreFocus], the first real
 * result carries [firstResultFocus] for the explicit move, and every other card
 * carries none. A key is (filter, item id), so the same identity serves the
 * explicit move and the return.
 */
@Composable
private fun <T> TvCardRow(
    items: List<T>,
    keyOf: (T) -> String,
    firstTargetKey: String?,
    restoreKey: String?,
    firstResultFocus: FocusRequester,
    restoreFocus: FocusRequester,
    card: @Composable (T, FocusRequester?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        items.forEach { item ->
            val key = keyOf(item)
            val requester = when {
                restoreKey != null && key == restoreKey -> restoreFocus
                key == firstTargetKey -> firstResultFocus
                else -> null
            }
            card(item, requester)
        }
    }
}

@Composable
private fun TvChannelCard(channel: Channel, focusRequester: FocusRequester?, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

@Composable
private fun TvMediaCard(
    title: String,
    posterUrl: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(180.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .clickable(onClick = onClick)
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
 * Identified by (filter, item id) rather than by index, so the same key serves
 * both the explicit "Voir les résultats" move and the return from a player or a
 * fiche. The screen holds no mutable "have I attached it yet" flag: that would
 * fire on recomposition and move the focus, the exact thing SR-13 forbids.
 */
private fun SearchState.firstResultTarget(): String? = when (filter) {
    SearchFilter.All -> listOf(
        SearchFilter.Channels to channels.firstItemId { it.id },
        SearchFilter.Films to films.firstItemId { it.id },
        SearchFilter.Series to series.firstItemId { it.id },
    ).firstOrNull { (_, id) -> id != null }
        ?.let { (type, id) -> resultKey(type, id!!) }

    SearchFilter.Channels -> channels.firstItemId { it.id }?.let { resultKey(filter, it) }
    SearchFilter.Films -> films.firstItemId { it.id }?.let { resultKey(filter, it) }
    SearchFilter.Series -> series.firstItemId { it.id }?.let { resultKey(filter, it) }
}

private fun <T> SearchSection<T>?.firstItemId(select: (T) -> String): String? =
    (this as? SearchSection.Loaded)?.items?.firstOrNull()?.let(select)

/** The identity of one card across a return: its filter and its item id. */
private fun resultKey(filter: SearchFilter, itemId: String): String = "${filter.name}:$itemId"
