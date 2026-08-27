package tv.lumo.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.SignUpDestination
import tv.lumo.android.feature.auth.navigation.authMobileScreen
import tv.lumo.android.feature.auth.navigation.signUpMobileScreen
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.navigation.liveMobileScreen
import tv.lumo.android.feature.onboarding.navigation.onboardingMobileScreen
import tv.lumo.android.feature.search.navigation.searchMobileScreen
import tv.lumo.android.feature.series.navigation.seriesMobileScreen
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.settings.navigation.settingsMobileScreen
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.navigation.sourceMobileScreen
import tv.lumo.android.feature.vod.navigation.vodMobileScreen

/**
 * Every destination the phone application can reach.
 *
 * The graph is assembled from extension functions each feature contributes, so
 * adding a screen means adding a module and one line here — and a feature never
 * has to know what else exists.
 *
 * VOD, series and search stay registered even though nothing points at them any
 * more (see [MobileDestinations]). Removing them from the graph as well would
 * turn a screen that is merely unreachable into a crash for anything that still
 * names its route — a saved back stack, a deep link, a notification.
 */
@Composable
fun LumoMobileNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        onboardingMobileScreen()
        // The two halves of the way in point at each other, and the application
        // is what holds the wire. A feature never navigates to another feature's
        // route on its own (docs/architecture.md §3) — and these two happen to
        // share a module today, which is exactly the kind of coincidence that
        // stops being true later.
        authMobileScreen(
            onCreateAccount = { navController.navigate(SignUpDestination.route) },
        )
        signUpMobileScreen(
            // `popBackStack` rather than `navigate`: coming back from sign-up to
            // sign-in is a return, not a new destination, and pushing one would
            // build sign-in / sign-up / sign-in for anyone who hesitates twice.
            onSignIn = { navController.popBackStack(AuthDestination.route, false) },
        )
        sourceMobileScreen()
        liveMobileScreen()
        vodMobileScreen()
        seriesMobileScreen()
        searchMobileScreen()
        settingsMobileScreen()
    }
}

/**
 * The screen the application opens on, decided by the session rather than fixed.
 *
 * `core:data` answers the question — signed in or not, and with a source or
 * without — and this is where that answer becomes one of *this* application's
 * routes. The mapping lives here and not in `core:data` because routes belong to
 * feature modules and `core:` may not reference them; it is also the one place
 * where the television is entitled to a different answer, and takes it (US-05).
 *
 * @return null while the session is still being read. The caller must not
 * compose the graph yet: a `NavHost` keeps the start destination it was first
 * given, so guessing here would pin the guess for the whole session.
 */
fun mobileStartRoute(start: AppStart): String? = when (start) {
    AppStart.Loading -> null
    // Sign-in, not onboarding. Onboarding is where the choice between signing in
    // and creating an account is *meant* to be made, and it is still a
    // placeholder with nothing to press — so the way in is the screen that works,
    // and the two halves link to each other directly. When onboarding becomes a
    // real screen, this line moves back to it and those links come out.
    AppStart.SignedOut -> AuthDestination.route
    // Nothing to watch yet, so the first screen is the one that fixes that
    // (US-06, US-07) rather than an empty catalogue.
    AppStart.NeedsSource -> SourceDestination.route
    AppStart.Ready -> LiveDestination.route
}

/**
 * What the bottom bar offers, in order.
 *
 * Three, not eight. VOD, series and search are outside the vertical this sprint
 * finishes, and a demo that offers three doors onto placeholder screens explains
 * itself badly — the reviewer remembers the empty rooms, not the journey that
 * works.
 *
 * Onboarding and authentication are absent for a different reason: they are not
 * places one returns to. They are the way in, and a tab that takes a signed-in
 * user back to a sign-up form is a tab that will be pressed by accident.
 */
val MobileDestinations: List<LumoDestination> = listOf(
    LiveDestination,
    SourceDestination,
    SettingsDestination,
)
