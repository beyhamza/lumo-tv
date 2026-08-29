package tv.lumo.android.feature.vod.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodDetailDestination
import tv.lumo.android.feature.vod.VodDetailMobileScreen
import tv.lumo.android.feature.vod.VodDetailTvScreen
import tv.lumo.android.feature.vod.VodMobileScreen
import tv.lumo.android.feature.vod.VodPlayerDestination
import tv.lumo.android.feature.vod.VodPlayerMobileScreen
import tv.lumo.android.feature.vod.VodPlayerTvScreen
import tv.lumo.android.feature.vod.VodTvScreen

/**
 * Three screens, and the wires between them held by the application.
 *
 * Where a film takes the user is not this feature's business (docs/architecture.md
 * §3): the grid says "this film was chosen", the detail screen says "play this",
 * and the NavHost decides what that means. It is the same arrangement the channel
 * feature uses, and it is what lets the television compose the same routes with a
 * different set of surfaces.
 */
fun NavGraphBuilder.vodMobileScreen(onOpenFilm: (filmId: String) -> Unit) {
    composable(route = VodDestination.route) { VodMobileScreen(onOpenFilm = onOpenFilm) }
}

fun NavGraphBuilder.vodDetailMobileScreen(
    onPlay: (filmId: String, sourceId: String, title: String?, atMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    composable(
        route = VodDetailDestination.route,
        arguments = listOf(
            navArgument(VodDetailDestination.ARG_FILM_ID) { type = NavType.StringType },
        ),
    ) { entry ->
        VodDetailMobileScreen(
            filmId = entry.arguments?.getString(VodDetailDestination.ARG_FILM_ID).orEmpty(),
            onPlay = onPlay,
            onBack = onBack,
        )
    }
}

fun NavGraphBuilder.vodPlayerMobileScreen(onBack: () -> Unit) {
    composable(
        route = VodPlayerDestination.route,
        arguments = listOf(
            navArgument(VodPlayerDestination.ARG_FILM_ID) { type = NavType.StringType },
            navArgument(VodPlayerDestination.ARG_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(VodPlayerDestination.ARG_SOURCE_ID) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(VodPlayerDestination.ARG_AT) {
                type = NavType.LongType
                // The beginning. A player never resumes on its own — the value
                // here is one somebody chose on the film's own screen.
                defaultValue = 0L
            },
        ),
    ) { entry ->
        VodPlayerMobileScreen(
            filmId = entry.arguments?.getString(VodPlayerDestination.ARG_FILM_ID).orEmpty(),
            sourceId = entry.arguments?.getString(VodPlayerDestination.ARG_SOURCE_ID).orEmpty(),
            title = entry.arguments?.getString(VodPlayerDestination.ARG_TITLE)
                ?.takeIf { it.isNotEmpty() },
            resumeFromMs = entry.arguments?.getLong(VodPlayerDestination.ARG_AT) ?: 0L,
            onBack = onBack,
        )
    }
}

/**
 * Where the television's detail screen leaves the film it was showing, for the
 * grid.
 *
 * Public because the application writes it and this module reads it — a string
 * literal spelled out twice in two modules is a string literal that will be
 * misspelled once. The channel side has its own, and the two are separate on
 * purpose: a film id left where a channel id is expected would scroll the wrong
 * grid to nothing.
 */
const val KEY_RETURNED_FILM: String = "returnedFilmId"

/**
 * The television's grid (S5-09).
 *
 * `onOpenFilm` opens the film's own screen rather than playing it, which is the
 * one place this differs from `liveTvScreen`: a channel is played, a film is
 * chosen, and choosing needs a year, a running time and a synopsis no card holds.
 */
fun NavGraphBuilder.vodTvScreen(onOpenFilm: (filmId: String) -> Unit) {
    composable(route = VodDestination.route) { entry ->
        // Written by the detail screen on its way out, read here on the way back
        // in. The saved state handle rather than a shared view model: the two
        // screens are different back stack entries, and this is the channel
        // Navigation itself provides for exactly this.
        val handle = entry.savedStateHandle

        VodTvScreen(
            onOpenFilm = onOpenFilm,
            returnedFilmId = handle.get<String>(KEY_RETURNED_FILM),
            // Cleared once used, so leaving and coming back to this tab later
            // does not re-focus a film from a previous visit.
            onReturnHandled = { handle.remove<String>(KEY_RETURNED_FILM) },
        )
    }
}

/**
 * The television's film screen (S5-09).
 *
 * `onBack` carries the film id rather than taking none, because returning to the
 * grid is not enough: US-10's rule is that the list comes back **positioned on
 * what was being looked at**, and thirty thousand posters returning to the head
 * have lost the viewer's place.
 */
fun NavGraphBuilder.vodDetailTvScreen(
    onPlay: (filmId: String, sourceId: String, title: String?, atMs: Long) -> Unit,
    onBack: (filmId: String) -> Unit,
) {
    composable(
        route = VodDetailDestination.route,
        arguments = listOf(
            navArgument(VodDetailDestination.ARG_FILM_ID) { type = NavType.StringType },
        ),
    ) { entry ->
        VodDetailTvScreen(
            filmId = entry.arguments?.getString(VodDetailDestination.ARG_FILM_ID).orEmpty(),
            onPlay = onPlay,
            onBack = onBack,
        )
    }
}

/**
 * The television's film player (S5-09).
 *
 * `onBack` takes nothing: it returns to the film's own screen, which is the entry
 * underneath, and that screen is what tells the grid where to put the focus. The
 * channel player has to carry an id because there is no screen between it and the
 * grid.
 */
fun NavGraphBuilder.vodPlayerTvScreen(onBack: () -> Unit) {
    composable(
        route = VodPlayerDestination.route,
        arguments = listOf(
            navArgument(VodPlayerDestination.ARG_FILM_ID) { type = NavType.StringType },
            navArgument(VodPlayerDestination.ARG_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(VodPlayerDestination.ARG_SOURCE_ID) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(VodPlayerDestination.ARG_AT) {
                type = NavType.LongType
                // The beginning. A player never resumes on its own — the value
                // here is one somebody chose on the film's own screen.
                defaultValue = 0L
            },
        ),
    ) { entry ->
        VodPlayerTvScreen(
            filmId = entry.arguments?.getString(VodPlayerDestination.ARG_FILM_ID).orEmpty(),
            sourceId = entry.arguments?.getString(VodPlayerDestination.ARG_SOURCE_ID).orEmpty(),
            title = entry.arguments?.getString(VodPlayerDestination.ARG_TITLE)
                ?.takeIf { it.isNotEmpty() },
            resumeFromMs = entry.arguments?.getLong(VodPlayerDestination.ARG_AT) ?: 0L,
            onBack = onBack,
        )
    }
}
