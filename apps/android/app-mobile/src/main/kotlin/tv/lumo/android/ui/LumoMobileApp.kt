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
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.designsystem.component.LumoMobileNavBar
import tv.lumo.android.core.designsystem.component.LumoMobileSectionTabs
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.series.EpisodePlayerDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.switcher.SourceSwitcherMobile
import tv.lumo.android.feature.vod.VodPlayerDestination
import tv.lumo.android.navigation.ExploreSections
import tv.lumo.android.navigation.LumoMobileNavHost
import tv.lumo.android.navigation.MobileDestinations
import tv.lumo.android.navigation.leaveDetailOfPreviousSource
import tv.lumo.android.navigation.mobileStartRoute
import tv.lumo.android.navigation.switchExploreSectionTo
import tv.lumo.android.navigation.switchTopLevelTo
import tv.lumo.android.navigation.topLevelRouteOf

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
    // The entry of the bar this screen belongs to. Asked of the destination's
    // ancestors and not of its route: a section of Explore is never `explore`.
    val currentTopLevel = topLevelRouteOf(
        hierarchy = backStackEntry?.destination?.hierarchy?.map { it.route }?.toList().orEmpty(),
        topLevel = MobileDestinations,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            // The source being browsed, above every section (US-018). Placed
            // here and written in `feature:source`: the shell decides where it
            // goes and what a change of source does to the back stack, and
            // nothing else. Shown on the same condition as the bar below, for
            // the same two reasons.
            if (startState != AppStart.SignedOut && currentRoute !in PLAYER_ROUTES) {
                SourceSwitcherMobile(
                    onSwitched = { navController.leaveDetailOfPreviousSource() },
                    onLastSourceLost = { navController.switchTopLevelTo(SourceDestination) },
                    // "My sources" (US-024): the list, and everything that can
                    // be done to a source. It left the bar with US-017 and is
                    // reached from here and from Settings.
                    onOpenSources = { navController.switchTopLevelTo(SourceDestination) },
                )
            }

            // The sections of Explore, over the three screens that are one
            // (US-017). Drawn here and not by any of them: the strip names three
            // features at once, and only the application may. Absent on a film's
            // own screen and on a player — those are not sections, they are
            // somewhere one went *from* a section.
            if (ExploreSections.any { it.route == currentRoute }) {
                LumoMobileSectionTabs(
                    sections = ExploreSections,
                    selectedRoute = currentRoute,
                    onSelect = { navController.switchExploreSectionTo(it) },
                )
            }

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
                    selectedRoute = currentTopLevel,
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
