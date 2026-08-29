package tv.lumo.android.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.model.EpisodeEntity
import tv.lumo.android.core.database.model.SeasonEntity
import tv.lumo.android.core.database.model.SeriesEntity

/**
 * Series reads and writes, and the tree underneath them.
 *
 * The listing half is [VodDao]'s, unchanged: a catalogue of a few thousand series
 * paginates out of SQLite exactly as thirty thousand films do, and a second set of
 * behaviours would be a second set to get right.
 *
 * The tree half is new, and it is where the care is. See [replaceTree].
 */
@Dao
interface SeriesDao {

    @Query(
        """
        SELECT * FROM series
        WHERE source_id = :sourceId AND category_id = :categoryId
        ORDER BY position, name
        """,
    )
    fun pagedByCategory(sourceId: String, categoryId: String): PagingSource<Int, SeriesEntity>

    @Query(
        """
        SELECT * FROM series
        WHERE source_id = :sourceId
        ORDER BY position, name
        """,
    )
    fun pagedBySource(sourceId: String): PagingSource<Int, SeriesEntity>

    /** Local search over the cache. A `LIKE`, like the other two catalogues. */
    @Query(
        """
        SELECT * FROM series
        WHERE source_id = :sourceId AND name LIKE '%' || :query || '%'
        ORDER BY position, name
        """,
    )
    fun pagedBySearch(sourceId: String, query: String): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE id = :id")
    fun observe(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<SeriesEntity>

    @Query("SELECT count(*) FROM series WHERE source_id = :sourceId")
    suspend fun countForSource(sourceId: String): Int

    /** The seasons of one series, in order. */
    @Query("SELECT * FROM season WHERE series_id = :seriesId ORDER BY season_number")
    fun observeSeasons(seriesId: String): Flow<List<SeasonEntity>>

    /**
     * Every episode of one series, in tree order.
     *
     * One query rather than one per season: a tree is tens of rows, and a screen
     * that read each season separately would issue a query per season tab.
     */
    @Query(
        """
        SELECT * FROM episode
        WHERE series_id = :seriesId
        ORDER BY season_number, episode_number
        """,
    )
    fun observeEpisodes(seriesId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episode WHERE id IN (:ids)")
    suspend fun episodesByIds(ids: List<String>): List<EpisodeEntity>

    @Upsert
    suspend fun upsert(series: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE source_id = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    /**
     * Replaces a source's series.
     *
     * Delete-then-insert, as the other two catalogues do — and here it takes the
     * trees with it through the foreign key. That is the right cost: a
     * re-synchronisation that renamed or dropped half a catalogue leaves trees
     * whose series no longer exist, and the next open refetches one for the series
     * somebody actually looks at.
     */
    @Transaction
    suspend fun replaceForSource(sourceId: String, series: List<SeriesEntity>) {
        deleteBySource(sourceId)
        upsert(series)
    }

    /**
     * Replaces the tree of one series, and stamps it.
     *
     * <h2>Delete-then-insert here, unlike on the server, and the difference is
     * deliberate</h2>
     *
     * The server upserts episodes so their identifiers survive — a saved position
     * points at one. **This cache does not need to preserve anything**: the
     * identifiers it stores are the server's, and they arrive with every response.
     * Rewriting them changes nothing, while a diff would be code to get right for
     * no property anybody depends on.
     *
     * The stamp is written in the same transaction as the tree. A tree without its
     * stamp is refetched on every open; a stamp without its tree claims freshness
     * over nothing.
     *
     * **An empty tree is a legitimate answer** and empties the table. This is only
     * ever called with what the server returned for one series, so "no seasons"
     * came from a panel rather than from a read that failed.
     */
    @Transaction
    suspend fun replaceTree(
        seriesId: String,
        seasons: List<SeasonEntity>,
        episodes: List<EpisodeEntity>,
        fetchedAt: Long,
    ) {
        // The seasons cascade to their episodes, so one delete clears both.
        deleteSeasons(seriesId)
        upsertSeasons(seasons)
        upsertEpisodes(episodes)
        stampTree(seriesId, fetchedAt)
    }

    @Query("DELETE FROM season WHERE series_id = :seriesId")
    suspend fun deleteSeasons(seriesId: String)

    @Upsert
    suspend fun upsertSeasons(seasons: List<SeasonEntity>)

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Query("UPDATE series SET tree_fetched_at = :fetchedAt WHERE id = :seriesId")
    suspend fun stampTree(seriesId: String, fetchedAt: Long)

    /** Writes the synopsis a tree fetch brought back with it. */
    @Query("UPDATE series SET plot = :plot WHERE id = :seriesId")
    suspend fun updatePlot(seriesId: String, plot: String?)
}
