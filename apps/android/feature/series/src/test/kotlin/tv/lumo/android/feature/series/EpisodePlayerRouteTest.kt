package tv.lumo.android.feature.series

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * CW-27: a "Continue" card that launches the **next** episode tells the player
 * which series to go back to; any other launch does not.
 */
class EpisodePlayerRouteTest {

    @Test
    fun `CW-27 the next episode carries its series`() {
        assertThat(EpisodePlayerDestination.routeFor("e2", "Pilot", 0L, nextOfSeries = "s1"))
            .isEqualTo("series/player/e2?title=Pilot&at=0&series=s1")
    }

    @Test
    fun `a resume carries no series - a failure offers plain Back`() {
        assertThat(EpisodePlayerDestination.routeFor("e1", null, 120_000L))
            .isEqualTo("series/player/e1?title=&at=120000&series=")
    }

    @Test
    fun `the route declares the argument the destination reads`() {
        assertThat(EpisodePlayerDestination.route).contains("series={${EpisodePlayerDestination.ARG_SERIES}}")
    }
}
