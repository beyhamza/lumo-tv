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
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.VodDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.VodItemEntity
import tv.lumo.android.core.database.paging.VodPager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.model.ContentType
import tv.lumo.android.network.generated.model.Category as ApiCategory
import tv.lumo.android.network.generated.model.VodItem as ApiVodItem

/**
 * The film catalogue, offline first.
 *
 * The shape is [CatalogueRepository]'s, deliberately and almost line for line:
 * every read comes out of Room, [refresh] is the only thing that talks to the
 * server, and where an answer came from is a value rather than a guess. US-13
 * asks for the offline behaviour US-08 asked for, and a film grid that behaved
 * differently from a channel grid would be a second set of rules to get right for
 * no gain to anybody. **The divergence would be the defect.**
 *
 * The two places it genuinely differs, both forced by what a film is:
 *
 * <h2>The synopsis is fetched, not synchronised</h2>
 *
 * A listing never carries one. On an Xtream panel a synopsis costs one HTTP call
 * *per film*, so loading it for a catalogue of thirty thousand at every refresh is
 * not slow — it is the kind of thing that gets our address banned by somebody's
 * provider. [film] fetches one, for the film somebody actually opened, and the
 * server remembers it so the second open costs nothing.
 *
 * <h2>Categories are replaced per content type</h2>
 *
 * Films and channels share the `category` table, keyed by `content_type`. This
 * refresh and [CatalogueRepository]'s run at different moments, and a replacement
 * that wiped every category of the source would have each one silently empty the
 * other's strip. Both go through `replaceForSourceAndType`.
 */
@Singleton
class VodRepository @Inject internal constructor(
    private val api: CatalogApi,
    private val calls: ApiCaller,
    private val categoryDao: CategoryDao,
    private val vodDao: VodDao,
    private val pager: VodPager,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) {

    /**
     * Why each source's film cache is stale, keyed by source id.
     *
     * Its own map, not [CatalogueRepository]'s: a source whose channels
     * synchronised and whose films did not is an ordinary outcome — the server
     * lets a film catalogue fail without failing the source (S5-03) — and one
     * banner over both lists would be wrong on one of them.
     */
    private val staleness = MutableStateFlow<Map<String, LumoError?>>(emptyMap())

    /** The film categories of one source, and whether they are fresh. */
    fun categories(sourceId: String): Flow<Cached<List<Category>>> =
        combine(
            categoryDao.observeBySource(sourceId, ContentType.VOD.value),
            staleness,
        ) { entities, stale ->
            cached(sourceId, entities.map(CategoryEntity::asCategory), stale)
        }

    /**
     * Films, paginated out of SQLite.
     *
     * @param categoryId null for every film of the source.
     */
    fun films(sourceId: String, categoryId: String? = null): Flow<PagingData<VodItem>> {
        val entities = if (categoryId == null) {
            pager.filmsInSource(sourceId)
        } else {
            pager.filmsInCategory(sourceId, categoryId)
        }
        return entities.map { page -> page.map(VodItemEntity::asVodItem) }
    }

    /** Local search over what has already been synchronised. See [CatalogueRepository.search]. */
    fun search(sourceId: String, query: String): Flow<PagingData<VodItem>> =
        pager.search(sourceId, query).map { page -> page.map(VodItemEntity::asVodItem) }

    /**
     * One film, from the cache, kept current.
     *
     * The Flow emits what Room holds — immediately, with whatever synopsis is
     * cached, which on a first open is none. [refreshFilm] is what fills it in,
     * and a detail screen calls both: it renders the poster and the title without
     * waiting for a network round trip, and the synopsis appears under them when
     * it arrives.
     *
     * That split is the same one the grid makes, and it is what makes the offline
     * case ordinary: on a train the Flow still emits, and the refresh fails
     * quietly next to a screen that already has something on it.
     */
    fun film(id: String): Flow<VodItem?> =
        vodDao.observe(id).map { it?.asVodItem() }

    /**
     * Fetches the synopsis of one film and writes it to the cache.
     *
     * <h3>Once per film, not once per opening</h3>
     *
     * The server already only calls the user's panel the first time — it stamps
     * `plot_fetched_at` and answers from its own row afterwards. This adds the
     * device's half: a film whose synopsis is already cached here is not asked
     * for again at all.
     *
     * The guard is on the cached row and not on a flag of ours, because the
     * question it answers is exactly "does this device have the synopsis". A film
     * the server genuinely has no synopsis for is re-asked on each opening, which
     * is one cheap request against the server's own row — deciding otherwise
     * would mean caching an absence, and an absence that expires.
     */
    suspend fun refreshFilm(id: String): LumoResult<Unit> = withContext(io) {
        if (vodDao.observe(id).first()?.plot != null) return@withContext LumoResult.Success(Unit)

        val result = calls.call { api.getVodItem(UUID.fromString(id)) }
        if (result is LumoResult.Failure) return@withContext result

        val item = (result as LumoResult.Success).value
        // Upsert rather than update: a film reached by a deep link or a resume
        // rail may not be in the cache at all, and a screen that showed nothing
        // because a refresh had not run yet would be the cache getting in the way.
        vodDao.upsert(listOf(item.asEntity()))
        LumoResult.Success(Unit)
    }

    /**
     * Pulls the whole film catalogue of one source into the cache.
     *
     * Walks the pagination to the end at the contract's largest page and writes
     * once, for the reasons written on [CatalogueRepository.refresh] — a cache
     * holding the first page only is a screen's scroll position, and a partially
     * applied replacement is a catalogue that is neither the old one nor the new
     * one.
     *
     * **A source with no films is a success, not a failure.** Most M3U playlists
     * carry none, and an empty page here means an empty grid and an empty
     * category strip — not a banner telling somebody their source is broken.
     */
    suspend fun refresh(sourceId: String): LumoResult<Unit> = withContext(io) {
        val id = UUID.fromString(sourceId)

        val categories = calls.call { api.listCategories(id, ContentType.VOD) }
        if (categories is LumoResult.Failure) return@withContext markStale(sourceId, categories)

        val films = mutableListOf<VodItemEntity>()
        var page = 0
        while (true) {
            val result = calls.call { api.listVod(id, page = page, size = PAGE_SIZE) }
            if (result is LumoResult.Failure) return@withContext markStale(sourceId, result)

            val body = (result as LumoResult.Success).value
            films += body.items.map { it.asEntity() }

            // The second condition stops a server that reports a total it does
            // not have. Without it this loop hammers the API and never ends.
            if (page + 1 >= body.totalPages || page + 1 >= MAX_PAGES) break
            page++
        }

        categoryDao.replaceForSourceAndType(
            sourceId,
            ContentType.VOD.value,
            (categories as LumoResult.Success).value.items.map { it.asEntity() },
        )
        vodDao.replaceForSource(sourceId, films)

        staleness.update { it + (sourceId to null) }
        LumoResult.Success(Unit)
    }

    /** How many films the cache holds for a source. Drives the empty state. */
    suspend fun cachedFilmCount(sourceId: String): Int =
        withContext(io) { vodDao.countForSource(sourceId) }

    /** Drops one source's films, for a source the user just deleted. */
    suspend fun forget(sourceId: String) = withContext(io) {
        vodDao.deleteBySource(sourceId)
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

    private companion object {
        /** The contract's cap. Fewer round trips for the same thirty thousand rows. */
        const val PAGE_SIZE = 200

        /** 200 000 films. Past that, the server is not describing a catalogue. */
        const val MAX_PAGES = 1_000
    }
}

// ---- mapping ---------------------------------------------------------------

private fun ApiVodItem.asEntity() = VodItemEntity(
    id = id.toString(),
    sourceId = sourceId.toString(),
    categoryId = categoryId?.toString(),
    externalId = externalId,
    name = name,
    posterUrl = posterUrl,
    year = year,
    durationSeconds = durationSeconds,
    rating = rating,
    // Null in every listing, by design. On the single-film read it is the point
    // of the call, and this same mapping is what writes it.
    plot = plot,
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

private fun VodItemEntity.asVodItem() = VodItem(
    id = id,
    sourceId = sourceId,
    categoryId = categoryId,
    name = name,
    posterUrl = posterUrl,
    year = year,
    durationSeconds = durationSeconds,
    rating = rating,
    plot = plot,
    isAdult = isAdult,
)

private fun CategoryEntity.asCategory() = Category(
    id = id,
    name = name,
    channelCount = channelCount,
)
