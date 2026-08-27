package tv.lumo.androidtv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.navigation.authTvScreen
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.live.navigation.KEY_RETURNED_CHANNEL
import tv.lumo.android.feature.live.navigation.livePlayerTvScreen
import tv.lumo.android.feature.live.navigation.liveTvScreen
import tv.lumo.android.feature.onboarding.navigation.onboardingTvScreen
import tv.lumo.android.feature.search.navigation.searchTvScreen
import tv.lumo.android.feature.series.navigation.seriesTvScreen
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.settings.navigation.settingsTvScreen
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.navigation.sourceTvScreen
import tv.lumo.android.feature.vod.navigation.vodTvScreen

/**
 * The same eight features as the phone, each contributing its television
 * surface instead of its phone one.
 *
 * The routes are identical on purpose: one vocabulary for both applications
 * means a deep link, an analytics event or a bug report reads the same wherever
 * it came from.
 */
@Composable
fun LumoTvNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        onboardingTvScreen()
        authTvScreen()
        sourceTvScreen()
        liveTvScreen(
            onPlay = { channelId, name ->
                navController.navigate(PlayerDestination.routeFor(channelId, name))
            },
        )
        livePlayerTvScreen(
            onBack = { channelId ->
                // US-10: the grid comes back positioned on the channel that was
                // being watched. The id is left on the entry underneath before
                // popping, which is the one moment both entries exist.
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_RETURNED_CHANNEL, channelId)
                navController.popBackStack()
            },
        )
        vodTvScreen()
        seriesTvScreen()
        searchTvScreen()
        settingsTvScreen()
    }
}

/**
 * The same four situations as the phone, and one of them gets a different answer.
 *
 * A television that is not signed in must **not** open a sign-in form. Typing an
 * email and a password on a remote control is the first place people give up
 * (docs/architecture.md §5), which is why the product has a device-code flow at
 * all: the set shows a code, the phone approves it (US-05).
 *
 * `S2-12` built that screen, and this is where it is reached: the television
 * surface of `feature:auth` is the activation, not a sign-in form. Onboarding
 * keeps its placeholder and stops being the way in.
 *
 * @return null while the session is still being read; the caller must not
 * compose the graph yet.
 */
fun tvStartRoute(start: AppStart): String? = when (start) {
    AppStart.Loading -> null
    AppStart.SignedOut -> AuthDestination.route
    // A source is registered on the phone, in practice — but a television that
    // opens on an empty catalogue with no explanation is worse than one that
    // says what is missing, and the source screen is where that is said.
    AppStart.NeedsSource -> SourceDestination.route
    AppStart.Ready -> LiveDestination.route
}

/**
 * The rail's contents, in D-pad order.
 *
 * Live first: it is what the television is for, and it should be the shortest
 * journey from the rail. VOD, series and search are outside the vertical this
 * sprint finishes and are gone from the rail — on a television that matters more
 * than on a phone, because every extra rail item is another `DOWN` press between
 * the viewer and the one thing they came for.
 *
 * Three items also keeps the rail's own rule easy to hold: `RIGHT` enters the
 * content, `LEFT` comes back, and nothing here is reachable only by travelling
 * through everything else (US-10).
 */
val TvDestinations: List<LumoDestination> = listOf(
    LiveDestination,
    SourceDestination,
    SettingsDestination,
)
