package tv.lumo.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.model.RecentChannelEntity
import tv.lumo.android.core.database.model.UnresolvedFavorite

@Dao
interface RecentChannelDao {

    /**
     * The recently watched channels, joined to what they point at.
     *
     * An inner join, so an entry whose channel is not cached produces no row —
     * exactly as a favourite does. A channel dropped by the last synchronisation
     * leaves the list silently, which is the right behaviour: it left the
     * subscription, the application did not break.
     */
    @Query(
        """
        SELECT c.* FROM recent_channel r
        JOIN channel c ON c.id = r.channel_id
        ORDER BY r.position
        """,
    )
    fun observe(): Flow<List<ChannelEntity>>

    /** Entries whose channel this device has never cached. */
    @Query(
        """
        SELECT DISTINCT r.source_id AS source_id, r.channel_id AS channel_id
        FROM recent_channel r
        WHERE NOT EXISTS (SELECT 1 FROM channel c WHERE c.id = r.channel_id)
        """,
    )
    suspend fun unresolved(): List<UnresolvedFavorite>

    @Upsert
    suspend fun upsert(entries: List<RecentChannelEntity>)

    @Query("DELETE FROM recent_channel")
    suspend fun deleteAll()

    /**
     * Replaces the window whole.
     *
     * A merge would keep entries the server's rolling window has already dropped,
     * and the list would grow past the size that makes it useful.
     */
    @Transaction
    suspend fun replace(entries: List<RecentChannelEntity>) {
        deleteAll()
        upsert(entries)
    }
}
