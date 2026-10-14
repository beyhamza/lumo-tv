package tv.lumo.android.feature.watchlist

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.component.LumoStateMessage
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.feature.watchlist.navigation.WatchlistActions

/**
 * "My list" on a phone (US-022, S11-E05): a grid of posters for the active source,
 * most recently added first, the All / Films / Series filters when the list holds
 * both types, and the compact panel of an unavailable card.
 *
 * The states are the ones `watchlist-states.md` retained, and none of them borrows
 * another's face: loading is a spinner, never a flash of "empty"; a failed load
 * keeps what is on screen; offline with nothing loaded says so instead of claiming
 * the list is empty.
 */
@Composable
fun WatchlistMobileScreen(
    actions: WatchlistActions,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Each arrival — including the return from a sheet where the item may just have
    // been removed — re-reads quietly, keeping what is on screen.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Notices(state = state, onRetry = viewModel::retry)

        if (state.filters.isNotEmpty() && state.phase != WatchlistPhase.NoSource) {
            Filters(state.filters, state.filter, viewModel::selectFilter)
        }

        when (val phase = state.phase) {
            WatchlistPhase.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is WatchlistPhase.LoadFailed -> LumoStateMessage(
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
                onAction = viewModel::retry,
            )

            WatchlistPhase.NoSource -> LumoStateMessage(
                title = stringResource(R.string.feature_watchlist_title),
                body = stringResource(R.string.feature_watchlist_no_source),
            )

            WatchlistPhase.Ready -> when {
                state.confirmedEmpty -> LumoStateMessage(
                    title = stringResource(R.string.feature_watchlist_empty_title),
                    body = stringResource(R.string.feature_watchlist_empty_body),
                    actionLabel = stringResource(R.string.feature_watchlist_open_films),
                    onAction = actions.onOpenFilms,
                    secondaryActionLabel = stringResource(R.string.feature_watchlist_open_series),
                    onSecondaryAction = actions.onOpenSeriesCatalogue,
                )

                state.filterEmpty -> Column(
                    modifier = Modifier.padding(LumoSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                ) {
                    Text(
                        text = filterEmptyLabel(state.filter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Button(onClick = { viewModel.selectFilter(WatchlistFilter.All) }) {
                        Text(stringResource(R.string.feature_watchlist_show_all))
                    }
                }

                else -> Grid(
                    state = state,
                    onSelect = { entry -> select(entry, actions, viewModel::openPanel) },
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }

    state.panel?.let { entry ->
        UnavailablePanel(
            entry = entry,
            state = state,
            onRemove = { viewModel.remove(entry) },
            onClose = viewModel::closePanel,
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm)
            .clip(LumoShapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        // Offline too: a re-read is the only thing a press can do, and it is what
        // has to happen before removal comes back (WL-08).
        if (!state.rereading) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.feature_watchlist_retry)) }
        }
    }
}

@Composable
private fun Filters(
    filters: List<WatchlistFilter>,
    selected: WatchlistFilter,
    onSelect: (WatchlistFilter) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun Grid(
    state: WatchlistState,
    onSelect: (WatchlistEntry) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD_MIN_WIDTH),
        contentPadding = PaddingValues(LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.id }) { entry ->
            Card(entry = entry, onSelect = onSelect)
        }

        if (state.hasMore || state.loadMoreFailed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
                ) {
                    if (state.loadMoreFailed) {
                        Text(
                            text = stringResource(R.string.feature_watchlist_load_more_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.loadingMore) {
                        CircularProgressIndicator()
                    } else {
                        OutlinedButton(onClick = onLoadMore) {
                            Text(
                                stringResource(
                                    if (state.loadMoreFailed) {
                                        R.string.feature_watchlist_retry
                                    } else {
                                        R.string.feature_watchlist_load_more
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(entry: WatchlistEntry, onSelect: (WatchlistEntry) -> Unit) {
    val description = cardDescription(entry)

    Column(
        modifier = Modifier
            .clip(LumoShapes.medium)
            .clickable { onSelect(entry) }
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(posterUrl = entry.cardPoster, title = entry.title)
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (entry.available) {
                typeLabel(entry.type)
            } else {
                stringResource(R.string.feature_watchlist_unavailable)
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (entry.available) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

/**
 * The compact panel of an unavailable card (WL-03). A dialog with the known title
 * and the removal, not a sheet: there is no catalogue record behind it to show.
 */
@Composable
private fun UnavailablePanel(
    entry: WatchlistEntry,
    state: WatchlistState,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val removal = state.removal?.takeIf { it.itemId == entry.id }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(entry.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                Text(
                    text = "${stringResource(R.string.feature_watchlist_unavailable)} · ${typeLabel(entry.type)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.feature_watchlist_panel_body))
                panelNote(state, removal)?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onRemove, enabled = state.canRemove) {
                Text(
                    stringResource(
                        when {
                            removal?.busy == true -> R.string.feature_watchlist_removing
                            removal?.unconfirmed == true -> R.string.feature_watchlist_retry
                            else -> R.string.feature_watchlist_remove
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = removal?.busy != true) {
                Text(stringResource(R.string.feature_watchlist_close))
            }
        },
    )
}

/** The sentence under the panel's text: unconfirmed, refused, or offline. */
@Composable
internal fun panelNote(state: WatchlistState, removal: RemovalState?): String? = when {
    removal?.unconfirmed == true -> stringResource(R.string.feature_watchlist_unconfirmed)
    removal?.rejected == true -> stringResource(R.string.feature_watchlist_rejected)
    state.offline -> stringResource(R.string.feature_watchlist_remove_offline)
    else -> null
}

/** Three posters across a narrow phone, more on a tablet. */
private val CARD_MIN_WIDTH = 104.dp
