package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached channel (US-08, "Hors ligne").
 *
 * Column names are the ones in docs/domain-model.md §2, verbatim. The device
 * cache, the server schema and the contract use one vocabulary; a `channelName`
 * here and a `name` there is how a mapping bug becomes invisible.
 *
 * **`stream_url` is deliberately absent.** The server has the column, this cache
 * does not. A stream URL is credential-bearing — Xtream panels put the username
 * and password straight into the path — so the contract exposes it only through
 * `GET /channels/{id}/playback`, on demand, per playback. Caching it here would
 * put a replayable credential in a database that survives on the device and
 * lands in `adb backup` output, in exchange for saving one request per channel
 * the user actually watches.
 */
@Entity(
    tableName = "channel",
    indices = [
        // The browse query: channels of a category, in order (US-08).
        Index(value = ["source_id", "category_id", "position"]),
        // Local search, and the ORDER BY of an uncategorised listing.
        Index(value = ["source_id", "name"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "category_id")
    val categoryId: String?,

    @ColumnInfo(name = "external_id")
    val externalId: String?,

    @ColumnInfo(name = "name")
    val name: String,

    /**
     * `tvg-logo` from the user's own playlist. Lumo ships no bundled artwork and
     * no fallback logo of its own (AGENTS.md §1).
     */
    @ColumnInfo(name = "logo_url")
    val logoUrl: String?,

    @ColumnInfo(name = "tvg_id")
    val tvgId: String?,

    /**
     * The channel number the provider assigns — `tvg-chno` in an M3U, the panel's
     * own field in Xtream. Null for the many playlists that carry none.
     *
     * **Not [position].** That one is a display index reassigned at every
     * ingestion; this is the number the user knows by heart and types on a remote
     * control, and the two diverge the moment a channel drops out of the
     * playlist. Cached because an offline grid that hides the numbers an online
     * grid shows reads as a bug, not as a cache.
     */
    @ColumnInfo(name = "number")
    val number: Int?,

    /** Definition as the source advertises it — `HD`, `FHD`, `4K` — verbatim. */
    @ColumnInfo(name = "quality")
    val quality: String?,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "is_adult")
    val isAdult: Boolean,
)
