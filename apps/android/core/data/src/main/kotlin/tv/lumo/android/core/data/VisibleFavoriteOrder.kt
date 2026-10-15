package tv.lumo.android.core.data

/**
 * Translation between the **visible** order of a favourite group — the favourites
 * of the active source — and the **full** group order that the contract's
 * `position` indexes (S11-00, guarantee G1).
 *
 * A port of the API's `VisibleFavoriteOrder`, kept pure so it can be tested on its
 * own, plus the one thing a client needs on top of it: the list of single
 * `PATCH /me/favorites/{id}` moves that turns the full order into the target.
 *
 * <h2>Why a visible slot is never sent as it stands</h2>
 *
 * `position` is an index in the group **as a whole**. A group whose members
 * alternate sources has gaps in the visible list, so "slot 0 of what I see" is not
 * "index 0 of the group". From `[A1, B1, A2, B2, A3]`, moving A3 before A1 must give
 * `[A3, B1, A1, B2, A2]`: the visible permutation is projected back onto the same
 * places, and B1/B2 stay at indexes 1 and 3 (FO-02).
 *
 * <h2>Nothing here is atomic, and nothing pretends to be</h2>
 *
 * The contract has no batch: a reorder is a sequence of single moves, and an
 * interruption leaves the group **valid but partially permuted**. That is the
 * product behaviour retained in S11-00, not an oversight.
 */
object VisibleFavoriteOrder {

    /**
     * The index in the full group of the member at [visibleSlot] among those
     * matching [visible].
     *
     * @throws IndexOutOfBoundsException for a slot outside the visible range —
     * rejected rather than clamped, as on the server.
     */
    fun <T> fullIndex(groupOrder: List<T>, visible: (T) -> Boolean, visibleSlot: Int): Int {
        var seen = 0
        groupOrder.forEachIndexed { index, member ->
            if (visible(member)) {
                if (seen == visibleSlot) return index
                seen++
            }
        }
        throw IndexOutOfBoundsException("visible slot $visibleSlot out of range for $seen visible members")
    }

    /**
     * Moves the visible member at [fromSlot] to [toSlot] and returns the resulting
     * full order. Members that do not match [visible] keep their exact index.
     */
    fun <T> applyVisibleMove(groupOrder: List<T>, visible: (T) -> Boolean, fromSlot: Int, toSlot: Int): List<T> {
        val members = groupOrder.filter(visible).toMutableList()
        if (fromSlot !in members.indices) throw IndexOutOfBoundsException("from slot $fromSlot out of range")
        if (toSlot !in members.indices) throw IndexOutOfBoundsException("to slot $toSlot out of range")

        members.add(toSlot, members.removeAt(fromSlot))
        return withVisibleOrder(groupOrder, visible, members)
    }

    /**
     * The full order once the visible members read [visibleOrder], everything else
     * left where it was.
     *
     * What a drag produces: the whole visible list in its new order rather than one
     * move. Members of [visibleOrder] absent from the group — removed from another
     * device meanwhile — are ignored; members of the group absent from
     * [visibleOrder] are treated as invisible and keep their place. The second is
     * what keeps a favourite this device has not resolved yet from being shuffled
     * by a gesture made without seeing it.
     */
    fun <T> target(groupOrder: List<T>, visibleOrder: List<T>): List<T> {
        val inGroup = groupOrder.toSet()
        val ordered = visibleOrder.filter { it in inGroup }.distinct()
        val shown = ordered.toSet()
        return withVisibleOrder(groupOrder, { it in shown }, ordered)
    }

    /**
     * The single moves — `member` to `position` in the full group, with the
     * contract's semantics (the member is taken out, inserted at `position`, the
     * others shift) — that turn [current] into [target].
     *
     * Walks the target from the head and brings the expected member up wherever the
     * current order disagrees, which is exactly how the API's guarantee test
     * reaches a permutation. Positions already right cost nothing, so a move of
     * one place in a plain list is one `PATCH`.
     *
     * @throws IllegalArgumentException when the two are not permutations of each
     * other: a plan over two different sets would move something that is not there.
     */
    fun <T> movesToReach(current: List<T>, target: List<T>): List<FavoriteMove<T>> {
        require(current.size == target.size && current.toSet() == target.toSet() && current.toSet().size == current.size) {
            "target must be a permutation of the current order"
        }
        val work = current.toMutableList()
        val moves = mutableListOf<FavoriteMove<T>>()
        target.forEachIndexed { position, expected ->
            if (work[position] != expected) {
                work.remove(expected)
                work.add(position, expected)
                moves += FavoriteMove(expected, position)
            }
        }
        return moves
    }

    /** Applies one move with the contract's semantics. For tests and for the planner's own checks. */
    fun <T> applyMove(order: List<T>, move: FavoriteMove<T>): List<T> {
        val work = order.toMutableList()
        work.remove(move.member)
        work.add(move.position.coerceAtMost(work.size), move.member)
        return work
    }

    private fun <T> withVisibleOrder(groupOrder: List<T>, visible: (T) -> Boolean, members: List<T>): List<T> {
        var next = 0
        return groupOrder.map { member -> if (visible(member)) members[next++] else member }
    }
}

/** One `PATCH /me/favorites/{id}` with `position`: [member] goes to [position] in its group. */
data class FavoriteMove<T>(val member: T, val position: Int)
