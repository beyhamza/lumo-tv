package tv.lumo.android.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.search.SearchDestination
import tv.lumo.android.feature.search.SearchMobileScreen
import tv.lumo.android.feature.search.SearchTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.searchMobileScreen() {
    composable(route = SearchDestination.route) { SearchMobileScreen() }
}

fun NavGraphBuilder.searchTvScreen() {
    composable(route = SearchDestination.route) { SearchTvScreen() }
}
