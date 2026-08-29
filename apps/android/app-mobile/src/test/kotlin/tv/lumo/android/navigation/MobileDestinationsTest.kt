package tv.lumo.android.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.vod.VodDestination

/**
 * What the bottom bar offers (US-13).
 *
 * <h2>Six lines of `buildList`, and worth a test anyway</h2>
 *
 * Because the requirement is a *negative* one, and a negative requirement is the
 * kind that gets silently undone. US-13 asks that a source with no films must not
 * show a films tab — a tab that opens onto an empty grid is a promise nobody can
 * keep, and most M3U playlists carry channels and nothing else.
 *
 * Nothing crashes if this regresses. The bar simply gains a door onto an empty
 * room, on exactly the sources whose owners would never see a film there, and it
 * would be found by a user rather than by a build.
 *
 * The order is asserted too, and deliberately: it is the reading order of a
 * catalogue — the channels, then the films, then what has been picked out of
 * them — and a list that quietly reorders itself moves a target under somebody's
 * thumb between two launches.
 */
class MobileDestinationsTest {

    @Test
    fun `a source with no films offers no films tab`() {
        val routes = mobileDestinations(hasFilms = false).map { it.route }

        assertThat(routes).doesNotContain(VodDestination.route)
        assertThat(routes).containsExactly(
            LiveDestination.route,
            FavoritesDestination.route,
            SourceDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `a source with films offers the tab, after the channels`() {
        val routes = mobileDestinations(hasFilms = true).map { it.route }

        assertThat(routes).containsExactly(
            LiveDestination.route,
            VodDestination.route,
            FavoritesDestination.route,
            SourceDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `nothing else moves when the tab appears`() {
        val without = mobileDestinations(hasFilms = false).map { it.route }
        val with = mobileDestinations(hasFilms = true).map { it.route }

        // The films tab is inserted, and that is the whole difference. A bar that
        // reshuffled around it would relocate four other targets for a fifth one
        // arriving a moment after launch.
        assertThat(with.filterNot { it == VodDestination.route }).isEqualTo(without)
    }
}
