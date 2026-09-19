package tv.lumo.androidtv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
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
        TvDestinations.forEach { destination ->
            assertThat(catalogueRootAfterSourceSwitch(destination.route)).isNull()
        }
        // Spelled out for the three the story names, so that removing one from
        // the rail cannot quietly empty this test.
        listOf(LiveDestination, VodDestination, SeriesDestination).forEach { destination ->
            assertThat(catalogueRootAfterSourceSwitch(destination.route)).isNull()
        }
        listOf(SourceDestination, SettingsDestination).forEach { destination ->
            assertThat(catalogueRootAfterSourceSwitch(destination.route)).isNull()
        }
    }

    @Test
    fun `no route at all goes nowhere`() {
        // The graph has not drawn its first entry yet.
        assertThat(catalogueRootAfterSourceSwitch(null)).isNull()
    }

    @Test
    fun `every detail goes back to a section the rail actually offers`() {
        val sections = TvDestinations.map { it.route }

        // A detail sent to a route that is not a section would pop to nothing
        // and fall back to a navigation nobody can undo with the rail.
        assertThat(sections).containsAtLeastElementsIn(SourceScopedDetails.values)
    }
}
