package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.model.EpgImportStatus
import tv.lumo.android.network.generated.model.EpgAttemptStatus

/**
 * The 24-hour rule and where the age comes from (US-16, C1 D4, S9-03).
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * **The boundary.** "Strictly more than 24 hours" is the product's sentence;
 * `>=` compiles just as well and turns a guide imported yesterday at this hour
 * into an old one, on every device, at the same minute.
 *
 * **Whose clock.** The starting age is two server timestamps, so a television
 * whose clock is an hour ahead cannot age the guide by an hour. A negative age —
 * the server's answer produced before its own last import — is a clock the
 * server disagrees with, and D4 forbids hiding it behind "up to date".
 *
 * **An attempt in progress outranks a recent success.** The cache may hold a
 * mix (D3, no snapshot), and "imported five minutes ago" over mixed rows is the
 * false freshness the rule exists to prevent.
 */
class EpgFreshnessTest {

    private val imported = Instant.parse("2026-09-23T20:00:00Z")

    @Test
    fun `at 24 hours exactly the guide is still fresh, one second later it is stale`() {
        // Fetched at the moment the server answered, so the age is all server
        // time and the device clock adds nothing.
        val generated = imported.plus(Duration.ofHours(24))

        val exactly = EpgFreshness.of(succeeded(), generated, fetchedAt = generated, now = generated)
        val over = EpgFreshness.of(succeeded(), generated, fetchedAt = generated, now = generated.plusSeconds(1))

        assertThat(exactly).isEqualTo(EpgFreshness.Fresh(Duration.ofHours(24)))
        assertThat(over).isEqualTo(EpgFreshness.Stale(Duration.ofHours(24).plusSeconds(1)))
    }

    @Test
    fun `the age progresses with the device clock from the server's own starting age`() {
        val generated = imported.plus(Duration.ofHours(2))
        // The device fetched it with a clock five hours off the server's:
        // irrelevant, only the time elapsed *since* the fetch counts.
        val fetched = Instant.parse("2026-09-24T03:00:00Z")

        val later = EpgFreshness.of(succeeded(), generated, fetched, now = fetched.plus(Duration.ofHours(3)))

        assertThat(later).isEqualTo(EpgFreshness.Fresh(Duration.ofHours(5)))
    }

    @Test
    fun `a server answer older than its own last import is a clock skew, said as such`() {
        // generated_at before last_successful_import_at: impossible on one
        // clock. Age zero, and flagged — never a silent "up to date".
        val generated = imported.minus(Duration.ofMinutes(10))

        val freshness = EpgFreshness.of(succeeded(), generated, fetchedAt = generated, now = generated)

        assertThat(freshness).isEqualTo(EpgFreshness.Fresh(Duration.ZERO, unreliable = true))
    }

    @Test
    fun `an attempt running, failed or interrupted outranks a recent success`() {
        val generated = imported.plusSeconds(60)

        assertThat(EpgFreshness.of(status(EpgAttemptStatus.RUNNING), generated, generated, generated))
            .isEqualTo(EpgFreshness.AttemptRunning(imported))
        assertThat(EpgFreshness.of(status(EpgAttemptStatus.FAILED), generated, generated, generated))
            .isEqualTo(EpgFreshness.AttemptFailed(imported))
        assertThat(EpgFreshness.of(status(EpgAttemptStatus.INTERRUPTED), generated, generated, generated))
            .isEqualTo(EpgFreshness.AttemptInterrupted(imported))
    }

    @Test
    fun `no configuration, no fetch and no success are three different unknowns`() {
        val now = imported

        assertThat(EpgFreshness.of(status(configured = false), now, now, now))
            .isEqualTo(EpgFreshness.NotConfigured)
        assertThat(EpgFreshness.of(null, null, null, now))
            .isEqualTo(EpgFreshness.Unknown)
        // Migrated on the server: status UNKNOWN, no date. Nothing to age.
        assertThat(EpgFreshness.of(status(EpgAttemptStatus.UNKNOWN, imported = null), now, now, now))
            .isEqualTo(EpgFreshness.Unknown)
    }

    @Test
    fun `a status without provenance still ages, and says the age is unreliable`() {
        // The cache row of a build before this one, or a fetch whose clock was
        // not recorded: the only clock left is the device's.
        val freshness = EpgFreshness.of(succeeded(), null, null, now = imported.plus(Duration.ofHours(1)))

        assertThat(freshness).isEqualTo(EpgFreshness.Fresh(Duration.ofHours(1), unreliable = true))
    }

    private fun succeeded() = status(EpgAttemptStatus.SUCCEEDED)

    private fun status(
        attempt: EpgAttemptStatus = EpgAttemptStatus.SUCCEEDED,
        configured: Boolean = true,
        imported: Instant? = this.imported,
    ) = EpgImportStatus(
        configured = configured,
        lastSuccessfulImportAt = imported,
        lastAttemptStartedAt = null,
        lastAttemptFinishedAt = null,
        lastAttemptStatus = attempt,
    )
}
