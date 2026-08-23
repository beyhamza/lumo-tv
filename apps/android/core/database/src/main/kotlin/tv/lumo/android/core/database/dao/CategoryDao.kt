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

    @Query("DELETE FROM category WHERE source_id = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Transaction
    suspend fun replaceForSource(sourceId: String, categories: List<CategoryEntity>) {
        deleteBySource(sourceId)
        upsert(categories)
    }
}
