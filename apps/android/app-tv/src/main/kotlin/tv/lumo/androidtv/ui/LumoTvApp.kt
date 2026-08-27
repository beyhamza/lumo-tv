package tv.lumo.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.designsystem.component.LumoTvNavRail
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.androidtv.navigation.LumoTvNavHost
import tv.lumo.androidtv.navigation.TvDestinations
import tv.lumo.androidtv.navigation.tvStartRoute

/**
 * The television shell: a rail on the left, content to its right.
 *
 * The layout is a `Row` and not a `Scaffold` with a bottom bar, and that is the
 * point — Compose's focus search follows the visual arrangement, so a horizontal
 * layout is what makes `RIGHT` move from the rail into the content and `LEFT`
 * come back. A bottom bar on a television would be reachable only by walking
 * `DOWN` past everything on the screen.
 *
 * The start destination and the graph key work exactly as on the phone, and for
 * the same reasons — see `LumoMobileApp`. The one that matters more here: after a
 * sign-out, `BACK` is a physical key people press repeatedly, and it must not
 * walk back into an account that is gone.
 */
@Composable
fun LumoTvApp(
    startState: AppStart,
    navController: NavHostController = rememberNavController(),
) {
    val startRoute = tvStartRoute(startState)

    if (startRoute == null) {
        // Ink rather than nothing: a television draws black between frames
        // anyway, and a background that matches the theme means the first real
        // frame does not arrive as a flash.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LumoColors.Ink),
        )
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(LumoColors.Ink),
    ) {
        LumoTvNavRail(
            destinations = TvDestinations,
            selectedRoute = currentRoute,
            onSelect = { navController.switchTopLevelTo(it) },
        )

        key(startRoute) {
            LumoTvNavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Same top-level behaviour as the phone, for the same reason: BACK from any
 * destination returns to the start rather than replaying every rail item the
 * user has focused. On a television that matters more — BACK is a physical key
 * people press repeatedly to get out (US-10).
 */
private fun NavHostController.switchTopLevelTo(destination: LumoDestination) {
    val alreadyThere = currentBackStackEntry?.destination?.hierarchy
        ?.any { it.route == destination.route } == true
    if (alreadyThere) return

    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
