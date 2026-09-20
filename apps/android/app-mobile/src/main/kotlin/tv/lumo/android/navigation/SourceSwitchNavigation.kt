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
 * `popBackStack` first: when the detail was opened from its grid, the grid is
 * underneath, and returning to it is a return — it keeps the grid's own entry and
 * builds no second one.
 *
 * The fallback covers a detail screen with no grid under it. That used to take a
 * deep link; since US-017 the home screen produces it every day — the "Details"
 * button of a "Continue" card opens a film or a series straight over Home. The rule is the same one: back to
 * **the catalogue** of the new source, not to Home, because the story says where a
 * detail of the old source leads and does not make an exception for how one got
 * there. The detail is replaced rather than covered, so BACK from the catalogue
 * goes to Home and never to a film of a source that was left.
 *
 * On the phone the catalogue is a section of Explore, and nothing here has to know
 * it: navigating to `vod` or `series` brings the parent graph with it, so the bar
 * lights Explore and the strip shows the right section.
 *
 * From Home itself nothing moves: it is not in [SourceScopedDetails], it follows
 * the active source by itself, and a change of source simply reloads it.
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
