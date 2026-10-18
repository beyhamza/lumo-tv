package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.map
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.model.HOME_RAIL_SIZE
import tv.lumo.android.core.data.model.ResumableFilm
import tv.lumo.android.core.data.model.ResumableSeries
import tv.lumo.android.core.data.model.continueWatchingOf
import tv.lumo.android.core.data.model.resumableFilms
import tv.lumo.android.core.data.model.UnavailableKind
import tv.lumo.android.core.data.model.unavailableFilms
import tv.lumo.android.core.data.valueOrNull

/**
 * What somebody can carry on watching, assembled (US-017, US-019).
 *
 * <h2>Why this is a repository and no longer two view models</h2>
 *
 * The film grid resolved saved positions into films, the series grid resolved
 * them into one card per series, and each did it inside its own view model. The
 * home screen needs both at once, and a feature may not import another — so the
 * assembly moved down here, where the three screens read the same answer. The
 * rules themselves did not move and were not rewritten: they are
 * [resumableFilms], [SeriesRepository.resumable] and [continueWatchingOf], each
 * documented and tested where it lives.
 *
 * <h2>It is not cached, and that is inherited</h2>
 *
 * A position is written on one device and read on another, so it lives on the
 * server and nowhere else — the whole argument is on [ProgressRepository]. What it
 * costs is visible here: offline, nothing below can be read. Each read says so
 * with a failure rather than an empty list (S11-05): "unknown" and "nothing in
 * progress" are different answers, and the home screen keeps the cards it had.
 *
 * Every read is scoped to **one source** (US-018), and the server does the
 * scoping: see [ProgressRepository.continueWatching].
 */
@Singleton
class ContinueWatchingRepository @Inject internal constructor(
    private val progress: ProgressRepository,
    private val vod: VodRepository,
    private val series: SeriesRepository,
) {

    /** Films started and not finished, most recently watched first. A failure stays one. */
    suspend fun films(sourceId: String): LumoResult<List<ResumableFilm>> =
        filmCards(sourceId).map { it.first }

    /** One card per series, most recently watched first. A failure stays one. */
    suspend fun series(sourceId: String): LumoResult<List<ResumableSeries>> =
        seriesCards(sourceId).map { it.first }

    /**
     * The film cards, and the cards of films the server no longer has (CW-28).
     *
     * Every film of the rail is looked up (`VodRepository.resolveFilms`). When that
     * lookup fails, the cache answers; a row the cache cannot answer either makes
     * the whole read a failure, so that the home screen keeps the cards it had
     * rather than dropping one for an outage (S11-05).
     */
    private suspend fun filmCards(
        sourceId: String,
    ): LumoResult<Pair<List<ResumableFilm>, List<ContinueItem.Unavailable>>> {
        val read = progress.continueWatching(sourceId)
        if (read is LumoResult.Failure) return read
        val rows = (read as LumoResult.Success).value
            // Said again on this side: the server was asked for one source, and a
            // rail must not depend on a filter it cannot see having been applied.
            .filter { it.sourceId == sourceId }

        val lookup = vod.resolveFilms(sourceId, rows.map { it.filmId })
        val failure = lookup.failure
        if (failure != null) {
            val known = lookup.found.mapTo(HashSet()) { it.id }
            if (rows.any { it.filmId !in known }) return failure
        }
        val live = rows.filterNot { it.filmId in lookup.gone }
        return LumoResult.Success(resumableFilms(live, lookup.found) to unavailableFilms(rows, lookup.gone))
    }

    /**
     * The series cards, and the cards of series whose episode is gone (CW-28).
     *
     * A failed lookup of the episodes is not a failure of the rail: the cards are
     * built from the cache as they always were, and nothing is said to be gone.
     */
    private suspend fun seriesCards(
        sourceId: String,
    ): LumoResult<Pair<List<ResumableSeries>, List<ContinueItem.Unavailable>>> {
        val read = progress.episodesInProgress(sourceId)
        if (read is LumoResult.Failure) return read
        val rows = (read as LumoResult.Success).value.filter { it.sourceId == sourceId }

        // Only the rows that could make a card are worth asking about.
        val asked = rows.filter { !it.hidden && (it.inContinue || it.finished) }.map { it.episodeId }
        val gone = series.goneEpisodes(sourceId, asked).valueOrNull().orEmpty()
        return LumoResult.Success(series.resumable(rows, gone) to series.unavailable(rows, gone))
    }

    /**
     * "Remove from Continue watching" (S12-04, CW-10): hidden on every device, the
     * position kept. A series card names the episode of the row it was built from —
     * the server hides the whole series from it.
     *
     * A failure stays one: the caller keeps the card and says the removal was not
     * confirmed (CW-26).
     */
    suspend fun hide(item: ContinueItem): LumoResult<Unit> = when (item) {
        is ContinueItem.Film -> progress.hideFilm(item.sourceId, item.resume.film.id)
        is ContinueItem.Show -> progress.hideEpisode(item.sourceId, item.resume.rowEpisodeId)
        is ContinueItem.Unavailable -> when (item.kind) {
            UnavailableKind.FILM -> progress.hideFilm(item.sourceId, item.ref)
            UnavailableKind.EPISODE -> progress.hideEpisode(item.sourceId, item.ref)
        }
    }

    /**
     * Films and series merged into the home screen's rail.
     *
     * The two requests leave together: they are independent, and the home screen
     * is the first thing somebody sees — two round trips in a row would be a rail
     * that arrives twice as late for no reason.
     *
     * Either half failing is a failure of the whole (S11-05): half a rail drawn as
     * if it were the rail would drop the series somebody is in the middle of. The
     * home screen keeps the cards it had and says the rail could not be updated.
     */
    suspend fun all(sourceId: String, limit: Int = HOME_RAIL_SIZE): LumoResult<List<ContinueItem>> =
        coroutineScope {
            val films = async { filmCards(sourceId) }
            val shows = async { seriesCards(sourceId) }

            val filmCards = films.await()
            val showCards = shows.await()
            when {
                filmCards is LumoResult.Failure -> filmCards
                showCards is LumoResult.Failure -> showCards
                else -> filmCards.map { (found, goneFilms) ->
                    val (shown, goneSeries) = (showCards as LumoResult.Success).value
                    continueWatchingOf(found, shown, sourceId, limit, unavailable = goneFilms + goneSeries)
                }
            }
        }
}
