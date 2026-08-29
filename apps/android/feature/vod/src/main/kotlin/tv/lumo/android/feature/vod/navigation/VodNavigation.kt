package tv.lumo.android.feature.vod.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodDetailDestination
import tv.lumo.android.feature.vod.VodDetailMobileScreen
import tv.lumo.android.feature.vod.VodMobileScreen
import tv.lumo.android.feature.vod.VodPlayerDestination
import tv.lumo.android.feature.vod.VodPlayerMobileScreen
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
    onPlay: (filmId: String, title: String?) -> Unit,
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
        ),
    ) { entry ->
        VodPlayerMobileScreen(
            filmId = entry.arguments?.getString(VodPlayerDestination.ARG_FILM_ID).orEmpty(),
            title = entry.arguments?.getString(VodPlayerDestination.ARG_TITLE)
                ?.takeIf { it.isNotEmpty() },
            onBack = onBack,
        )
    }
}

/** The television's grid. Still the placeholder — it is S5-09's task. */
fun NavGraphBuilder.vodTvScreen() {
    composable(route = VodDestination.route) { VodTvScreen() }
}
