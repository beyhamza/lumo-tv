package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.WatchProgress

/**
 * When a film leaves the "continue watching" rail (S5-11).
 *
 * <h2>Finished is a threshold, and the default when there is no duration is the
 * whole decision</h2>
 *
 * Nothing tells us a film ended. A player killed by the system, a set-top box
 * unplugged and somebody who sat through the credits all stop writing at some
 * position, so "finished" can only ever be "close enough to the end that offering
 * to resume would be absurd".
 *
 * The case that matters is the one with **no duration at all**, which is most
 * films on most panels. It never leaves the rail, and that asymmetry is
 * deliberate: a film that lingers is an annoyance somebody dismisses in one
 * gesture, a film that vanishes before the end is a loss they cannot recover,
 * because nothing else records where they were.
 */
class WatchProgressTest {

    @Test
    fun `a film with no known duration never counts as finished`() {
        // Many panels state no running time. Guessing from the position would
        // mean guessing that a long film has ended.
        assertThat(progress(positionMs = 7_200_000, durationMs = null).finished).isFalse()
    }

    @Test
    fun `past ninety-five per cent, it is finished`() {
        val ninetySix = progress(positionMs = 5_184_000, durationMs = 5_400_000)

        assertThat(ninetySix.finished).isTrue()
    }

    @Test
    fun `just under the threshold, it stays in the rail`() {
        val ninetyFour = progress(positionMs = 5_076_000, durationMs = 5_400_000)

        assertThat(ninetyFour.finished).isFalse()
    }

    @Test
    fun `a duration of zero is treated as no duration`() {
        // Some sources send `0`. Dividing by it would make every position
        // "finished" and empty the rail on exactly the films it exists for.
        assertThat(progress(positionMs = 1_000, durationMs = 0).finished).isFalse()
    }

    private fun progress(positionMs: Long, durationMs: Long?) = WatchProgress(
        sourceId = "source",
        filmId = "film",
        positionMs = positionMs,
        durationMs = durationMs,
    )
}
