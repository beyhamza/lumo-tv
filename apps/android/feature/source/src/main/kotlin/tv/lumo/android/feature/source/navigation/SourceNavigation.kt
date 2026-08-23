package tv.lumo.android.feature.source.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.SourceMobileScreen
import tv.lumo.android.feature.source.SourceTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.sourceMobileScreen() {
    composable(route = SourceDestination.route) { SourceMobileScreen() }
}

fun NavGraphBuilder.sourceTvScreen() {
    composable(route = SourceDestination.route) { SourceTvScreen() }
}
