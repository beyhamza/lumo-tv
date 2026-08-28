package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ChannelResolver
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.database.dao.RecentChannelDao
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.model.RecentChannelEntity
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.model.RecentChannel as ApiRecentChannel

/**
 * The channels this account watched recently (M5, `SRV-06`), offline first.
 *
 * <h2>Reading it is what was missing</h2>
 *
 * The table, the rolling window and both endpoints were delivered a sprint ago,
 * and `PlaybackRepository.recordWatched` has been writing to them ever since. What
 * nobody did was **read** them: the list existed on the server and appeared on no
 * screen. This is that half.
 *
 * <h2>Its whole value is that it is not local</h2>
 *
 * A device-local history would need no endpoint at all. What makes this worth
 * having is that the television knows what was watched on the phone — which is
 * exactly the argument `docs/design/api-gaps.md` M5 makes for putting it on the
 * server, and the reason the order comes from there rather than from a sort here.
 *
 * <h2>Written on playback, never on focus</h2>
 *
 * That rule lives in [PlaybackRepository] and is worth repeating at the reading
 * end: on a television the D-pad crosses twenty channels on its way to one, and a
 * list built from what the remote passed over is the user's own history made
 * worse.
 */
@Singleton
class RecentChannelRepository @Inject internal constructor(
    private val api: UserdataApi,
    private val calls: ApiCaller,
    private val recentDao: RecentChannelDao,
    private val resolver: ChannelResolver,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) {

    /**
     * The window, most recent first, joined to the cached catalogue.
     *
     * An entry whose channel is not cached yields no row — the same rule as a
     * favourite, for the same two reasons: it may have left the subscription at
     * the last synchronisation, or it may belong to a source this device has never
     * pulled.
     */
    fun recent(): Flow<List<Channel>> =
        recentDao.observe().map { rows -> rows.map(ChannelEntity::asChannel) }

    /**
     * Pulls the window and resolves what it points at.
     *
     * Replaced whole, never merged: the server's window rolls, and a merge would
     * keep entries it has already dropped — the list would grow past the size that
     * makes it useful in the first place.
     */
    suspend fun refresh(): LumoResult<Unit> = withContext(io) {
        when (val result = calls.call { api.listRecentChannels(WINDOW) }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> {
                recentDao.replace(result.value.items.mapIndexed(::asEntity))
                resolver.resolve(recentDao.unresolved().map { it.sourceId to it.channelId })
                LumoResult.Success(Unit)
            }
        }
    }

    private companion object {
        /**
         * How many entries to ask for.
         *
         * A chip's worth, not a history: the contract's own window is fifty, and
         * anything past what fits on one screen of a strip is stored for a feature
         * that does not exist.
         */
        const val WINDOW = 20
    }
}

// ---- mapping ---------------------------------------------------------------

/**
 * The server's order is the index, and it is not stored on the response.
 *
 * `RecentChannel` carries identifiers and a timestamp; the contract states that
 * the list arrives most recent first. Keeping the index rather than the timestamp
 * means the cache sorts on the same thing the server ordered by, instead of on a
 * clock two devices may disagree about.
 */
private fun asEntity(index: Int, recent: ApiRecentChannel) = RecentChannelEntity(
    channelId = recent.channelId.toString(),
    sourceId = recent.sourceId.toString(),
    position = index,
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
