package tv.lumo.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.model.CategoryEntity

@Dao
interface CategoryDao {

    /**
     * Categories are counted in tens, not thousands, so a Flow of the whole list
     * is the right shape here — unlike channels.
     */
    @Query(
        """
        SELECT * FROM category
        WHERE source_id = :sourceId AND content_type = :contentType
        ORDER BY position, name
        """,
    )
    fun observeBySource(sourceId: String, contentType: String): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsert(categories: List<CategoryEntity>)

    /** Every category of a source, both content types. For a source being forgotten. */
    @Query("DELETE FROM category WHERE source_id = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM category WHERE source_id = :sourceId AND content_type = :contentType")
    suspend fun deleteBySourceAndType(sourceId: String, contentType: String)

    /**
     * Replaces the categories of one source **for one content type**.
     *
     * Scoped to the type, and that is not a refinement: channels and films are
     * refreshed by two repositories, at two moments, and they share this table.
     * A replacement that deleted every row of the source would have each refresh
     * silently empty the other one's category strip — a bug that only appears on
     * a source carrying both, which is most of them.
     */
    @Transaction
    suspend fun replaceForSourceAndType(
        sourceId: String,
        contentType: String,
        categories: List<CategoryEntity>,
    ) {
        deleteBySourceAndType(sourceId, contentType)
        upsert(categories)
    }
}
