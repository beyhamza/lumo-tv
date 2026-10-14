package tv.lumo.android.navigation

import androidx.navigation.NavHostController
import tv.lumo.android.R
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.watchlist.WatchlistDestination

/**
 * *Library*: the bar entry under which the phone gathers the two halves of "Ma
 * bibliothèque" (US-022) — **favourite channels** and **My list** (*À regarder*).
 *
 * Built exactly as [ExploreDestination] is, for the same reasons: a navigation
 * graph whose children are the two screens under the routes they already had, a
 * strip of sections drawn by the shell above them, and each screen keeping its own
 * view model and its own saved state. The favourites screen is untouched; it gains
 * a neighbour, not a tab index.
 *
 * The graph's title is the bar's "Library", which `feature:favorites` already
 * carries; the two section labels are this application's, because they name two
 * features at once.
 */
object LibraryDestination : LumoDestination {
    override val route: String = "library"
    override val titleRes: Int = tv.lumo.android.feature.favorites.R.string.feature_favorites_library_title
}

/**
 * The favourites screen as a section of Library: same route, the label of the
 * strip ("Favourite channels") rather than the bar's ("Library").
 */
object FavoritesSection : LumoDestination {
    override val route: String = FavoritesDestination.route
    override val titleRes: Int = R.string.app_mobile_library_favorites
}

/** The sections of Library, in the order of the strip. Favourites first: it is what Library opened on. */
val LibrarySections: List<LumoDestination> = listOf(
    FavoritesSection,
    WatchlistDestination,
)

/**
 * Moves between the sections of Library: the rule of
 * [switchExploreSectionTo], one graph over — `BACK` from My list goes Home, not
 * through the favourites.
 */
internal fun NavHostController.switchLibrarySectionTo(section: LumoDestination) {
    if (currentBackStackEntry?.destination?.route == section.route) return

    navigate(section.route) {
        popUpTo(LibraryDestination.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
