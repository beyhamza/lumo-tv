package tv.lumo.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The unified search, on a phone (US-021, S10-02, design S10-E01/S10-E02/S10-E03).
 *
 * <h2>This file draws; it decides nothing</h2>
 *
 * Every rule — the 350 ms delay, the trim, the hundred-character limit, which
 * types exist, the preview of four versus pages of twenty, cancelling a stale
 * answer — lives in [SearchViewModel] and `core:data`, where it is tested without
 * a screen. What is here is layout and the platform's text input, and that split
 * is deliberate: a screen that computed a request would make the shipped path
 * differ from the tested one.
 *
 * <h2>The composition flag, and why the field keeps its own text</h2>
 *
 * A `TextField` reports every keystroke of an in-progress word; Q9 asks for the
 * delay to start once the composition is *validated*, so the field hands the
 * view model `TextFieldValue.composition != null` and the view model decides. The
 * field holds the `TextFieldValue` and not the query, because only the platform
 * type carries the composition range.
 *
 * <h2>Absent, empty and loading are three different things</h2>
 *
 * The filters come from [SearchState.filters], which is the source's real
 * presence: a type it does not carry has no chip. A type it carries but that
 * matched nothing keeps its chip and shows [SearchSection.Loaded] with no items.
 * And a request in flight shows a spinner, never an empty list — an absence is a
 * reply, a spinner is not.
 */
@Composable
fun SearchMobileScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.query))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        SearchField(
            value = field,
            onValueChange = {
                field = it
                viewModel.onQueryChanged(it.text, composing = it.composition != null)
            },
            onSubmit = viewModel::onSearchSubmitted,
        )

        if (state.tooLong) {
            Text(
                text = stringResource(R.string.feature_search_too_long),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Only the filters the source can answer, plus All (SR-11).
        FilterChips(
            filters = state.filters,
            selected = state.filter,
            onSelect = viewModel::onFilterSelected,
        )

        when {
            state.invitation -> Invitation()
            state.filter == SearchFilter.All -> GroupedResults(state, viewModel)
            else -> TypeResults(state, viewModel)
        }
    }
}

/** The single field, a pill, with the active composition passed through untouched. */
@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
) {
    val description = stringResource(R.string.feature_search_hint)
    val clearDescription = stringResource(R.string.feature_search_clear)

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
            if (value.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_search_hint),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description },
            )
        }

        if (value.text.isNotEmpty()) {
            Text(
                text = stringResource(R.string.feature_search_glyph_clear),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onValueChange(TextFieldValue("")) }
                    .padding(LumoSpacing.xs)
                    .semantics { contentDescription = clearDescription },
            )
        }
    }
}

/**
 * The filters, in the order [SearchState.filters] gives them.
 *
 * A tab rather than a chip that commits: selecting one is the same act as "Voir
 * tous", so the view model runs a page 0 of twenty in both cases (Q9).
 */
@Composable
private fun FilterChips(
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
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(filter) },
                    )
                    .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
            ) {
                Text(
                    text = filter.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** The empty field: an invitation, and nothing loaded (Q9, SR-04). */
@Composable
private fun Invitation() {
    Text(
        text = stringResource(R.string.feature_search_invitation),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The grouped view: one section per present type, four hits at most, "Voir tous"
 * when the type has more (Q9, SR-06).
 *
 * The channels are a list and the films/series two columns, which is the phone
 * layout of the design. Every section is drawn from its own [SearchSection], so a
 * film section that failed leaves the channel section readable (Q9, SR-10).
 */
@Composable
private fun GroupedResults(state: SearchState, viewModel: SearchViewModel) {
    Group(
        title = stringResource(R.string.feature_search_filter_channels),
        section = state.channels,
        query = state.query.trim(),
        onSeeAll = { viewModel.onFilterSelected(SearchFilter.Channels) },
        onRetry = { viewModel.onRetry(SearchFilter.Channels) },
    ) { channel -> ChannelRow(channel) }

    Group(
        title = stringResource(R.string.feature_search_filter_films),
        section = state.films,
        query = state.query.trim(),
        columns = 2,
        onSeeAll = { viewModel.onFilterSelected(SearchFilter.Films) },
        onRetry = { viewModel.onRetry(SearchFilter.Films) },
    ) { film -> MediaCard(title = film.name, posterUrl = film.posterUrl) }

    Group(
        title = stringResource(R.string.feature_search_filter_series),
        section = state.series,
        query = state.query.trim(),
        columns = 2,
        onSeeAll = { viewModel.onFilterSelected(SearchFilter.Series) },
        onRetry = { viewModel.onRetry(SearchFilter.Series) },
    ) { series -> MediaCard(title = series.name, posterUrl = series.posterUrl) }
}

/** The list of one type: twenty a page, "Afficher plus" on demand (Q9, SR-07). */
@Composable
private fun TypeResults(state: SearchState, viewModel: SearchViewModel) {
    val filter = state.filter
    when (filter) {
        SearchFilter.All -> Unit // The grouped view is drawn by [GroupedResults].

        SearchFilter.Channels -> TypeList(
            section = state.channels,
            query = state.query.trim(),
            onRetry = { viewModel.onRetry(filter) },
            onLoadMore = { viewModel.onLoadMore(filter) },
        ) { channel -> ChannelRow(channel) }

        SearchFilter.Films -> TypeList(
            section = state.films,
            query = state.query.trim(),
            columns = 2,
            onRetry = { viewModel.onRetry(filter) },
            onLoadMore = { viewModel.onLoadMore(filter) },
        ) { film -> MediaCard(title = film.name, posterUrl = film.posterUrl) }

        SearchFilter.Series -> TypeList(
            section = state.series,
            query = state.query.trim(),
            columns = 2,
            onRetry = { viewModel.onRetry(filter) },
            onLoadMore = { viewModel.onLoadMore(filter) },
        ) { series -> MediaCard(title = series.name, posterUrl = series.posterUrl) }
    }
}

/**
 * One section of the grouped view.
 *
 * An [SearchSection.Idle] section is one this source does not carry, or that the
 * filter did not ask for: it draws nothing rather than a title over emptiness.
 */
@Composable
private fun <T> Group(
    title: String,
    section: SearchSection<T>,
    query: String,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
    columns: Int = 1,
    item: @Composable (T) -> Unit,
) {
    if (section is SearchSection.Idle) return

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        SectionHeader(
            title = title,
            action = if (section is SearchSection.Loaded && section.hasMore) {
                stringResource(R.string.feature_search_see_all) to onSeeAll
            } else {
                null
            },
        )
        SectionBody(section, query, columns, onRetry, item)
    }
}

/** The list of one type, with its paging actions. */
@Composable
private fun <T> TypeList(
    section: SearchSection<T>,
    query: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    columns: Int = 1,
    item: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        when (section) {
            SearchSection.Idle, SearchSection.Loading -> LoadingLine()
            is SearchSection.Failed -> ErrorLine(onRetry)
            is SearchSection.Loaded -> {
                SectionBody(section, query, columns, onRetry, item)

                when {
                    // A next page in flight: what is on screen stays, nothing moves.
                    section.loadingMore -> LoadingLine()
                    // The next page failed: the pages already here stay, and the
                    // retry concerns that page alone (SR-07).
                    section.pageError != null -> PageErrorLine(onRetry)
                    section.hasMore -> TextButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.feature_search_show_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SectionBody(
    section: SearchSection<T>,
    query: String,
    columns: Int,
    onRetry: () -> Unit,
    item: @Composable (T) -> Unit,
) {
    when (section) {
        SearchSection.Idle -> Unit
        SearchSection.Loading -> LoadingLine()
        is SearchSection.Failed -> ErrorLine(onRetry)
        is SearchSection.Loaded ->
            if (section.items.isEmpty()) EmptyLine(query) else Rows(section.items, columns, item)
    }
}

/** Lays items out in one column, or in rows of [columns] with an even last row. */
@Composable
private fun <T> Rows(items: List<T>, columns: Int, item: @Composable (T) -> Unit) {
    if (columns <= 1) {
        Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
            items.forEach { item(it) }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                row.forEach { cell ->
                    Box(modifier = Modifier.weight(1f)) { item(cell) }
                }
                // Pads a last row of one so it does not stretch to full width.
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        action?.let { (label, onClick) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
                    .padding(LumoSpacing.xs),
            )
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaCard(title: String, posterUrl: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
        LumoPoster(
            posterUrl = posterUrl,
            title = title,
            modifier = Modifier.fillMaxWidth(),
            shape = LumoShapes.small,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LoadingLine() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.feature_search_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A present type that matched nothing — its filter stays (SR-11). */
@Composable
private fun EmptyLine(query: String) {
    Text(
        text = stringResource(R.string.feature_search_empty, query),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorLine(onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.feature_search_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.feature_search_retry))
        }
    }
}

@Composable
private fun PageErrorLine(onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.feature_search_page_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.feature_search_retry))
        }
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
