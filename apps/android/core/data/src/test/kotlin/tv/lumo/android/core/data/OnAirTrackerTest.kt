package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.data.model.Cached
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.EpgChannelWindow
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.data.model.EpgWindow
import tv.lumo.android.core.data.repository.EpgRepository

/**
 * One guide request per page, never per card (US-16, S7-03 and S7-04 by S9-03).
 *
 * The three screens of S9-03 — the player's bar, the television's grid, the
 * home screen's Live cards — count their requests through this one class, so
 * this is where the count is held: a page costs one request, a page shown
 * again costs none, a new page costs one for its new channels only. And what
 * a card with no guide gets: nothing, not a placeholder.
 *
 * The repository is a fake that records what it was asked, because the count
 * *is* the subject; `EpgRepositoryTest` holds what a request does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnAirTrackerTest {

    private val now = Instant.parse("2026-09-24T20:30:00Z")
    private val clock = MovableClock(now)
    private val epg = RecordingEpg()

    @Test
    fun `a page of channels is one request, and the same page again is none`() = runTest {
        val tracker = OnAirTracker(epg, clock, backgroundScope)
        val page = (1..8).map { "c$it" }

        tracker.show("source-a", page)
        runCurrent()
        tracker.show("source-a", page)
        runCurrent()

        assertThat(epg.requests).hasSize(1)
        assertThat(epg.requests.single().channelIds).isEqualTo(page)
        assertThat(epg.requests.single().from).isEqualTo(now)
        assertThat(epg.requests.single().to).isEqualTo(now.plus(Duration.ofHours(3)))
    }

    @Test
    fun `the next page asks for its new channels only`() = runTest {
        val tracker = OnAirTracker(epg, clock, backgroundScope)
        val first = (1..8).map { "c$it" }
        val second = (5..12).map { "c$it" }

        tracker.show("source-a", first)
        runCurrent()
        tracker.show("source-a", second)
        runCurrent()

        assertThat(epg.requests).hasSize(2)
        assertThat(epg.requests[1].channelIds).containsExactly("c9", "c10", "c11", "c12").inOrder()
    }

    @Test
    fun `what is on air is present for a channel with a programme, and absent for one without`() = runTest {
        epg.programmes["c1"] = listOf(programme("p1", now.minusSeconds(600), now.plusSeconds(600)))
        epg.programmes["c2"] = emptyList()
        val tracker = OnAirTracker(epg, clock, backgroundScope)

        tracker.show("source-a", listOf("c1", "c2"))
        runCurrent()

        assertThat(tracker.onAir.value.keys).containsExactly("c1")
        assertThat(tracker.onAir.value.getValue("c1").title).isEqualTo("Programme p1")
        // The window is held for a screen that wants "next" as well.
        assertThat(tracker.programmes.value.getValue("c1").map { it.id }).containsExactly("p1")
        assertThat(tracker.programmes.value.getValue("c2")).isEmpty()
    }

    @Test
    fun `a tick moves what is on air with the clock, without a request`() = runTest {
        epg.programmes["c1"] = listOf(
            programme("p1", now.minusSeconds(600), now.plusSeconds(600)),
            programme("p2", now.plusSeconds(600), now.plusSeconds(3600)),
        )
        val tracker = OnAirTracker(epg, clock, backgroundScope)
        tracker.show("source-a", listOf("c1"))
        runCurrent()
        assertThat(tracker.onAir.value.getValue("c1").id).isEqualTo("p1")

        clock.now = now.plusSeconds(1200)
        tracker.tick()

        assertThat(tracker.onAir.value.getValue("c1").id).isEqualTo("p2")
        assertThat(epg.requests).hasSize(1)
    }

    @Test
    fun `another source drops everything and asks again`() = runTest {
        epg.programmes["c1"] = listOf(programme("p1", now.minusSeconds(600), now.plusSeconds(600)))
        val tracker = OnAirTracker(epg, clock, backgroundScope)
        tracker.show("source-a", listOf("c1"))
        runCurrent()

        tracker.show("source-b", listOf("c1"))
        runCurrent()

        assertThat(epg.requests.map { it.sourceId }).containsExactly("source-a", "source-b").inOrder()
    }

    @Test
    fun `a window about to end is replaced on the next page shown`() = runTest {
        val tracker = OnAirTracker(epg, clock, backgroundScope)
        tracker.show("source-a", listOf("c1"))
        runCurrent()

        // Fifteen minutes before the window's end: a television left on the
        // grid all evening would otherwise run out of guide.
        clock.now = now.plus(Duration.ofHours(3)).minus(Duration.ofMinutes(14))
        tracker.show("source-a", listOf("c1"))
        runCurrent()

        assertThat(epg.requests).hasSize(2)
        assertThat(epg.requests[1].from).isEqualTo(clock.now)
    }

    @Test
    fun `the cache emission is drawn before the server's answer replaces it`() = runTest {
        epg.cached["c1"] = listOf(programme("p-cached", now.minusSeconds(60), now.plusSeconds(60)))
        epg.programmes["c1"] = listOf(programme("p-fresh", now.minusSeconds(60), now.plusSeconds(60)))
        val seen = mutableListOf<String?>()
        val tracker = OnAirTracker(epg, clock, backgroundScope)
        epg.onEmit = { seen += tracker.onAir.value["c1"]?.id }

        tracker.show("source-a", listOf("c1"))
        runCurrent()

        // Recorded *before* each emission is applied: nothing, then the cache.
        assertThat(seen).containsExactly(null, "p-cached").inOrder()
        assertThat(tracker.onAir.value.getValue("c1").id).isEqualTo("p-fresh")
    }

    // ---- doubles -----------------------------------------------------------

    private fun programme(id: String, start: Instant, end: Instant) = EpgProgramme(
        id = id,
        startsAt = start,
        endsAt = end,
        title = "Programme $id",
        description = null,
        category = null,
    )

    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private class Request(val sourceId: String, val channelIds: List<String>, val from: Instant, val to: Instant)

    /** Answers with what [programmes] holds per channel; the cache emission with [cached]. */
    private class RecordingEpg : EpgRepository {
        val requests = mutableListOf<Request>()
        val programmes = mutableMapOf<String, List<EpgProgramme>>()
        val cached = mutableMapOf<String, List<EpgProgramme>>()
        var onEmit: () -> Unit = {}

        override fun window(
            sourceId: String,
            channelIds: List<String>,
            from: Instant,
            to: Instant,
        ): Flow<Cached<EpgWindow>> = flow {
            requests += Request(sourceId, channelIds, from, to)
            onEmit()
            emit(Cached(window(sourceId, channelIds, from, to, cached), DataOrigin.Cache))
            onEmit()
            emit(Cached(window(sourceId, channelIds, from, to, programmes), DataOrigin.Network))
        }

        private fun window(
            sourceId: String,
            channelIds: List<String>,
            from: Instant,
            to: Instant,
            source: Map<String, List<EpgProgramme>>,
        ) = EpgWindow(
            sourceId = sourceId,
            from = from,
            to = to,
            fetchedAt = null,
            generatedAt = null,
            importStatus = null,
            channels = channelIds.map { EpgChannelWindow(it, null, source[it].orEmpty()) },
        )

        override suspend fun forget(sourceId: String) = Unit
    }
}
