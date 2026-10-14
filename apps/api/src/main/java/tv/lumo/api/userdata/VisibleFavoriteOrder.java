package tv.lumo.api.userdata;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Translation between the <em>visible</em> order of a favourite group (the
 * favourites of one source) and the <em>full</em> group order the contract's
 * {@code position} indexes.
 *
 * <p>{@code position} is an index into the group as a whole, and a
 * {@code PATCH /me/favorites/{id}} moves one favourite to such an index. A client
 * that shows only the active source's favourites cannot send the visible slot as
 * it stands: a group whose members alternate sources has gaps, and the visible
 * slot would land somewhere else entirely. This class is the translation, kept
 * free of the database and the request so it can be exercised on its own
 * (S11-00 guarantee G1,
 * {@code docs/backlog/sprint-11/S11-00-guarantees.md}).
 *
 * <p>Nothing here is atomic, and nothing pretends to be: a reorder the user sees
 * is a sequence of single-favourite moves, and an interruption leaves the group
 * <em>valid but partially permuted</em>. That is the product behaviour, not an
 * oversight; the absence of a batch endpoint is explicit in S11-00.
 */
public final class VisibleFavoriteOrder {

    private VisibleFavoriteOrder() {
    }

    /**
     * The index in the full group of the favourite sitting at {@code visibleSlot}
     * among the members matching {@code visible}.
     *
     * @throws IndexOutOfBoundsException if {@code visibleSlot} is negative or past
     *     the last visible member
     */
    public static <T> int fullIndex(List<T> groupOrder, Predicate<T> visible, int visibleSlot) {
        int seen = 0;
        for (int i = 0; i < groupOrder.size(); i++) {
            if (visible.test(groupOrder.get(i))) {
                if (seen == visibleSlot) {
                    return i;
                }
                seen++;
            }
        }
        throw new IndexOutOfBoundsException(
                "visible slot " + visibleSlot + " out of range for " + seen + " visible members");
    }

    /**
     * Moves the visible member at {@code fromSlot} to {@code toSlot} and returns the
     * resulting full group order.
     *
     * <p>Members that do not match {@code visible} keep the exact index they had:
     * the permutation is projected back onto the same visible positions. From
     * {@code [A1, B1, A2, B2, A3]}, moving visible slot 2 to slot 0 yields
     * {@code [A3, B1, A1, B2, A2]} — {@code B1} and {@code B2} stay at 1 and 3.
     */
    public static <T> List<T> applyVisibleMove(List<T> groupOrder, Predicate<T> visible,
                                               int fromSlot, int toSlot) {
        List<T> visibleMembers = new ArrayList<>();
        for (T member : groupOrder) {
            if (visible.test(member)) {
                visibleMembers.add(member);
            }
        }
        if (fromSlot < 0 || fromSlot >= visibleMembers.size()) {
            throw new IndexOutOfBoundsException("from slot " + fromSlot + " out of range");
        }
        if (toSlot < 0 || toSlot >= visibleMembers.size()) {
            throw new IndexOutOfBoundsException("to slot " + toSlot + " out of range");
        }

        T moved = visibleMembers.remove(fromSlot);
        visibleMembers.add(toSlot, moved);

        List<T> result = new ArrayList<>(groupOrder);
        int visibleIndex = 0;
        for (int i = 0; i < result.size(); i++) {
            if (visible.test(result.get(i))) {
                result.set(i, visibleMembers.get(visibleIndex++));
            }
        }
        return result;
    }
}
