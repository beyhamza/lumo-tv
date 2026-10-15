package tv.lumo.android.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.PrimaryPlay
import tv.lumo.android.core.data.model.SheetPlay
import tv.lumo.android.core.data.model.sheetPlayOf

/**
 * The words on the series sheet's main button (S12-03): which episode, and whether
 * it is watched, resumed or watched again. "Resume" without saying which episode is
 * a button somebody presses to find out.
 */
@Composable
internal fun seriesPrimaryLabel(episode: Episode, play: SheetPlay, clock: (Long) -> String): String {
    val which = stringResource(R.string.feature_series_season_episode, episode.seasonNumber, episode.episodeNumber)
    return when (val primary = play.primary) {
        PrimaryPlay.Watch -> stringResource(R.string.feature_series_watch_episode, which)
        is PrimaryPlay.Resume -> stringResource(R.string.feature_series_resume_episode, which, clock(primary.positionMs))
        PrimaryPlay.WatchAgain -> stringResource(R.string.feature_series_watch_again_episode, which)
    }
}

/** "Start S1 E4 over", beside a Resume. */
@Composable
internal fun seriesStartOverLabel(episode: Episode): String = stringResource(
    R.string.feature_series_start_over_episode,
    stringResource(R.string.feature_series_season_episode, episode.seasonNumber, episode.episodeNumber),
)

/** The player's title for an episode: its name, or its number. */
@Composable
internal fun episodePlayerTitle(episode: Episode): String =
    episode.name ?: stringResource(R.string.feature_series_episode, episode.episodeNumber)

/**
 * Where an episode row starts (S12-03): its own position when started and not
 * finished, the beginning otherwise — resuming somebody into the credits is not
 * resuming. The same rule as the sheet's main button, one episode at a time.
 */
internal fun EpisodeProgress?.rowStartMs(episode: Episode): Long =
    (sheetPlayOf(this?.positionMs, this?.durationMs ?: episode.durationSeconds?.let { it * 1_000L }).primary as? PrimaryPlay.Resume)
        ?.positionMs ?: 0L

/** Milliseconds as a clock, the hour only when there is one. The players' format. */
internal fun Long.asSheetClock(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
