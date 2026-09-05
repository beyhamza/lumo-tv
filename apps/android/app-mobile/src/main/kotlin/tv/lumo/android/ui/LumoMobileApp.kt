package tv.lumo.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import tv.lumo.android.core.designsystem.component.LumoMobileNavBar
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.series.EpisodePlayerDestination
import tv.lumo.android.feature.vod.VodPlayerDestination
import tv.lumo.android.navigation.LumoMobileNavHost
import tv.lumo.android.navigation.MobileDestinations
import tv.lumo.android.navigation.mobileStartRoute

/**
 * The phone shell: a navigation bar at the bottom and a NavHost above it.
 *
 * This file and its TV counterpart are the only two places where the two
 * applications legitimately differ. Everything either of them can do lives in a
 * `core:` or `feature:` module, so if logic appears here, it has been written in
 * a place the television cannot reach (docs/architecture.md §3).
 *
 * <h2>Nothing is drawn until the session has been read</h2>
 *
 * A `NavHost` keeps the start destination it was first composed with. Composing
 * it before the session is known would mean guessing, and a wrong guess is not
 * recoverable by re-rendering — it is a signed-in user landing on the onboarding
 * screen on every launch, which is exactly the bug this task exists to remove.
 *
 * <h2>A session change rebuilds the graph, deliberately</h2>
 *
 * `key(startRoute)` throws the whole graph away when the start destination
 * changes, which in practice means when the session appears or disappears. That
 * is the behaviour a sign-out needs and it comes with the back stack cleared,
 * which is the point: after signing out, `BACK` must not walk back into the
 * account. A token rotation does *not* reach here — `AppStartDecision` filters
 * those out, and the comment there explains why it has to.
 */
@Composable
fun LumoMobileApp(
    startState: AppStart,
    navController: NavHostController = rememberNavController(),
) {
    val startRoute = mobileStartRoute(startState)

    if (startRoute == null) {
        // The session is still being read. A plain background rather than a
        // spinner: this lasts a frame or two on a DataStore read, and a spinner
        // that flashes is more noticeable than a background that does not.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            key(startRoute) {
                LumoMobileNavHost(
                    navController = navController,
                    startDestination = startRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                )
            }

            // No bar for a signed-out user. It would offer the catalogue and the
            // settings of an account that does not exist yet, and the only screen
            // reachable while signed out is the one already on display.
            //
            // And none over a video, channel or film alike: the player hides the
            // system bars and fills the panel (US-09), so leaving ours across the
            // bottom would be the one strip of chrome in an otherwise full-screen
            // picture.
            if (startState != AppStart.SignedOut && currentRoute !in PLAYER_ROUTES) {
                LumoMobileNavBar(
                    destinations = MobileDestinations,
                    selectedRoute = currentRoute,
                    onSelect = { navController.switchTopLevelTo(it) },
                )
            }
        }
    }
}

/**
 * The two full-screen players.
 *
 * A set rather than a second `||`, because the list grows with every surface that
 * fills the panel — and the condition it feeds is the one that decides whether a
 * strip of our chrome sits across somebody's film.
 */
private val PLAYER_ROUTES = setOf(
    PlayerDestination.route,
    VodPlayerDestination.route,
    // The third player, missed when it was added: without it the bottom bar sat
    // across an episode while the two other players had the screen to themselves.
    EpisodePlayerDestination.route,
)

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
