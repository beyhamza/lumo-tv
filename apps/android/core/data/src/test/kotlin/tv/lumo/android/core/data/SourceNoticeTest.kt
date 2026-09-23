package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import org.junit.Test
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.SyncStep

/**
 * What four screens say about the source over their content (US-024, lot C4).
 *
 * The home screen and the three grids share this mapping. What is pinned is what
 * a connected account with one healthy source never shows: the real step of a
 * refresh, the failure in the words of its code, and whether the catalogue on
 * screen is an old one.
 */
class SourceNoticeTest {

    private val a = benchSource("Source A")
    private val synced = OffsetDateTime.parse("2026-09-01T10:00:00Z")

    @Test
    fun `a ready source says nothing`() {
        assertThat(a.notice()).isNull()
        assertThat(ActiveSourceState.Selected(a.key, a, listOf(a)).notice()).isNull()
    }

    @Test
    fun `a choice remembered offline says the server was not reached, and nothing about the source`() {
        // US-024, "Indisponibilité et hors ligne" (S8-06). Nothing is known about
        // the source, so nothing is said about it; what is said is that the list
        // on screen is the device's own copy and may be old.
        assertThat(ActiveSourceState.Selected(a.key, source = null, sources = emptyList()).notice())
            .isEqualTo(SourceNotice.Unreached)
        // No choice at all is a face of the screen, not a notice over it.
        assertThat(ActiveSourceState.Unavailable.notice()).isNull()
    }

    @Test
    fun `an outage is the only notice with a second control, and it is not an error`() {
        val outage = SourceNotice.Unreached.wording()

        assertThat(outage.retry).isEqualTo(R.string.core_data_catalogue_retry)
        assertThat(outage.action).isEqualTo(R.string.core_data_notice_change_source)
        // Nothing is wrong with the catalogue but its age: not in red.
        assertThat(outage.failed).isFalse()
        // ...and still a stop on a television, unlike a refresh in progress.
        assertThat(outage.actionable).isTrue()

        val refreshing = SourceNotice.Refreshing(SyncStep.CONNECTING, hasCatalogue = true).wording()
        assertThat(refreshing.retry).isNull()
        assertThat(refreshing.actionable).isFalse()
        assertThat(SourceNotice.Failed(null, hasCatalogue = true).wording().actionable).isTrue()
    }

    @Test
    fun `a refresh shows its real step, pending included`() {
        val parsing = a.copy(
            status = SourceStatus.SYNCING,
            syncStep = SyncStep.PARSING_VOD,
            lastSyncedAt = synced,
        )
        val accepted = a.copy(status = SourceStatus.PENDING, syncStep = null)

        assertThat(parsing.notice())
            .isEqualTo(SourceNotice.Refreshing(SyncStep.PARSING_VOD, hasCatalogue = true))
        assertThat(accepted.notice())
            .isEqualTo(SourceNotice.Refreshing(step = null, hasCatalogue = false))
    }

    @Test
    fun `a failure carries its code, and says whether an older catalogue is on screen`() {
        val refused = a.copy(
            status = SourceStatus.ERROR,
            errorCode = IngestionErrorCode.SOURCE_AUTH_FAILED,
        )

        assertThat(refused.notice())
            .isEqualTo(SourceNotice.Failed(IngestionErrorCode.SOURCE_AUTH_FAILED, hasCatalogue = false))
        assertThat(refused.copy(lastSyncedAt = synced).notice())
            .isEqualTo(SourceNotice.Failed(IngestionErrorCode.SOURCE_AUTH_FAILED, hasCatalogue = true))
    }

    @Test
    fun `only a failure with a previous catalogue says it may be out of date`() {
        val stale = SourceNotice.Failed(IngestionErrorCode.SOURCE_UNREACHABLE, hasCatalogue = true)
        val first = SourceNotice.Failed(IngestionErrorCode.SOURCE_UNREACHABLE, hasCatalogue = false)

        assertThat(stale.wording().hint).isEqualTo(R.string.core_data_notice_failed_stale)
        assertThat(first.wording().hint).isNull()
        assertThat(stale.wording().message).isEqualTo(R.string.core_data_ingestion_unreachable)
        assertThat(stale.wording().failed).isTrue()
    }

    @Test
    fun `a refresh is worded with its step, never with a percentage`() {
        val wording = SourceNotice.Refreshing(SyncStep.FETCHING_EPG, hasCatalogue = true).wording()

        assertThat(wording.message).isEqualTo(R.string.core_data_sync_step_epg)
        // Browsing goes on, playback waits: said only when there is a catalogue.
        assertThat(wording.hint).isEqualTo(R.string.core_data_notice_refreshing_hint)
        assertThat(SourceNotice.Refreshing(null).wording().hint)
            .isEqualTo(R.string.core_data_notice_importing_hint)
        assertThat(wording.failed).isFalse()
    }
}
