package tv.lumo.android.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import tv.lumo.android.core.data.EmptyGrid
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.emptyGridOf
import tv.lumo.android.core.data.labelRes
import tv.lumo.android.core.data.messageRes
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.model.ResumableFilm
import tv.lumo.android.core.data.model.WatchProgress
import tv.lumo.android.core.data.wording
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.component.LumoSourceNotice
import tv.lumo.android.core.designsystem.component.LumoStateMessage
import tv.lumo.android.core.designsystem.effect.PollWhile
import tv.lumo.android.core.designsystem.effect.SOURCE_NOTICE_POLL_MILLIS
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The film catalogue on the phone (US-13).
 *
 * <h2>A grid, where the channels got a list</h2>
 *
 * Same mechanics, different shape, and the difference is not decoration: a
 * channel is chosen by a name somebody already knows, so a list of names is the
 * right instrument. **A film is chosen by looking.** A list of film titles is a
 * catalogue nobody can browse, which is why every product that has ever shown
 * films has shown posters — and why the poster, not the title, is what a card is.
 *
 * <h2>Two columns, and the number is the argument</h2>
 *
 * A poster is 2:3. Three columns on a phone puts a poster at about 120 dp wide,
 * which is under the size at which somebody recognises a film they have seen. Two
 * is the widest layout the screen affords, and recognising the picture is the
 * whole job of this screen.
 *
 * <h2>A card with no poster is not a card with a placeholder</h2>
 *
 * It is its title on a flat colour — see `LumoPoster`. Lumo ships no artwork of
 * its own (CLAUDE.md, règle 2), and on a real source a great many posters are
 * advertised over `http`, which this application does not permit. "No poster" and
 * "the poster did not load" are one outcome from where the user is sitting.
 */
@Composable
fun VodMobileScreen(
    onOpenFilm: (filmId: String) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state.step) {
            VodStep.Loading -> Centered { CircularProgressIndicator() }

            VodStep.NoSource -> Message(
                title = stringResource(R.string.feature_vod_no_source_title),
                body = stringResource(R.string.feature_vod_no_source_body),
            )

            VodStep.NeedsChoice -> Message(
                title = stringResource(R.string.feature_vod_needs_choice_title),
                body = stringResource(R.string.feature_vod_needs_choice_body),
            )

            // The two faces of a first import, which used to share one "not
            // ready" sentence: one says wait and shows the real step, the other
            // says why and where it is fixed (US-024).
            VodStep.Importing -> LumoStateMessage(
                title = stringResource(DataR.string.core_data_first_import_title),
                detail = stringResource(
                    (state.notice as? SourceNotice.Refreshing)?.step.labelRes(),
                ),
                body = stringResource(DataR.string.core_data_first_import_body),
                actionLabel = stringResource(DataR.string.core_data_notice_open_sources),
                onAction = onOpenSources,
            )

            VodStep.ImportFailed -> LumoStateMessage(
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
            VodStep.Unreachable -> LumoStateMessage(
                title = stringResource(DataR.string.core_data_unreached_title),
                body = stringResource(DataR.string.core_data_unreached_body),
                actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                onAction = viewModel::refreshSource,
                secondaryActionLabel = stringResource(DataR.string.core_data_notice_change_source),
                onSecondaryAction = onOpenSources,
            )

            VodStep.Browsing -> {
                Header(state = state, onRefresh = viewModel::refresh)

                // Over the grid and never instead of it: the catalogue stays
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
                            horizontal = LumoSpacing.md,
                            vertical = LumoSpacing.xs,
                        ),
                    )
                }

                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    singleLine = true,
                    label = { Text(stringResource(R.string.feature_vod_search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LumoSpacing.md),
                )

                ContinueWatching(films = state.continueWatching, onOpen = onOpenFilm)

                Categories(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = viewModel::onCategorySelected,
                )

                // An empty grid after a search is not an empty catalogue, and the
                // two need different sentences: one is "try another word", the
                // other is "your source has no films". Only the loaded, settled
                // case can tell them apart, which is what `itemCount` after a
                // finished append means.
                if (
                    state.query.isBlank() &&
                    emptyGridOf(films.itemCount, state.refreshing, state.refreshFailed) ==
                    EmptyGrid.Unavailable
                ) {
                    // Failed to load is not empty (US-024): "this source has no
                    // films" would be a statement about the source made from a
                    // request that never reached it.
                    LumoStateMessage(
                        title = stringResource(DataR.string.core_data_catalogue_unavailable_title),
                        body = stringResource(DataR.string.core_data_catalogue_unavailable_body),
                        actionLabel = stringResource(DataR.string.core_data_catalogue_retry),
                        onAction = viewModel::refresh,
                    )
                } else if (films.itemCount == 0 && !state.refreshing) {
                    Message(
                        title = if (state.query.isBlank()) {
                            stringResource(R.string.feature_vod_empty_title)
                        } else {
                            stringResource(R.string.feature_vod_no_results_title)
                        },
                        body = if (state.query.isBlank()) {
                            stringResource(R.string.feature_vod_empty_body)
                        } else {
                            stringResource(R.string.feature_vod_no_results_body)
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
                            count = films.itemCount,
                            key = films.itemKey { it.id },
                        ) { index ->
                            // Null is a placeholder Paging has not loaded yet. It
                            // draws at the right size so the grid does not reflow
                            // as windows arrive.
                            FilmCard(film = films[index], onOpen = onOpenFilm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: VodState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_vod_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Discreet, and only when it is true — the same rule the channel
            // screen follows, for the same reason: serving the cache is what this
            // screen does well, and a banner would call it a failure.
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_vod_offline),
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
                    Text(stringResource(R.string.feature_vod_refresh))
                }
            }
        }
    }

    if (state.refreshFailed) {
        Text(
            text = stringResource(R.string.feature_vod_refresh_failed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = LumoSpacing.md),
        )
    }
}

/**
 * The "continue watching" rail (S5-11).
 *
 * At the head of the catalogue, and a **rail** rather than a filter — unlike the
 * television, where the same list is a chip. A phone has the height to carry a
 * row above the grid; a television's category strip has no room for a second
 * mechanism, which is the ruling `S4-08` made for the channels and it holds here.
 *
 * Absent when empty rather than drawn with a "nothing yet" placeholder: a heading
 * over an empty row on the first visit is a promise about a feature nobody has
 * used, taking space from the catalogue they came for.
 *
 * The bar under each poster is the position, and it is the only thing on this
 * screen that is not in the grid below. **Drawn only when the length is known** —
 * many panels state none, and a bar with no denominator would be a fraction of
 * nothing.
 */
@Composable
private fun ContinueWatching(films: List<ResumableFilm>, onOpen: (String) -> Unit) {
    if (films.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
        Text(
            text = stringResource(R.string.feature_vod_continue_watching),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = LumoSpacing.md),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = LumoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            items(films.size) { index ->
                val entry = films[index]
                Column(
                    modifier = Modifier
                        .width(RAIL_POSTER_WIDTH)
                        .clickable { onOpen(entry.film.id) },
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
                ) {
                    LumoPoster(
                        posterUrl = entry.film.posterUrl,
                        title = entry.film.name,
                        modifier = Modifier.fillMaxWidth(),
                        overlay = { PositionBar(entry.progress) },
                    )
                    Text(
                        text = entry.film.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** How far in, drawn across the foot of the poster. Nothing when the length is unknown. */
@Composable
private fun BoxScope.PositionBar(progress: WatchProgress) {
    val duration = progress.durationMs ?: return
    if (duration <= 0L) return

    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(RAIL_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((progress.positionMs.toFloat() / duration).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** The film categories. The channel screen's strip, unchanged — see `LiveMobileScreen`. */
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
                label = stringResource(R.string.feature_vod_all_categories),
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
        }
        items(categories.size) { index ->
            val category = categories[index]
            CategoryChip(
                // The count is the server's and is null when it did not count. An
                // absent count is a chip without a number, not a zero.
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

/**
 * One film: its poster, and its title under it.
 *
 * The title is under the picture and not over it. A title burned into the corner
 * of a poster is unreadable on the half of posters that have something bright
 * there, and every scrim heavy enough to fix that hides the picture the card
 * exists to show.
 *
 * The year sits beside the title because two films share a name often enough that
 * a catalogue without it makes somebody open the wrong one. It is absent when the
 * source did not state one — many do not — and an absent year is nothing, never a
 * dash.
 */
@Composable
private fun FilmCard(film: VodItem?, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.clickable(enabled = film != null) {
            film?.let { onOpen(it.id) }
        },
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(
            posterUrl = film?.posterUrl,
            title = film?.name.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = film?.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        film?.year?.let { year ->
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

/** See the class documentation: two, because recognising the picture is the job. */
private const val COLUMNS = 2

/** Narrower than a grid card: the rail is a shortcut, not a second catalogue. */
private val RAIL_POSTER_WIDTH = 110.dp

private val RAIL_BAR_HEIGHT = 4.dp
