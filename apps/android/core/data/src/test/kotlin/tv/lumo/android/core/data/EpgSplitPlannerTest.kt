package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.internal.EpgSplitPlanner
import tv.lumo.android.core.data.internal.EpgSplitPlanner.Request

/**
 * How a `422 EPG_WINDOW_TOO_LARGE` is cut down (C1 D2, C1-11).
 *
 * The plan is pure — what to ask next, never asking — so the two things that
 * matter can be read off: **channels before the window**, and **an end**. A
 * planner that halved for ever would, on a single programme larger than the
 * ceiling, make requests until somebody pulled the plug.
 */
class EpgSplitPlannerTest {

    private val from = Instant.parse("2026-09-24T18:00:00Z")
    private val to = from.plus(Duration.ofHours(4))

    @Test
    fun `channels are halved first, and the window is left alone`() {
        val parts = EpgSplitPlanner.split(Request(listOf("a", "b", "c", "d", "e"), from, to))

        assertThat(parts).containsExactly(
            Request(listOf("a", "b", "c"), from, to, level = 1),
            Request(listOf("d", "e"), from, to, level = 1),
        ).inOrder()
    }

    @Test
    fun `a single channel is split by halving its window`() {
        val parts = EpgSplitPlanner.split(Request(listOf("a"), from, to, level = 1))

        val middle = from.plus(Duration.ofHours(2))
        assertThat(parts).containsExactly(
            Request(listOf("a"), from, middle, level = 2),
            Request(listOf("a"), middle, to, level = 2),
        ).inOrder()
    }

    @Test
    fun `three levels, then the plan is exhausted`() {
        var requests = listOf(Request((1..8).map { "c$it" }, from, to))
        var levels = 0

        while (true) {
            val next = requests.flatMap { EpgSplitPlanner.split(it) ?: return@flatMap emptyList() }
            if (next.isEmpty()) break
            requests = next
            levels++
        }

        assertThat(levels).isEqualTo(EpgSplitPlanner.MAX_LEVELS)
        // Eight channels, three halvings: one channel each, the window intact.
        assertThat(requests).hasSize(8)
        assertThat(requests.map { it.channelIds.single() }).containsExactly(
            "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8",
        ).inOrder()
        assertThat(requests.all { it.from == from && it.to == to }).isTrue()
        assertThat(EpgSplitPlanner.split(requests.first())).isNull()
    }

    @Test
    fun `a window too short to halve ends the plan early`() {
        // One nanosecond: the smallest window an Instant can name, and one that
        // cannot be cut. A plan must end here rather than loop on equal bounds.
        val tiny = Request(listOf("a"), from, from.plusNanos(1), level = 0)

        assertThat(EpgSplitPlanner.split(tiny)).isNull()
    }
}
