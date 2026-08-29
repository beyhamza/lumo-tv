package tv.lumo.android.feature.series.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.lumo.android.feature.series.EpisodePlayerDestination
import tv.lumo.android.feature.series.EpisodePlayerMobileScreen
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.SeriesDetailDestination
import tv.lumo.android.feature.series.SeriesDetailMobileScreen
import tv.lumo.android.feature.series.SeriesMobileScreen
import tv.lumo.android.feature.series.SeriesTvScreen

/**
 * Three screens, and the wires between them held by the application.
 *
 * Where a series takes the viewer is not this feature's business
 * (docs/architecture.md §3): the grid says "this series was chosen", the detail
 * screen says "play this episode", and the NavHost decides what that means.
 */
fun NavGraphBuilder.seriesMobileScreen(onOpenSeries: (seriesId: String) -> Unit) {
    composable(route = SeriesDestination.route) {
        SeriesMobileScreen(onOpenSeries = onOpenSeries)
    }
}

fun NavGraphBuilder.seriesDetailMobileScreen(
    onPlay: (episodeId: String, title: String?) -> Unit,
    onBack: () -> Unit,
) {
    composable(
        route = SeriesDetailDestination.route,
        arguments = listOf(
            navArgument(SeriesDetailDestination.ARG_SERIES_ID) { type = NavType.StringType },
        ),
    ) { entry ->
        SeriesDetailMobileScreen(
            seriesId = entry.arguments?.getString(SeriesDetailDestination.ARG_SERIES_ID).orEmpty(),
            onPlay = onPlay,
            onBack = onBack,
        )
    }
}

fun NavGraphBuilder.episodePlayerMobileScreen(onBack: () -> Unit) {
    composable(
        route = EpisodePlayerDestination.route,
        arguments = listOf(
            navArgument(EpisodePlayerDestination.ARG_EPISODE_ID) { type = NavType.StringType },
            navArgument(EpisodePlayerDestination.ARG_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        EpisodePlayerMobileScreen(
            episodeId = entry.arguments?.getString(EpisodePlayerDestination.ARG_EPISODE_ID)
                .orEmpty(),
            title = entry.arguments?.getString(EpisodePlayerDestination.ARG_TITLE)
                ?.takeIf { it.isNotEmpty() },
            onBack = onBack,
        )
    }
}

/** The television's grid. Still the placeholder — it is S6-06's task. */
fun NavGraphBuilder.seriesTvScreen() {
    composable(route = SeriesDestination.route) { SeriesTvScreen() }
}
