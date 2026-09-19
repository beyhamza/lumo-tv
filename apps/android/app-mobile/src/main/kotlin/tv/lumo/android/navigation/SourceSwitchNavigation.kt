package tv.lumo.android.navigation

import androidx.navigation.NavHostController
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.SeriesDetailDestination
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodDetailDestination

/**
 * The screens that show **one item of one source**, and the catalogue each one
 * belongs to (US-018).
 *
 * A film or a series on screen when the source changes is an item of the source
 * that was just left. Keeping it would leave somebody reading a synopsis — and
 * one press from playing a film — out of a catalogue they are no longer in. So
 * the rule is: from a detail screen, back to its catalogue, which by then shows
 * the new source; from a catalogue, nowhere — the section stays open.
 *
 * Here and not in a feature, because it names two features' routes at once and
 * the application is the only module allowed to know both. A map rather than a
 * `when`, so the test can walk it.
 *
 * The players are absent on purpose. The shell draws no switcher over a video,
 * so the source cannot be changed from one; what happens to a playback whose
 * source is deleted elsewhere is the sixty-second check of US-024, not this.
 */
internal val SourceScopedDetails: Map<String, String> = mapOf(
    VodDetailDestination.route to VodDestination.route,
    SeriesDetailDestination.route to SeriesDestination.route,
)

/** Where to go back to after a change of source, or null to stay put. */
internal fun catalogueRootAfterSourceSwitch(currentRoute: String?): String? =
    SourceScopedDetails[currentRoute]

/**
 * Leaves a detail screen that belongs to the source that was just left.
 *
 * `popBackStack` first: the grid is underneath in every path the application
 * offers, and returning to it is a return — it keeps the grid's own entry and
 * builds no second one. The fallback covers a detail screen with no grid under
 * it, which only a deep link can produce.
 */
internal fun NavHostController.leaveDetailOfPreviousSource() {
    val detail = currentBackStackEntry?.destination?.route ?: return
    val root = catalogueRootAfterSourceSwitch(detail) ?: return

    if (!popBackStack(root, inclusive = false)) {
        navigate(root) {
            popUpTo(detail) { inclusive = true }
            launchSingleTop = true
        }
    }
}
