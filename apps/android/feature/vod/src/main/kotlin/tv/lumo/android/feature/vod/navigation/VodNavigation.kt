package tv.lumo.android.feature.vod.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodMobileScreen
import tv.lumo.android.feature.vod.VodTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.vodMobileScreen() {
    composable(route = VodDestination.route) { VodMobileScreen() }
}

fun NavGraphBuilder.vodTvScreen() {
    composable(route = VodDestination.route) { VodTvScreen() }
}
