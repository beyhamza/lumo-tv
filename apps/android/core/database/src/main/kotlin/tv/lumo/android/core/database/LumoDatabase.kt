package tv.lumo.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.dao.FavoriteDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.model.FavoriteEntity
import tv.lumo.android.core.database.model.FavoriteGroupEntity

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
        FavoriteGroupEntity::class,
        FavoriteEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class LumoDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun favoriteDao(): FavoriteDao
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

/**
 * 2 → 3: favourites and their groups (US-12).
 *
 * Two tables and their indexes, and **no foreign key from `favorite` to
 * `channel`** — see [FavoriteEntity] for why: a favourite can legitimately point
 * at a channel this device has not cached, and a constraint would lose the
 * favourite rather than wait for the channel.
 *
 * Nothing is backfilled and nothing needs to be. The favourites live on the
 * server; the first refresh after this migration fills both tables, and until it
 * runs the screens show an empty state rather than wrong data.
 *
 * The statements are the ones Room generates for these entities, written out
 * because `DatabaseModule` still has no `fallbackToDestructiveMigration` and the
 * alternative to this file is wiping a synchronised catalogue.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite_group` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `is_default` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `source_id` TEXT NOT NULL,
                `channel_id` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_favorite_group_id_position` " +
                "ON `favorite` (`group_id`, `position`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_favorite_channel_id` ON `favorite` (`channel_id`)",
        )
    }
}
