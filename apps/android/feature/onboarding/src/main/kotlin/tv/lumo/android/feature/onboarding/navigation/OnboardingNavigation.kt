package tv.lumo.android.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.onboarding.OnboardingDestination
import tv.lumo.android.feature.onboarding.OnboardingMobileScreen
import tv.lumo.android.feature.onboarding.OnboardingTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.onboardingMobileScreen() {
    composable(route = OnboardingDestination.route) { OnboardingMobileScreen() }
}

fun NavGraphBuilder.onboardingTvScreen() {
    composable(route = OnboardingDestination.route) { OnboardingTvScreen() }
}
