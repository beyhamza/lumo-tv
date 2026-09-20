package tv.lumo.android.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Where the television's home screen puts the focus by itself (US-017, US-10).
 *
 * The defect these guard against is the one `tv-focus-map.md` opens with: not a
 * crash, not a wrong value — a remote that does nothing on arrival, or a focus
 * that jumps out from under a thumb. Neither shows on a screenshot and neither
 * fails anything else in this build.
 *
 * What is **not** proved here is that Compose honours the request: that a card
 * scrolled into a `LazyRow` takes the focus, that `LEFT` from the first card
 * reaches the nav rail. Those are checked with a remote control, on a television.
 */
class HomeFocusTest {

    private val keys = listOf("film:a", "series:b", "favorite:c", "recent:c")

    @Test
    fun `on arrival the first card of the first rail takes the focus`() {
        assertThat(homeFocusTarget(keys, returnKey = null, placedKey = null, focusedKey = null))
            .isEqualTo("film:a")
    }

    @Test
    fun `back from a player, the card that launched it takes the focus`() {
        assertThat(homeFocusTarget(keys, returnKey = "favorite:c", placedKey = null, focusedKey = null))
            .isEqualTo("favorite:c")
    }

    @Test
    fun `a card that is gone on the way back falls to the first one`() {
        // The film was watched to the end: it has left "Continue".
        assertThat(homeFocusTarget(keys, returnKey = "film:gone", placedKey = null, focusedKey = null))
            .isEqualTo("film:a")
    }

    @Test
    fun `a rail arriving late moves the focus only while the remote is untouched`() {
        // Cold start: favourites were there first and got the focus.
        val early = listOf("favorite:c", "recent:c")
        assertThat(homeFocusTarget(early, null, placedKey = null, focusedKey = null))
            .isEqualTo("favorite:c")

        // "Continue" arrives above. Nobody has pressed anything: follow it.
        assertThat(homeFocusTarget(keys, null, placedKey = "favorite:c", focusedKey = "favorite:c"))
            .isEqualTo("film:a")

        // Somebody has moved along the rail in the meantime: leave them alone.
        assertThat(homeFocusTarget(keys, null, placedKey = "favorite:c", focusedKey = "recent:c"))
            .isNull()
    }

    @Test
    fun `a focus that left the content is never pulled back`() {
        // In the nav rail, or on a button: no card holds the focus.
        assertThat(homeFocusTarget(keys, null, placedKey = "film:a", focusedKey = null)).isNull()
    }

    @Test
    fun `with no card there is nothing to focus`() {
        assertThat(homeFocusTarget(emptyList(), "film:a", null, null)).isNull()
    }

    @Test
    fun `a channel in both rails is two cards to the focus`() {
        assertThat(favoriteKey("c")).isNotEqualTo(recentKey("c"))
    }

    // ---- the position bar ----------------------------------------------------

    @Test
    fun `the bar is drawn from a known length only`() {
        assertThat(progressFraction(positionMs = 30, durationMs = 120)).isEqualTo(0.25f)
        assertThat(progressFraction(positionMs = 30, durationMs = null)).isNull()
        assertThat(progressFraction(positionMs = 30, durationMs = 0)).isNull()
    }

    @Test
    fun `a next episode offered from its start has no bar`() {
        // An empty track would read as "none of this watched", which is the
        // opposite of why the series is in the rail.
        assertThat(progressFraction(positionMs = 0, durationMs = 120)).isNull()
    }

    @Test
    fun `a position past the stated length fills the bar and no more`() {
        assertThat(progressFraction(positionMs = 500, durationMs = 120)).isEqualTo(1f)
    }
}
