package tv.lumo.android.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.model.VodItemEntity

/**
 * Film reads and writes.
 *
 * The listing methods return a [PagingSource] for the reason [ChannelDao]'s do —
 * a catalogue of thirty thousand films is ordinary, and materialising that list
 * drops frames on a TV box. The shape is the channel one on purpose: a film grid
 * that paginated differently from a channel grid would be a second set of
 * behaviours to get right for no gain to anybody.
 */
@Dao
interface VodDao {

    @Query(
        """
        SELECT * FROM vod_item
        WHERE source_id = :sourceId AND category_id = :categoryId
        ORDER BY position, name
        """,
    )
    fun pagedByCategory(sourceId: String, categoryId: String): PagingSource<Int, VodItemEntity>

    @Query(
        """
        SELECT * FROM vod_item
        WHERE source_id = :sourceId
        ORDER BY position, name
        """,
    )
    fun pagedBySource(sourceId: String): PagingSource<Int, VodItemEntity>

    /**
     * Local search over the cache — a `LIKE`, like the channel one, and for the
     * same reason: the server owns real search (a trigram index and tolerance for
     * a typo), and this exists so searching still answers on a train.
     */
    @Query(
        """
        SELECT * FROM vod_item
        WHERE source_id = :sourceId AND name LIKE '%' || :query || '%'
        ORDER BY position, name
        """,
    )
    fun pagedBySearch(sourceId: String, query: String): PagingSource<Int, VodItemEntity>

    @Query("SELECT * FROM vod_item WHERE id = :id")
    fun observe(id: String): Flow<VodItemEntity?>

    /**
     * Several films by id, for a rail built from something that carries
     * identifiers only.
     *
     * **The order is not honoured, and callers must not expect it to be.**
     * SQLite answers an `IN` in whatever order it likes; a "continue watching"
     * rail wants the server's order, which the caller holds. So the caller
     * re-orders, driven by the list it asked for.
     */
    @Query("SELECT * FROM vod_item WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<VodItemEntity>

    @Query("SELECT count(*) FROM vod_item WHERE source_id = :sourceId")
    suspend fun countForSource(sourceId: String): Int

    /**
     * Writes the synopsis of one film, and nothing else about it.
     *
     * Separate from [upsert] deliberately: the synopsis arrives from a different
     * operation, at a different moment, and an upsert of the whole row here would
     * need a row the caller does not have.
     */
    @Query("UPDATE vod_item SET plot = :plot WHERE id = :id")
    suspend fun updatePlot(id: String, plot: String?)

    @Upsert
    suspend fun upsert(items: List<VodItemEntity>)

    @Query("DELETE FROM vod_item WHERE source_id = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    /**
     * Replaces a source's films in one transaction.
     *
     * Delete-then-insert, exactly as [ChannelDao.replaceForSource] does, and the
     * reasoning transfers unchanged: a re-synchronisation can drop half a
     * catalogue, and a partially applied replacement shows films the user's
     * subscription no longer carries — worse than a stale list, because it looks
     * current.
     *
     * **It empties the cached synopses, and that is stated rather than worked
     * around.** Keeping them would mean either holding every plot of the source
     * in memory across the swap, or a delete-not-in over thirty thousand
     * identifiers — past SQLite's variable limit. What it costs is one HTTP call
     * the next time somebody opens a film they had opened before, which is the
     * same trade the server already makes: fetch a synopsis for the film being
     * looked at, never for the thousand being scrolled past.
     */
    @Transaction
    suspend fun replaceForSource(sourceId: String, items: List<VodItemEntity>) {
        deleteBySource(sourceId)
        upsert(items)
    }
}
