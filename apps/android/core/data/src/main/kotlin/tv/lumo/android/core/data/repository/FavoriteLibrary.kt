package tv.lumo.android.core.data.repository

import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.model.FavoriteRemovalOutcome
import tv.lumo.android.core.data.model.FavoriteReorderOutcome

/**
 * What the library's favourites screen needs from [FavoriteRepository] (US-022,
 * S11-02/S11-03).
 *
 * An interface for the screen's sake, as [ActiveSourceRepository] and
 * [WatchlistRepository] are: the view model's transitions — a confirmation, a
 * partial removal and its retry, a reorder that stops halfway — are tested over a
 * fake, without Room or a server.
 */
interface FavoriteLibrary {

    fun groups(): Flow<List<FavoriteGroup>>

    fun favorites(groupId: String? = null): Flow<List<FavoriteChannel>>

    suspend fun refresh(): LumoResult<Unit>

    /** Files a channel in a group; `groupId` null is the default group. */
    suspend fun add(channelId: String, groupId: String? = null): LumoResult<Unit>

    /** Removes one membership. Already gone (`404`) counts as removed. */
    suspend fun remove(favoriteId: String): LumoResult<Unit>

    suspend fun createGroup(name: String): LumoResult<FavoriteGroup>

    suspend fun renameGroup(groupId: String, name: String): LumoResult<Unit>

    suspend fun moveGroup(groupId: String, position: Int): LumoResult<Unit>

    suspend fun deleteGroup(groupId: String): LumoResult<Unit>

    /**
     * Gives the favourites of [visibleOrder] that order within [groupId], every
     * other member of the group keeping its place (S11-00, G1). Reads the group
     * first, sends the single moves needed, re-reads after — success or not.
     */
    suspend fun reorderVisible(groupId: String, visibleOrder: List<String>): FavoriteReorderOutcome

    /**
     * Removes [channelId] from every group (S11-00, G2).
     *
     * @param restrictTo on a retry, the favourite ids still left of the first
     * attempt: only those are targeted, never a membership added since.
     */
    suspend fun removeEverywhere(channelId: String, restrictTo: Set<String>? = null): FavoriteRemovalOutcome
}
