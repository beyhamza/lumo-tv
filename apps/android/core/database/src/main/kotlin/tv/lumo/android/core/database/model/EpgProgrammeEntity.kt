package tv.lumo.android.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One programme of the guide, cached (US-16, S7-02 taken up by S9-03).
 *
 * <h2>The first cache here that goes stale on its own</h2>
 *
 * A channel stays a channel; a programme of 20:00 is of no interest at 21:30.
 * So this table, unlike the three catalogue caches, is **purged** — what ended
 * before D−1 goes, mirroring the server's own retention — and every read of it
 * carries the moment it was fetched, so that a screen can say how old it is
 * (`EpgWindow`). Without the purge a device that watches television every
 * evening keeps a guide for ever.
 *
 * <h2>Keyed by the server's programme id, associated by `(source_id, tvg_id)`</h2>
 *
 * The server associates programmes with channels through the channel's
 * `tvg_id`, within one source (C1 §1, `CatalogReadRepository.findProgrammes`).
 * The cache keeps the same association rather than a `channel_id`: two channels
 * of one source that share a `tvg_id` share their programmes, and the server
 * sends the same programme — same UUID — under each of them. One row per UUID
 * here, joined to as many channels as carry that `tvg_id` at read time, is what
 * keeps the upsert from fighting itself over the same key.
 *
 * `source_id` is part of the association and not a convenience: two sources
 * can use the same `tvg_id` text for two different channels (C1-05), and a join
 * that ignored the source would mix their guides.
 *
 * Times are epoch milliseconds, as everywhere else in this schema: a
 * comparison in SQL over an integer is what the overlap query needs, and the
 * device's zone is applied at display time only (guide-interactions.md, "heure
 * locale").
 *
 * No foreign key to `channel`, for the reason `favorite` gives: a programme may
 * arrive for a channel this device has not cached yet, and a constraint would
 * drop the programme rather than wait for the channel.
 */
@Entity(
    tableName = "epg_programme",
    indices = [
        // The window read: programmes of a channel that end after `from`,
        // ordered by start. `ends_at` last so the range scan stays on the index.
        Index(value = ["source_id", "tvg_id", "ends_at"]),
    ],
)
data class EpgProgrammeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "tvg_id")
    val tvgId: String,

    /** Epoch milliseconds, inclusive. */
    @ColumnInfo(name = "starts_at")
    val startsAt: Long,

    /** Epoch milliseconds, exclusive. */
    @ColumnInfo(name = "ends_at")
    val endsAt: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    /** Genre as the guide advertises it. Free text, never an enum. */
    @ColumnInfo(name = "category")
    val category: String?,
)

/**
 * What the server said about a source's guide import, the last time it was
 * asked — and when it was asked (C1 D3, S9-03).
 *
 * <h2>Two dates that are not the same thing, kept side by side on purpose</h2>
 *
 * [lastSuccessfulImportAt] is the **server's** date of the last guide import that
 * finished, and it is the one the interface shows as "last guide import". It
 * says nothing about how fresh the provider's listings are, and neither
 * [generatedAt] nor [fetchedAt] stands in for it (C1 D3: "ne pas inventer de
 * date de publication XMLTV").
 *
 * [fetchedAt] is the **device's** clock when this row was written. It is what
 * an offline screen shows as the age of its local data, and it is what lets the
 * age of the guide keep moving after the fetch: the age starts at
 * `generated_at − last_successful_import_at` — two server timestamps, so a
 * device clock set wrong cannot skew it — and grows by `now − fetched_at`
 * (C1 D4). `EpgFreshness` in `core:data` is the one place that arithmetic lives.
 *
 * One row per source: the metadata is source level on the server too, and a
 * row per window would only repeat it.
 */
@Entity(tableName = "epg_import_status")
data class EpgImportStatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "configured")
    val configured: Boolean,

    /** Server clock, epoch milliseconds. Null when no import has succeeded. */
    @ColumnInfo(name = "last_successful_import_at")
    val lastSuccessfulImportAt: Long?,

    @ColumnInfo(name = "last_attempt_started_at")
    val lastAttemptStartedAt: Long?,

    @ColumnInfo(name = "last_attempt_finished_at")
    val lastAttemptFinishedAt: Long?,

    /**
     * The contract's `EpgAttemptStatus`, stored as its wire text so that a value
     * added to the contract after this build shipped survives a round trip
     * through the cache rather than failing to be stored.
     */
    @ColumnInfo(name = "last_attempt_status")
    val lastAttemptStatus: String,

    /** `EpgGrid.generated_at`: the server's clock when it answered. */
    @ColumnInfo(name = "generated_at")
    val generatedAt: Long,

    /** The device's clock when the answer was written here. */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)

/**
 * One row of the window read: a programme, and the channel it was reached
 * through.
 *
 * The join produces one row per (channel, programme) pair, so a programme
 * shared by two channels of the batch comes back twice — once under each — which
 * is exactly the shape the server's own answer has (C1 D2: "ne pas supprimer une
 * ligne de grille au nom d'un dédoublonnage global").
 */
data class EpgChannelProgrammeRow(
    @ColumnInfo(name = "channel_id")
    val channelId: String,

    @Embedded
    val programme: EpgProgrammeEntity,
)
