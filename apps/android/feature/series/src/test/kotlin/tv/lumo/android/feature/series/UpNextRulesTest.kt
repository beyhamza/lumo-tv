package tv.lumo.android.feature.series

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.EpisodeProgress

/**
 * S13-03: the offer at the real end of an episode — CW-12 to CW-15 — and the
 * countdown, without a player.
 */
class UpNextRulesTest {

    private val next = Episode(
        id = "s2e1",
        seriesId = "series",
        sourceId = "source",
        seasonNumber = 2,
        episodeNumber = 1,
        name = null,
        durationSeconds = 2_400,
        plot = null,
    )

    @Test
    fun `autoplay counts down from the surface's number - 10 on TV, 5 on a phone (CW-13)`() {
        assertThat(upNextOffer(next, null, autoplay = true, countdownSeconds = 10)?.secondsLeft).isEqualTo(10)
        assertThat(upNextOffer(next, null, autoplay = true, countdownSeconds = 5)?.secondsLeft).isEqualTo(5)
    }

    @Test
    fun `autoplay off offers the next episode without a countdown (CW-14)`() {
        val offer = upNextOffer(next, null, autoplay = false, countdownSeconds = 10)

        assertThat(offer?.episode).isEqualTo(next)
        assertThat(offer?.secondsLeft).isNull()
        assertThat(offer?.due).isFalse()
    }

    @Test
    fun `no next episode is no offer - the screen goes back to the series (CW-15)`() {
        assertThat(upNextOffer(null, null, autoplay = true, countdownSeconds = 10)).isNull()
    }

    @Test
    fun `a next episode already started resumes at its position (CW-12)`() {
        val started = progress(positionMs = 600_000L, durationMs = 2_400_000L)

        assertThat(upNextOffer(next, started, autoplay = true, countdownSeconds = 5)?.resumeAtMs).isEqualTo(600_000L)
    }

    @Test
    fun `a next episode already finished starts again from its beginning`() {
        val finished = progress(positionMs = 2_390_000L, durationMs = 2_400_000L)

        assertThat(upNextOffer(next, finished, autoplay = true, countdownSeconds = 5)?.resumeAtMs).isNull()
    }

    @Test
    fun `another episode's progress is never applied`() {
        val other = progress(positionMs = 600_000L, durationMs = 2_400_000L).copy(episodeId = "s1e9")

        assertThat(upNextOffer(next, other, autoplay = true, countdownSeconds = 5)?.resumeAtMs).isNull()
    }

    @Test
    fun `the countdown reaches zero after its number of ticks, and only then is due`() {
        var offer = upNextOffer(next, null, autoplay = true, countdownSeconds = 5)!!
        repeat(4) {
            offer = offer.tick()
            assertThat(offer.due).isFalse()
        }
        offer = offer.tick()

        assertThat(offer.secondsLeft).isEqualTo(0)
        assertThat(offer.due).isTrue()
    }

    @Test
    fun `an interaction cancels the countdown for good and keeps the offer`() {
        val offer = upNextOffer(next, null, autoplay = true, countdownSeconds = 10)!!.tick().cancelled()

        assertThat(offer.episode).isEqualTo(next)
        assertThat(offer.secondsLeft).isNull()
        // Ticks after a cancellation do not bring it back.
        assertThat(offer.tick().secondsLeft).isNull()
        assertThat(offer.tick().due).isFalse()
    }

    private fun progress(positionMs: Long, durationMs: Long) = EpisodeProgress(
        sourceId = "source",
        episodeId = "s2e1",
        positionMs = positionMs,
        durationMs = durationMs,
    )
}
