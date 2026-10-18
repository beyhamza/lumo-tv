package tv.lumo.android.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The second line of the next-episode card, on both surfaces (S13-03).
 *
 * "S2 E1" always — a season transition is visible at a glance — then "Resumes
 * at 12:04" when the next episode was already started (CW-12).
 */
@Composable
internal fun upNextDetail(offer: UpNext): String {
    val number = stringResource(
        R.string.feature_series_season_episode,
        offer.episode.seasonNumber,
        offer.episode.episodeNumber,
    )
    val resume = offer.resumeAtMs?.let {
        stringResource(R.string.feature_series_up_next_resume_at, it.asResumeClock())
    }
    return listOfNotNull(number, resume).joinToString(" · ")
}

private fun Long.asResumeClock(): String {
    val total = (this / 1_000L).coerceAtLeast(0L)
    val hours = total / 3_600L
    val minutes = (total / 60L) % 60L
    val seconds = total % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
