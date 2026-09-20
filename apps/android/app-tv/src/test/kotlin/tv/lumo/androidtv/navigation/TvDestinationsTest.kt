package tv.lumo.androidtv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.favorites.FavoritesTvDestination
import tv.lumo.android.feature.home.HomeDestination
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.search.SearchDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.vod.VodDestination

/**
 * What the rail offers, and where the television opens (US-13, US-017).
 *
 * <h2>The reversal cost more to accept here, and it is still right</h2>
 *
 * The earliest version of this file proved the films entry was absent on a source
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
 * What is guarded is the line that survived: an *empty* catalogue is a reply and
 * belongs in the rail; an *unbuilt* screen is a promise and does not. Series
 * crossed that line with `S6-06` and "My library" with US-017 — both by gaining a
 * screen, which is the rule the right way round. Search is the last destination
 * it still keeps out.
 *
 * <h2>And because every entry is a stop, the rail does not grow for free</h2>
 *
 * US-017 added Home and My library. Source left to pay for one of them; the rail
 * is six entries and this file is what notices a seventh.
 */
class TvDestinationsTest {

    @Test
    fun `the rail offers Home, the three catalogues, My library and Settings, in that order`() {
        assertThat(TvDestinations.map { it.route }).containsExactly(
            HomeDestination.route,
            LiveDestination.route,
            VodDestination.route,
            SeriesDestination.route,
            FavoritesDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `the catalogues sit together, straight under Home`() {
        val routes = TvDestinations.map { it.route }

        // Live is one press from the head of the rail, whatever else is in it.
        // The catalogues stay together, in the order they shipped; everything
        // below them is somewhere one goes occasionally. A rail that reorders
        // itself between two releases moves a target from under somebody who has
        // learnt where it was.
        assertThat(routes[1]).isEqualTo(LiveDestination.route)
        assertThat(routes[2]).isEqualTo(VodDestination.route)
        assertThat(routes[3]).isEqualTo(SeriesDestination.route)
    }

    @Test
    fun `My library is the favourites route under the television's own label`() {
        // Two labels for two surfaces (decisions table), one route for both
        // applications: a deep link or a bug report reads the same wherever it
        // came from.
        assertThat(FavoritesTvDestination.route).isEqualTo(FavoritesDestination.route)
        assertThat(FavoritesTvDestination.titleRes).isNotEqualTo(FavoritesDestination.titleRes)
        assertThat(TvDestinations).contains(FavoritesTvDestination)
    }

    @Test
    fun `the source screen left the rail`() {
        // Still in the graph, and reached from the switcher at the foot of the
        // rail and from Settings.
        assertThat(TvDestinations.map { it.route }).doesNotContain(SourceDestination.route)
    }

    @Test
    fun `a screen that does not exist yet is not in the rail`() {
        // Search has no television screen — only `LumoTvPlaceholder`. A rail entry
        // onto one would cost every viewer a `DOWN` press to reach a sentence
        // saying the feature is not built, which is the one thing worse than an
        // empty catalogue. It joins the day its screen lands (sprint 10), exactly
        // as series and the library did, and this test is what will change with it.
        assertThat(TvDestinations.map { it.route }).doesNotContain(SearchDestination.route)
    }

    // ---- where the television opens ------------------------------------------

    @Test
    fun `a signed-in set with a source lands on Home`() {
        assertThat(tvStartRoute(AppStart.Ready)).isEqualTo(HomeDestination.route)
    }

    @Test
    fun `Home is the head of the rail, so BACK from any other entry returns to it`() {
        // The rail pops to the graph's start destination. The two being the same
        // screen is what makes "BACK goes Home, BACK on Home leaves" true — and
        // BACK is a key people press repeatedly to get out (US-10).
        assertThat(TvDestinations.first().route).isEqualTo(tvStartRoute(AppStart.Ready))
    }

    @Test
    fun `the three other situations open where they always did`() {
        assertThat(tvStartRoute(AppStart.Loading)).isNull()
        // The activation code, never a sign-in form (US-05).
        assertThat(tvStartRoute(AppStart.SignedOut)).isEqualTo(AuthDestination.route)
        assertThat(tvStartRoute(AppStart.NeedsSource)).isEqualTo(SourceDestination.route)
    }
}
