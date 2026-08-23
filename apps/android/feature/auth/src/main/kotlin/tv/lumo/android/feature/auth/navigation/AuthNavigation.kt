package tv.lumo.android.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.AuthMobileScreen
import tv.lumo.android.feature.auth.AuthTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.authMobileScreen() {
    composable(route = AuthDestination.route) { AuthMobileScreen() }
}

fun NavGraphBuilder.authTvScreen() {
    composable(route = AuthDestination.route) { AuthTvScreen() }
}
