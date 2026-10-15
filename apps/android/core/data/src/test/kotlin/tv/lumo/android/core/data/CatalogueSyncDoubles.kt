package tv.lumo.android.core.data

import tv.lumo.android.core.database.dao.CatalogueSyncDao
import tv.lumo.android.core.database.model.CatalogueSyncEntity

/**
 * The `catalogue_sync` table, in a map keyed like its primary key.
 *
 * Shared by every repository test that builds a catalogue repository, for the
 * reason `SeriesDoubles` gives: one fake, not one per test drifting apart.
 */
internal class FakeCatalogueSyncDao : CatalogueSyncDao {

    val stored = mutableMapOf<Pair<String, String>, CatalogueSyncEntity>()

    override suspend fun upsert(sync: CatalogueSyncEntity) {
        stored[sync.sourceId to sync.contentType] = sync
    }

    override suspend fun isSynchronised(sourceId: String, contentType: String): Boolean =
        (sourceId to contentType) in stored

    override suspend fun sourceIds(): List<String> = stored.keys.map { it.first }.distinct()

    override suspend fun delete(sourceId: String, contentType: String) {
        stored.remove(sourceId to contentType)
    }
}
