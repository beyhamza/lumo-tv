package tv.lumo.android.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.home.HomeDestination
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.SeriesDetailDestination
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodDetailDestination

/**
 * Where a change of source leaves the user (US-018).
 *
 * Two criteria that pull in opposite directions, which is why they are pinned
 * together: **keep the section that is open**, and **never keep a detail screen
 * that belongs to the old source**. Popping too eagerly throws somebody out of
 * Films for having changed source from Films; not popping leaves them one press
 * from playing a film out of a catalogue they have left.
 */
class SourceSwitchNavigationTest {

    @Test
    fun `a film or a series of the old source goes back to its own catalogue`() {
        assertThat(catalogueRootAfterSourceSwitch(VodDetailDestination.route))
            .isEqualTo(VodDestination.route)
        assertThat(catalogueRootAfterSourceSwitch(SeriesDetailDestination.route))
            .isEqualTo(SeriesDestination.route)
    }

    @Test
    fun `a section stays open`() {
        (MobileDestinations + ExploreSections).forEach { destination ->
            assertThat(catalogueRootAfterSourceSwitch(destination.route)).isNull()
        }
        // Spelled out for the three the story names, so that moving one in the
        // navigation cannot quietly empty this test — it already happened once,
        // when they went from the bar to the sections of Explore.
        listOf(LiveDestination, VodDestination, SeriesDestination).forEach { destination ->
            assertThat(catalogueRootAfterSourceSwitch(destination.route)).isNull()
        }
        listOf(FavoritesDestination, SourceDestination, SettingsDestination).forEach { destination ->
            assertThat(catalogueRootAfterSourceSwitch(destination.route)).isNull()
        }
    }

    @Test
    fun `Home stays open, and reloads by itself`() {
        // US-017: the home screen follows the active source on its own. Changing
        // source from it is not a navigation at all.
        assertThat(catalogueRootAfterSourceSwitch(HomeDestination.route)).isNull()
    }

    @Test
    fun `no route at all goes nowhere`() {
        // The graph has not drawn its first entry yet.
        assertThat(catalogueRootAfterSourceSwitch(null)).isNull()
    }

    @Test
    fun `every detail goes back to a section the navigation actually offers`() {
        // The catalogues are sections of Explore since US-017, so that is where a
        // detail has to lead: a route that is neither in the bar nor in the strip
        // would be a screen nothing on display can name or leave.
        val sections = ExploreSections.map { it.route }

        assertThat(sections).containsAtLeastElementsIn(SourceScopedDetails.values)
    }

    @Test
    fun `a detail opened from Home still goes to its catalogue, not back to Home`() {
        // The "Details" button of a "Continue" card puts a film straight over
        // Home, with no grid underneath. Where it leads after a change of source
        // depends on what it is, never on how somebody got there.
        assertThat(SourceScopedDetails.values).doesNotContain(HomeDestination.route)
    }
}
