package tv.lumo.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import tv.lumo.android.core.database.model.CatalogueSyncEntity

/** See [CatalogueSyncEntity]. */
@Dao
interface CatalogueSyncDao {

    @Upsert
    suspend fun upsert(sync: CatalogueSyncEntity)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM catalogue_sync " +
            "WHERE source_id = :sourceId AND content_type = :contentType)",
    )
    suspend fun isSynchronised(sourceId: String, contentType: String): Boolean

    @Query("DELETE FROM catalogue_sync WHERE source_id = :sourceId AND content_type = :contentType")
    suspend fun delete(sourceId: String, contentType: String)
}
