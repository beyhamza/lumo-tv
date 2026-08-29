package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached film (US-13, "Hors ligne" — the same requirement as US-08's channels).
 *
 * Column names are the server's, verbatim, for the reason given on
 * [ChannelEntity]: one vocabulary across the contract, the server schema and this
 * cache is what keeps a mapping bug visible.
 *
 * **`stream_url` is absent**, exactly as it is on [ChannelEntity] and for exactly
 * the same reason. **`container_extension` is absent too**, and that one is not
 * about secrecy: it is a fragment the *server* uses to build an Xtream playback
 * URL, it is null for every M3U film (ADR 0009), and a client has nothing to do
 * with it. The contract does not carry it either.
 *
 * <h2>Why [plot] is here, and why it is usually null</h2>
 *
 * The synopsis is not in a listing — on an Xtream panel it costs one HTTP call
 * *per film*, so `GET /vod/{id}` fetches it when somebody opens a film and the
 * server remembers it. Caching it here gives the same film, opened again on a
 * train, its synopsis.
 *
 * It is null for every row a refresh writes, and populated only by opening the
 * film. See [tv.lumo.android.core.database.dao.VodDao.replaceForSource] for what
 * a re-synchronisation does to it, which is stated rather than hidden.
 */
@Entity(
    tableName = "vod_item",
    indices = [
        // The browse query: films of a category, in order.
        Index(value = ["source_id", "category_id", "position"]),
        // Local search, and the ORDER BY of an uncategorised listing.
        Index(value = ["source_id", "name"]),
    ],
)
data class VodItemEntity(
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
     * The poster the user's own source advertises. Lumo ships no bundled artwork
     * and no fallback poster of its own (CLAUDE.md, règle 2): null means a card
     * renders its title, never a picture of ours standing in for one of theirs.
     */
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,

    /** Release year, when the source states one. Many do not. */
    @ColumnInfo(name = "year")
    val year: Int?,

    /**
     * Running time in seconds, and null far more often than not.
     *
     * A screen that needs it to decide whether something was watched to the end
     * has to cope without it — which is why `PlaybackProgress` carries its own
     * duration rather than reading this one.
     */
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int?,

    /**
     * Whatever the source calls a rating, verbatim: `7.4`, `PG-13` and `★★★★` all
     * occur. Text and not a number, because turning it into one would mean this
     * layer deciding what the provider meant — the decision already refused for
     * `ChannelEntity.quality`.
     */
    @ColumnInfo(name = "rating")
    val rating: String?,

    /** Synopsis, filled by opening the film. Null in everything a refresh writes. */
    @ColumnInfo(name = "plot")
    val plot: String?,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "is_adult")
    val isAdult: Boolean,
)
