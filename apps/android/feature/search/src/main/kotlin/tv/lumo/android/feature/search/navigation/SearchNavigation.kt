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
 *
 * Where a result takes the user is the application's business, not this
 * feature's (docs/architecture.md §3): the screen says "this row was chosen", and
 * the NavHost decides whether that is playback or a fiche.
 */
fun NavGraphBuilder.searchMobileScreen(
    onPlayChannel: (channelId: String, name: String?) -> Unit,
    onOpenFilm: (filmId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
) {
    composable(route = SearchDestination.route) {
        SearchMobileScreen(
            onPlayChannel = onPlayChannel,
            onOpenFilm = onOpenFilm,
            onOpenSeries = onOpenSeries,
        )
    }
}

fun NavGraphBuilder.searchTvScreen() {
    composable(route = SearchDestination.route) { SearchTvScreen() }
}
