package tv.lumo.android.feature.series.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.lumo.android.feature.series.EpisodePlayerDestination
import tv.lumo.android.feature.series.EpisodePlayerMobileScreen
import tv.lumo.android.feature.series.EpisodePlayerTvScreen
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.SeriesDetailDestination
import tv.lumo.android.feature.series.SeriesDetailMobileScreen
import tv.lumo.android.feature.series.SeriesDetailTvScreen
import tv.lumo.android.feature.series.SeriesMobileScreen
import tv.lumo.android.feature.series.SeriesTvScreen

/**
 * Three screens, and the wires between them held by the application.
 *
 * Where a series takes the viewer is not this feature's business
 * (docs/architecture.md §3): the grid says "this series was chosen", the detail
 * screen says "play this episode", and the NavHost decides what that means.
 */
fun NavGraphBuilder.seriesMobileScreen(
    onOpenSeries: (seriesId: String) -> Unit,
    onPlay: (episodeId: String, title: String?, atMs: Long) -> Unit,
) {
    composable(route = SeriesDestination.route) {
        SeriesMobileScreen(onOpenSeries = onOpenSeries, onPlay = onPlay)
    }
}

fun NavGraphBuilder.seriesDetailMobileScreen(
    onPlay: (episodeId: String, title: String?, atMs: Long) -> Unit,
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
            navArgument(EpisodePlayerDestination.ARG_AT) {
                type = NavType.LongType
                // The beginning. A player never resumes on its own — the value
                // here is one somebody chose on the screen before it.
                defaultValue = 0L
            },
        ),
    ) { entry ->
        EpisodePlayerMobileScreen(
            episodeId = entry.arguments?.getString(EpisodePlayerDestination.ARG_EPISODE_ID)
                .orEmpty(),
            title = entry.arguments?.getString(EpisodePlayerDestination.ARG_TITLE)
                ?.takeIf { it.isNotEmpty() },
            resumeFromMs = entry.arguments?.getLong(EpisodePlayerDestination.ARG_AT) ?: 0L,
            onBack = onBack,
        )
    }
}

/**
 * Where the television's series screen leaves the series it was showing, for the
 * grid.
 *
 * Public because the application writes it and this module reads it — a string
 * literal spelled out twice in two modules is one that will be misspelled once.
 * Films and channels have their own, and the three are separate on purpose: an id
 * left where another kind is expected would scroll the wrong grid to nothing.
 */
const val KEY_RETURNED_SERIES: String = "returnedSeriesId"

/**
 * The television's grid (S6-06).
 *
 * `onOpenSeries` opens the series' own screen, which is the only thing it could
 * do: a series is not played, an episode is.
 */
fun NavGraphBuilder.seriesTvScreen(onOpenSeries: (seriesId: String) -> Unit) {
    composable(route = SeriesDestination.route) { entry ->
        // Written by the detail screen on its way out, read here on the way back
        // in. The saved state handle rather than a shared view model: the two
        // screens are different back stack entries, and this is the channel
        // Navigation itself provides for exactly this.
        val handle = entry.savedStateHandle

        SeriesTvScreen(
            onOpenSeries = onOpenSeries,
            returnedSeriesId = handle.get<String>(KEY_RETURNED_SERIES),
            // Cleared once used, so leaving and coming back to this tab later
            // does not re-focus a series from a previous visit.
            onReturnHandled = { handle.remove<String>(KEY_RETURNED_SERIES) },
        )
    }
}

/**
 * The television's series screen (S6-06).
 *
 * `onBack` carries the series id rather than taking none, because returning to the
 * grid is not enough: US-10's rule is that the list comes back **positioned on
 * what was being looked at**, and fifty thousand posters returning to the head
 * have lost the viewer's place.
 */
fun NavGraphBuilder.seriesDetailTvScreen(
    onPlay: (episodeId: String, title: String?, atMs: Long) -> Unit,
    onBack: (seriesId: String) -> Unit,
) {
    composable(
        route = SeriesDetailDestination.route,
        arguments = listOf(
            navArgument(SeriesDetailDestination.ARG_SERIES_ID) { type = NavType.StringType },
        ),
    ) { entry ->
        SeriesDetailTvScreen(
            seriesId = entry.arguments?.getString(SeriesDetailDestination.ARG_SERIES_ID)
                .orEmpty(),
            onPlay = onPlay,
            onBack = onBack,
        )
    }
}

/**
 * The television's episode player (S6-06).
 *
 * `onBack` takes nothing: it returns to the series' own screen, which is the entry
 * underneath, and that screen is what tells the grid where to put the focus.
 *
 * **One entry, however many episodes are watched.** Advancing to the next one
 * happens inside the screen rather than through this graph, so an evening of six
 * episodes leaves `BACK` one press from the series instead of six.
 */
fun NavGraphBuilder.episodePlayerTvScreen(onBack: () -> Unit) {
    composable(
        route = EpisodePlayerDestination.route,
        arguments = listOf(
            navArgument(EpisodePlayerDestination.ARG_EPISODE_ID) { type = NavType.StringType },
            navArgument(EpisodePlayerDestination.ARG_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(EpisodePlayerDestination.ARG_AT) {
                type = NavType.LongType
                // The beginning. A player never resumes on its own — the value
                // here is one somebody chose on the screen before it.
                defaultValue = 0L
            },
        ),
    ) { entry ->
        EpisodePlayerTvScreen(
            episodeId = entry.arguments?.getString(EpisodePlayerDestination.ARG_EPISODE_ID)
                .orEmpty(),
            title = entry.arguments?.getString(EpisodePlayerDestination.ARG_TITLE)
                ?.takeIf { it.isNotEmpty() },
            resumeFromMs = entry.arguments?.getLong(EpisodePlayerDestination.ARG_AT) ?: 0L,
            onBack = onBack,
        )
    }
}
