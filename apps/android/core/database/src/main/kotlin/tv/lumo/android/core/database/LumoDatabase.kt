package tv.lumo.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.dao.FavoriteDao
import tv.lumo.android.core.database.dao.RecentChannelDao
import tv.lumo.android.core.database.dao.SeriesDao
import tv.lumo.android.core.database.dao.VodDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.model.FavoriteEntity
import tv.lumo.android.core.database.model.FavoriteGroupEntity
import tv.lumo.android.core.database.model.RecentChannelEntity
import tv.lumo.android.core.database.model.EpisodeEntity
import tv.lumo.android.core.database.model.SeasonEntity
import tv.lumo.android.core.database.model.SeriesEntity
import tv.lumo.android.core.database.model.VodItemEntity

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
        RecentChannelEntity::class,
        VodItemEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class LumoDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentChannelDao(): RecentChannelDao
    abstract fun vodDao(): VodDao
    abstract fun seriesDao(): SeriesDao
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

/**
 * 3 → 4: the recently watched window (M5, `S4-08`).
 *
 * One table, cached for the same reason the favourites are: the list is short by
 * construction — the server keeps a rolling window — and it is the first thing
 * somebody looks at when they turn the television on. No foreign key to `channel`,
 * same reasoning as `favorite`.
 *
 * Nothing to backfill. The window lives on the server, and the first refresh after
 * this migration fills it.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recent_channel` (
                `channel_id` TEXT NOT NULL,
                `source_id` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                PRIMARY KEY(`channel_id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recent_channel_position` " +
                "ON `recent_channel` (`position`)",
        )
    }
}

/**
 * 4 → 5: the film catalogue (US-13).
 *
 * One table, cached for the reason the channels are: US-13 asks for the same
 * offline behaviour US-08 asked for, and a grid that empties on a train is not a
 * cache. `category` is untouched — films and channels share it, keyed by
 * `content_type`, which is why `CategoryDao` replaces one type at a time.
 *
 * Nothing to backfill: no build before this one ever ingested a film, and the
 * first refresh after this migration fills the table. Until it runs the screen
 * shows an empty state rather than wrong data.
 *
 * The statements are the ones Room generates for [VodItemEntity], written out
 * because `DatabaseModule` still has no `fallbackToDestructiveMigration` and the
 * alternative to this file is wiping a synchronised catalogue.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vod_item` (
                `id` TEXT NOT NULL,
                `source_id` TEXT NOT NULL,
                `category_id` TEXT,
                `external_id` TEXT,
                `name` TEXT NOT NULL,
                `poster_url` TEXT,
                `year` INTEGER,
                `duration_seconds` INTEGER,
                `rating` TEXT,
                `plot` TEXT,
                `position` INTEGER NOT NULL,
                `is_adult` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vod_item_source_id_category_id_position` " +
                "ON `vod_item` (`source_id`, `category_id`, `position`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vod_item_source_id_name` " +
                "ON `vod_item` (`source_id`, `name`)",
        )
    }
}

/**
 * 5 → 6: series, seasons and episodes (US-15).
 *
 * Three tables, and the first foreign keys this schema has had. `favorite` and
 * `recent_channel` deliberately have none — they point at channels this device may
 * not have cached, and a constraint would lose the favourite rather than wait for
 * the channel. A season is the opposite case: it only ever exists as part of one
 * series' tree, written in one transaction, and a season whose series is gone is a
 * row nothing can reach. The cascade is what stops the table growing at every
 * re-synchronisation.
 *
 * Nothing to backfill: no build before this one ever stored a series, and the
 * first refresh fills the list. The trees arrive one at a time, when somebody
 * opens a series.
 *
 * The statements are the ones Room generates for these entities, written out
 * because `DatabaseModule` still has no `fallbackToDestructiveMigration`.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `series` (
                `id` TEXT NOT NULL,
                `source_id` TEXT NOT NULL,
                `category_id` TEXT,
                `external_id` TEXT,
                `name` TEXT NOT NULL,
                `poster_url` TEXT,
                `year` INTEGER,
                `episode_run_time` INTEGER,
                `rating` TEXT,
                `plot` TEXT,
                `tree_fetched_at` INTEGER,
                `position` INTEGER NOT NULL,
                `is_adult` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_series_source_id_category_id_position` " +
                "ON `series` (`source_id`, `category_id`, `position`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_series_source_id_name` " +
                "ON `series` (`source_id`, `name`)",
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `season` (
                `id` TEXT NOT NULL,
                `series_id` TEXT NOT NULL,
                `season_number` INTEGER NOT NULL,
                `episode_count` INTEGER,
                `poster_url` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`series_id`) REFERENCES `series`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_season_series_id_season_number` " +
                "ON `season` (`series_id`, `season_number`)",
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `episode` (
                `id` TEXT NOT NULL,
                `series_id` TEXT NOT NULL,
                `season_id` TEXT NOT NULL,
                `source_id` TEXT NOT NULL,
                `external_id` TEXT,
                `season_number` INTEGER NOT NULL,
                `episode_number` INTEGER NOT NULL,
                `name` TEXT,
                `duration_seconds` INTEGER,
                `plot` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`season_id`) REFERENCES `season`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_episode_season_id_episode_number` " +
                "ON `episode` (`season_id`, `episode_number`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_episode_series_id` ON `episode` (`series_id`)",
        )
    }
}
