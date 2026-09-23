package tv.lumo.android.core.data

import java.time.Duration
import java.time.Instant
import tv.lumo.android.core.data.model.EpgImportStatus
import tv.lumo.android.network.generated.model.EpgAttemptStatus

/**
 * How much to trust a guide, by the C1 D4 rules — a pure function, so that the
 * three surfaces cannot disagree about it.
 *
 * <h2>The threshold informs, it never blocks</h2>
 *
 * Strictly more than 24 hours since the last successful import makes the guide
 * [Stale]; at 24 hours exactly it is still [Fresh] (US-16, C1 D4). Neither
 * stops the grid or the channel: a stale guide is shown with its import date,
 * and a channel plays whatever its guide says.
 *
 * <h2>Where the age comes from</h2>
 *
 * It starts at `generated_at − last_successful_import_at` — two timestamps of the
 * **server's** clock, so a device whose clock is wrong by an hour does not add
 * that hour — and grows by `now − fetched_at` on the **device's** clock. An age
 * that comes out negative is a clock the server itself disagrees with; it is
 * clamped to zero and flagged [Fresh.unreliable], never hidden behind "up to
 * date" (D4: "ne pas masquer une incohérence d'horloge").
 *
 * <h2>An attempt outranks a date</h2>
 *
 * A guide whose last attempt is running, failed or was interrupted may hold a
 * mix of old and new programmes (D3: no atomic snapshot). So [AttemptRunning],
 * [AttemptFailed] and [AttemptInterrupted] come first, even when the last
 * success was a minute ago — the sentence is "import in progress" or "last
 * import incomplete", with the last success date beside it, and no claim of
 * freshness.
 */
sealed interface EpgFreshness {

    /** No guide URL on the source. Every list is empty by construction. */
    data object NotConfigured : EpgFreshness

    /**
     * Nothing to date it by: never fetched on this device, or configured but
     * no import has succeeded under the current configuration (also what a
     * migrated source reports: dates unknown, status `UNKNOWN`).
     */
    data object Unknown : EpgFreshness

    /**
     * @param age since the last successful import, as best it can be known.
     * @param unreliable the clocks disagreed and [age] was clamped, or the
     * window carried no provenance to progress from.
     */
    data class Fresh(val age: Duration, val unreliable: Boolean = false) : EpgFreshness

    /** Strictly older than [STALE_AFTER]. Programmes are kept and dated. */
    data class Stale(val age: Duration, val unreliable: Boolean = false) : EpgFreshness

    data class AttemptRunning(val lastSuccessfulImportAt: Instant?) : EpgFreshness

    data class AttemptFailed(val lastSuccessfulImportAt: Instant?) : EpgFreshness

    data class AttemptInterrupted(val lastSuccessfulImportAt: Instant?) : EpgFreshness

    companion object {

        /** US-16's threshold. The product's number, not a tuning constant. */
        val STALE_AFTER: Duration = Duration.ofHours(24)

        /**
         * @param status what the server said, or null when never fetched here.
         * @param generatedAt the server's clock on that answer.
         * @param fetchedAt the device's clock when it was stored.
         * @param now the device's clock.
         */
        fun of(
            status: EpgImportStatus?,
            generatedAt: Instant?,
            fetchedAt: Instant?,
            now: Instant,
        ): EpgFreshness {
            if (status == null) return Unknown
            if (!status.configured) return NotConfigured

            // Always an `else`: the enum is generated from the contract, and a
            // value added to it must degrade to "unknown", not fail to compile
            // on regeneration nor be filed silently (LumoError's rule).
            when (status.lastAttemptStatus) {
                EpgAttemptStatus.RUNNING -> return AttemptRunning(status.lastSuccessfulImportAt)
                EpgAttemptStatus.FAILED -> return AttemptFailed(status.lastSuccessfulImportAt)
                EpgAttemptStatus.INTERRUPTED -> return AttemptInterrupted(status.lastSuccessfulImportAt)
                EpgAttemptStatus.SUCCEEDED, EpgAttemptStatus.UNKNOWN -> Unit
                else -> Unit
            }

            val imported = status.lastSuccessfulImportAt ?: return Unknown

            var unreliable = false
            val age = if (generatedAt == null || fetchedAt == null) {
                // No provenance to progress from: the only clock left is the
                // device's, against a server date. Said as such.
                unreliable = true
                Duration.between(imported, now)
            } else {
                Duration.between(imported, generatedAt) + Duration.between(fetchedAt, now)
            }.let { computed ->
                if (computed.isNegative) {
                    unreliable = true
                    Duration.ZERO
                } else {
                    computed
                }
            }

            return if (age > STALE_AFTER) Stale(age, unreliable) else Fresh(age, unreliable)
        }
    }
}
