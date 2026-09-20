package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.model.HOME_RAIL_SIZE
import tv.lumo.android.core.data.model.ResumableFilm
import tv.lumo.android.core.data.model.ResumableSeries
import tv.lumo.android.core.data.model.continueWatchingOf
import tv.lumo.android.core.data.model.resumableFilms

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
 * costs is visible here: offline, every list below is empty. The home screen
 * treats that as a section with nothing in it and hides it, which is the honest
 * rendering of "unknown" for a rail whose entire content is a position.
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

    /** Films started and not finished, most recently watched first. */
    suspend fun films(sourceId: String): List<ResumableFilm> {
        val rows = progress.continueWatching(sourceId)
            // Said again on this side: the server was asked for one source, and a
            // rail must not depend on a filter it cannot see having been applied.
            .filter { it.sourceId == sourceId }

        return resumableFilms(rows, vod.filmsByIds(sourceId, rows.map { it.filmId }))
    }

    /** One card per series, most recently watched first. */
    suspend fun series(sourceId: String): List<ResumableSeries> =
        series.resumable(
            progress.episodesInProgress(sourceId).filter { it.sourceId == sourceId },
        )

    /**
     * Films and series merged into the home screen's rail.
     *
     * The two requests leave together: they are independent, and the home screen
     * is the first thing somebody sees — two round trips in a row would be a rail
     * that arrives twice as late for no reason.
     */
    suspend fun all(sourceId: String, limit: Int = HOME_RAIL_SIZE): List<ContinueItem> =
        coroutineScope {
            val films = async { films(sourceId) }
            val shows = async { series(sourceId) }

            continueWatchingOf(films.await(), shows.await(), sourceId, limit)
        }
}
