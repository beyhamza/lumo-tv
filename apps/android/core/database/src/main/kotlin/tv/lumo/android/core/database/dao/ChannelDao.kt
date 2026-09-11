package tv.lumo.android.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.model.ChannelEntity

/**
 * Channel reads and writes.
 *
 * The listing methods return a [PagingSource], not a `List` and not a `Flow` of
 * one: a source with fifteen thousand channels is normal in this product, and
 * materialising that list allocates on every emission and drops frames on a TV
 * box (US-08, "la liste se pagine de façon fluide au-delà de 500 chaînes").
 * Paging 3 reads windows straight out of SQLite and invalidates itself when the
 * table changes.
 */
@Dao
interface ChannelDao {

    @Query(
        """
        SELECT * FROM channel
        WHERE source_id = :sourceId AND category_id = :categoryId
        ORDER BY position, name
        """,
    )
    fun pagedByCategory(sourceId: String, categoryId: String): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT * FROM channel
        WHERE source_id = :sourceId
        ORDER BY position, name
        """,
    )
    fun pagedBySource(sourceId: String): PagingSource<Int, ChannelEntity>

    /**
     * Local search over the cache.
     *
     * Deliberately a `LIKE`, not FTS: the server owns real search (trigram index,
     * `GET /sources/{id}/channels?q=`). This one exists so search still returns
     * something on a train, over what has already been synchronised.
     */
    @Query(
        """
        SELECT * FROM channel
        WHERE source_id = :sourceId AND name LIKE '%' || :query || '%'
        ORDER BY position, name
        """,
    )
    fun pagedBySearch(sourceId: String, query: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channel WHERE id = :id")
    fun observe(id: String): Flow<ChannelEntity?>

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun byId(id: String): ChannelEntity?

    /**
     * The channel after this one in the source's own order — the order every
     * listing here uses, `position` then `name` — so that "next channel" on the
     * player lands where `DOWN` in the full grid would.
     */
    @Query(
        """
        SELECT * FROM channel
        WHERE source_id = :sourceId
          AND (position > :position OR (position = :position AND name > :name))
        ORDER BY position, name
        LIMIT 1
        """,
    )
    suspend fun nextAfter(sourceId: String, position: Int, name: String): ChannelEntity?

    @Query("SELECT count(*) FROM channel WHERE source_id = :sourceId")
    suspend fun countForSource(sourceId: String): Int

    @Upsert
    suspend fun upsert(channels: List<ChannelEntity>)

    @Query("DELETE FROM channel WHERE source_id = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    /**
     * Replaces a source's channels in one transaction.
     *
     * Delete-then-insert rather than a diff: a re-synchronisation can renumber
     * or drop half the catalogue, and a partially applied replacement is a
     * catalogue that shows channels the user's subscription no longer carries.
     */
    @Transaction
    suspend fun replaceForSource(sourceId: String, channels: List<ChannelEntity>) {
        deleteBySource(sourceId)
        upsert(channels)
    }
}
