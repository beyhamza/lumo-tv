package tv.lumo.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.ChannelEntity

/**
 * The offline-first catalogue cache (docs/architecture.md §3).
 *
 * What lives here: what the user browses. What does not: anything credential
 * bearing. No session token (that is `core:auth`, encrypted, in DataStore), no
 * Xtream password (it never leaves the server), no stream URL (see
 * [ChannelEntity]). This database is unencrypted on purpose — its contents are
 * a channel list, and paying SQLCipher's cost on a TV box to protect a list of
 * channel names would be security theatre.
 *
 * `exportSchema = true`, with the JSON committed under `core/database/schemas/`:
 * that file is what makes a migration reviewable in a diff, and what Room's
 * migration tests read.
 */
@Database(
    entities = [
        ChannelEntity::class,
        CategoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LumoDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
}

/**
 * 1 → 2: the channel number and the quality badge.
 *
 * Both were added to the contract after this cache was written (`M3` and `M4` in
 * `docs/design/api-gaps.md`), so the online grid could show a number and a `FHD`
 * badge that the offline one could not. Two nullable columns and no backfill:
 * null is the honest value for every row already there — the last ingestion did
 * not carry these fields, and the next re-synchronisation fills them in.
 *
 * Written rather than dropped. `DatabaseModule` has no
 * `fallbackToDestructiveMigration`, deliberately: the alternative to this file is
 * wiping a synchronised catalogue and pulling fifteen thousand channels back over
 * somebody's mobile data.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE channel ADD COLUMN number INTEGER")
        connection.execSQL("ALTER TABLE channel ADD COLUMN quality TEXT")
    }
}
