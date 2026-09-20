package tv.lumo.android.feature.source

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.SyncOutcome
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.SyncStep

/**
 * "My sources", as values (US-024).
 *
 * The screen's rules are pure functions of `MySourcesState`, so they are held
 * here without a coroutine or a server: which number is known, which source
 * carries the mark, what each answer to *Refresh* turns into, and that a switch
 * moved on one source moves nothing on another. Deleting — the order, the caches,
 * what is browsed next — is `SourceRemoverTest`'s, in `core:data`, where it lives.
 */
class MySourcesStateTest {

    private val synced = OffsetDateTime.parse("2026-09-01T10:00:00Z")

    private val panel = source("Source A", SourceKind.XTREAM, channels = 1_248, lastSyncedAt = synced)
    private val playlist = source("Source B", SourceKind.M3U_URL, channels = 40, lastSyncedAt = synced)

    // ---- the list ------------------------------------------------------------

    @Test
    fun `an unknown count is absent, never zero`() {
        // Channels not counted yet (never ingested), films and series not asked.
        val fresh = source("Source C", SourceKind.XTREAM, channels = null, lastSyncedAt = null)

        val row = sourceRowsOf(listOf(fresh), activeId = null).single()

        assertThat(row.channels).isNull()
        assertThat(row.films).isNull()
        assertThat(row.series).isNull()
    }

    @Test
    fun `known counts are shown, a real zero included`() {
        val rows = sourceRowsOf(
            sources = listOf(panel),
            activeId = null,
            counts = mapOf(panel.key to ContentCounts(films = 312, series = 0)),
        )

        assertThat(rows.single().channels).isEqualTo(1_248)
        assertThat(rows.single().films).isEqualTo(312)
        // The server listed none: that is a number, and it is the server's.
        assertThat(rows.single().series).isEqualTo(0)
    }

    @Test
    fun `a playlist never shows a series count, whatever was counted`() {
        val rows = sourceRowsOf(
            sources = listOf(playlist),
            activeId = null,
            // Even if something had put a number there: the format cannot carry
            // series (adr/0010), and "0 series" would blame the subscription.
            counts = mapOf(playlist.key to ContentCounts(films = 3, series = 0)),
        )

        assertThat(rows.single().showsSeries).isFalse()
        assertThat(rows.single().series).isNull()
        assertThat(rows.single().films).isEqualTo(3)
    }

    @Test
    fun `the active source carries the mark, and only it`() {
        val rows = sourceRowsOf(listOf(panel, playlist), activeId = playlist.key)

        assertThat(rows.map { it.active }).containsExactly(false, true).inOrder()
        // Several sources and none chosen yet: nobody is marked.
        assertThat(sourceRowsOf(listOf(panel, playlist), activeId = null).none { it.active }).isTrue()
    }

    @Test
    fun `a refresh shows its real step, a failure its reason, its age and its way out`() {
        val failedAt = OffsetDateTime.parse("2026-09-19T08:00:00Z")
        val refreshing = panel.copy(status = SourceStatus.SYNCING, syncStep = SyncStep.PARSING_VOD)
        val refused = panel.copy(
            status = SourceStatus.ERROR,
            errorCode = IngestionErrorCode.SOURCE_AUTH_FAILED,
            lastErrorAt = failedAt,
        )
        val down = panel.copy(status = SourceStatus.ERROR, errorCode = IngestionErrorCode.SOURCE_UNREACHABLE)

        assertThat(sourceRowsOf(listOf(refreshing), null).single().state)
            .isEqualTo(SourceRowState.Refreshing(SyncStep.PARSING_VOD))
        assertThat(sourceRowsOf(listOf(refused), null).single().state).isEqualTo(
            SourceRowState.Failed(IngestionErrorCode.SOURCE_AUTH_FAILED, failedAt, SourceExit.FixCredentials),
        )
        // A failed import keeps the source, and offers to try again — not a new add.
        assertThat((sourceRowsOf(listOf(down), null).single().state as SourceRowState.Failed).exit)
            .isEqualTo(SourceExit.Retry)
        // The last *successful* synchronisation survives the failure.
        assertThat(sourceRowsOf(listOf(refused), null).single().lastSyncedAt).isEqualTo(synced)
    }

    @Test
    fun `the list follows the account, and unknown is not empty`() {
        val listed = MySourcesState().following(
            ActiveSourceState.Selected(panel.key, panel, listOf(panel, playlist)),
        )
        assertThat(listed.phase).isEqualTo(MySourcesPhase.Listed)
        assertThat(listed.rows.map { it.label }).containsExactly("Source A", "Source B").inOrder()

        assertThat(listed.following(ActiveSourceState.None).phase).isEqualTo(MySourcesPhase.Empty)
        // An unreachable server proves nothing: not the add flow, a retry.
        assertThat(MySourcesState().following(ActiveSourceState.Unavailable).phase)
            .isEqualTo(MySourcesPhase.Unavailable)
        assertThat(
            MySourcesState().following(ActiveSourceState.Selected(panel.key, null, emptyList())).phase,
        ).isEqualTo(MySourcesPhase.Unavailable)
        // Several and none chosen: listed, nobody marked.
        assertThat(
            MySourcesState().following(ActiveSourceState.NeedsChoice(listOf(panel, playlist))).activeId,
        ).isNull()
    }

    @Test
    fun `what belonged to a source that is gone goes with it`() {
        val before = MySourcesState()
            .following(ActiveSourceState.Selected(panel.key, panel, listOf(panel, playlist)))
            .copy(
                counts = mapOf(playlist.key to ContentCounts(films = 3)),
                refreshes = mapOf(playlist.key to RefreshControl.Wait(3)),
            )

        val after = before.following(ActiveSourceState.Selected(panel.key, panel, listOf(panel)))

        assertThat(after.counts).isEmpty()
        assertThat(after.refreshes).isEmpty()
    }

    // ---- refresh -------------------------------------------------------------

    @Test
    fun `an accepted refresh shows the source refreshing at once, and disables the control`() {
        val pending = panel.copy(status = SourceStatus.PENDING)
        val state = listed().afterSync(panel.key, SyncOutcome.Accepted(pending))

        val row = state.rows.first { it.id == panel.key }
        assertThat(row.state).isEqualTo(SourceRowState.Refreshing(null))
        assertThat(row.refreshing).isTrue()
        assertThat(row.refresh).isEqualTo(RefreshControl.Idle)
    }

    @Test
    fun `a refresh already running is not an error`() {
        val state = listed().afterSync(panel.key, SyncOutcome.AlreadyRunning)

        assertThat(state.rows.first { it.id == panel.key }.refresh).isEqualTo(RefreshControl.Idle)
    }

    @Test
    fun `a rate limit is a wait, with the server's delay in minutes`() {
        val state = listed().afterSync(panel.key, SyncOutcome.RateLimited(retryAfterSeconds = 170))

        assertThat(state.rows.first { it.id == panel.key }.refresh).isEqualTo(RefreshControl.Wait(3))
        // The other source was not told to wait.
        assertThat(state.rows.first { it.id == playlist.key }.refresh).isEqualTo(RefreshControl.Idle)
    }

    @Test
    fun `a rate limit without a header has no number, and none is invented`() {
        val state = listed().afterSync(panel.key, SyncOutcome.RateLimited(retryAfterSeconds = null))

        assertThat(state.rows.first { it.id == panel.key }.refresh).isEqualTo(RefreshControl.Wait(null))
    }

    @Test
    fun `a refresh that could not be asked says whether the network was the reason`() {
        val offline = SyncOutcome.Failed(LumoError.Offline(IOException("no route")))
        val refused = SyncOutcome.Failed(LumoError.Api(ErrorCode.INTERNAL_ERROR, detail = null))

        assertThat(listed().afterSync(panel.key, offline).rows.first().refresh)
            .isEqualTo(RefreshControl.Failed(offline = true))
        assertThat(listed().afterSync(panel.key, refused).rows.first().refresh)
            .isEqualTo(RefreshControl.Failed(offline = false))
    }

    @Test
    fun `the control reads refreshing while the request is in flight`() {
        val asking = listed().copy(refreshes = mapOf(panel.key to RefreshControl.Requesting))

        assertThat(asking.rows.first { it.id == panel.key }.refreshing).isTrue()
        assertThat(asking.rows.first { it.id == playlist.key }.refreshing).isFalse()
    }

    // ---- automatic refresh ---------------------------------------------------

    @Test
    fun `a switch moves its own source, at once, and no other`() {
        val state = listed().togglingAutoSync(panel.key, enabled = false)

        val rows = state.rows.associateBy { it.id }
        assertThat(rows.getValue(panel.key).autoSync).isFalse()
        assertThat(rows.getValue(panel.key).autoSyncPending).isTrue()
        assertThat(rows.getValue(playlist.key).autoSync).isTrue()
        assertThat(rows.getValue(playlist.key).autoSyncPending).isFalse()
    }

    @Test
    fun `a saved switch takes the server's value`() {
        val state = listed()
            .togglingAutoSync(panel.key, enabled = false)
            .autoSyncSettled(panel.key, updated = panel.copy(autoSync = false))

        val row = state.rows.first { it.id == panel.key }
        assertThat(row.autoSync).isFalse()
        assertThat(row.autoSyncPending).isFalse()
        assertThat(state.autoSyncFailed).isEmpty()
    }

    @Test
    fun `a refused switch goes back to where the server has it, and says so`() {
        val state = listed()
            .togglingAutoSync(panel.key, enabled = false)
            .autoSyncSettled(panel.key, updated = null)

        assertThat(state.rows.first { it.id == panel.key }.autoSync).isTrue()
        assertThat(state.autoSyncFailed).containsExactly(panel.key)
        // Trying again clears the sentence.
        assertThat(state.togglingAutoSync(panel.key, enabled = false).autoSyncFailed).isEmpty()
    }

    // ---- counts --------------------------------------------------------------

    @Test
    fun `totals are asked once per successful synchronisation`() {
        val never = source("Source C", SourceKind.XTREAM, channels = null, lastSyncedAt = null)

        // Never ingested: nothing to count, and the listing would answer 409.
        assertThat(sourcesToCount(listOf(never, panel), countedAt = emptyMap())).containsExactly(panel)
        // Counted for the synchronisation it still shows: not asked again at each poll.
        assertThat(sourcesToCount(listOf(panel), countedAt = mapOf(panel.key to synced))).isEmpty()
        // A refresh succeeded: the numbers on screen have to move.
        val refreshed = panel.copy(lastSyncedAt = synced.plusHours(1))
        assertThat(sourcesToCount(listOf(refreshed), countedAt = mapOf(panel.key to synced)))
            .containsExactly(refreshed)
    }

    // ---- dialogs -------------------------------------------------------------

    @Test
    fun `a name is saved only when it is one, and a new one`() {
        val dialog = SourceDialog.Rename(panel.key, currentLabel = "Source A", value = "Source A")

        assertThat(dialog.canSave).isFalse()
        assertThat(dialog.copy(value = "   ").canSave).isFalse()
        assertThat(dialog.copy(value = " Source A ").canSave).isFalse()
        assertThat(dialog.copy(value = "Salon").canSave).isTrue()
        assertThat(dialog.copy(value = "Salon", saving = true).canSave).isFalse()
    }

    // ---- helpers -----------------------------------------------------------

    private fun listed() = MySourcesState().following(
        ActiveSourceState.Selected(panel.key, panel, listOf(panel, playlist)),
    )

    private val Source.key: String get() = id.toString()

    /** A bench source. Nothing here names a real provider (AGENTS.md §1). */
    private fun source(
        label: String,
        kind: SourceKind,
        channels: Int?,
        lastSyncedAt: OffsetDateTime?,
    ) = Source(
        id = UUID.nameUUIDFromBytes(label.toByteArray()),
        label = label,
        kind = kind,
        status = SourceStatus.READY,
        autoSync = true,
        channelCount = channels,
        lastSyncedAt = lastSyncedAt,
    )
}
