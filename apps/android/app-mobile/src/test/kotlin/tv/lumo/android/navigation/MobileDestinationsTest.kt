package tv.lumo.android.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.vod.VodDestination

/**
 * What the bottom bar offers (US-13).
 *
 * <h2>This file used to assert the opposite, and that is the point of keeping it</h2>
 *
 * The earlier version proved that a source with no films showed **no** films tab,
 * and called it a negative requirement worth guarding. It was a real rule, it was
 * tested, and it was wrong: the owner of a panel carrying a hundred and forty
 * thousand films could not find them and reported the feature as missing. **An
 * absence is indistinguishable from a bug.**
 *
 * So the tab is unconditional, and what is guarded now is the line that survived
 * the reversal: **an empty catalogue belongs in the bar, an unbuilt screen does
 * not.** A grid that says "this source offers only channels" is a reply. A tab
 * onto `LumoMobilePlaceholder` is a promise, and that is why series and search
 * stay out until their screens exist.
 *
 * The order is asserted for the reason it always was: it is the reading order of a
 * catalogue, and a bar that quietly reorders itself moves a target under somebody's
 * thumb between two launches.
 */
class MobileDestinationsTest {

    @Test
    fun `the bar offers films, whatever the source holds`() {
        val routes = MobileDestinations.map { it.route }

        // No condition and no argument: there is nothing left to decide. A source
        // with no films opens onto a grid that says so.
        assertThat(routes).containsExactly(
            LiveDestination.route,
            VodDestination.route,
            FavoritesDestination.route,
            SourceDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `films come straight after the channels`() {
        val routes = MobileDestinations.map { it.route }

        // The reading order of a catalogue, and the two catalogues sit together:
        // favourites is a selection out of them and comes after.
        assertThat(routes.indexOf(VodDestination.route))
            .isEqualTo(routes.indexOf(LiveDestination.route) + 1)
    }

    @Test
    fun `a screen that does not exist yet is not in the bar`() {
        // The distinction that survived the reversal. Series have no phone screen
        // until S6-05 — only `LumoMobilePlaceholder` — and a tab onto a placeholder
        // is a promise rather than a reply. It joins the day the screen lands, and
        // this test is what will have to change with it.
        assertThat(MobileDestinations.map { it.route })
            .doesNotContain(SeriesDestination.route)
    }
}
