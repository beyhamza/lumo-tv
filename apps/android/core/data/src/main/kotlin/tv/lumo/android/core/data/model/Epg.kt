package tv.lumo.android.core.data.model

import java.time.Instant
import tv.lumo.android.core.data.EpgFreshness
import tv.lumo.android.network.generated.model.EpgAttemptStatus
import tv.lumo.android.network.generated.model.EpgMappingStatus

/**
 * One programme of the guide, as a screen wants one (US-16, S9-03).
 *
 * Instants and not local times: "on air now" is a comparison between instants,
 * and two identical local times can name two different instants across a
 * daylight-saving change (guide-interactions.md, "heure locale"). The device's
 * zone is applied at display time, in the one formatting helper of
 * `core:designsystem`.
 *
 * No channel id on the programme. The server associates programmes with
 * channels through `tvg_id`, and the same programme — same [id] — is sent under
 * every channel that shares one; which channel a programme is being shown for
 * is [EpgChannelWindow]'s to say.
 */
data class EpgProgramme(
    val id: String,
    /** Inclusive. */
    val startsAt: Instant,
    /** Exclusive: a programme is over at the instant it ends. */
    val endsAt: Instant,
    val title: String,
    val description: String?,
    /** Genre as the guide advertises it. Free text. */
    val category: String?,
)

/**
 * The server's account of a source's guide import (C1 D3), as a screen reads it.
 *
 * [lastSuccessfulImportAt] is the date the interface calls "last guide import"
 * — never "programmes up to date": an import that succeeded this morning can
 * carry listings the provider published last week, and nothing here can tell.
 *
 * [lastAttemptStatus] is the contract's own enum, kept rather than copied for
 * the reason `LumoError.Api` keeps `ErrorCode`: the contract is the one source
 * of truth for it, and a `when` over it always writes its `else`.
 */
data class EpgImportStatus(
    /** False means every list under it is empty by construction, and a screen says so. */
    val configured: Boolean,
    val lastSuccessfulImportAt: Instant?,
    val lastAttemptStartedAt: Instant?,
    val lastAttemptFinishedAt: Instant?,
    val lastAttemptStatus: EpgAttemptStatus,
)

/**
 * The programmes of one channel over the window that was asked for.
 *
 * @param mappingStatus what the server said about the channel's `tvg_id`, or
 * null when the answer came from the cache alone, which does not know. `MAPPED`
 * only says an identifier is set, not that programmes exist for it; `NO_TVG_ID`
 * says the list is empty by construction. In S9-03 a screen shows nothing in
 * both cases — the distinction is for the Guide view (S9-04) to explain.
 */
data class EpgChannelWindow(
    val channelId: String,
    val mappingStatus: EpgMappingStatus?,
    /** Overlapping the window, full times kept, ordered by start then id. */
    val programmes: List<EpgProgramme>,
)

/**
 * The guide of a batch of channels over one slot, and how old it is.
 *
 * <h2>The age is a value of the model, not a decision of the repository</h2>
 *
 * S7-02's rule, and the reason this type carries three dates rather than a
 * boolean. A repository that decided "stale" on its own would produce three
 * thresholds on three surfaces; the screen decides what is worth saying, from
 * [freshness], which is the same pure function everywhere.
 *
 * <h2>Three dates that are not the same thing</h2>
 *
 * - [importStatus]`.lastSuccessfulImportAt` — the **server's** last guide import.
 *   The only date shown as "last guide import".
 * - [generatedAt] — the **server's** clock when it produced the answer this
 *   window came from. With the import date it gives the guide's starting age,
 *   from two timestamps of one clock.
 * - [fetchedAt] — the **device's** clock when that answer was written to the
 *   cache. What an offline screen shows as the age of its local data, and what
 *   the guide's age grows from.
 *
 * All three are null together, when the source's guide has never been fetched
 * on this device: a window served from an empty cache has no provenance to
 * claim. Served from a filled cache they are the ones stored with the last
 * fetch of this source — which is what "the date of its retrieval" means for
 * cached data.
 */
data class EpgWindow(
    val sourceId: String,
    val from: Instant,
    val to: Instant,
    val fetchedAt: Instant?,
    val generatedAt: Instant?,
    val importStatus: EpgImportStatus?,
    /** One entry per requested channel id, in the order requested. */
    val channels: List<EpgChannelWindow>,
) {

    /** How much to trust this guide at [now], by the C1 D4 rules. */
    fun freshness(now: Instant): EpgFreshness =
        EpgFreshness.of(importStatus, generatedAt, fetchedAt, now)

    /** The programmes of one channel of the batch, empty for an id that was not in it. */
    fun programmesOf(channelId: String): List<EpgProgramme> =
        channels.firstOrNull { it.channelId == channelId }?.programmes.orEmpty()
}
