package tv.lumo.android.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.model.UnavailableKind
import tv.lumo.android.feature.home.navigation.HomeActions

/**
 * One "Continue" card, as either surface draws it.
 *
 * [ContinueItem] has two shapes because pressing one does two different things;
 * a card has one shape because both are a poster, a title and a bar. This is where
 * the two are reconciled, **once**, so that the phone and the television cannot
 * come to disagree about which player a card opens or where it starts — the only
 * part of a card that has a wrong answer.
 */
internal class ContinueCardView(
    val key: String,
    val title: String,
    /** Which episode pressing the card opens. Null for a film. */
    val subtitle: String?,
    val posterUrl: String?,
    /** How far in, or null when there is no bar to draw. See [progressFraction]. */
    val fraction: Float?,
    /** The primary action: straight into the player, at the stored position. */
    val resume: (HomeActions) -> Unit,
    /** The secondary one: the film's or the series' own screen. Null when there is none. */
    val open: ((HomeActions) -> Unit)?,
    /**
     * Set when the film or episode is no longer in the catalogue (CW-28): pressing
     * the card explains that instead of playing, and the only option is removing it.
     */
    val unavailable: UnavailableKind? = null,
)

@Composable
internal fun continueCardOf(item: ContinueItem): ContinueCardView = when (item) {
    is ContinueItem.Film -> {
        val film = item.resume.film
        val progress = item.resume.progress

        ContinueCardView(
            key = item.key,
            title = film.name,
            subtitle = null,
            posterUrl = film.posterUrl,
            fraction = progressFraction(progress.positionMs, progress.durationMs),
            resume = { it.onResumeFilm(film.id, film.sourceId, film.name, progress.positionMs) },
            open = { it.onOpenFilm(film.id) },
        )
    }

    is ContinueItem.Show -> {
        val series = item.resume.series
        val episode = item.resume.episode
        // What the player calls it until its own request answers. The episode's
        // title when the panel has one — far from always — and its number if not.
        val playerTitle = episode.name
            ?: stringResource(R.string.feature_home_episode, episode.episodeNumber)

        ContinueCardView(
            key = item.key,
            title = series.name,
            subtitle = stringResource(
                R.string.feature_home_season_episode,
                episode.seasonNumber,
                episode.episodeNumber,
            ),
            // The series' poster and not a season's: the card is the series'.
            posterUrl = series.posterUrl,
            fraction = progressFraction(
                positionMs = item.resume.positionMs,
                durationMs = episode.durationSeconds?.let { it * MILLIS_PER_SECOND },
            ),
            resume = { actions ->
                // The next episode is launched as such: should it fail, the player
                // offers the series rather than another episode (CW-27).
                if (item.resume.offersNext) {
                    actions.onPlayNextEpisode(episode.id, playerTitle, item.resume.positionMs, series.id)
                } else {
                    actions.onResumeEpisode(episode.id, playerTitle, item.resume.positionMs)
                }
            },
            open = { it.onOpenSeries(series.id) },
        )
    }

    is ContinueItem.Unavailable -> ContinueCardView(
        key = item.key,
        title = item.title ?: stringResource(R.string.feature_home_continue_unavailable_title),
        subtitle = stringResource(R.string.feature_home_continue_unavailable_subtitle),
        posterUrl = null,
        fraction = null,
        // Never a player: the screen explains, see `unavailable`.
        resume = {},
        open = null,
        unavailable = item.kind,
    )
}

/** The label of the way back to the catalogue a gone card belongs to (CW-28). */
internal fun goneCatalogueLabel(kind: UnavailableKind): Int = when (kind) {
    UnavailableKind.FILM -> R.string.feature_home_continue_gone_films
    UnavailableKind.EPISODE -> R.string.feature_home_continue_gone_series
}

/** Where "Back to the catalogue" leads from a card whose content is gone (CW-28). */
internal fun HomeActions.openCatalogueOf(kind: UnavailableKind) = when (kind) {
    UnavailableKind.FILM -> onOpenFilms()
    UnavailableKind.EPISODE -> onOpenSeriesCatalogue()
}

/**
 * How much of the bar is filled, or null for no bar at all.
 *
 * **Null when the length is unknown** — many panels state none, and a bar with no
 * denominator is a fraction of nothing. **Null at position zero** too: that is a
 * series offering its *next* episode, and an empty track under a poster reads as
 * "you have watched none of this", which is the opposite of why the card is there.
 */
internal fun progressFraction(positionMs: Long, durationMs: Long?): Float? {
    if (durationMs == null || durationMs <= 0L || positionMs <= 0L) return null
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

private const val MILLIS_PER_SECOND = 1_000L
