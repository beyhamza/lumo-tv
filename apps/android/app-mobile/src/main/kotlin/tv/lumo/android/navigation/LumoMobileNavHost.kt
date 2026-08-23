package tv.lumo.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.navigation.authMobileScreen
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.navigation.liveMobileScreen
import tv.lumo.android.feature.onboarding.OnboardingDestination
import tv.lumo.android.feature.onboarding.navigation.onboardingMobileScreen
import tv.lumo.android.feature.search.SearchDestination
import tv.lumo.android.feature.search.navigation.searchMobileScreen
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.navigation.seriesMobileScreen
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.settings.navigation.settingsMobileScreen
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.navigation.sourceMobileScreen
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.navigation.vodMobileScreen

/**
 * Every destination the phone application can reach.
 *
 * The graph is assembled from extension functions each feature contributes, so
 * adding a screen means adding a module and one line here — and a feature never
 * has to know what else exists.
 */
@Composable
fun LumoMobileNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        // Onboarding first: without a source there is nothing to show, and the
        // first thing the product asks of a new user is a playlist or an Xtream
        // account (US-06, US-07). Once sessions are wired up this becomes a
        // decision based on SessionManager.isSignedIn.
        startDestination = OnboardingDestination.route,
        modifier = modifier,
    ) {
        onboardingMobileScreen()
        authMobileScreen()
        sourceMobileScreen()
        liveMobileScreen()
        vodMobileScreen()
        seriesMobileScreen()
        searchMobileScreen()
        settingsMobileScreen()
    }
}

/**
 * What the bottom bar offers, in order.
 *
 * All eight while the product is a scaffold, so every placeholder is reachable.
 * When the real screens land this shrinks to the four or five a thumb can reach
 * comfortably, and the rest move behind them.
 */
val MobileDestinations: List<LumoDestination> = listOf(
    OnboardingDestination,
    AuthDestination,
    SourceDestination,
    LiveDestination,
    VodDestination,
    SeriesDestination,
    SearchDestination,
    SettingsDestination,
)
