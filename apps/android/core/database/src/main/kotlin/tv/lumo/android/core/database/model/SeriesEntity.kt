package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached series (US-15).
 *
 * Column names are the server's, verbatim, for the reason given on
 * [ChannelEntity]: one vocabulary across the contract, the server schema and this
 * cache is what keeps a mapping bug visible.
 *
 * **A [VodItemEntity] minus one field and plus two.** No `container_extension` —
 * a series is not played, its episodes are — and an [episodeRunTime] the panel
 * states as indicative. The second addition is [treeFetchedAt], which is the
 * whole of what makes a series different from a film on this side.
 */
@Entity(
    tableName = "series",
    indices = [
        Index(value = ["source_id", "category_id", "position"]),
        Index(value = ["source_id", "name"]),
    ],
)
data class SeriesEntity(
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
     * and no fallback of its own (CLAUDE.md, règle 2).
     */
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,

    /** First-broadcast year, when the source states one. */
    @ColumnInfo(name = "year")
    val year: Int?,

    /**
     * Typical episode length in minutes, as the panel states it.
     *
     * **Indicative, and never used as a duration.** Deciding whether an episode
     * was watched to the end needs *that* episode's length
     * ([EpisodeEntity.durationSeconds]); using this in its place would compute a
     * threshold against a number belonging to no episode in particular.
     */
    @ColumnInfo(name = "episode_run_time")
    val episodeRunTime: Int?,

    /** Whatever the source calls a rating, verbatim. See [VodItemEntity.rating]. */
    @ColumnInfo(name = "rating")
    val rating: String?,

    /** Synopsis. Arrives with the tree, so it is null until one has been fetched. */
    @ColumnInfo(name = "plot")
    val plot: String?,

    /**
     * When this device last stored a tree for this series, or null if it never
     * has.
     *
     * <h2>The field that makes a series different from a film here</h2>
     *
     * A film's synopsis, once cached, is cached: it never changes. **A series in
     * production gains an episode a week**, so an old tree is not merely old, it
     * is missing what somebody is looking for.
     *
     * A nullable timestamp rather than a boolean and a date, because the three
     * states a screen has to tell apart fall out of it directly: never fetched,
     * fetched and fresh, fetched and stale. A boolean beside a date would allow a
     * fourth state that means nothing and would need resetting somewhere.
     */
    @ColumnInfo(name = "tree_fetched_at")
    val treeFetchedAt: Long?,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "is_adult")
    val isAdult: Boolean,
)

/**
 * A cached season.
 *
 * **A foreign key here, unlike anywhere else in this database.** [FavoriteEntity]
 * deliberately has none, because a favourite legitimately points at a channel this
 * device has not cached and a constraint would lose the favourite rather than wait
 * for the channel. A season is the opposite case: it is only ever written as part
 * of one series' tree, in one transaction, and a season whose series is gone is a
 * row nothing can reach. Letting it cascade is what stops the table growing with
 * every re-synchronisation.
 */
@Entity(
    tableName = "season",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["series_id", "season_number"], unique = true)],
)
data class SeasonEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "series_id")
    val seriesId: String,

    /**
     * As the panel numbers it. **Zero occurs** and means specials on many panels;
     * it is passed through rather than renamed, because deciding it means
     * "specials" would be this layer interpreting somebody's convention.
     */
    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,

    /**
     * How many episodes the panel claims this season has.
     *
     * **It can disagree with what is stored, and the stored list wins.** A panel
     * announcing twenty-four and returning twenty-two has twenty-two episodes
     * somebody can watch. Kept because it is occasionally the only hint that a
     * season is incomplete.
     */
    @ColumnInfo(name = "episode_count")
    val episodeCount: Int?,

    /** Season artwork when the panel has some, which is uncommon. */
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,
)

/**
 * A cached episode: the thing that is played, and the thing a saved position
 * points at.
 *
 * **`stream_url` and `container_extension` are absent**, for the reasons they are
 * absent from [VodItemEntity]: the first is credential-bearing and comes from
 * `GET /episodes/{id}/playback` one at a time, the second is a fragment only the
 * server uses.
 */
@Entity(
    tableName = "episode",
    foreignKeys = [
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["id"],
            childColumns = ["season_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["season_id", "episode_number"]),
        // The rail's lookup: a saved position knows an episode id and nothing else.
        Index(value = ["series_id"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "series_id")
    val seriesId: String,

    @ColumnInfo(name = "season_id")
    val seasonId: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "external_id")
    val externalId: String?,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,

    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int,

    /**
     * Episode title, when the panel has one. **Null far more often than a film's**
     * — a screen shows "Episode 4" rather than an empty line.
     */
    @ColumnInfo(name = "name")
    val name: String?,

    /** Length of *this* episode. What decides whether it was watched to the end. */
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long?,

    @ColumnInfo(name = "plot")
    val plot: String?,
)
