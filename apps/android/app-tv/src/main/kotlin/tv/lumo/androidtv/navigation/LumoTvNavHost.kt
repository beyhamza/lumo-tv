package tv.lumo.androidtv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.navigation.authTvScreen
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.navigation.liveTvScreen
import tv.lumo.android.feature.onboarding.OnboardingDestination
import tv.lumo.android.feature.onboarding.navigation.onboardingTvScreen
import tv.lumo.android.feature.search.SearchDestination
import tv.lumo.android.feature.search.navigation.searchTvScreen
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.navigation.seriesTvScreen
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.settings.navigation.settingsTvScreen
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.navigation.sourceTvScreen
import tv.lumo.android.feature.vod.VodDestination
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
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        // The television's first screen is activation, not sign-in: typing an
        // email and a password on a remote control is the first place people
        // give up (docs/architecture.md §5). US-05 replaces this placeholder
        // with the device-code flow.
        startDestination = OnboardingDestination.route,
        modifier = modifier,
    ) {
        onboardingTvScreen()
        authTvScreen()
        sourceTvScreen()
        liveTvScreen()
        vodTvScreen()
        seriesTvScreen()
        searchTvScreen()
        settingsTvScreen()
    }
}

/**
 * The rail's contents, in D-pad order.
 *
 * Live first among the content destinations: it is what the television is for,
 * and it should be the shortest journey from the rail.
 */
val TvDestinations: List<LumoDestination> = listOf(
    OnboardingDestination,
    AuthDestination,
    SourceDestination,
    LiveDestination,
    VodDestination,
    SeriesDestination,
    SearchDestination,
    SettingsDestination,
)
