package tv.lumo.androidtv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.feature.live.LiveDestination
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
 */
class TvDestinationsTest {

    @Test
    fun `the rail offers films, whatever the source holds`() {
        val routes = TvDestinations.map { it.route }

        assertThat(routes).containsExactly(
            LiveDestination.route,
            VodDestination.route,
            SourceDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `channels stay first, and films sit next to them`() {
        val routes = TvDestinations.map { it.route }

        // Live is the shortest journey from the rail, whatever else is in it —
        // the rule the rail was built on. The two catalogues sit together;
        // everything below them is somewhere one goes occasionally.
        assertThat(routes.first()).isEqualTo(LiveDestination.route)
        assertThat(routes[1]).isEqualTo(VodDestination.route)
    }

    @Test
    fun `a screen that does not exist yet is not in the rail`() {
        // Series have no television screen until S6-06 — only `LumoTvPlaceholder`.
        // A rail entry onto one would cost every viewer a `DOWN` press to reach a
        // sentence saying the feature is not built, which is the one thing worse
        // than an empty catalogue.
        assertThat(TvDestinations.map { it.route }).doesNotContain(SeriesDestination.route)
    }
}
