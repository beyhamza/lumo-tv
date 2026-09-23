package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * "En ce moment" and "Ensuite" from a list and a clock (US-16, S9-03).
 *
 * Pure, so the cases that go wrong on a real evening can be written down: the
 * gap between two programmes, a window with only the past or only the future
 * in it, the instant a programme ends, and midnight — which is nothing to an
 * instant and everything to a local time.
 *
 * Fictional programmes throughout (AGENTS.md §1).
 */
class EpgNowTest {

    private val p1 = programme("p1", "2026-09-24T20:00:00Z", "2026-09-24T21:00:00Z")
    private val p2 = programme("p2", "2026-09-24T21:00:00Z", "2026-09-24T22:30:00Z")
    private val p3 = programme("p3", "2026-09-24T23:00:00Z", "2026-09-25T00:30:00Z")

    @Test
    fun `during a programme it is current and the following one is next`() {
        val result = currentAndNext(listOf(p1, p2, p3), at("2026-09-24T20:30:00Z"))

        assertThat(result).isEqualTo(NowAndNext(current = p1, next = p2))
    }

    @Test
    fun `a programme's interval includes its start and excludes its end`() {
        // At 21:00:00 exactly the 20:00 programme is over and the 21:00 one is on.
        val result = currentAndNext(listOf(p1, p2, p3), at("2026-09-24T21:00:00Z"))

        assertThat(result.current).isEqualTo(p2)
        assertThat(result.next).isEqualTo(p3)
    }

    @Test
    fun `in a gap nothing is current and the next programme is what follows the gap`() {
        // 22:30–23:00 has nothing in it. The bar then shows "next" alone, and
        // never invents a "current" from the gap's neighbours.
        val result = currentAndNext(listOf(p1, p2, p3), at("2026-09-24T22:45:00Z"))

        assertThat(result).isEqualTo(NowAndNext(current = null, next = p3))
    }

    @Test
    fun `a window of the past only yields nothing at all`() {
        val result = currentAndNext(listOf(p1, p2), at("2026-09-25T06:00:00Z"))

        assertThat(result).isEqualTo(NowAndNext(current = null, next = null))
    }

    @Test
    fun `a window of the future only yields a next and no current`() {
        val result = currentAndNext(listOf(p2, p3), at("2026-09-24T19:00:00Z"))

        assertThat(result).isEqualTo(NowAndNext(current = null, next = p2))
    }

    @Test
    fun `a programme crossing midnight is current on both sides of it`() {
        // 23:00 to 00:30 in UTC; the same test holds in any zone, because the
        // comparison is between instants and a date change is not an event.
        val before = currentAndNext(listOf(p2, p3), at("2026-09-24T23:59:59Z"))
        val after = currentAndNext(listOf(p2, p3), at("2026-09-25T00:00:01Z"))

        assertThat(before.current).isEqualTo(p3)
        assertThat(after.current).isEqualTo(p3)
        assertThat(after.next).isNull()
    }

    @Test
    fun `an empty list is nothing, not an error`() {
        assertThat(currentAndNext(emptyList(), at("2026-09-24T20:30:00Z")))
            .isEqualTo(NowAndNext(null, null))
    }

    private fun at(text: String): Instant = Instant.parse(text)

    private fun programme(id: String, start: String, end: String) = EpgProgramme(
        id = id,
        startsAt = Instant.parse(start),
        endsAt = Instant.parse(end),
        title = "Programme $id",
        description = null,
        category = null,
    )
}
