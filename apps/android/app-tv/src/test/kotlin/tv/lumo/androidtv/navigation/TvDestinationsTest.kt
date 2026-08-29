package tv.lumo.androidtv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.search.SearchDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.vod.VodDestination

/**
 * What the rail offers (US-13).
 *
 * <h2>The reversal cost more to accept here, and it is still right</h2>
 *
 * The earlier version of this file proved the films entry was absent on a source
 * with no films, and argued that it weighed more on a television than on a phone:
 * a rail entry is a **mandatory stop on the way down**, so a door onto an empty
 * room costs every viewer a press, on every journey.
 *
 * That cost is real. It is also the smaller one. Hiding the films is what made
 * somebody with a hundred and forty thousand of them conclude the feature did not
 * exist — and on a television there is nowhere else to go and look, no second
 * screen, no address bar. A press spent reaching a grid that explains itself beats
 * a feature nobody can find.
 *
 * What is guarded now is the line that survived: an *empty* catalogue is a reply
 * and belongs in the rail; an *unbuilt* screen is a promise and does not.
 *
 * **Series crossed that line with `S6-06`**, and they crossed it by gaining a
 * screen rather than by gaining a catalogue — which is the rule the right way
 * round. Search is the last destination the rule still keeps out.
 */
class TvDestinationsTest {

    @Test
    fun `the rail offers both catalogues, whatever the source holds`() {
        val routes = TvDestinations.map { it.route }

        assertThat(routes).containsExactly(
            LiveDestination.route,
            VodDestination.route,
            SeriesDestination.route,
            SourceDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `channels stay first, and the catalogues sit next to them`() {
        val routes = TvDestinations.map { it.route }

        // Live is the shortest journey from the rail, whatever else is in it —
        // the rule the rail was built on. The catalogues sit together, in the
        // order they shipped; everything below them is somewhere one goes
        // occasionally. A rail that reorders itself between two releases moves a
        // target from under somebody who has learnt where it was.
        assertThat(routes.first()).isEqualTo(LiveDestination.route)
        assertThat(routes[1]).isEqualTo(VodDestination.route)
        assertThat(routes[2]).isEqualTo(SeriesDestination.route)
    }

    @Test
    fun `a screen that does not exist yet is not in the rail`() {
        // Search has no television screen — only `LumoTvPlaceholder`. A rail entry
        // onto one would cost every viewer a `DOWN` press to reach a sentence
        // saying the feature is not built, which is the one thing worse than an
        // empty catalogue. It joins the day its screen lands, exactly as series
        // did, and this test is what will have to change with it.
        assertThat(TvDestinations.map { it.route }).doesNotContain(SearchDestination.route)
    }
}
