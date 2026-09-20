package tv.lumo.android.core.data.repository

import androidx.paging.PagingData
import androidx.paging.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.model.Cached
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.Season
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.ResumableSeries
import tv.lumo.android.core.data.model.SeriesTree
import tv.lumo.android.core.data.model.episodeAfter
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.SeriesDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.EpisodeEntity
import tv.lumo.android.core.database.model.SeasonEntity
import tv.lumo.android.core.database.model.SeriesEntity
import tv.lumo.android.core.database.paging.SeriesPager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.model.ContentType
import tv.lumo.android.network.generated.model.Category as ApiCategory
import tv.lumo.android.network.generated.model.SeriesDetail as ApiSeriesDetail
import tv.lumo.android.network.generated.model.Series as ApiSeries

/**
 * The series catalogue, offline first (US-15).
 *
 * The shape is [VodRepository]'s and, above it, [CatalogueRepository]'s: every read
 * comes out of Room, [refresh] is the only thing that talks to the server, and
 * where an answer came from is a value rather than a guess. **Divergence would be
 * the defect.**
 *
 * <h2>What a tree changes, and it is the whole of this class's new surface</h2>
 *
 * A film has one on-demand field — its synopsis — and a nullable string is enough
 * to describe it. A series has a *tree*, and a screen has to tell three situations
 * apart that look alike: a panel that genuinely lists no seasons, a tree on its
 * way, and a provider that did not answer. Rendering any two of them the same
 * produces either an empty screen that reads as a series with no episodes, or a
 * spinner that never stops on a series already held.
 *
 * So [tree] returns a [SeriesTree] rather than a list, and [loadTree] moves it
 * between states. The states are named where they are defined; what belongs here is
 * that **the cache decides, not the caller**: a tree already stored is emitted
 * before any request is made.
 *
 * <h2>A series whose tree was never loaded still appears in the grid</h2>
 *
 * Poster, title, year — what the listing carries, and enough to choose with. The
 * tree is what a *detail* screen needs, and asking for one per card would be the
 * request-per-series this whole design refuses.
 */
@Singleton
class SeriesRepository @Inject internal constructor(
    private val api: CatalogApi,
    private val calls: ApiCaller,
    private val categoryDao: CategoryDao,
    private val seriesDao: SeriesDao,
    private val pager: SeriesPager,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) {

    /** Why each source's series cache is stale, keyed by source id. See [VodRepository]. */
    private val staleness = MutableStateFlow<Map<String, LumoError?>>(emptyMap())

    /**
     * The tree state of each series this process has looked at.
     *
     * In memory rather than in Room, and only the *state*: the tree itself is
     * cached, this is what is being done about it right now. A `Loading` written to
     * disk would survive a process death and leave a spinner nothing can clear.
     */
    private val treeStates = MutableStateFlow<Map<String, SeriesTree>>(emptyMap())

    /** The series categories of one source, and whether they are fresh. */
    fun categories(sourceId: String): Flow<Cached<List<Category>>> =
        combine(
            categoryDao.observeBySource(sourceId, ContentType.SERIES.value),
            staleness,
        ) { entities, stale ->
            cached(sourceId, entities.map(CategoryEntity::asCategory), stale)
        }

    /** Series, paginated out of SQLite. @param categoryId null for every series. */
    fun series(sourceId: String, categoryId: String? = null): Flow<PagingData<Series>> {
        val entities = if (categoryId == null) {
            pager.seriesInSource(sourceId)
        } else {
            pager.seriesInCategory(sourceId, categoryId)
        }
        return entities.map { page -> page.map(SeriesEntity::asSeries) }
    }

    /** Local search over what has already been synchronised. See [VodRepository.search]. */
    fun search(sourceId: String, query: String): Flow<PagingData<Series>> =
        pager.search(sourceId, query).map { page -> page.map(SeriesEntity::asSeries) }

    /** One series, from the cache, kept current. */
    fun one(id: String): Flow<Series?> = seriesDao.observe(id).map { it?.asSeries() }

    /**
     * The tree of one series, as a state a screen can render directly.
     *
     * Three sources combined into one value: what Room holds, what this process is
     * doing about it, and the stamp that says whether what Room holds is old. A
     * screen reading three flows would render one against the other at least once,
     * and the wrong state would be on screen for exactly one frame — the kind of
     * bug only users see.
     */
    fun tree(seriesId: String): Flow<SeriesTree> =
        combine(
            seriesDao.observeSeasons(seriesId),
            seriesDao.observeEpisodes(seriesId),
            seriesDao.observe(seriesId),
            treeStates,
        ) { seasons, episodes, series, states ->
            val stored = series?.treeFetchedAt
            when {
                // Something is cached: it is what the viewer sees, whatever is
                // happening behind it. A refresh running over a tree already held
                // must not put a spinner over the episodes somebody knows.
                stored != null -> SeriesTree.Loaded(
                    seasons = assemble(seasons, episodes),
                    stale = states[seriesId] == SeriesTree.Loading,
                )
                else -> states[seriesId] ?: SeriesTree.Idle
            }
        }

    /**
     * Fetches the tree of one series, if it is worth fetching.
     *
     * <h3>The freshness decision is the server's, not this one's</h3>
     *
     * The server holds the authoritative stamp and applies the six-hour window; it
     * serves a stale tree while refreshing behind the answer. **This side's stamp
     * decides something narrower**: whether to put a spinner on screen. A device
     * that has a tree shows it and asks anyway — because another device may have
     * refreshed since, and because the request is one call either way.
     *
     * A device with nothing shows [SeriesTree.Loading] and waits, because there is
     * nothing else to show.
     */
    suspend fun loadTree(seriesId: String): LumoResult<Unit> = withContext(io) {
        val cached = seriesDao.observe(seriesId).first()?.treeFetchedAt != null
        if (!cached) {
            treeStates.update { it + (seriesId to SeriesTree.Loading) }
        } else {
            // Marked so a screen can say it is refreshing without losing what it
            // already draws. `tree` reads this as `stale`, never as a spinner.
            treeStates.update { it + (seriesId to SeriesTree.Loading) }
        }

        val result = calls.call { api.getSeries(UUID.fromString(seriesId)) }
        if (result is LumoResult.Failure) {
            // Nothing cached and nothing coming: the one state that is neither a
            // tree nor a wait. With something cached, the entry is simply dropped
            // and `tree` goes on emitting what is held.
            treeStates.update {
                if (cached) it - seriesId else it + (seriesId to SeriesTree.Unavailable)
            }
            return@withContext result
        }

        val detail = (result as LumoResult.Success).value
        store(seriesId, detail)
        treeStates.update { it - seriesId }
        LumoResult.Success(Unit)
    }

    private suspend fun store(seriesId: String, detail: ApiSeriesDetail) {
        // The series row itself, so a tree reached by a deep link has a poster and
        // a title even when no refresh has run.
        seriesDao.upsert(listOf(detail.series.asEntity()))

        val seasons = mutableListOf<SeasonEntity>()
        val episodes = mutableListOf<EpisodeEntity>()
        for (season in detail.seasons) {
            val seasonId = "$seriesId:${season.seasonNumber}"
            seasons += SeasonEntity(
                id = seasonId,
                seriesId = seriesId,
                seasonNumber = season.seasonNumber,
                episodeCount = season.episodeCount,
                posterUrl = season.posterUrl,
            )
            for (episode in season.episodes) {
                episodes += EpisodeEntity(
                    id = episode.id.toString(),
                    seriesId = episode.seriesId.toString(),
                    seasonId = seasonId,
                    sourceId = episode.sourceId.toString(),
                    externalId = episode.externalId,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    name = episode.name,
                    durationSeconds = episode.durationSeconds,
                    plot = episode.plot,
                )
            }
        }

        seriesDao.replaceTree(seriesId, seasons, episodes, System.currentTimeMillis())
        seriesDao.updatePlot(seriesId, detail.series.plot)
    }

    /**
     * Pulls the whole series catalogue of one source into the cache.
     *
     * The flat list only. Walking the trees here would be one request per series
     * against the user's own provider, which is what `GET /series/{id}` exists to
     * avoid.
     *
     * **A source with no series is a success.** Every M3U playlist is one
     * (`adr/0010`), and an empty page means an empty grid — not a banner telling
     * somebody their source is broken.
     */
    suspend fun refresh(sourceId: String): LumoResult<Unit> = withContext(io) {
        val id = UUID.fromString(sourceId)

        val categories = calls.call { api.listCategories(id, ContentType.SERIES) }
        if (categories is LumoResult.Failure) return@withContext markStale(sourceId, categories)

        val rows = mutableListOf<SeriesEntity>()
        var page = 0
        while (true) {
            val result = calls.call { api.listSeries(id, page = page, size = PAGE_SIZE) }
            if (result is LumoResult.Failure) return@withContext markStale(sourceId, result)

            val body = (result as LumoResult.Success).value
            rows += body.items.map { it.asEntity() }

            if (page + 1 >= body.totalPages || page + 1 >= MAX_PAGES) break
            page++
        }

        categoryDao.replaceForSourceAndType(
            sourceId,
            ContentType.SERIES.value,
            (categories as LumoResult.Success).value.items.map { it.asEntity() },
        )
        seriesDao.replaceForSource(sourceId, rows)

        staleness.update { it + (sourceId to null) }
        LumoResult.Success(Unit)
    }

    /** How many series the cache holds for a source. Drives the empty state. */
    suspend fun cachedSeriesCount(sourceId: String): Int =
        withContext(io) { seriesDao.countForSource(sourceId) }

    /** Several series from the cache, by id. For a rail. See [VodRepository.filmsByIds]. */
    suspend fun seriesByIds(ids: List<String>): List<Series> = withContext(io) {
        if (ids.isEmpty()) emptyList() else seriesDao.byIds(ids).map { it.asSeries() }
    }

    /** Several episodes from the cache, by id. What a resume rail resolves through. */
    suspend fun episodesByIds(ids: List<String>): List<Episode> = withContext(io) {
        if (ids.isEmpty()) emptyList() else seriesDao.episodesByIds(ids).map { it.asEpisode() }
    }

    /**
     * The episode that follows this one, or null (S6-06).
     *
     * **Answered from the cache and nothing else.** An episode ending is the worst
     * possible moment to make a request: the network is exactly where it was a
     * moment ago, the panel is slow, and the countdown would be spent watching a
     * spinner rather than deciding. The tree is in Room because the viewer opened
     * this series to reach this episode — if it were not, they could not have.
     *
     * A tree that is *not* held returns null, which is the honest answer: nothing
     * is offered rather than something guessed. That happens after a process death
     * mid-episode, and offering nothing there is what the last episode of a series
     * already does — one behaviour, not two.
     *
     * The rule itself is `List<Season>.episodeAfter`, which is where it is
     * documented and where it is tested.
     */
    suspend fun nextEpisode(episodeId: String): Episode? = withContext(io) {
        val current = seriesDao.episodesByIds(listOf(episodeId)).firstOrNull()
            ?: return@withContext null

        assemble(
            seasons = seriesDao.seasonsOf(current.seriesId),
            episodes = seriesDao.episodesOf(current.seriesId),
        ).episodeAfter(episodeId)
    }
    /**
     * Turns saved episode positions into one card per series (S6-08).
     *
     * <h2>The rule, and every line of it is a case somebody hits</h2>
     *
     * - **One card per series, never one per episode.** Somebody who watched three
     *   episodes last night has three rows and wants one card. The most recently
     *   touched row wins, which is the order the server already returns.
     * - **Under the threshold** — that episode, at its position.
     * - **Over it** — the *next* episode, from the beginning. What somebody wants
     *   after the credits is the following episode, not the credits again.
     * - **Over it with nothing after** — the series leaves the rail. It is
     *   finished, and offering to start it over is not an offer.
     *
     * <h2>Only series whose tree this device holds, and it is assumed</h2>
     *
     * Going from an episode to its series needs the tree, and the tree is in Room
     * only for series opened at least once **on this device** — which is every
     * series somebody has begun watching here. The exception is resuming on the
     * television something started on the phone; that resolves the moment the
     * series screen is opened, which is the normal way in.
     *
     * The alternative would be a request per row at the moment a catalogue screen
     * opens, and a rail that costs twelve calls to the panel is not a rail.
     */
    suspend fun resumable(rows: List<EpisodeProgress>): List<ResumableSeries> =
        withContext(io) {
            val episodes = seriesDao.episodesByIds(rows.map { it.episodeId })
                .associateBy { it.id }

            // Insertion order is the server's order — most recently touched first —
            // and `distinctBy` keeps the first it sees. That is the "one card per
            // series" rule and the "most recent wins" rule in one step.
            rows.mapNotNull { row -> episodes[row.episodeId]?.let { row to it } }
                .distinctBy { (_, episode) -> episode.seriesId }
                .mapNotNull { (row, episode) -> card(row, episode) }
        }

    private suspend fun card(row: EpisodeProgress, episode: EpisodeEntity): ResumableSeries? {
        val series = seriesDao.byIds(listOf(episode.seriesId)).firstOrNull() ?: return null

        // Under the threshold: this episode, where they left it.
        if (!row.finished) {
            return ResumableSeries(
                series.asSeries(),
                episode.asEpisode(),
                row.positionMs,
                row.updatedAtMillis,
            )
        }

        // Past it: the next one, from the beginning. Null means the series is
        // finished and leaves the rail.
        val next = assemble(
            seasons = seriesDao.seasonsOf(episode.seriesId),
            episodes = seriesDao.episodesOf(episode.seriesId),
        ).episodeAfter(episode.id) ?: return null

        return ResumableSeries(
            series.asSeries(),
            next,
            positionMs = 0L,
            updatedAtMillis = row.updatedAtMillis,
        )
    }

    /**
     * How many series the source carries, or null when that is not known.
     *
     * The film counter's twin — see [VodRepository.filmCount] for why it is a
     * `size = 1` listing and why an unknown number stays null. The caller does not
     * ask at all for a playlist: an M3U source cannot carry series (`adr/0010`),
     * and "0 series" under one would present a property of the format as a
     * property of the subscription.
     */
    suspend fun seriesCount(sourceId: String): Int? = withContext(io) {
        val listed = calls.call { api.listSeries(UUID.fromString(sourceId), page = 0, size = 1) }
        when (listed) {
            is LumoResult.Success -> listed.value.totalElements.toCountOrNull()
            is LumoResult.Failure -> seriesDao.countForSource(sourceId).takeIf { it > 0 }
        }
    }

    /** Drops one source's series, for a source the user just deleted. */
    suspend fun forget(sourceId: String) = withContext(io) {
        // Seasons and episodes cascade with their series.
        seriesDao.deleteBySource(sourceId)
        staleness.update { it - sourceId }
    }

    private fun markStale(sourceId: String, failure: LumoResult.Failure): LumoResult.Failure {
        staleness.update { it + (sourceId to failure.error) }
        return failure
    }

    private fun <T> cached(
        sourceId: String,
        value: T,
        stale: Map<String, LumoError?>,
    ): Cached<T> {
        val refreshed = stale.containsKey(sourceId) && stale[sourceId] == null
        return Cached(
            value = value,
            origin = if (refreshed) DataOrigin.Network else DataOrigin.Cache,
            staleReason = stale[sourceId],
        )
    }

    /**
     * Puts the episodes back under their seasons.
     *
     * Two flat queries and one grouping rather than one query per season: a tree is
     * tens of rows, and a screen that read each season separately would issue a
     * query per tab somebody touches.
     *
     * **A season with no episodes survives this**, which is the point of grouping
     * from the season list rather than from the episodes.
     */
    private fun assemble(seasons: List<SeasonEntity>, episodes: List<EpisodeEntity>): List<Season> {
        val byNumber = episodes.groupBy { it.seasonNumber }
        return seasons.map { season ->
            Season(
                seasonNumber = season.seasonNumber,
                episodeCount = season.episodeCount,
                posterUrl = season.posterUrl,
                episodes = byNumber[season.seasonNumber].orEmpty().map { it.asEpisode() },
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 1_000
    }
}

// ---- mapping ---------------------------------------------------------------

private fun ApiSeries.asEntity() = SeriesEntity(
    id = id.toString(),
    sourceId = sourceId.toString(),
    categoryId = categoryId?.toString(),
    externalId = externalId,
    name = name,
    posterUrl = posterUrl,
    year = year,
    episodeRunTime = episodeRunTime,
    rating = rating,
    // Arrives with the tree, never with the listing.
    plot = plot,
    // Never written from a listing: only `replaceTree` stamps it, and only after a
    // tree is actually stored. Writing it here would claim a tree this row has not
    // got, and the next open would show an empty series.
    treeFetchedAt = null,
    position = position,
    isAdult = isAdult,
)

private fun ApiCategory.asEntity() = CategoryEntity(
    id = id.toString(),
    sourceId = sourceId.toString(),
    externalId = externalId,
    name = name,
    contentType = contentType.value,
    position = position,
    channelCount = channelCount,
)

private fun SeriesEntity.asSeries() = Series(
    id = id,
    sourceId = sourceId,
    categoryId = categoryId,
    name = name,
    posterUrl = posterUrl,
    year = year,
    episodeRunTime = episodeRunTime,
    rating = rating,
    plot = plot,
    isAdult = isAdult,
)

private fun EpisodeEntity.asEpisode() = Episode(
    id = id,
    seriesId = seriesId,
    sourceId = sourceId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    name = name,
    durationSeconds = durationSeconds,
    plot = plot,
)

private fun CategoryEntity.asCategory() = Category(
    id = id,
    name = name,
    channelCount = channelCount,
)
