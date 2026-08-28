package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined group of favourites (US-12).
 *
 * Cached whole rather than fetched per screen: a list of groups is counted in
 * units, and the screen that shows them is the one a person opens when they have
 * no network and want the channels they chose themselves.
 *
 * Column names are the server's, verbatim, as everywhere else in this cache.
 */
@Entity(tableName = "favorite_group")
data class FavoriteGroupEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "position")
    val position: Int,

    /**
     * The group an add without a group lands in, and where a deleted group empties
     * into.
     *
     * Cached because it decides what a screen *calls* that group: while this is
     * true and the name is still the server's own, a client renders its own
     * translated wording; once the user has renamed it, their name wins. Working
     * that out needs the flag offline as much as online.
     */
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
)

/**
 * One channel filed in one group.
 *
 * **No foreign key to `channel`, deliberately.** A favourite can point at a
 * channel this device has never cached — starred on the phone, opened on the
 * television before that source was ever synchronised here — and a foreign key
 * would refuse the insert and lose the favourite rather than the join. The
 * missing channel is fetched by identifier instead
 * (`GET /sources/{id}/channels?ids=`), and until it arrives the row simply does
 * not render.
 *
 * The same shape covers the other case, which is permanent: a channel dropped
 * from the playlist at the last re-synchronisation. That favourite is an orphan
 * for good, and an orphan is not an error — the channel left the subscription,
 * the application did not break.
 */
@Entity(
    tableName = "favorite",
    indices = [
        // The listing query: one group, in the user's own order.
        Index(value = ["group_id", "position"]),
        // Answers "is this channel starred?" while a grid scrolls past it.
        Index(value = ["channel_id"]),
    ],
)
data class FavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "group_id")
    val groupId: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "channel_id")
    val channelId: String,

    @ColumnInfo(name = "position")
    val position: Int,
)

/**
 * A favourite joined to the channel it points at.
 *
 * The join is what turns two identifiers into a row a person can read, and it
 * happens in SQLite rather than in Kotlin because the alternative is loading
 * every favourite and every channel to match them in memory.
 */
data class FavoriteChannelRow(
    @ColumnInfo(name = "favorite_id")
    val favoriteId: String,

    @ColumnInfo(name = "group_id")
    val groupId: String,

    @ColumnInfo(name = "favorite_position")
    val favoritePosition: Int,

    @Embedded
    val channel: ChannelEntity,
)

/** A favourite whose channel is not in this device's cache yet. */
data class UnresolvedFavorite(
    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "channel_id")
    val channelId: String,
)
