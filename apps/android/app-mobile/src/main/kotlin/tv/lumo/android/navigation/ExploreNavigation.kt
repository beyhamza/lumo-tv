package tv.lumo.android.navigation

import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import tv.lumo.android.R
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.vod.VodDestination

/**
 * *Explore*: the bar entry under which the phone gathers Live, Films and Series
 * (US-017, decisions table — "Direct, Films et Séries dans Explorer").
 *
 * <h2>A nested graph, not a screen</h2>
 *
 * There is no `ExploreMobileScreen`. `explore` is the route of a **navigation
 * graph** whose three children are the three catalogue screens exactly as they
 * were, under the routes they always had. The shell draws a strip of sections
 * above them and that is the whole container.
 *
 * It was chosen over a screen hosting the three behind a tab index because it
 * changes nothing it does not have to:
 *
 * - **The three screens and their routes are untouched.** `live`, `vod` and
 *   `series` still resolve — a saved back stack, a deep link, and the rule that
 *   sends a film of the previous source back to `vod` (`SourceScopedDetails`)
 *   all keep working without knowing Explore exists.
 * - **The open section survives by construction.** It is not a remembered index,
 *   it is *the entry on the back stack*: a rotation restores it, a player pops back
 *   onto it, and leaving for Home and coming back restores it through the same
 *   `saveState`/`restoreState` pair the bar has always used — along with the
 *   section's scroll position and filters, which an index could not have kept.
 * - **Each screen keeps its own view model scope.** Three screens in one entry
 *   would have shared one.
 *
 * It lives in the application because it names three features at once, and the
 * application is the only module allowed to (docs/architecture.md §3).
 */
object ExploreDestination : LumoDestination {
    override val route: String = "explore"
    override val titleRes: Int = R.string.app_mobile_explore
}

/**
 * The sections of Explore, in the order of the strip.
 *
 * The reading order of a catalogue, and the order these three held in the bar
 * before they moved under one entry: somebody who knew where Films was still does.
 * Live first, which also makes it what Explore opens on the first time.
 *
 * All three are always there, whatever the source holds — the ruling recorded on
 * [MobileDestinations]: an empty catalogue is a reply, an absence reads as a bug.
 */
val ExploreSections: List<LumoDestination> = listOf(
    LiveDestination,
    VodDestination,
    SeriesDestination,
)

/**
 * Which entry of the bar the current screen belongs to, or null for none.
 *
 * The bar used to compare its entries with the current route, which was enough
 * while every entry was a screen. `explore` never is the current route — one of
 * its sections is — so the question is asked of the destination's **ancestors**:
 * the first of them that the bar offers is the entry to light.
 *
 * @param hierarchy the routes of the current destination and of its parent
 * graphs, innermost first — `NavDestination.hierarchy`, as strings, so that this
 * stays a function a JVM test can call.
 */
internal fun topLevelRouteOf(hierarchy: List<String?>, topLevel: List<LumoDestination>): String? {
    val offered = topLevel.map { it.route }.toSet()
    return hierarchy.firstOrNull { it in offered }
}

/**
 * Moves between top-level destinations without stacking them.
 *
 * `popUpTo(start) { saveState }` plus `restoreState` is what makes the bar behave
 * the way people expect: each destination keeps its own scroll position, pressing
 * back from anywhere returns to the start — Home — rather than walking the history
 * of every tab visited, and tapping the current tab does nothing instead of
 * pushing a duplicate. `BACK` on Home itself leaves the application, which is the
 * platform's convention for a start destination.
 *
 * For `explore` the saved state is the graph's whole stack, so coming back to it
 * reopens **the section that was open**, where it was scrolled to.
 *
 * It also serves a section named directly — "All channels" on the home screen goes
 * to `live`, not to whichever section was last open. Navigation adds the parent
 * graph by itself, so the bar lights Explore either way.
 */
internal fun NavHostController.switchTopLevelTo(destination: LumoDestination) {
    val alreadyThere = currentBackStackEntry?.destination?.hierarchy
        ?.any { it.route == destination.route } == true
    if (alreadyThere) return

    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Moves between the sections of Explore.
 *
 * The bar's own rule, one level down: pop to the graph rather than to the start
 * destination, so that `BACK` from Films goes to Home and not through Live, and so
 * that each section is saved on the way out and restored on the way back in.
 */
internal fun NavHostController.switchExploreSectionTo(section: LumoDestination) {
    if (currentBackStackEntry?.destination?.route == section.route) return

    navigate(section.route) {
        popUpTo(ExploreDestination.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
