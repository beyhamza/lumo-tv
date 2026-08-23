package tv.lumo.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = true,
)
abstract class LumoDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
}
