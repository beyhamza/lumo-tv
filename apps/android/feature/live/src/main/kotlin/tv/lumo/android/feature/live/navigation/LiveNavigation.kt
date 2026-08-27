package tv.lumo.android.feature.live.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.LiveMobileScreen
import tv.lumo.android.feature.live.LiveTvScreen
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.live.PlayerMobileScreen
import tv.lumo.android.feature.live.PlayerTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 *
 * Where a channel takes the user is the application's business, not this
 * feature's: the callbacks are wired in the NavHost, as they are for the two
 * halves of signing in.
 */
fun NavGraphBuilder.liveMobileScreen(onPlay: (channelId: String, name: String?) -> Unit) {
    composable(route = LiveDestination.route) { LiveMobileScreen(onPlay = onPlay) }
}

fun NavGraphBuilder.livePlayerMobileScreen(onBack: () -> Unit) {
    composable(
        route = PlayerDestination.route,
        arguments = listOf(
            navArgument(PlayerDestination.ARG_CHANNEL_ID) { type = NavType.StringType },
            navArgument(PlayerDestination.ARG_NAME) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        PlayerMobileScreen(
            channelId = entry.arguments?.getString(PlayerDestination.ARG_CHANNEL_ID).orEmpty(),
            channelName = entry.arguments?.getString(PlayerDestination.ARG_NAME)
                ?.takeIf { it.isNotEmpty() },
            onBack = onBack,
        )
    }
}

/**
 * The television's grid (S2-13).
 *
 * `onPlay` is the same callback as the phone's and carries the same two values.
 * What it opens differs: `S2-14` gives the television its own player, because a
 * screen driven by a remote is not a screen driven by a thumb.
 */
fun NavGraphBuilder.liveTvScreen(onPlay: (channelId: String, name: String?) -> Unit) {
    composable(route = LiveDestination.route) { entry ->
        // Written by the player on its way out, read here on the way back in.
        // The saved state handle rather than a shared view model: the two screens
        // are different back stack entries, and this is the channel that
        // Navigation itself provides for exactly this.
        val handle = entry.savedStateHandle

        LiveTvScreen(
            onPlay = onPlay,
            returnedChannelId = handle.get<String>(KEY_RETURNED_CHANNEL),
            // Cleared once used, so that leaving and coming back to this tab
            // later does not re-focus a channel from a previous visit.
            onReturnHandled = { handle.remove<String>(KEY_RETURNED_CHANNEL) },
        )
    }
}

/**
 * Where the television player leaves the channel it was playing, for the grid.
 *
 * Public because the application writes it and this module reads it, and a string
 * literal spelled out twice in two modules is a string literal that will be
 * misspelled once.
 */
const val KEY_RETURNED_CHANNEL: String = "returnedChannelId"

/**
 * The television's player (US-10).
 *
 * `onBack` carries the channel id rather than taking none, because returning to
 * the grid is not enough: US-10 asks for the list to come back **positioned on the
 * channel that was being watched**. The application writes that id where the grid
 * will read it; a catalogue of fifteen thousand channels that returns to the top
 * has, in practice, lost the viewer's place.
 */
fun NavGraphBuilder.livePlayerTvScreen(onBack: (channelId: String) -> Unit) {
    composable(
        route = PlayerDestination.route,
        arguments = listOf(
            navArgument(PlayerDestination.ARG_CHANNEL_ID) { type = NavType.StringType },
            navArgument(PlayerDestination.ARG_NAME) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        PlayerTvScreen(
            channelId = entry.arguments?.getString(PlayerDestination.ARG_CHANNEL_ID).orEmpty(),
            channelName = entry.arguments?.getString(PlayerDestination.ARG_NAME)
                ?.takeIf { it.isNotEmpty() },
            onBack = onBack,
        )
    }
}
