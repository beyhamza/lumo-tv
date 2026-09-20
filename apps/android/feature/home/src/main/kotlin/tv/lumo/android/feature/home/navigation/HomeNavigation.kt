package tv.lumo.android.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.home.HomeDestination
import tv.lumo.android.feature.home.HomeMobileScreen
import tv.lumo.android.feature.home.HomeTvScreen

/**
 * Everywhere the home screen can send somebody.
 *
 * <h2>Why it is a bundle of lambdas and not a `NavController`</h2>
 *
 * Every one of these leads into **another feature** — a player, a detail screen, a
 * catalogue, the add-source form — and a feature never navigates to another
 * feature's route on its own (docs/architecture.md §3). The home screen says what
 * was pressed; the application, which is the only module allowed to know every
 * route, decides where that goes. Gathered in one type because both surfaces take
 * the same set, and eleven parameters repeated in four signatures is where one of
 * them ends up wired to the wrong player.
 *
 * <h2>Resuming carries its position, opening does not</h2>
 *
 * [onResumeFilm] and [onResumeEpisode] are the **primary** action of a "Continue"
 * card and go straight to a player with the stored position — *"l'accueil
 * Continuer lance toujours directement la lecture"* (decisions, 17 September). The
 * position comes from the card: a player never resumes on its own, and these two
 * are the places where somebody chose to.
 *
 * [onOpenLive] serves two controls that lead to the same place — "All channels"
 * under the Live rail and "Live" in the invitation to explore — so that they cannot
 * come to lead to two.
 */
data class HomeActions(
    val onResumeFilm: (filmId: String, sourceId: String, title: String, atMs: Long) -> Unit,
    val onResumeEpisode: (episodeId: String, title: String?, atMs: Long) -> Unit,
    val onOpenFilm: (filmId: String) -> Unit,
    val onOpenSeries: (seriesId: String) -> Unit,
    val onPlayChannel: (channelId: String, name: String?) -> Unit,
    val onOpenLibrary: () -> Unit,
    val onOpenLive: () -> Unit,
    val onOpenFilms: () -> Unit,
    val onOpenSeriesCatalogue: () -> Unit,
    /** The phone's add-source flow. The television has none and never calls it. */
    val onAddSource: () -> Unit,
    val onOpenSources: () -> Unit,
)

/** The phone's home screen. */
fun NavGraphBuilder.homeMobileScreen(actions: HomeActions) {
    composable(route = HomeDestination.route) { HomeMobileScreen(actions = actions) }
}

/**
 * The television's home screen.
 *
 * No saved-state key for "the card that was playing", unlike the channel grid's
 * `KEY_RETURNED_CHANNEL`. The grid needs the player to tell it which channel it
 * ended on, because zapping changes it. A home card launches one thing, so the
 * screen remembers by itself which card was pressed and gives the focus back to
 * it — see `HomeTvScreen`.
 */
fun NavGraphBuilder.homeTvScreen(actions: HomeActions) {
    composable(route = HomeDestination.route) { HomeTvScreen(actions = actions) }
}
