package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * The Guide's "En ce moment" list (US-16, S9-04-05).
 *
 * <h2>What this screen owns, and what it does not</h2>
 *
 * The grouped request itself — one per page, never per card, and the dropping
 * of an answer for a source that has changed — is `OnAirTracker`'s, held by
 * `OnAirTrackerTest` in `core:data`. What this screen owns is *which* ids form
 * a page ([epgPageIds]) and what it draws from the windows it holds against a
 * clock ([LiveState.nowAndNext]): the current programme *and* the next one, or
 * nothing at all when the guide has none.
 *
 * <h2>The half of the task that is silence</h2>
 *
 * A channel without `tvg_id`, a source without a guide and a guide that has not
 * loaded all arrive as an absent entry, and the row must then draw the channel's
 * name and no programme line — never "programme unavailable" (S7-03, S9-04).
 */
class GuideNowListTest {

    private val now = Instant.parse("2026-09-24T20:30:00Z")

    // ---- what the rows say -------------------------------------------------

    @Test
    fun `a channel shows its current programme and the one after it`() {
        val state = LiveState(
            guideProgrammes = mapOf(
                "c1" to listOf(
                    programme("p1", now.minusSeconds(1800), now.plusSeconds(900)),
                    programme("p2", now.plusSeconds(900), now.plusSeconds(4500)),
                ),
            ),
        )

        val onAir = state.nowAndNext("c1", now)

        assertThat(onAir.current?.id).isEqualTo("p1")
        assertThat(onAir.next?.id).isEqualTo("p2")
    }

    @Test
    fun `a channel with no guide draws no programme line`() {
        val onAir = LiveState().nowAndNext("c1", now)

        assertThat(onAir.current).isNull()
        assertThat(onAir.next).isNull()
    }

    @Test
    fun `a gap in the guide has a next programme and no current one`() {
        val state = LiveState(
            guideProgrammes = mapOf(
                "c1" to listOf(programme("p2", now.plusSeconds(600), now.plusSeconds(3600))),
            ),
        )

        val onAir = state.nowAndNext("c1", now)

        assertThat(onAir.current).isNull()
        assertThat(onAir.next?.id).isEqualTo("p2")
    }

    @Test
    fun `the last programme of the window has a current one and no next`() {
        val state = LiveState(
            guideProgrammes = mapOf(
                "c1" to listOf(programme("p1", now.minusSeconds(600), now.plusSeconds(600))),
            ),
        )

        val onAir = state.nowAndNext("c1", now)

        assertThat(onAir.current?.id).isEqualTo("p1")
        assertThat(onAir.next).isNull()
    }

    // ---- the page that becomes one request ---------------------------------

    @Test
    fun `a page of visible cards is one page of channel ids`() {
        val ids = epgPageIds(visible = listOf(0, 1, 2), itemCount = 100) { "c$it" }

        assertThat(ids).hasSize(EPG_PAGE_SIZE)
        assertThat(ids.first()).isEqualTo("c0")
        assertThat(ids.last()).isEqualTo("c${EPG_PAGE_SIZE - 1}")
    }

    @Test
    fun `a scroll within one page asks for no new page`() {
        val first = epgPageIds(visible = listOf(0, 1, 2), itemCount = 100) { "c$it" }
        val scrolled = epgPageIds(visible = listOf(1, 2, 3), itemCount = 100) { "c$it" }

        assertThat(scrolled).isEqualTo(first)
    }

    @Test
    fun `nothing visible asks for nothing`() {
        assertThat(epgPageIds(visible = emptyList(), itemCount = 100) { "c$it" }).isEmpty()
        assertThat(epgPageIds(visible = listOf(0), itemCount = 0) { "c$it" }).isEmpty()
    }

    @Test
    fun `the last page is clamped to the channels that exist`() {
        val ids = epgPageIds(visible = listOf(85), itemCount = 90) { "c$it" }

        assertThat(ids).hasSize(18)
        assertThat(ids.first()).isEqualTo("c72")
        assertThat(ids.last()).isEqualTo("c89")
    }

    private fun programme(id: String, start: Instant, end: Instant) = EpgProgramme(
        id = id,
        startsAt = start,
        endsAt = end,
        title = "Programme $id",
        description = null,
        category = null,
    )
}
