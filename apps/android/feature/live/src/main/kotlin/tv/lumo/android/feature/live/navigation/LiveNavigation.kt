package tv.lumo.android.feature.live.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.LiveMobileScreen
import tv.lumo.android.feature.live.LiveTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.liveMobileScreen() {
    composable(route = LiveDestination.route) { LiveMobileScreen() }
}

fun NavGraphBuilder.liveTvScreen() {
    composable(route = LiveDestination.route) { LiveTvScreen() }
}
