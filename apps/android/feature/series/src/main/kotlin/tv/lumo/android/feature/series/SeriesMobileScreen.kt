package tv.lumo.android.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The series catalogue on the phone (US-15).
 *
 * **The films grid, and not a line of difference** — same pager configuration,
 * same two columns, same category strip, same search. A viewer who has learnt one
 * of the two catalogues has learnt the other, and divergence would be the defect.
 *
 * The one thing it does differently is where a card goes: opening a series opens a
 * *tree*, and that tree costs a call to the user's own panel. Nothing on this
 * screen triggers one.
 */
@Composable
fun SeriesMobileScreen(
    onOpenSeries: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state.step) {
            SeriesStep.Loading -> Centered { CircularProgressIndicator() }

            SeriesStep.NoSource -> Message(
                title = stringResource(R.string.feature_series_no_source_title),
                body = stringResource(R.string.feature_series_no_source_body),
            )

            SeriesStep.NotReadyYet -> Message(
                title = stringResource(R.string.feature_series_not_ready_title),
                body = stringResource(R.string.feature_series_not_ready_body),
            )

            SeriesStep.Browsing -> {
                Header(state = state, onRefresh = viewModel::refresh)

                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    singleLine = true,
                    label = { Text(stringResource(R.string.feature_series_search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LumoSpacing.md),
                )

                Categories(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = viewModel::onCategorySelected,
                )

                if (items.itemCount == 0 && !state.refreshing) {
                    Message(
                        title = if (state.query.isBlank()) {
                            stringResource(R.string.feature_series_empty_title)
                        } else {
                            stringResource(R.string.feature_series_no_results_title)
                        },
                        // Three sentences, not one. A playlist that *cannot* carry
                        // series and a panel that offers none are different facts,
                        // and telling an Xtream user their panel cannot do
                        // something it can would be the worse mistake (adr/0010).
                        body = when {
                            state.query.isNotBlank() ->
                                stringResource(R.string.feature_series_no_results_body)
                            state.isPlaylist ->
                                stringResource(R.string.feature_series_empty_playlist)
                            else -> stringResource(R.string.feature_series_empty_panel)
                        },
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(COLUMNS),
                        contentPadding = PaddingValues(LumoSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = items.itemCount,
                            key = items.itemKey { it.id },
                        ) { index ->
                            SeriesCard(series = items[index], onOpen = onOpenSeries)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: SeriesState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_series_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_series_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.refreshing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(start = LumoSpacing.sm)
                        .size(LumoSpacing.md),
                )
            } else {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.feature_series_refresh))
                }
            }
        }
    }

    if (state.refreshFailed) {
        Text(
            text = stringResource(R.string.feature_series_refresh_failed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = LumoSpacing.md),
        )
    }
}

/** The film screen's strip, unchanged. */
@Composable
private fun Categories(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        modifier = Modifier.padding(vertical = LumoSpacing.sm),
    ) {
        item {
            CategoryChip(
                label = stringResource(R.string.feature_series_all_categories),
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
        }
        items(categories.size) { index ->
            val category = categories[index]
            CategoryChip(
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
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(LumoShapes.small)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/** One series: its poster, its title, its year. The film card, unchanged. */
@Composable
private fun SeriesCard(series: Series?, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.clickable(enabled = series != null) {
            series?.let { onOpen(it.id) }
        },
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(
            posterUrl = series?.posterUrl,
            title = series?.name.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = series?.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        series?.year?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Message(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Two, for the reason the films grid has two: recognising the picture is the job. */
private const val COLUMNS = 2
