package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A channel this account watched recently (M5, `SRV-06`).
 *
 * <h2>Cached for the same reason the favourites are</h2>
 *
 * The list is short by construction — the server keeps a rolling window — and it
 * is the first thing somebody looks at when they turn the television on. Reaching
 * for the network to draw it would make the one screen that has to be instant the
 * one that waits.
 *
 * <h2>Its value is that it is not local</h2>
 *
 * [position] is the server's order, and the whole point of the feature: what makes
 * this list worth having is that it knows what was watched **on the phone**. A
 * device-local history would not need an endpoint at all — and would not be the
 * rail api-gaps.md M5 asked for.
 *
 * No foreign key to `channel`, same reasoning as `FavoriteEntity`: the channel may
 * not be cached on this device yet, and a constraint would drop the entry rather
 * than wait for it.
 */
@Entity(
    tableName = "recent_channel",
    indices = [Index(value = ["position"])],
)
data class RecentChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    val channelId: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    /** The server's order, most recent first. */
    @ColumnInfo(name = "position")
    val position: Int,
)
