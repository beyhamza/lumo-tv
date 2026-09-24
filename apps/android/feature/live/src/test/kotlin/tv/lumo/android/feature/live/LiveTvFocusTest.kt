package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The television grid's arrival focus (S9-04-03).
 *
 * <h2>What this guards, and why it is not a UI test</h2>
 *
 * The one thing the acceptance names about focus is the negative: **no focus on
 * a hidden element or a skeleton**. A Paging placeholder is drawn at a card's
 * size so the grid keeps its shape, and it has no channel behind it; focusing it
 * would leave the remote on a dead end that appears and disappears as the grid
 * scrolls. The rule lives in [arrivalFocusIndex] as a pure function precisely so
 * it can be pinned here, without an emulator — the D-pad itself still has to be
 * verified on a real remote (`docs/design/tv-focus-map.md`).
 *
 * The same helper must not yank a loaded card out from under a viewer, which is
 * the "return from the player" and "user already moved" half of the contract
 * (US-10).
 */
class LiveTvFocusTest {

    @Test
    fun `a loaded card keeps the focus`() {
        val index = arrivalFocusIndex(count = 10, isLoaded = { true }, current = 3)

        assertThat(index).isEqualTo(3)
    }

    @Test
    fun `arrival skips a skeleton and lands on the first real card`() {
        // The first two cards are windows Paging has not loaded yet.
        val index = arrivalFocusIndex(count = 10, isLoaded = { it >= 2 }, current = 0)

        assertThat(index).isEqualTo(2)
    }

    @Test
    fun `a skeleton at the current index gives way to the first real card`() {
        // The target points at a placeholder (index 4); 6 and 7 are loaded.
        val index = arrivalFocusIndex(count = 10, isLoaded = { it == 6 || it == 7 }, current = 4)

        assertThat(index).isEqualTo(6)
    }

    @Test
    fun `nothing loaded yet leaves the target where it was and invents no index`() {
        val index = arrivalFocusIndex(count = 3, isLoaded = { false }, current = 0)

        assertThat(index).isEqualTo(0)
    }

    @Test
    fun `an empty grid has no target to move to`() {
        val index = arrivalFocusIndex(count = 0, isLoaded = { true }, current = 4)

        assertThat(index).isEqualTo(4)
    }
}
