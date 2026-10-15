package tv.lumo.android.core.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.GlobalFavoriteRemoval
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.VisibleFavoriteOrder
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ChannelResolver
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.model.FavoriteMembership
import tv.lumo.android.core.data.model.FavoriteRemovalOutcome
import tv.lumo.android.core.data.model.FavoriteReorderOutcome
import tv.lumo.android.core.database.dao.FavoriteDao
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.model.FavoriteChannelRow
import tv.lumo.android.core.database.model.FavoriteEntity
import tv.lumo.android.core.database.model.FavoriteGroupEntity
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.model.AddFavoriteRequest
import tv.lumo.android.network.generated.model.CreateFavoriteGroupRequest
import tv.lumo.android.network.generated.model.UpdateFavoriteGroupRequest
import tv.lumo.android.network.generated.model.UpdateFavoriteRequest
import tv.lumo.android.network.generated.model.Channel as ApiChannel
import tv.lumo.android.network.generated.model.Favorite as ApiFavorite
import tv.lumo.android.network.generated.model.FavoriteGroup as ApiFavoriteGroup

/**
 * Favourites and the groups the user files them in (US-12), offline first.
 *
 * <h2>Reads come out of Room, exactly as the catalogue does</h2>
 *
 * A screen never waits on HTTP to render a group. This matters more here than it
 * does for the catalogue: favourites are the list somebody curated by hand, and
 * it is the one they open on a train.
 *
 * <h2>A favourite is two identifiers, and the join is what makes it a row</h2>
 *
 * `Favorite` carries `channel_id` and `source_id` and deliberately no name — a
 * name copied onto it would be a name the next ingestion has already changed.
 * Turning that into something a person can read is a join against the cached
 * catalogue, which this device usually already has.
 *
 * When it does not — a source starred on the phone and opened on a television
 * that never synchronised it — [refresh] resolves the missing identifiers through
 * `GET /sources/{id}/channels?ids=`. That parameter exists for this and nothing
 * else, and it is **capped at 100**: a group of three hundred favourites is three
 * calls, and a caller that does not chunk gets a hundred rows back with no error
 * and no indication that the rest was dropped. The chunking lives here, once.
 *
 * <h2>Writes need the network, and say so</h2>
 *
 * Nothing is queued. A modification made offline is refused with the error that
 * caused it, because a queue needs conflict resolution — two devices renaming the
 * same group — and that is a bigger commitment than this sprint makes. US-12
 * states it as an acceptance criterion rather than leaving it to be discovered.
 *
 * After a write the cache is brought back in line, and by one of two routes. Adds,
 * removals, creations and renames touch one row and are applied locally. Moves and
 * group deletions make the **server** renumber rows this device holds, so those
 * re-read the two lists: guessing what the renumbering did is how a local order
 * quietly stops matching the account's.
 */
@Singleton
class FavoriteRepository @Inject internal constructor(
    private val api: UserdataApi,
    private val calls: ApiCaller,
    private val favoriteDao: FavoriteDao,
    private val resolver: ChannelResolver,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) : FavoriteLibrary {

    /** Every group of the account, in the user's order. */
    override fun groups(): Flow<List<FavoriteGroup>> =
        favoriteDao.observeGroups().map { rows -> rows.map(FavoriteGroupEntity::asGroup) }

    /**
     * The favourites of one group, or of every group, joined to their channels.
     *
     * A favourite whose channel is not in the cache yields no row — no error, no
     * placeholder. Either the channel left the subscription at the last
     * re-synchronisation, or it has not been synchronised on this device yet, and
     * neither is something to put in front of a person.
     *
     * @param groupId null for every group.
     */
    override fun favorites(groupId: String?): Flow<List<FavoriteChannel>> =
        favoriteDao.observeFavorites(groupId).map { rows -> rows.map(FavoriteChannelRow::asFavorite) }

    /** The starred channel ids, for a grid drawing its hearts as it scrolls. */
    fun favoritedChannelIds(): Flow<Set<String>> =
        favoriteDao.observeFavoritedChannelIds().map { it.toSet() }

    /**
     * Pulls the groups and the favourites into the cache, and resolves the
     * channels this device does not have.
     *
     * Both lists are replaced whole rather than merged: a favourite removed from
     * another device has to disappear here, and a merge would keep it forever.
     */
    override suspend fun refresh(): LumoResult<Unit> = withContext(io) {
        val groups = calls.call { api.listFavoriteGroups() }
        if (groups is LumoResult.Failure) return@withContext groups

        val favorites = calls.call { api.listFavorites(null) }
        if (favorites is LumoResult.Failure) return@withContext favorites

        favoriteDao.replaceGroups(
            (groups as LumoResult.Success).value.items.map(ApiFavoriteGroup::asEntity),
        )
        favoriteDao.replaceFavorites(
            (favorites as LumoResult.Success).value.items.map(ApiFavorite::asEntity),
        )

        resolveMissingChannels()
    }

    // ---- writes -------------------------------------------------------------

    /** Stars a channel. `groupId` null lands it in the account's default group. */
    override suspend fun add(channelId: String, groupId: String?): LumoResult<Unit> =
        withContext(io) {
            val request = AddFavoriteRequest(UUID.fromString(channelId))
                .let { if (groupId == null) it else it.copy(groupId = UUID.fromString(groupId)) }

            when (val result = calls.call { api.addFavorite(request) }) {
                is LumoResult.Failure -> result
                is LumoResult.Success -> {
                    favoriteDao.upsertFavorites(listOf(result.value.asEntity()))
                    // The group may have just been created server-side by this very
                    // call — that is what "created on the first add" means — and a
                    // favourite in a group this device has never heard of renders
                    // nowhere.
                    if (favoriteDao.countGroup(result.value.groupId.toString()) == 0) {
                        pullGroups()
                    } else {
                        LumoResult.Success(Unit)
                    }
                }
            }
        }

    /**
     * Removes one membership.
     *
     * `404 FAVORITE_NOT_FOUND` is a success: the favourite is gone, which is what
     * was asked — removed from another device, or by an earlier attempt whose
     * answer was lost. Either way the list is re-read afterwards: the server
     * renumbers the group, and the cache follows the server rather than a guess.
     */
    override suspend fun remove(favoriteId: String): LumoResult<Unit> = withContext(io) {
        when (val result = calls.empty { api.removeFavorite(UUID.fromString(favoriteId)) }) {
            is LumoResult.Failure -> if (GlobalFavoriteRemoval.isAlreadyGone(result.error)) {
                removedLocally(favoriteId)
            } else {
                result
            }
            is LumoResult.Success -> removedLocally(favoriteId)
        }
    }

    /**
     * The removal was accepted: the row goes, and the list is re-read for the
     * renumbering. A re-read that fails does not turn the accepted removal into a
     * failure — the next refresh brings the positions back in line.
     */
    private suspend fun removedLocally(favoriteId: String): LumoResult<Unit> {
        favoriteDao.deleteFavorite(favoriteId)
        pullFavorites()
        return LumoResult.Success(Unit)
    }

    /**
     * Moves a favourite into another group, reorders it within its own, or both.
     *
     * One call rather than a removal followed by an add: that pair loses the
     * position, and a connection dropped between the two loses the favourite.
     *
     * The server renumbers both groups afterwards, so the lists are re-read rather
     * than patched — see the class note.
     */
    suspend fun move(
        favoriteId: String,
        groupId: String? = null,
        position: Int? = null,
    ): LumoResult<Unit> = withContext(io) {
        val request = UpdateFavoriteRequest(
            groupId = groupId?.let(UUID::fromString),
            position = position,
        )
        when (val result = calls.call { api.updateFavorite(UUID.fromString(favoriteId), request) }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> pullFavorites()
        }
    }

    override suspend fun createGroup(name: String): LumoResult<FavoriteGroup> = withContext(io) {
        when (val result = calls.call { api.createFavoriteGroup(CreateFavoriteGroupRequest(name)) }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> {
                favoriteDao.upsertGroups(listOf(result.value.asEntity()))
                LumoResult.Success(result.value.asGroup())
            }
        }
    }

    override suspend fun renameGroup(groupId: String, name: String): LumoResult<Unit> = withContext(io) {
        val request = UpdateFavoriteGroupRequest(name = name)
        when (val result = calls.call { api.updateFavoriteGroup(UUID.fromString(groupId), request) }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> {
                favoriteDao.upsertGroups(listOf(result.value.asEntity()))
                LumoResult.Success(Unit)
            }
        }
    }

    /** Moves a group in the list. The server renumbers the others, so groups are re-read. */
    override suspend fun moveGroup(groupId: String, position: Int): LumoResult<Unit> = withContext(io) {
        val request = UpdateFavoriteGroupRequest(position = position)
        when (val result = calls.call { api.updateFavoriteGroup(UUID.fromString(groupId), request) }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> pullGroups()
        }
    }

    /**
     * Deletes a group. Its favourites are **not** deleted: the server moves them
     * to the default group, which is why both lists are re-read afterwards.
     */
    override suspend fun deleteGroup(groupId: String): LumoResult<Unit> = withContext(io) {
        when (val result = calls.call { api.deleteFavoriteGroup(UUID.fromString(groupId)) }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> when (val groups = pullGroups()) {
                is LumoResult.Failure -> groups
                is LumoResult.Success -> pullFavorites()
            }
        }
    }

    /**
     * The visible reorder of a group, as single moves (S11-00, G1 and G2).
     *
     * 1. The group is **read from the server**, not from the cache: the plan is
     *    made against the order the moves will apply to (G3).
     * 2. [VisibleFavoriteOrder.target] places [visibleOrder] in the slots those
     *    favourites occupy, everything else where it is;
     *    [VisibleFavoriteOrder.movesToReach] turns that into `PATCH`es.
     * 3. The moves go one at a time. The first failure stops the sequence; what
     *    was accepted is kept — there is no rollback and no batch.
     * 4. The whole list is re-read, whatever happened, so the cache shows the
     *    server's order and never a guess that outlived a failure.
     */
    override suspend fun reorderVisible(
        groupId: String,
        visibleOrder: List<String>,
    ): FavoriteReorderOutcome = withContext(io) {
        val read = calls.call { api.listFavorites(UUID.fromString(groupId)) }
        if (read is LumoResult.Failure) return@withContext FavoriteReorderOutcome.NotApplied(read.error)

        val current = (read as LumoResult.Success).value.items
            .filter { it.groupId.toString() == groupId }
            .sortedBy { it.position }
            .map { it.id.toString() }
        val moves = VisibleFavoriteOrder.movesToReach(current, VisibleFavoriteOrder.target(current, visibleOrder))

        var applied = 0
        var failure: LumoError? = null
        for (move in moves) {
            val request = UpdateFavoriteRequest(position = move.position)
            when (val result = calls.call { api.updateFavorite(UUID.fromString(move.member), request) }) {
                is LumoResult.Failure -> {
                    failure = result.error
                    break
                }
                is LumoResult.Success -> applied++
            }
        }

        if (applied > 0 || failure != null) pullFavorites()

        when (val error = failure) {
            null -> FavoriteReorderOutcome.Applied
            else -> FavoriteReorderOutcome.NotFullyApplied(applied = applied, planned = moves.size, error = error)
        }
    }

    /**
     * "Remove from all my favourites", one `DELETE` per membership, in sequence.
     *
     * The memberships are read from the server first (G3); on a retry, only the
     * ones left of the first attempt are targeted ([GlobalFavoriteRemoval]). Every
     * removal is attempted even after one fails — each is independent, and the
     * user asked for all of them. The list is then re-read, and the verdict comes
     * from that re-read when there is one (FO-05 → FO-08).
     */
    override suspend fun removeEverywhere(
        channelId: String,
        restrictTo: Set<String>?,
    ): FavoriteRemovalOutcome = withContext(io) {
        val read = calls.call { api.listFavorites(null) }
        if (read is LumoResult.Failure) return@withContext FavoriteRemovalOutcome.NotStarted(read.error)

        val onServer = (read as LumoResult.Success).value.items
            .filter { it.channelId.toString() == channelId }
            .map { FavoriteMembership(favoriteId = it.id.toString(), groupId = it.groupId.toString()) }
        val targets = GlobalFavoriteRemoval.targets(onServer, restrictTo)

        val failures = linkedMapOf<String, LumoError>()
        for (membership in targets) {
            val result = calls.empty { api.removeFavorite(UUID.fromString(membership.favoriteId)) }
            if (result is LumoResult.Failure && !GlobalFavoriteRemoval.isAlreadyGone(result.error)) {
                failures[membership.favoriteId] = result.error
            } else {
                favoriteDao.deleteFavorite(membership.favoriteId)
            }
        }

        val reread = calls.call { api.listFavorites(null) }
        val presentAfter = when (reread) {
            is LumoResult.Failure -> null
            is LumoResult.Success -> {
                favoriteDao.replaceFavorites(reread.value.items.map(ApiFavorite::asEntity))
                resolveMissingChannels()
                reread.value.items.mapTo(HashSet()) { it.id.toString() }
            }
        }
        GlobalFavoriteRemoval.outcome(targets, failures, presentAfter)
    }

    // ---- internals ----------------------------------------------------------

    private suspend fun pullGroups(): LumoResult<Unit> =
        when (val result = calls.call { api.listFavoriteGroups() }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> {
                favoriteDao.replaceGroups(result.value.items.map(ApiFavoriteGroup::asEntity))
                LumoResult.Success(Unit)
            }
        }

    private suspend fun pullFavorites(): LumoResult<Unit> =
        when (val result = calls.call { api.listFavorites(null) }) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> {
                favoriteDao.replaceFavorites(result.value.items.map(ApiFavorite::asEntity))
                resolveMissingChannels()
            }
        }

    /**
     * Fetches the channels that favourites point at and this device does not hold.
     *
     * Delegated to [ChannelResolver] because the recently watched window needs the
     * same thing, and the chunking it does is a trap that would have been written
     * correctly once and wrongly the second time.
     *
     * Always a success: the groups and the favourites are already written by now,
     * and what is missing is only the readable name of a favourite whose source has
     * never been synchronised here.
     */
    private suspend fun resolveMissingChannels(): LumoResult<Unit> {
        resolver.resolve(favoriteDao.unresolvedFavorites().map { it.sourceId to it.channelId })
        return LumoResult.Success(Unit)
    }
}

// ---- mapping ---------------------------------------------------------------

private fun ApiFavoriteGroup.asEntity() = FavoriteGroupEntity(
    id = id.toString(),
    name = name,
    position = position,
    isDefault = isDefault,
)

private fun ApiFavorite.asEntity() = FavoriteEntity(
    id = id.toString(),
    groupId = groupId.toString(),
    sourceId = sourceId.toString(),
    channelId = channelId.toString(),
    position = position,
)

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

private fun ApiFavoriteGroup.asGroup() = FavoriteGroup(
    id = id.toString(),
    name = name,
    position = position,
    isDefault = isDefault,
)

private fun FavoriteGroupEntity.asGroup() = FavoriteGroup(
    id = id,
    name = name,
    position = position,
    isDefault = isDefault,
)

private fun FavoriteChannelRow.asFavorite() = FavoriteChannel(
    favoriteId = favoriteId,
    groupId = groupId,
    position = favoritePosition,
    channel = Channel(
        id = channel.id,
        sourceId = channel.sourceId,
        categoryId = channel.categoryId,
        name = channel.name,
        logoUrl = channel.logoUrl,
        number = channel.number,
        quality = channel.quality,
        isAdult = channel.isAdult,
    ),
)
