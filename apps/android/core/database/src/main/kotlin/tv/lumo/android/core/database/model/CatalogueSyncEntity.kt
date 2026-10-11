package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * That a whole catalogue of one type was pulled for a source on this device, and
 * when (BUG-R020-16-01).
 *
 * <h2>Why a row count cannot say it</h2>
 *
 * The `channel`, `vod_item` and `series` tables are not only filled by a refresh.
 * Favourites and recently watched channels are resolved one by one into
 * `channel`, the "Continue" rail into `vod_item`, a series opened from a deep
 * link into `series`: each is a genuine row of that source, and none of them
 * means the catalogue is here. A screen that read "rows > 0" as "already
 * synchronised" showed a television just paired to an account with three
 * favourites a three-channel source, and never asked for the rest.
 *
 * One row per source and content type, written by a refresh **after** its rows,
 * so a crash in between leaves no row and the next opening refreshes again — the
 * safe way to be wrong. Gone with the source (`forget`) and with the account
 * (`clearAllTables` at sign-out).
 */
@Entity(
    tableName = "catalogue_sync",
    primaryKeys = ["source_id", "content_type"],
)
data class CatalogueSyncEntity(
    @ColumnInfo(name = "source_id")
    val sourceId: String,

    /** The contract's `ContentType` wire value: `LIVE`, `VOD`, `SERIES`. */
    @ColumnInfo(name = "content_type")
    val contentType: String,

    /** Device clock, epoch milliseconds, at the end of the refresh. */
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long,
)
