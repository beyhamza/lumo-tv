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
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.favorites.navigation.favoritesMobileScreen
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.live.navigation.liveMobileScreen
import tv.lumo.android.feature.live.navigation.livePlayerMobileScreen
import tv.lumo.android.feature.onboarding.navigation.onboardingMobileScreen
import tv.lumo.android.feature.search.navigation.searchMobileScreen
import tv.lumo.android.feature.series.navigation.seriesMobileScreen
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.settings.navigation.settingsMobileScreen
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.navigation.sourceMobileScreen
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodDetailDestination
import tv.lumo.android.feature.vod.VodPlayerDestination
import tv.lumo.android.feature.vod.navigation.vodDetailMobileScreen
import tv.lumo.android.feature.vod.navigation.vodMobileScreen
import tv.lumo.android.feature.vod.navigation.vodPlayerMobileScreen

/**
 * Every destination the phone application can reach.
 *
 * The graph is assembled from extension functions each feature contributes, so
 * adding a screen means adding a module and one line here — and a feature never
 * has to know what else exists.
 *
 * Series and search stay registered even though nothing points at them any more
 * (see [mobileDestinations]). Removing them from the graph as well would turn a
 * screen that is merely unreachable into a crash for anything that still names
 * its route — a saved back stack, a deep link, a notification.
 *
 * The same applies to the three film routes on a source that carries no films:
 * the tab is gone, the routes stay. A deep link to a film is not made invalid by
 * a playlist that has none.
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
        liveMobileScreen(
            onPlay = { channelId, name ->
                navController.navigate(PlayerDestination.routeFor(channelId, name))
            },
        )
        livePlayerMobileScreen(onBack = { navController.popBackStack() })
        // Same player, and the wire is held here rather than in either feature:
        // favourites has no business knowing that feature:live exists.
        favoritesMobileScreen(
            onPlay = { channelId, name ->
                navController.navigate(PlayerDestination.routeFor(channelId, name))
            },
        )
        vodMobileScreen(
            onOpenFilm = { filmId ->
                navController.navigate(VodDetailDestination.routeFor(filmId))
            },
        )
        vodDetailMobileScreen(
            onPlay = { filmId, sourceId, title, atMs ->
                navController.navigate(
                    VodPlayerDestination.routeFor(filmId, sourceId, title, atMs),
                )
            },
            onBack = { navController.popBackStack() },
        )
        vodPlayerMobileScreen(onBack = { navController.popBackStack() })
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
 * Four or five, not eight. Series and search are still placeholders, and a bar
 * that offers doors onto empty rooms explains itself badly — the reviewer
 * remembers the empty rooms, not the journey that works.
 *
 * **Films are the fifth, and only when the source has any.** US-13 asks for
 * exactly that, and it is not a refinement: most M3U playlists carry channels and
 * nothing else, and a tab that opens onto an empty grid is a promise nobody can
 * keep. `CatalogueSections` answers the question with one request, and the answer
 * is false until something says otherwise — so the bar draws immediately with
 * what is certain and gains a tab, rather than offering one and taking it away
 * under somebody's thumb.
 *
 * Films sit after the channels because that is the order of a catalogue, and
 * before favourites because a shelf comes before a selection from it.
 *
 * Favourites earns its place beside the catalogue rather than inside it, and that
 * is the structural point of US-12: a group belongs to the account and can hold
 * channels from two sources, so there is no source under which it could sit.
 *
 * Onboarding and authentication are absent for a different reason: they are not
 * places one returns to. They are the way in, and a tab that takes a signed-in
 * user back to a sign-up form is a tab that will be pressed by accident.
 */
fun mobileDestinations(hasFilms: Boolean): List<LumoDestination> = buildList {
    add(LiveDestination)
    if (hasFilms) add(VodDestination)
    add(FavoritesDestination)
    add(SourceDestination)
    add(SettingsDestination)
}
