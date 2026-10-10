package tv.lumo.api.userdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G1 — the filtered permutation is a pure function (S11-00).
 *
 * <p>No database, no request, no Spring: the whole point of the guarantee is that
 * the translation between a visible slot and a full-group index is decidable on
 * its own. The reference case is FO-02, from
 * {@code docs/design/0.2.0/favorite-organization-cases.md}: two source-A members
 * must not move when a third one is dragged past them.
 */
class VisibleFavoriteOrderTest {

    /** {@code A*} belongs to the active source; {@code B*} does not. */
    private static final Predicate<String> ACTIVE_SOURCE = member -> member.startsWith("A");

    @Test
    @DisplayName("a visible slot maps to the full-group index, skipping other sources")
    void visibleSlotMapsToFullIndex() {
        List<String> group = List.of("A1", "B1", "A2", "B2", "A3");

        assertThat(VisibleFavoriteOrder.fullIndex(group, ACTIVE_SOURCE, 0)).isZero();
        assertThat(VisibleFavoriteOrder.fullIndex(group, ACTIVE_SOURCE, 1)).isEqualTo(2);
        assertThat(VisibleFavoriteOrder.fullIndex(group, ACTIVE_SOURCE, 2)).isEqualTo(4);
    }

    @Test
    @DisplayName("FO-02: moving A3 to the front leaves B1 and B2 at their indexes")
    void movingAVisibleMemberKeepsTheOthersInPlace() {
        List<String> group = List.of("A1", "B1", "A2", "B2", "A3");

        List<String> reordered = VisibleFavoriteOrder.applyVisibleMove(group, ACTIVE_SOURCE, 2, 0);

        assertThat(reordered).containsExactly("A3", "B1", "A1", "B2", "A2");
        // The two members of the other source did not slide: this is the whole
        // reason a single PATCH on the visible slot cannot express the move.
        assertThat(reordered.get(1)).isEqualTo("B1");
        assertThat(reordered.get(3)).isEqualTo("B2");
    }

    @Test
    @DisplayName("moving down within the active source also leaves the others alone")
    void movingDownKeepsTheOtherSourceInPlace() {
        List<String> group = List.of("A1", "B1", "A2", "B2", "A3");

        List<String> reordered = VisibleFavoriteOrder.applyVisibleMove(group, ACTIVE_SOURCE, 0, 2);

        assertThat(reordered).containsExactly("A2", "B1", "A3", "B2", "A1");
        assertThat(reordered.get(1)).isEqualTo("B1");
        assertThat(reordered.get(3)).isEqualTo("B2");
    }

    @Test
    @DisplayName("a group with a single visible member is a fixed point")
    void aSingleVisibleMemberCannotMove() {
        List<String> group = List.of("B1", "A1", "B2");

        assertThat(VisibleFavoriteOrder.applyVisibleMove(group, ACTIVE_SOURCE, 0, 0))
                .containsExactly("B1", "A1", "B2");
    }

    @Test
    @DisplayName("a group made only of the active source behaves like a plain list move")
    void aFullyVisibleGroupIsAPlainMove() {
        List<String> group = List.of("A1", "A2", "A3");

        assertThat(VisibleFavoriteOrder.applyVisibleMove(group, ACTIVE_SOURCE, 2, 0))
                .containsExactly("A3", "A1", "A2");
    }

    @Test
    @DisplayName("the result is contiguous: it is a permutation, never a hole")
    void theResultIsAlwaysAPermutation() {
        List<String> group = List.of("A1", "B1", "A2", "B2", "A3");

        List<String> reordered = VisibleFavoriteOrder.applyVisibleMove(group, ACTIVE_SOURCE, 1, 2);

        assertThat(reordered).hasSize(group.size()).containsExactlyInAnyOrderElementsOf(group);
    }

    @Test
    @DisplayName("a slot outside the visible range is rejected, not clamped")
    void outOfRangeSlotsAreRejected() {
        List<String> group = List.of("A1", "B1", "A2");

        assertThatThrownBy(() -> VisibleFavoriteOrder.fullIndex(group, ACTIVE_SOURCE, 2))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> VisibleFavoriteOrder.applyVisibleMove(group, ACTIVE_SOURCE, 0, 5))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
