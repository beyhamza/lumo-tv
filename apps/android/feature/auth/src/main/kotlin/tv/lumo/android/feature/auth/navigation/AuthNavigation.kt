package tv.lumo.android.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.AuthMobileScreen
import tv.lumo.android.feature.auth.AuthTvScreen
import tv.lumo.android.feature.auth.SignUpDestination
import tv.lumo.android.feature.auth.SignUpMobileScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 *
 * <h2>Where the screens go next is the application's business</h2>
 *
 * The cross-links between signing in and creating an account arrive as callbacks
 * rather than being navigated from inside the feature. A feature never depends on
 * another feature (docs/architecture.md §3), and while these two happen to share a
 * module today, a screen that navigated by itself would be a screen that has to be
 * rewritten the first time one of them moves.
 */
fun NavGraphBuilder.authMobileScreen(onCreateAccount: () -> Unit) {
    composable(route = AuthDestination.route) {
        AuthMobileScreen(onCreateAccount = onCreateAccount)
    }
}

fun NavGraphBuilder.signUpMobileScreen(onSignIn: () -> Unit) {
    composable(route = SignUpDestination.route) {
        SignUpMobileScreen(onSignIn = onSignIn)
    }
}

/**
 * The television signs in by device code (US-05, `S2-12`), so it installs only
 * this placeholder for now — and never a registration screen.
 */
fun NavGraphBuilder.authTvScreen() {
    composable(route = AuthDestination.route) { AuthTvScreen() }
}
