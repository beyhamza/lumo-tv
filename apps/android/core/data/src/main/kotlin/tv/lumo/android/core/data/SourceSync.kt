package tv.lumo.android.core.data

import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.Source

/**
 * What became of a manual "Refresh" (`POST /sources/{id}/sync`), in the terms a
 * screen acts on (US-024).
 *
 * <h2>Two of the refusals are not errors</h2>
 *
 * `409 SOURCE_SYNC_IN_PROGRESS` means the thing that was asked for is already
 * happening: the screen shows it happening and says nothing else. `429
 * SOURCE_SYNC_RATE_LIMITED` is a wait with a length, and the length is the
 * server's — one manual synchronisation per source every five minutes today,
 * configurable, and never a constant on this side (c4-previous-catalogue.md §D4).
 * Folding either into "the refresh failed" would send somebody to press the
 * button again, which is the one thing both answers say not to do.
 *
 * Shared by the phone and the television, which handle them identically.
 */
sealed interface SyncOutcome {

    /** `202`: accepted. [source] is already `PENDING`; polling takes over. */
    data class Accepted(val source: Source) : SyncOutcome

    /** `409 SOURCE_SYNC_IN_PROGRESS`. Polling takes over, exactly as for [Accepted]. */
    data object AlreadyRunning : SyncOutcome

    /**
     * `429 SOURCE_SYNC_RATE_LIMITED`.
     *
     * @param retryAfterSeconds the `Retry-After` header, or null when the server
     * sent none or sent one this build cannot read. **Never replaced by a
     * default**: a screen with no number says "in a few minutes" without one,
     * rather than printing a duration nobody promised.
     */
    data class RateLimited(val retryAfterSeconds: Int?) : SyncOutcome

    /** `404 SOURCE_NOT_FOUND`: deleted elsewhere. A proof, not a failure (US-018). */
    data object Gone : SyncOutcome

    /** Anything else. The source keeps the state it had. */
    data class Failed(val error: LumoError) : SyncOutcome
}

fun LumoResult<Source>.asSyncOutcome(): SyncOutcome = when (this) {
    is LumoResult.Success -> SyncOutcome.Accepted(value)
    is LumoResult.Failure -> when (val error = error) {
        is LumoError.Api -> when (error.code) {
            ErrorCode.SOURCE_SYNC_IN_PROGRESS -> SyncOutcome.AlreadyRunning
            ErrorCode.SOURCE_SYNC_RATE_LIMITED -> SyncOutcome.RateLimited(error.retryAfterSeconds)
            ErrorCode.SOURCE_NOT_FOUND -> SyncOutcome.Gone
            else -> SyncOutcome.Failed(error)
        }
        else -> SyncOutcome.Failed(error)
    }
}

/**
 * A server delay, in whole minutes, for a sentence like "again in about 3 min".
 *
 * Rounded **up**: telling somebody to come back in two minutes when the server
 * said 170 seconds earns them a second refusal. Null stays null — the caller
 * then words the wait without a number.
 */
fun retryAfterMinutes(seconds: Int?): Int? =
    seconds?.takeIf { it > 0 }?.let { (it + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE }

private const val SECONDS_PER_MINUTE = 60
