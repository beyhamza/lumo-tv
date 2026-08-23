package tv.lumo.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.designsystem.component.LumoMobileNavBar
import tv.lumo.android.navigation.LumoMobileNavHost
import tv.lumo.android.navigation.MobileDestinations

/**
 * The phone shell: a navigation bar at the bottom and a NavHost above it.
 *
 * This file and its TV counterpart are the only two places where the two
 * applications legitimately differ. Everything either of them can do lives in a
 * `core:` or `feature:` module, so if logic appears here, it has been written in
 * a place the television cannot reach (docs/architecture.md §3).
 */
@Composable
fun LumoMobileApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            LumoMobileNavHost(
                navController = navController,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            )

            LumoMobileNavBar(
                destinations = MobileDestinations,
                selectedRoute = currentRoute,
                onSelect = { navController.switchTopLevelTo(it) },
            )
        }
    }
}

/**
 * Moves between top-level destinations without stacking them.
 *
 * `popUpTo(start) { saveState }` plus `restoreState` is what makes the bar
 * behave the way people expect: each destination keeps its own scroll position,
 * pressing back from anywhere returns to the start rather than walking the
 * history of every tab visited, and tapping the current tab does nothing instead
 * of pushing a duplicate.
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
