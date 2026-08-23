package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached category (docs/domain-model.md §2, `category`).
 *
 * `channel_count` is derived server-side and not part of the entity there; it is
 * stored here because US-08 renders "N chaînes" next to every category and
 * counting locally would mean a second query per row.
 */
@Entity(
    tableName = "category",
    indices = [Index(value = ["source_id", "content_type", "position"])],
)
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "external_id")
    val externalId: String?,

    @ColumnInfo(name = "name")
    val name: String,

    /** `LIVE`, `VOD` or `SERIES` — the contract's `ContentType`, as text. */
    @ColumnInfo(name = "content_type")
    val contentType: String,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "channel_count")
    val channelCount: Int?,
)
