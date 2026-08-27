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

/** The television plays through its own screen (US-10, `S2-14`). */
fun NavGraphBuilder.liveTvScreen() {
    composable(route = LiveDestination.route) { LiveTvScreen() }
}
