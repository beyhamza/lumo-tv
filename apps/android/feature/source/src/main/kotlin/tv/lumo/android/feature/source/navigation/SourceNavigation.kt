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
/**
 * @param onDiscoverCatalogue where *Discover my catalogue* leads once a newly added
 * source is ready (US-024). The application's wire: this feature does not know
 * that Home exists.
 */
fun NavGraphBuilder.sourceMobileScreen(onDiscoverCatalogue: () -> Unit) {
    composable(route = SourceDestination.route) {
        SourceMobileScreen(onDiscoverCatalogue = onDiscoverCatalogue)
    }
}

fun NavGraphBuilder.sourceTvScreen() {
    composable(route = SourceDestination.route) { SourceTvScreen() }
}
