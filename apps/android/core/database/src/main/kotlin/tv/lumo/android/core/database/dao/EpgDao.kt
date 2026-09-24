package tv.lumo.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import tv.lumo.android.core.database.model.EpgChannelProgrammeRow
import tv.lumo.android.core.database.model.EpgImportStatusEntity
import tv.lumo.android.core.database.model.EpgProgrammeEntity

/**
 * The programme guide cache (US-16, S9-03).
 *
 * Reads are windowed, never whole: a screen asks for the programmes of *these*
 * channels over *this* slot, and that is the only shape a read has. Nothing
 * here returns a `Flow` — the guide does not change under a screen the way a
 * favourite does, and what a screen re-computes as time passes ("now") is a
 * pure function over what it already holds, not a new query (sprint-09.md,
 * "sans requête à la seconde").
 */
@Dao
interface EpgDao {

    /**
     * The programmes of [channelIds] overlapping `[from, to)`.
     *
     * The server's overlap rule, verbatim (C1 §1): a programme is in the window
     * when it **ends after `from`** and **starts before `to`** — so one that
     * straddles a bound is included with its full times, and one that ends
     * exactly at `from` or starts exactly at `to` is not.
     *
     * Joined through the channel table on `(source_id, tvg_id)`, which is how
     * the server associates them. A channel without a `tvg_id` matches nothing,
     * and so does one this device has not cached: both come back with no rows,
     * and the repository reports them as channels with an empty list.
     *
     * Ordered by start then id, the server's total order, so that the cache and
     * the network produce the same list for the same window.
     */
    @Query(
        """
        SELECT c.id AS channel_id, p.*
        FROM channel c
        JOIN epg_programme p ON p.source_id = c.source_id AND p.tvg_id = c.tvg_id
        WHERE c.id IN (:channelIds)
          AND p.ends_at > :from
          AND p.starts_at < :to
        ORDER BY p.starts_at, p.id
        """,
    )
    suspend fun window(channelIds: List<String>, from: Long, to: Long): List<EpgChannelProgrammeRow>

    @Upsert
    suspend fun upsert(programmes: List<EpgProgrammeEntity>)

    /**
     * The local retention, calqued on the server's (S7-02): what ended before
     * [cutoff] — D−1, by the repository's clock — is dropped, whatever its source.
     */
    @Query("DELETE FROM epg_programme WHERE ends_at < :cutoff")
    suspend fun purgeEndedBefore(cutoff: Long)

    @Query("DELETE FROM epg_programme WHERE source_id = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("SELECT * FROM epg_import_status WHERE source_id = :sourceId")
    suspend fun importStatus(sourceId: String): EpgImportStatusEntity?

    @Upsert
    suspend fun upsertImportStatus(status: EpgImportStatusEntity)

    @Query("DELETE FROM epg_import_status WHERE source_id = :sourceId")
    suspend fun deleteImportStatus(sourceId: String)

    /**
     * Writes one server answer: its programmes and the import status it came
     * with, together.
     *
     * One transaction, because the two are read together: a status that says
     * "imported an hour ago" over programmes from last week — or the reverse —
     * is the inconsistency C1 D3 asks the server to avoid with `REPEATABLE
     * READ`, and the cache must not reintroduce it on the way in.
     */
    @Transaction
    suspend fun store(programmes: List<EpgProgrammeEntity>, status: EpgImportStatusEntity) {
        upsert(programmes)
        upsertImportStatus(status)
    }

    /** Everything this source's guide left here, for a source the user deleted. */
    @Transaction
    suspend fun forget(sourceId: String) {
        deleteBySource(sourceId)
        deleteImportStatus(sourceId)
    }
}
