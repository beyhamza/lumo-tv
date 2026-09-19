package tv.lumo.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import tv.lumo.android.core.designsystem.component.LumoWordmark
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.designsystem.component.LumoTvNavRail
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.series.EpisodePlayerDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.switcher.SourceSwitcherTv
import tv.lumo.android.feature.vod.VodPlayerDestination
import tv.lumo.androidtv.navigation.LumoTvNavHost
import tv.lumo.androidtv.navigation.TvDestinations
import tv.lumo.androidtv.navigation.leaveDetailOfPreviousSource
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
        // The splash of the TV canvas: the mark, and three dots that say
        // something is being read. No focus, no interaction — the session is
        // being decrypted and the graph will replace this on its own.
        Splash()
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(LumoColors.Ink),
    ) {
        // No rail for a signed-out set, for the same reason as the phone's bar —
        // and one more that is particular to a television: the rail is the first
        // thing the D-pad lands on, so an activation screen behind a rail of
        // destinations that all refuse is a screen whose one useful control is
        // the hardest to reach (US-10).
        //
        // And none over a video. US-10 asks for full screen and for no overlay at
        // rest, and a rail down the left is both — plus a focus target competing
        // with the picture for a D-pad that should only be listening for OK and
        // BACK.
        if (startState != AppStart.SignedOut && currentRoute !in PLAYER_ROUTES) {
            LumoTvNavRail(
                destinations = TvDestinations,
                selectedRoute = currentRoute,
                onSelect = { navController.switchTopLevelTo(it) },
                // The source being browsed, at the foot of the rail (US-018).
                // Inside the rail rather than beside it, so it opens no focus
                // zone of its own: `DOWN` past the last destination reaches it,
                // `RIGHT` leaves for the content. Written in `feature:source`;
                // the shell only places it and owns what a change of source does
                // to the back stack.
                footer = {
                    SourceSwitcherTv(
                        onSwitched = { navController.leaveDetailOfPreviousSource() },
                        onLastSourceLost = { navController.switchTopLevelTo(SourceDestination) },
                        // The full "My sources" screen is S8-05's. Until then
                        // the entry leads to the screen that exists.
                        onOpenSources = { navController.switchTopLevelTo(SourceDestination) },
                    )
                },
            )
        }

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
 * `TV1 — Splash` in the canvas: the mark centred on ink, a row of three dots
 * under it with the first one lit. It is on screen for the time it takes to read
 * an encrypted DataStore, which is short enough that the dots do not animate —
 * a loader that has time to spin is a loader that should not exist.
 */
@Composable
private fun Splash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumoColors.Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xl),
        ) {
            LumoWordmark(height = 88.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (index == 0) LumoColors.Accent else LumoColors.SurfaceRaised),
                    )
                }
            }
        }
    }
}

/**
 * The two full-screen players.
 *
 * A set rather than a second `||`: the list grows with every surface that fills
 * the panel, and what it feeds is the decision of whether a rail of focus targets
 * sits down the left of somebody's film.
 */
private val PLAYER_ROUTES = setOf(
    PlayerDestination.route,
    VodPlayerDestination.route,
    // Added after being forgotten: an episode played with the rail down its left,
    // a stack of focus targets over the picture that US-10 says must be alone.
    EpisodePlayerDestination.route,
)

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
