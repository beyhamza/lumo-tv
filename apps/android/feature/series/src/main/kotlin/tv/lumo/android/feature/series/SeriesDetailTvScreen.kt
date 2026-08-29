package tv.lumo.android.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.Season
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.SeriesTree
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * One series, on a television (US-15, S6-06).
 *
 * <h2>The first screen in the application with two focus zones</h2>
 *
 * Every television surface before this one has had a single place the remote can
 * be — a grid, a strip, one or two buttons. This one has a **season selector** and
 * an **episode list**, and they are genuinely two: `UP` and `DOWN` walk the
 * episodes, `UP` from the first one reaches the seasons, `DOWN` comes back.
 *
 * That is why `docs/design/tv-focus-map.md` gains a section, and why the section is
 * a deliverable rather than paperwork. A second zone is where television
 * applications acquire the defect nobody notices in a simulator: a control that no
 * sequence of key presses reaches.
 *
 * <h2>The focus arrives on an episode, never on the season selector</h2>
 *
 * Somebody who opened a series came to watch one, and the shelf they need is the
 * episodes. `S2-13` made the same ruling about the category strip and it holds
 * here for the same reason: a selector that takes the arrival focus is a decision
 * imposed on somebody who did not ask to make one.
 *
 * **Which episode**, in full and finally (S6-08): the one being watched, then the
 * first not started, then the first listed. `S6-06` shipped only the third branch
 * because nothing saved an episode position then; the other two are what the
 * ten per cent it was short bought.
 *
 * **Within the open season only.** A viewer who chose season 3 is looking at season
 * 3, and moving the focus back to season 1 because that is where they stopped would
 * be the screen arguing with them.
 *
 * <h2>The season selector is hidden when there is one season</h2>
 *
 * A lone chip above its own episodes says nothing and costs a `UP` press to
 * discover that. The phone hides it for the same reason; on a remote the cost is
 * higher, because that press is one somebody makes before they know it was wasted.
 *
 * <h2>The screen is never blank while the tree loads</h2>
 *
 * Poster, title and facts come from the listing and are drawn on the first frame;
 * only the episode area waits. That is `S6-04`'s four states doing the work they
 * were separated for, and on a television it matters more than on a phone: a blank
 * screen at three metres is indistinguishable from a set that has lost its signal.
 */
@Composable
fun SeriesDetailTvScreen(
    seriesId: String,
    onPlay: (episodeId: String, title: String?, atMs: Long) -> Unit,
    onBack: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(seriesId) { viewModel.start(seriesId) }

    // BACK returns to the grid **and says which series was being looked at**, so
    // the remote comes back on that card rather than at the head of fifty thousand.
    // US-10's rule, one screen further out.
    BackHandler { onBack(seriesId) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscanEdges(top = true, end = true, bottom = true)
            .padding(LumoSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xl),
    ) {
        Header(state.series)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Tree(
                tree = state.tree,
                openSeason = state.openSeason,
                progress = state.progress,
                focusEpisodeId = state.resumeEpisodeId,
                onSelectSeason = viewModel::onSeasonSelected,
                onRetry = viewModel::retry,
                onPlay = onPlay,
            )
        }
    }
}

/** The poster, and nothing focusable. What the listing already carries. */
@Composable
private fun Header(series: Series?) {
    Column(
        modifier = Modifier.width(POSTER_WIDTH),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        LumoPoster(
            posterUrl = series?.posterUrl,
            title = series?.name.orEmpty(),
            modifier = Modifier.width(POSTER_WIDTH),
        )

        Text(
            text = series?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        series?.tvFacts()?.takeIf { it.isNotEmpty() }?.let { facts ->
            Text(
                text = facts.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )
        }

        // Capped, and it is the ruling `VodDetailTvScreen` already made and wrote
        // down: the complete answer is a scrolling block, which on a television is
        // a **third focus zone whose only job is to move text** — one the map would
        // have to describe as a place where `OK` does nothing. Long synopses are
        // truncated here and complete on the phone.
        series?.plot?.let { plot ->
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
                maxLines = SYNOPSIS_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Tree(
    tree: SeriesTree,
    openSeason: Season?,
    progress: Map<String, EpisodeProgress>,
    focusEpisodeId: String?,
    onSelectSeason: (Int) -> Unit,
    onRetry: () -> Unit,
    onPlay: (String, String?, Long) -> Unit,
) {
    when (tree) {
        // One frame, before the request leaves. Drawing the loading state here
        // would claim a call that has not been made.
        SeriesTree.Idle -> Unit

        SeriesTree.Loading -> Text(
            text = stringResource(R.string.feature_series_tv_loading),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDarkMuted,
            modifier = Modifier.padding(LumoSpacing.xl),
        )

        SeriesTree.Unavailable -> Unavailable(onRetry = onRetry)

        is SeriesTree.Loaded -> if (tree.seasons.isEmpty()) {
            TvMessage(
                title = stringResource(R.string.feature_series_no_seasons_title),
                body = stringResource(R.string.feature_series_no_seasons_body),
            )
        } else {
            Seasons(
                seasons = tree.seasons,
                openSeason = openSeason,
                onSelectSeason = onSelectSeason,
            )
            Episodes(
                season = openSeason,
                progress = progress,
                focusEpisodeId = focusEpisodeId,
                onPlay = onPlay,
            )
        }
    }
}

/**
 * The provider did not answer.
 *
 * A retry button, and it takes the focus — it is the only control on the screen at
 * this moment, and a television surface with nothing focusable is one where only
 * `BACK` answers.
 *
 * Never "this series has no episodes". The series is there; what failed is the call
 * that fills it in, and the two send somebody to two different places.
 */
@Composable
private fun Unavailable(onRetry: () -> Unit) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
        Text(
            text = stringResource(R.string.feature_series_tree_unavailable_title),
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
        )
        Text(
            text = stringResource(R.string.feature_series_tree_unavailable_body),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
        TvButton(
            label = stringResource(R.string.feature_series_retry),
            focusRequester = retry,
            onClick = onRetry,
        )
    }
}

/**
 * The season strip: the screen's upper focus zone.
 *
 * Hidden below two seasons, which is not only tidiness — it removes the zone
 * entirely, and a screen with one zone is a screen where `UP` from the first
 * episode is an edge rather than a surprise.
 */
@Composable
private fun Seasons(seasons: List<Season>, openSeason: Season?, onSelectSeason: (Int) -> Unit) {
    if (seasons.size < 2) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        contentPadding = PaddingValues(LumoSpacing.xs),
    ) {
        items(seasons.size) { index ->
            val season = seasons[index]
            TvCategoryChip(
                label = stringResource(R.string.feature_series_season, season.seasonNumber),
                selected = season.seasonNumber == openSeason?.seasonNumber,
                onClick = { onSelectSeason(season.seasonNumber) },
            )
        }
    }
}

/**
 * The episode list: the screen's lower zone, and where the focus arrives.
 *
 * A `LazyColumn` rather than the rows drawn one after another, because a season of
 * a long-running series is fifty rows and a television that composed all of them
 * would drop frames on the way in.
 *
 * **The focus lands on the episode to resume, and the list scrolls to it** (S6-08).
 * A season somebody is twelve episodes into opens on episode twelve rather than on
 * episode one — which on a remote is the difference between one press and twelve.
 *
 * The list is re-focused when the season changes: pressing `OK` on a season chip
 * and having the focus stay on the chip would leave somebody looking at a list they
 * cannot reach without pressing `DOWN` — which works, and is a press spent finding
 * out that the thing they asked for did happen.
 */
@Composable
private fun Episodes(
    season: Season?,
    progress: Map<String, EpisodeProgress>,
    focusEpisodeId: String?,
    onPlay: (String, String?, Long) -> Unit,
) {
    val episodes = season?.episodes.orEmpty()
    val target = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // The episode to resume, or the first. Resolved here rather than defaulted to
    // zero, so an episode dropped from the tree between the two falls back to the
    // head of the list instead of to nothing focusable.
    val focusIndex = episodes.indexOfFirst { it.id == focusEpisodeId }.coerceAtLeast(0)

    // Keyed on the season so a change moves the focus, on the count because the
    // rows are not composed on the frame a tree arrives, and on the index because
    // the saved positions answer a moment after the tree does.
    LaunchedEffect(season?.seasonNumber, episodes.size, focusIndex) {
        if (episodes.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(focusIndex)
        // Failing to focus is recoverable — the D-pad still works — and throwing
        // would take the screen down.
        runCatching { target.requestFocus() }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        contentPadding = PaddingValues(vertical = LumoSpacing.xs),
        modifier = Modifier.fillMaxHeight(),
    ) {
        items(episodes.size) { index ->
            EpisodeRow(
                episode = episodes[index],
                progress = progress[episodes[index].id],
                onPlay = onPlay,
                modifier = if (index == focusIndex) {
                    Modifier.focusRequester(target)
                } else {
                    Modifier
                },
            )
        }

        // The panel's own count, shown only when it disagrees with what it listed.
        // Not reconciled: the list is what somebody can watch, and the claim is
        // occasionally the only hint that a season is incomplete.
        val claimed = season?.episodeCount
        if (claimed != null && claimed != episodes.size) {
            item {
                Text(
                    text = stringResource(
                        R.string.feature_series_episode_count_mismatch,
                        episodes.size,
                        claimed,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                    modifier = Modifier.padding(top = LumoSpacing.sm),
                )
            }
        }
    }
}
/**
 * One episode, as a row the remote can land on.
 *
 * The number is always there and the title often is not, so the number is the label
 * when it has to be. **Never "untitled episode"**, which fills a line to say that
 * it is empty.
 *
 * `OK` plays it. There is no second control and no long press: an episode has one
 * thing somebody wants from it, and a gesture that does nothing is worse than no
 * gesture.
 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: EpisodeProgress?,
    onPlay: (String, String?, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = episode.name
        ?: stringResource(R.string.feature_series_episode, episode.episodeNumber)
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // Where they were, or the beginning. A finished episode starts
                // over, because resuming somebody into the credits is not
                // resuming.
                onPlay(episode.id, label, progress?.takeIf { !it.finished }?.positionMs ?: 0L)
            }
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = episode.episodeNumber.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDarkMuted,
            modifier = Modifier.width(NUMBER_WIDTH),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Only where there is a position (S6-08). A bar at zero on every row
            // would say that everybody has started everything, and at three metres
            // a wall of identical bars carries no information at all.
            progress?.fraction()?.let { fraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BAR_HEIGHT)
                        .clip(LumoShapes.small)
                        .background(LumoColors.Surface),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(LumoColors.Accent),
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
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )
        }
    }
}

/**
 * How far in, as a fraction, or null when there is nothing to draw.
 *
 * **Null without a stated duration**, which is common: a bar needs an end, and one
 * drawn full because the end is unknown is a bar that lies. Null when finished too
 * — a full bar on every episode of a watched season is ink that says nothing about
 * where somebody is.
 */
private fun EpisodeProgress.fraction(): Float? {
    val duration = durationMs ?: return null
    if (duration <= 0L || finished) return null
    return (positionMs.toFloat() / duration).coerceIn(0f, 1f)
}
/** A control the remote can land on. `VodDetailTvScreen`'s, with its argument. */
@Composable
private fun TvButton(label: String, focusRequester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoShapes.medium)
            .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = LumoSpacing.xl, vertical = LumoSpacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) LumoColors.OnAccent else LumoColors.OnDark,
        )
    }
}

/**
 * Year, typical episode length and rating, absent ones left out.
 *
 * The run time is the panel's own indication and never a duration: whether an
 * episode was watched to the end is decided against that episode's own length.
 */
@Composable
private fun Series.tvFacts(): List<String> = buildList {
    year?.let { add(it.toString()) }
    episodeRunTime?.let { add(stringResource(R.string.feature_series_run_time, it)) }
    rating?.let(::add)
}

/** The film screen's, for the film screen's reason: a picture seen from three metres. */
private val POSTER_WIDTH = 280.dp

/** Wide enough for three digits, so the titles line up down the whole season. */
private val NUMBER_WIDTH = 48.dp

/** Thicker than the phone's: three metres away, three device pixels is nothing. */
private val BAR_HEIGHT = 6.dp

/** See [Header]: a cap, stated, rather than a third focus zone. */
private const val SYNOPSIS_LINES = 8

private const val SECONDS_PER_MINUTE = 60L
