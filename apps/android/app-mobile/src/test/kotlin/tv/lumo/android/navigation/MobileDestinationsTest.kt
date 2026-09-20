package tv.lumo.android.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.home.HomeDestination
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.search.SearchDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodDetailDestination

/**
 * What the bottom bar offers, and where the application opens (US-13, US-017).
 *
 * <h2>This file has asserted the opposite before, twice, and that is the point of
 * keeping it</h2>
 *
 * The first version proved that a source with no films showed **no** films tab.
 * It was a real rule, it was tested, and it was wrong: the owner of a panel
 * carrying a hundred and forty thousand films could not find them and reported the
 * feature as missing. **An absence is indistinguishable from a bug.** The second
 * version pinned six entries in a row, unconditional.
 *
 * US-017 replaced those six with four — Home, Explore, Library, Settings — and
 * what is guarded now is the line that survived both reversals: **an empty
 * catalogue belongs in the navigation, an unbuilt screen does not.** The three
 * catalogues are all offered, always, as the sections of Explore; Search, still a
 * placeholder, is offered nowhere.
 *
 * The order is asserted for the reason it always was: a bar that quietly reorders
 * itself moves a target under somebody's thumb between two launches.
 */
class MobileDestinationsTest {

    @Test
    fun `the bar offers Home, Explore, Library and Settings, in that order`() {
        assertThat(MobileDestinations.map { it.route }).containsExactly(
            HomeDestination.route,
            ExploreDestination.route,
            FavoritesDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `Explore gathers the three catalogues, whatever the source holds`() {
        // No condition and no argument: there is nothing left to decide. A source
        // with no films — or a playlist, which can carry no series at all — opens
        // onto a grid that says so, in its own words.
        assertThat(ExploreSections.map { it.route }).containsExactly(
            LiveDestination.route,
            VodDestination.route,
            SeriesDestination.route,
        ).inOrder()
    }

    @Test
    fun `a catalogue is a section of Explore, never an entry of the bar as well`() {
        // Offered twice, a destination has two back stacks: the bar's
        // `saveState` would keep one Films and the strip another.
        val bar = MobileDestinations.map { it.route }

        ExploreSections.forEach { section -> assertThat(bar).doesNotContain(section.route) }
    }

    @Test
    fun `the source screen left the bar`() {
        // Still in the graph, and reached from Settings and from the switcher
        // above every section. What left is the fifth entry of a four-entry bar.
        assertThat(MobileDestinations.map { it.route }).doesNotContain(SourceDestination.route)
    }

    @Test
    fun `a screen that does not exist yet is offered nowhere`() {
        // The distinction that survived both reversals, and the last destination
        // it still keeps out. Search is `LumoMobilePlaceholder` and nothing else;
        // a tab onto it is a promise rather than a reply. It joins the day its
        // screen lands (sprint 10), and this test is what will change with it.
        val offered = (MobileDestinations + ExploreSections).map { it.route }

        assertThat(offered).doesNotContain(SearchDestination.route)
    }

    // ---- where the application opens -----------------------------------------

    @Test
    fun `a signed-in user with a source lands on Home`() {
        assertThat(mobileStartRoute(AppStart.Ready)).isEqualTo(HomeDestination.route)
    }

    @Test
    fun `Home is the first entry of the bar, so BACK from any other returns to it`() {
        // The bar pops to the graph's start destination. The two being the same
        // screen is what makes "BACK goes Home, BACK on Home leaves" true, and
        // nothing else in the code says they must be.
        assertThat(MobileDestinations.first().route).isEqualTo(mobileStartRoute(AppStart.Ready))
    }

    @Test
    fun `the three other situations open where they always did`() {
        assertThat(mobileStartRoute(AppStart.Loading)).isNull()
        assertThat(mobileStartRoute(AppStart.SignedOut)).isEqualTo(AuthDestination.route)
        // Nothing to watch yet: the screen that fixes that, not an empty home.
        assertThat(mobileStartRoute(AppStart.NeedsSource)).isEqualTo(SourceDestination.route)
    }

    // ---- which entry the bar lights ------------------------------------------

    @Test
    fun `a section of Explore lights Explore`() {
        // `NavDestination.hierarchy`, innermost first: the screen, its graph, the
        // root graph — which has no route.
        val onFilms = listOf(VodDestination.route, ExploreDestination.route, null)

        assertThat(topLevelRouteOf(onFilms, MobileDestinations)).isEqualTo(ExploreDestination.route)
    }

    @Test
    fun `an entry that is a screen lights itself`() {
        assertThat(topLevelRouteOf(listOf(HomeDestination.route, null), MobileDestinations))
            .isEqualTo(HomeDestination.route)
    }

    @Test
    fun `a screen that belongs to no entry lights none`() {
        // A film's own screen and a player sit at the root of the graph. Lighting
        // Explore over a film opened from Home would claim a journey nobody made.
        assertThat(topLevelRouteOf(listOf(VodDetailDestination.route, null), MobileDestinations)).isNull()
        assertThat(topLevelRouteOf(listOf(PlayerDestination.route, null), MobileDestinations)).isNull()
        assertThat(topLevelRouteOf(emptyList(), MobileDestinations)).isNull()
    }
}
