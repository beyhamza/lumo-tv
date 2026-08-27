package tv.lumo.android.feature.source

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.SyncStep

/**
 * What the server says a source is, turned into what the screen draws (S2-09).
 *
 * The part worth testing is not the polling loop — it is eight lines and a delay
 * — but the decision it feeds: which of the failures is which, and **what each
 * one offers as a way out**. S2-09 asks for four distinct messages *and* four
 * distinct exits, and the second half is the one that quietly rots: a message
 * with the wrong button cannot be acted on, and nothing crashes to say so.
 */
class SourceViewTest {

    @Test
    fun `accepted and running are one waiting state, with the phase the server names`() {
        // PENDING and SYNCING are the server's distinction — accepted versus
        // started — and not one a user can act on. Two different waiting screens
        // for it would be describing our implementation rather than their import.
        assertThat(viewOf(source(SourceStatus.PENDING)))
            .isEqualTo(SourceView.Importing(null))

        assertThat(viewOf(source(SourceStatus.SYNCING, step = SyncStep.PARSING_CHANNELS)))
            .isEqualTo(SourceView.Importing(SyncStep.PARSING_CHANNELS))
    }

    @Test
    fun `a ready source is counted, because ready alone says nothing`() {
        // US-06 and US-07 both ask for the number of channels found, and
        // SOURCE_EMPTY is the reason: a success screen that says only "ready" is
        // indistinguishable from one that imported nothing.
        val ready = viewOf(
            source(SourceStatus.READY, channels = 1_842, categories = 37),
        ) as SourceView.Ready

        assertThat(ready.channels).isEqualTo(1_842)
        assertThat(ready.categories).isEqualTo(37)
    }

    @Test
    fun `an expiry and a stream limit appear only when the panel gave them`() {
        val bare = viewOf(source(SourceStatus.READY)) as SourceView.Ready

        // Absent is not zero. A line that is not drawn beats a line saying "0",
        // which would read as a subscription allowing no streams at all.
        assertThat(bare.expiresAt).isNull()
        assertThat(bare.maxConnections).isNull()

        val xtream = viewOf(
            source(
                SourceStatus.READY,
                expiresAt = OffsetDateTime.parse("2027-03-01T00:00:00Z"),
                maxConnections = 2,
            ),
        ) as SourceView.Ready

        assertThat(xtream.expiresAt).isEqualTo("2027-03-01")
        assertThat(xtream.maxConnections).isEqualTo(2)
    }

    @Test
    fun `refused credentials send the user back to the form, not to a retry`() {
        // Offering "try again" to somebody whose password is wrong makes them
        // press it until they give up. US-06 asks for the form back, with the
        // host still in it.
        val failed = viewOf(
            source(SourceStatus.ERROR, error = IngestionErrorCode.SOURCE_AUTH_FAILED),
        ) as SourceView.Failed

        assertThat(failed.exit).isEqualTo(SourceExit.FixCredentials)
    }

    @Test
    fun `an unreachable server offers a retry, and is not a refusal`() {
        // The other half of the same rule: what the user typed may be perfectly
        // correct, so sending them back to edit it is the wrong instruction.
        val failed = viewOf(
            source(SourceStatus.ERROR, error = IngestionErrorCode.SOURCE_UNREACHABLE),
        ) as SourceView.Failed

        assertThat(failed.exit).isEqualTo(SourceExit.Retry)
    }

    @Test
    fun `a wrong address is corrected, not retried`() {
        val failed = viewOf(
            source(SourceStatus.ERROR, error = IngestionErrorCode.SOURCE_INVALID_FORMAT),
        ) as SourceView.Failed

        assertThat(failed.exit).isEqualTo(SourceExit.FixAddress)
    }

    @Test
    fun `an empty playlist is a failure with no button, and never a success`() {
        // US-07 states it: SOURCE_EMPTY rather than a success screen over an
        // empty list. And there is nothing to press — the playlist was read
        // correctly and holds nothing, which retrying will not change.
        val view = viewOf(source(SourceStatus.ERROR, error = IngestionErrorCode.SOURCE_EMPTY))

        assertThat(view).isInstanceOf(SourceView.Failed::class.java)
        assertThat((view as SourceView.Failed).exit).isEqualTo(SourceExit.None)
    }

    @Test
    fun `nothing this screen can do is offered as though it could`() {
        listOf(
            IngestionErrorCode.SOURCE_EXPIRED,
            IngestionErrorCode.SOURCE_TOO_LARGE,
            IngestionErrorCode.SOURCE_EMPTY,
        ).forEach { code ->
            val failed = viewOf(source(SourceStatus.ERROR, error = code)) as SourceView.Failed
            assertThat(failed.exit).isEqualTo(SourceExit.None)
        }
    }

    @Test
    fun `every failure the contract knows lands on a real exit`() {
        // By enumeration. A code added to the contract arrives here with the
        // cautious answer rather than an action that cannot help — and this is
        // what proves the `else` branch is deliberate rather than forgotten.
        IngestionErrorCode.entries.forEach { code ->
            val failed = viewOf(source(SourceStatus.ERROR, error = code)) as SourceView.Failed
            assertThat(failed.reason).isEqualTo(code)
            assertThat(SourceExit.entries).contains(failed.exit)
        }
    }

    private fun source(
        status: SourceStatus,
        step: SyncStep? = null,
        error: IngestionErrorCode? = null,
        channels: Int? = null,
        categories: Int? = null,
        expiresAt: OffsetDateTime? = null,
        maxConnections: Int? = null,
    ) = Source(
        id = UUID.randomUUID(),
        label = "Test source",
        kind = SourceKind.M3U_URL,
        status = status,
        autoSync = true,
        syncStep = step,
        errorCode = error,
        channelCount = channels,
        categoryCount = categories,
        expiresAt = expiresAt,
        maxConnections = maxConnections,
    )
}
