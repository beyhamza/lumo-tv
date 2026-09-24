package tv.lumo.android.feature.live.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.lumo.android.core.data.DirectView
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
fun NavGraphBuilder.liveMobileScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    onOpenSources: () -> Unit,
) {
    composable(route = LiveDestination.route) { entry ->
        val handle = entry.savedStateHandle
        // The home screen's explicit entry, written just after it navigated here
        // (S9-04-04). Read as a state flow so that a door pressed while Direct is
        // already open is seen too; removed the moment it is handled, so that
        // leaving and coming back — or a restored back stack — does not replay it.
        val requested by handle.getStateFlow<String?>(KEY_REQUESTED_VIEW, null).collectAsState()

        LiveMobileScreen(
            onPlay = onPlay,
            onOpenSources = onOpenSources,
            requestedView = requested?.let(DirectView::fromStored),
            onRequestHandled = { handle.remove<String>(KEY_REQUESTED_VIEW) },
        )
    }
}

fun NavGraphBuilder.livePlayerMobileScreen(onBack: () -> Unit, onOpenSources: () -> Unit) {
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
            onOpenSources = onOpenSources,
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
fun NavGraphBuilder.liveTvScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    onOpenSources: () -> Unit,
) {
    composable(route = LiveDestination.route) { entry ->
        // Written by the player on its way out, read here on the way back in.
        // The saved state handle rather than a shared view model: the two screens
        // are different back stack entries, and this is the channel that
        // Navigation itself provides for exactly this.
        val handle = entry.savedStateHandle
        val requested by handle.getStateFlow<String?>(KEY_REQUESTED_VIEW, null).collectAsState()

        LiveTvScreen(
            onPlay = onPlay,
            onOpenSources = onOpenSources,
            returnedChannelId = handle.get<String>(KEY_RETURNED_CHANNEL),
            // Cleared once used, so that leaving and coming back to this tab
            // later does not re-focus a channel from a previous visit.
            onReturnHandled = { handle.remove<String>(KEY_RETURNED_CHANNEL) },
            // The home screen's explicit entry, handled the same one-shot way
            // (S9-04-04): "All channels" and "TV guide" name a view, and the
            // request is gone as soon as the screen has acted on it.
            requestedView = requested?.let(DirectView::fromStored),
            onRequestHandled = { handle.remove<String>(KEY_REQUESTED_VIEW) },
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
 * Where the home screen's explicit entry leaves the view it asked for (S9-04-04).
 *
 * "All channels" and "TV guide" do not just open Direct: they name one of its two
 * views. The request travels in the Live entry's own saved-state handle — not in
 * the route — because [tv.lumo.android.feature.live.LiveDestination.route] is
 * navigated to generically by both shells' `switchTopLevelTo` and is the phone's
 * Explore section start destination; a `{view}` placeholder there is not a
 * concrete route and would break each of those. The handle is written after the
 * navigation, read as a state flow and removed on handling, so it wins over a
 * restored entry and never replays.
 *
 * Public because the applications write it and this module reads it, and a string
 * literal spelled out twice in several modules is one that will be misspelled
 * once.
 */
const val KEY_REQUESTED_VIEW: String = "requestedDirectView"

/**
 * The television's player (US-10).
 *
 * `onBack` carries the channel id rather than taking none, because returning to
 * the grid is not enough: US-10 asks for the list to come back **positioned on the
 * channel that was being watched**. The application writes that id where the grid
 * will read it; a catalogue of fifteen thousand channels that returns to the top
 * has, in practice, lost the viewer's place.
 */
fun NavGraphBuilder.livePlayerTvScreen(
    onBack: (channelId: String) -> Unit,
    onOpenSources: () -> Unit,
) {
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
            onOpenSources = onOpenSources,
        )
    }
}
