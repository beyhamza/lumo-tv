package tv.lumo.android.feature.series.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.SeriesMobileScreen
import tv.lumo.android.feature.series.SeriesTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.seriesMobileScreen() {
    composable(route = SeriesDestination.route) { SeriesMobileScreen() }
}

fun NavGraphBuilder.seriesTvScreen() {
    composable(route = SeriesDestination.route) { SeriesTvScreen() }
}
