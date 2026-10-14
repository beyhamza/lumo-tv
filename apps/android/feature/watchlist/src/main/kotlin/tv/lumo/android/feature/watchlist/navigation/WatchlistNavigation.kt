package tv.lumo.android.feature.watchlist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.watchlist.WatchlistDestination
import tv.lumo.android.feature.watchlist.WatchlistMobileScreen

/**
 * Where "My list" leads. Every way out names another feature — a film's sheet, a
 * series' sheet, the two catalogues of the empty state — so every one is a wire
 * the application holds (docs/architecture.md §3).
 */
data class WatchlistActions(
    val onOpenFilm: (filmId: String) -> Unit,
    val onOpenSeries: (seriesId: String) -> Unit,
    val onOpenFilms: () -> Unit,
    val onOpenSeriesCatalogue: () -> Unit,
)

fun NavGraphBuilder.watchlistMobileScreen(actions: WatchlistActions) {
    composable(route = WatchlistDestination.route) { WatchlistMobileScreen(actions = actions) }
}
