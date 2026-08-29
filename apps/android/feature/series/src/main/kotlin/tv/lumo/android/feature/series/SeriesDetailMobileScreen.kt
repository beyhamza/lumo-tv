package tv.lumo.android.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.Season
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.SeriesTree
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * One series, its seasons and its episodes (US-15).
 *
 * **The only genuinely new screen of the sprint**, and what makes it new is not the
 * layout — it is that the tree arrives separately from everything else on it.
 *
 * <h2>The screen is never empty while it waits</h2>
 *
 * Poster, title, year and synopsis come from the cache and are drawn immediately;
 * only the season area waits. That is the acceptance criterion, and it is what
 * `S6-04`'s four states exist to make possible: a spinner over the whole screen
 * would be a wait imposed on data already held.
 *
 * <h2>Four states, and none of them is a spinner over a tree</h2>
 *
 * - **[SeriesTree.Idle]** — nothing asked yet. One frame, and it draws the header.
 * - **[SeriesTree.Loading]** — a spinner **in the season area only**.
 * - **[SeriesTree.Loaded]** — the seasons. Empty is a real answer, and it says so.
 * - **[SeriesTree.Unavailable]** — the provider did not answer. A sentence and a
 *   retry, never an empty season list: telling somebody their series has no
 *   episodes is a different and far more alarming thing than saying their provider
 *   is not answering.
 *
 * <h2>The first season is open on arrival</h2>
 *
 * A selector waiting for a choice before anything appears is a decision imposed on
 * somebody who came to see episodes.
 */
@Composable
fun SeriesDetailMobileScreen(
    seriesId: String,
    onPlay: (episodeId: String, title: String?, atMs: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(seriesId) { viewModel.start(seriesId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Header(state.series)

        Tree(
            tree = state.tree,
            openSeason = state.openSeason,
            progress = state.progress,
            onSelectSeason = viewModel::onSeasonSelected,
            onRetry = viewModel::retry,
            onPlay = onPlay,
        )

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.feature_series_back))
        }
    }
}

/** What the listing already carries. Drawn before anything is asked of the panel. */
@Composable
private fun Header(series: Series?) {
    Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
        LumoPoster(
            posterUrl = series?.posterUrl,
            title = series?.name.orEmpty(),
            modifier = Modifier.width(POSTER_WIDTH),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            Text(
                text = series?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            series?.facts()?.takeIf { it.isNotEmpty() }?.let { facts ->
                Text(
                    text = facts.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Arrives with the tree, so it is absent until one has been fetched —
            // unlike a film's, which is its own call. Nothing is said about it
            // while it is missing: the seasons below are where the waiting shows.
            series?.plot?.let { plot ->
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun Tree(
    tree: SeriesTree,
    openSeason: Season?,
    progress: Map<String, EpisodeProgress>,
    onSelectSeason: (Int) -> Unit,
    onRetry: () -> Unit,
    onPlay: (String, String?, Long) -> Unit,
) {
    when (tree) {
        // One frame, before the request leaves. Drawing the loading state here
        // would claim a call that has not been made.
        SeriesTree.Idle -> Unit

        SeriesTree.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LumoSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        SeriesTree.Unavailable -> Column(
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.feature_series_tree_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                // Never "this series has no episodes". The series is there; what
                // failed is the call that fills it in, and the two send somebody
                // to two different places.
                text = stringResource(R.string.feature_series_tree_unavailable_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.feature_series_retry))
            }
        }

        is SeriesTree.Loaded -> if (tree.seasons.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                Text(
                    text = stringResource(R.string.feature_series_no_seasons_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Rare and real. Said as a fact about the provider rather than as
                // a failure of ours, because that is what it is.
                Text(
                    text = stringResource(R.string.feature_series_no_seasons_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Seasons(
                seasons = tree.seasons,
                openSeason = openSeason,
                onSelectSeason = onSelectSeason,
            )
            Episodes(season = openSeason, progress = progress, onPlay = onPlay)
        }
    }
}

/** The season strip. One season draws none: a lone chip above its own episodes says nothing. */
@Composable
private fun Seasons(seasons: List<Season>, openSeason: Season?, onSelectSeason: (Int) -> Unit) {
    if (seasons.size < 2) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        items(seasons.size) { index ->
            val season = seasons[index]
            val selected = season.seasonNumber == openSeason?.seasonNumber
            Text(
                text = stringResource(R.string.feature_series_season, season.seasonNumber),
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
                    .clickable { onSelectSeason(season.seasonNumber) }
                    .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
            )
        }
    }
}

@Composable
private fun Episodes(
    season: Season?,
    progress: Map<String, EpisodeProgress>,
    onPlay: (String, String?, Long) -> Unit,
) {
    val episodes = season?.episodes.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
        episodes.forEach { episode ->
            EpisodeRow(episode = episode, progress = progress[episode.id], onPlay = onPlay)
        }

        // The panel's own count, shown only when it disagrees with what it listed.
        // Not reconciled: the list is what somebody can watch, and the claim is
        // occasionally the only hint that a season is incomplete.
        val claimed = season?.episodeCount
        if (claimed != null && claimed != episodes.size) {
            Text(
                text = stringResource(
                    R.string.feature_series_episode_count_mismatch,
                    episodes.size,
                    claimed,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LumoSpacing.xs),
            )
        }
    }
}

/**
 * One episode.
 *
 * The number is always there and the title often is not, so the number is the
 * label when it has to be. **Never "untitled episode"**, which fills a line to say
 * that it is empty.
 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: EpisodeProgress?,
    onPlay: (String, String?, Long) -> Unit,
) {
    val label = episode.name
        ?: stringResource(R.string.feature_series_episode, episode.episodeNumber)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.medium)
            // Where they were, or the beginning. A finished episode starts over,
            // because resuming somebody into the credits is not resuming.
            .clickable {
                onPlay(
                    episode.id,
                    label,
                    progress?.takeIf { !it.finished }?.positionMs ?: 0L,
                )
            }
            .padding(vertical = LumoSpacing.sm, horizontal = LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = episode.episodeNumber.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LumoSpacing.lg),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xxs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // The bar S6-05 asked for and could not have, because nothing saved a
            // position then. Only where there is one: a bar at zero on every row
            // would say that everybody has started everything.
            progress?.fraction()?.let { fraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BAR_HEIGHT)
                        .clip(LumoShapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        episode.durationSeconds?.let { seconds ->
            Text(
                text = stringResource(
                    R.string.feature_series_minutes,
                    (seconds / SECONDS_PER_MINUTE).toInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How far in, as a fraction, or null when there is nothing to draw.
 *
 * **Null without a stated duration**, which is common: a bar needs an end, and a
 * bar drawn full because the end is unknown is a bar that lies. Null when finished
 * too — a full bar on every episode of a season somebody has watched is a wall of
 * ink that says nothing about where they are.
 */
private fun EpisodeProgress.fraction(): Float? {
    val duration = durationMs ?: return null
    if (duration <= 0L || finished) return null
    return (positionMs.toFloat() / duration).coerceIn(0f, 1f)
}
/**
 * Year, typical episode length and rating, absent ones left out.
 *
 * The run time is the panel's own indication and never a duration: whether an
 * episode was watched to the end is decided against that episode's own length.
 */
@Composable
private fun Series.facts(): List<String> = buildList {
    year?.let { add(it.toString()) }
    episodeRunTime?.let { add(stringResource(R.string.feature_series_run_time, it)) }
    rating?.let(::add)
}

private val POSTER_WIDTH = 140.dp

/** Thin enough to read as a mark on a row rather than as a control. */
private val BAR_HEIGHT = 3.dp

private const val SECONDS_PER_MINUTE = 60L
