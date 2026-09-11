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
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.paging.CataloguePager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.model.ContentType
import tv.lumo.android.network.generated.model.Category as ApiCategory
import tv.lumo.android.network.generated.model.Channel as ApiChannel

/**
 * The catalogue, offline first.
 *
 * <h2>The cache is the source of truth for reads, and the network fills it</h2>
 *
 * Every read below comes out of Room. Nothing on a screen ever waits on an HTTP
 * call to render a channel list, and closing the application on a train does not
 * empty it. [refresh] is the only thing that talks to the server, and its job is
 * to write the cache — not to answer a screen.
 *
 * That split is what US-08 asks for, and it is also what makes the offline case
 * ordinary rather than exceptional: the same code path renders the same list, and
 * the only difference is what [Cached.origin] says about it.
 *
 * <h2>Where the data came from is a value, not a guess</h2>
 *
 * A screen must be able to say "this is what we last synchronised" instead of
 * quietly showing week-old channels. It cannot work that out on its own — Room
 * has rows either way — so the repository, which is the only thing that knows
 * whether the last refresh succeeded, says it: every read is wrapped in a
 * [Cached] carrying a [DataOrigin] and, when the cache is being served after a
 * failure, the [LumoError] that caused it.
 *
 * Before any refresh has been attempted in this process, the rows in Room are by
 * definition from an earlier run, so the origin is [DataOrigin.Cache] with no
 * reason attached. That is not a placeholder: it is exactly what is true.
 */
@Singleton
class CatalogueRepository @Inject internal constructor(
    private val api: CatalogApi,
    private val calls: ApiCaller,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val pager: CataloguePager,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) {

    /**
     * Why each source's cache is stale, keyed by source id.
     *
     * A map rather than one field: a phone with two sources refreshes them
     * independently, and one failing must not put a banner over the other.
     * `null` for a source means its last refresh succeeded.
     */
    private val staleness = MutableStateFlow<Map<String, LumoError?>>(emptyMap())

    /** The live categories of one source, and whether they are fresh. */
    fun categories(sourceId: String): Flow<Cached<List<Category>>> =
        combine(
            categoryDao.observeBySource(sourceId, ContentType.LIVE.value),
            staleness,
        ) { entities, stale ->
            cached(sourceId, entities.map(CategoryEntity::asCategory), stale)
        }

    /**
     * Channels, paginated out of SQLite.
     *
     * A [PagingData] rather than a list, and it is not an optimisation: fifteen
     * thousand channels is an ordinary source, and materialising that list
     * allocates on every emission and drops frames on a TV box. Paging reads
     * windows straight out of the table and invalidates itself when [refresh]
     * rewrites it.
     *
     * @param categoryId null for every channel of the source.
     */
    fun channels(sourceId: String, categoryId: String? = null): Flow<PagingData<Channel>> {
        val entities = if (categoryId == null) {
            pager.channelsInSource(sourceId)
        } else {
            pager.channelsInCategory(sourceId, categoryId)
        }
        return entities.map { page -> page.map(ChannelEntity::asChannel) }
    }

    /**
     * Local search over what has already been synchronised.
     *
     * Deliberately the cache and not `GET /sources/{id}/channels?q=`: the server
     * owns real search, with a trigram index and tolerance for a typo, and this
     * one exists so that searching still answers on a train. A screen that wants
     * the good search calls the server itself; this is the floor, not the
     * ceiling.
     */
    fun search(sourceId: String, query: String): Flow<PagingData<Channel>> =
        pager.search(sourceId, query).map { page -> page.map(ChannelEntity::asChannel) }

    /**
     * Pulls the whole catalogue of one source into the cache.
     *
     * <h3>Yes, the whole thing, and yes, that is many requests</h3>
     *
     * A cache that holds the first page only is not a cache — it is a screen's
     * scroll position, and the second flick on a train hits an empty list. So
     * this walks the pagination to the end, at the largest page the contract
     * allows, and writes once at the end.
     *
     * It is the expensive call in this module and it belongs to a moment the user
     * chose: registering a source, or asking for a re-synchronisation. Nothing
     * calls it on a screen opening.
     *
     * <h3>Written whole, or not at all</h3>
     *
     * The pages are accumulated and handed to Room in one replacement per table.
     * A re-synchronisation can renumber or drop half a catalogue, and a partially
     * applied one leaves a list showing channels the user's subscription no
     * longer carries — worse than a stale list, because it looks current.
     *
     * The two tables are replaced in two transactions rather than one. A crash
     * between them leaves categories without their channels, which the next
     * refresh fixes; sharing one transaction would mean a DAO that knows about
     * both, which is a bigger commitment than the failure is worth.
     *
     * <h3>The categories replaced are the LIVE ones, and only those</h3>
     *
     * Channels and films share the `category` table, keyed by `content_type`, and
     * [VodRepository] refreshes its half at its own moment. A replacement scoped
     * to the source rather than to the type would have each refresh silently
     * empty the other one's category strip — visible only on a source carrying
     * both, which is most of them.
     */
    suspend fun refresh(sourceId: String): LumoResult<Unit> = withContext(io) {
        val id = UUID.fromString(sourceId)

        val categories = calls.call { api.listCategories(id, ContentType.LIVE) }
        if (categories is LumoResult.Failure) return@withContext markStale(sourceId, categories)

        val channels = mutableListOf<ChannelEntity>()
        var page = 0
        while (true) {
            val result = calls.call { api.listChannels(id, page = page, size = PAGE_SIZE) }
            if (result is LumoResult.Failure) return@withContext markStale(sourceId, result)

            val body = (result as LumoResult.Success).value
            channels += body.items.map { it.asEntity() }

            // `page + 1 >= totalPages` ends it normally. The second condition is
            // a stop against a server that reports a total it does not have: an
            // unbounded loop here would hammer the API and never finish.
            if (page + 1 >= body.totalPages || page + 1 >= MAX_PAGES) break
            page++
        }

        categoryDao.replaceForSourceAndType(
            sourceId,
            ContentType.LIVE.value,
            (categories as LumoResult.Success).value.items.map { it.asEntity() },
        )
        channelDao.replaceForSource(sourceId, channels)

        staleness.update { it + (sourceId to null) }
        LumoResult.Success(Unit)
    }

    /** How many channels the cache holds for a source. Drives the empty state. */
    suspend fun cachedChannelCount(sourceId: String): Int =
        withContext(io) { channelDao.countForSource(sourceId) }

    /** One channel from the cache, or null for an id a re-synchronisation dropped. */
    suspend fun channel(id: String): Channel? =
        withContext(io) { channelDao.byId(id)?.asChannel() }

    /**
     * The channel after [id] in its source's order, or null at the end of the
     * list.
     *
     * The order is the source's, not the grid's current filter: a player does
     * not know which category the viewer came from, and the source order is the
     * one thing both the grid and the player agree on.
     */
    suspend fun nextChannel(id: String): Channel? = withContext(io) {
        val current = channelDao.byId(id) ?: return@withContext null
        channelDao.nextAfter(current.sourceId, current.position, current.name)?.asChannel()
    }

    /** Drops one source's cache, for a source the user just deleted. */
    suspend fun forget(sourceId: String) = withContext(io) {
        channelDao.deleteBySource(sourceId)
        categoryDao.deleteBySource(sourceId)
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
        // Absent from the map means no refresh has been attempted this process,
        // so whatever Room holds came from an earlier run.
        val refreshed = stale.containsKey(sourceId) && stale[sourceId] == null
        return Cached(
            value = value,
            origin = if (refreshed) DataOrigin.Network else DataOrigin.Cache,
            staleReason = stale[sourceId],
        )
    }

    private companion object {
        /** The contract's cap. Fewer round trips for the same fifteen thousand rows. */
        const val PAGE_SIZE = 200

        /** 200 000 channels. Past that, the server is not describing a playlist. */
        const val MAX_PAGES = 1_000
    }
}

// ---- mapping ---------------------------------------------------------------
//
// The two directions live next to each other on purpose: a field added to the
// contract and forgotten in the cache is visible here, in one screenful, instead
// of being discovered when an offline screen renders a blank badge.

private fun ApiChannel.asEntity() = ChannelEntity(
    id = id.toString(),
    sourceId = sourceId.toString(),
    categoryId = categoryId?.toString(),
    externalId = externalId,
    name = name,
    logoUrl = logoUrl,
    tvgId = tvgId,
    number = number,
    quality = quality,
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

private fun ChannelEntity.asChannel() = Channel(
    id = id,
    sourceId = sourceId,
    categoryId = categoryId,
    name = name,
    logoUrl = logoUrl,
    number = number,
    quality = quality,
    isAdult = isAdult,
)

private fun CategoryEntity.asCategory() = Category(
    id = id,
    name = name,
    channelCount = channelCount,
)
