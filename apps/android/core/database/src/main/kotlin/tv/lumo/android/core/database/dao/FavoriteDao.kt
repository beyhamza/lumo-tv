package tv.lumo.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.model.FavoriteChannelRow
import tv.lumo.android.core.database.model.FavoriteEntity
import tv.lumo.android.core.database.model.FavoriteGroupEntity
import tv.lumo.android.core.database.model.UnresolvedFavorite

/**
 * Favourites and their groups, out of the cache.
 *
 * Unlike [ChannelDao] these return whole lists rather than a [androidx.paging.PagingSource].
 * The shapes are genuinely different: a source carries fifteen thousand channels,
 * while a group of favourites is a list somebody curated by hand and is counted
 * in tens. Paging a list of forty rows costs more than it saves.
 */
@Dao
interface FavoriteDao {

    // ---- groups -------------------------------------------------------------

    @Query("SELECT * FROM favorite_group ORDER BY position, name")
    fun observeGroups(): Flow<List<FavoriteGroupEntity>>

    @Query("SELECT count(*) FROM favorite_group WHERE id = :groupId")
    suspend fun countGroup(groupId: String): Int

    @Upsert
    suspend fun upsertGroups(groups: List<FavoriteGroupEntity>)

    @Query("DELETE FROM favorite_group")
    suspend fun deleteAllGroups()

    @Transaction
    suspend fun replaceGroups(groups: List<FavoriteGroupEntity>) {
        deleteAllGroups()
        upsertGroups(groups)
    }

    // ---- favourites ---------------------------------------------------------

    /**
     * The favourites of one group, or of every group, joined to their channels.
     *
     * An inner join, and that is the whole behaviour of an orphan: a favourite
     * whose channel is not in the cache produces no row. No error, no placeholder
     * — the channel either left the subscription or has not been synchronised on
     * this device yet, and neither is something to show a person.
     *
     * @param groupId null for every group.
     */
    @Query(
        """
        SELECT f.id AS favorite_id,
               f.group_id AS group_id,
               f.position AS favorite_position,
               c.*
        FROM favorite f
        JOIN favorite_group g ON g.id = f.group_id
        JOIN channel c ON c.id = f.channel_id
        WHERE :groupId IS NULL OR f.group_id = :groupId
        ORDER BY g.position, f.position
        """,
    )
    fun observeFavorites(groupId: String?): Flow<List<FavoriteChannelRow>>

    /** The starred channel ids, for a grid drawing its hearts. */
    @Query("SELECT DISTINCT channel_id FROM favorite")
    fun observeFavoritedChannelIds(): Flow<List<String>>

    /**
     * Favourites whose channel this device has never cached.
     *
     * What `GET /sources/{id}/channels?ids=` exists to answer. Grouped by source
     * because that parameter lives on a source's listing, so one call per source
     * is the floor.
     */
    @Query(
        """
        SELECT DISTINCT f.source_id AS source_id, f.channel_id AS channel_id
        FROM favorite f
        WHERE NOT EXISTS (SELECT 1 FROM channel c WHERE c.id = f.channel_id)
        """,
    )
    suspend fun unresolvedFavorites(): List<UnresolvedFavorite>

    @Upsert
    suspend fun upsertFavorites(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorite WHERE id = :favoriteId")
    suspend fun deleteFavorite(favoriteId: String)

    @Query("DELETE FROM favorite")
    suspend fun deleteAllFavorites()

    @Transaction
    suspend fun replaceFavorites(favorites: List<FavoriteEntity>) {
        deleteAllFavorites()
        upsertFavorites(favorites)
    }
}
