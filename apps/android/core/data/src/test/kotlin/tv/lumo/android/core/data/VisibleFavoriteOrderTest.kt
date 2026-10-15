package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * G1 — the filtered permutation is a pure function (S11-00), on the device.
 *
 * Mirrors the API's `VisibleFavoriteOrderTest` case for case, then adds what only
 * a client needs: the single moves that reach the target, replayed with the
 * contract's semantics. `A*` belongs to the active source, `B*` does not — two
 * abstract bench sources, no channel.
 */
class VisibleFavoriteOrderTest {

    private val activeSource: (String) -> Boolean = { it.startsWith("A") }

    // ---- the API's cases ---------------------------------------------------

    @Test
    fun `a visible slot maps to the full-group index, skipping other sources`() {
        val group = listOf("A1", "B1", "A2", "B2", "A3")

        assertThat(VisibleFavoriteOrder.fullIndex(group, activeSource, 0)).isEqualTo(0)
        assertThat(VisibleFavoriteOrder.fullIndex(group, activeSource, 1)).isEqualTo(2)
        assertThat(VisibleFavoriteOrder.fullIndex(group, activeSource, 2)).isEqualTo(4)
    }

    @Test
    fun `FO-02 - moving A3 to the front leaves B1 and B2 at their indexes`() {
        val reordered = VisibleFavoriteOrder.applyVisibleMove(listOf("A1", "B1", "A2", "B2", "A3"), activeSource, 2, 0)

        assertThat(reordered).containsExactly("A3", "B1", "A1", "B2", "A2").inOrder()
        assertThat(reordered[1]).isEqualTo("B1")
        assertThat(reordered[3]).isEqualTo("B2")
    }

    @Test
    fun `moving down within the active source also leaves the others alone`() {
        val reordered = VisibleFavoriteOrder.applyVisibleMove(listOf("A1", "B1", "A2", "B2", "A3"), activeSource, 0, 2)

        assertThat(reordered).containsExactly("A2", "B1", "A3", "B2", "A1").inOrder()
    }

    @Test
    fun `a group with a single visible member is a fixed point`() {
        assertThat(VisibleFavoriteOrder.applyVisibleMove(listOf("B1", "A1", "B2"), activeSource, 0, 0))
            .containsExactly("B1", "A1", "B2").inOrder()
    }

    @Test
    fun `FO-04 - a group made only of the active source behaves like a plain list move`() {
        assertThat(VisibleFavoriteOrder.applyVisibleMove(listOf("A1", "A2", "A3"), activeSource, 2, 0))
            .containsExactly("A3", "A1", "A2").inOrder()
    }

    @Test
    fun `the result is always a permutation, never a hole`() {
        val group = listOf("A1", "B1", "A2", "B2", "A3")

        val reordered = VisibleFavoriteOrder.applyVisibleMove(group, activeSource, 1, 2)

        assertThat(reordered).hasSize(group.size)
        assertThat(reordered).containsExactlyElementsIn(group)
    }

    @Test
    fun `a slot outside the visible range is rejected, not clamped`() {
        val group = listOf("A1", "B1", "A2")

        assertThrows(IndexOutOfBoundsException::class.java) {
            VisibleFavoriteOrder.fullIndex(group, activeSource, 2)
        }
        assertThrows(IndexOutOfBoundsException::class.java) {
            VisibleFavoriteOrder.applyVisibleMove(group, activeSource, 0, 5)
        }
    }

    // ---- what the device sends ---------------------------------------------

    @Test
    fun `FO-01 - swapping A1 and A2 around B1 gives A2, B1, A1`() {
        val current = listOf("A1", "B1", "A2")

        val target = VisibleFavoriteOrder.target(current, visibleOrder = listOf("A2", "A1"))

        assertThat(target).containsExactly("A2", "B1", "A1").inOrder()
        assertThat(replay(current, VisibleFavoriteOrder.movesToReach(current, target))).isEqualTo(target)
    }

    @Test
    fun `FO-02 - the single moves reach A3, B1, A1, B2, A2 and B keeps its places`() {
        val current = listOf("A1", "B1", "A2", "B2", "A3")

        val target = VisibleFavoriteOrder.target(current, visibleOrder = listOf("A3", "A1", "A2"))
        val moves = VisibleFavoriteOrder.movesToReach(current, target)
        val reached = replay(current, moves)

        assertThat(target).containsExactly("A3", "B1", "A1", "B2", "A2").inOrder()
        assertThat(reached).isEqualTo(target)
        assertThat(reached.indexOf("B1")).isEqualTo(1)
        assertThat(reached.indexOf("B2")).isEqualTo(3)
        // Every move is an index in the full group, never a visible slot.
        assertThat(moves.map { it.position }.all { it in current.indices }).isTrue()
    }

    @Test
    fun `a sequence stopped halfway leaves a valid permutation of the group`() {
        val current = listOf("A1", "B1", "A2", "B2", "A3")
        val moves = VisibleFavoriteOrder.movesToReach(
            current,
            VisibleFavoriteOrder.target(current, listOf("A3", "A1", "A2")),
        )

        // S11-00's explicit non-guarantee: no atomicity, but never a hole.
        val halfway = replay(current, moves.take(1))

        assertThat(halfway).containsExactlyElementsIn(current)
        assertThat(halfway).hasSize(current.size)
    }

    @Test
    fun `one place up in a plain list is one move`() {
        val current = listOf("A1", "A2", "A3")

        val moves = VisibleFavoriteOrder.movesToReach(current, VisibleFavoriteOrder.target(current, listOf("A1", "A3", "A2")))

        assertThat(moves).containsExactly(FavoriteMove("A3", 1))
    }

    @Test
    fun `nothing to move is no move at all`() {
        val current = listOf("A1", "B1", "A2")

        assertThat(VisibleFavoriteOrder.movesToReach(current, VisibleFavoriteOrder.target(current, listOf("A1", "A2"))))
            .isEmpty()
    }

    @Test
    fun `a favourite the gesture did not see keeps its place, and one gone meanwhile is ignored`() {
        // A9 is in the group on the server but not on this screen (not resolved
        // here); A0 was on screen but has been removed from another device.
        val current = listOf("A1", "A9", "A2")

        val target = VisibleFavoriteOrder.target(current, visibleOrder = listOf("A2", "A0", "A1"))

        assertThat(target).containsExactly("A2", "A9", "A1").inOrder()
    }

    @Test
    fun `a plan over two different sets is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            VisibleFavoriteOrder.movesToReach(listOf("A1", "A2"), listOf("A1", "A3"))
        }
    }

    private fun replay(start: List<String>, moves: List<FavoriteMove<String>>): List<String> =
        moves.fold(start) { order, move -> VisibleFavoriteOrder.applyMove(order, move) }
}
