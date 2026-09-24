package tv.lumo.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.data.DirectView
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.SignUpDestination
import tv.lumo.android.feature.auth.navigation.authMobileScreen
import tv.lumo.android.feature.auth.navigation.signUpMobileScreen
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.favorites.navigation.favoritesMobileScreen
import tv.lumo.android.feature.home.HomeDestination
import tv.lumo.android.feature.home.navigation.HomeActions
import tv.lumo.android.feature.home.navigation.homeMobileScreen
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.live.navigation.KEY_REQUESTED_VIEW
import tv.lumo.android.feature.live.navigation.liveMobileScreen
import tv.lumo.android.feature.live.navigation.livePlayerMobileScreen
import tv.lumo.android.feature.onboarding.navigation.onboardingMobileScreen
import tv.lumo.android.feature.search.navigation.searchMobileScreen
import tv.lumo.android.feature.series.EpisodePlayerDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.SeriesDetailDestination
import tv.lumo.android.feature.series.navigation.episodePlayerMobileScreen
import tv.lumo.android.feature.series.navigation.seriesDetailMobileScreen
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
 * Search stays registered even though nothing points at it (see
 * [MobileDestinations]). Removing an unreachable screen from the graph as well
 * would turn it into a crash for anything that still names its route — a saved
 * back stack, a deep link, a notification.
 *
 * The film routes are registered whatever the source carries. A deep link to a
 * film is not made invalid by a playlist that has none — it lands on the empty
 * state, which is an answer.
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
        // "My sources" is reached from a player too — a stream refused because
        // the provider turned the credentials down — and from the notices over
        // the catalogues. A push from wherever that was, so that BACK returns
        // there; the bar has no entry for it to move to (US-024).
        val openSources = {
            navController.navigate(SourceDestination.route) { launchSingleTop = true }
        }

        sourceMobileScreen(
            // "Discover my catalogue", once a newly added source is ready: Home,
            // by a move of the bar — it *is* the Home tab, not a screen over it.
            onDiscoverCatalogue = { navController.switchTopLevelTo(HomeDestination) },
        )

        // One wire, held here, for the three screens that hand an episode on: the
        // home screen's rail, the series grid's and the series' own list. Where an
        // episode takes somebody is the application's business, not a feature's.
        val playEpisode = { episodeId: String, title: String?, atMs: Long ->
            navController.navigate(EpisodePlayerDestination.routeFor(episodeId, title, atMs))
        }
        // The same, for a channel: the home screen, the catalogue and the library
        // all start the one live player.
        val playChannel = { channelId: String, name: String? ->
            navController.navigate(PlayerDestination.routeFor(channelId, name))
        }

        // What a signed-in user lands on (US-017). Every way out of it leads into
        // another feature, so every one of them is a wire held here.
        //
        // The home Live rail closes with two explicit doors (S9-04-04). They move
        // to Direct and then leave the view they named on the entry's own saved
        // state, which the screen reads as a one-shot: a door pressed while Direct
        // is already open still wins, and the request never replays on a return.
        val openLiveExplicit = { view: DirectView ->
            navController.switchTopLevelTo(LiveDestination)
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(KEY_REQUESTED_VIEW, view.name)
        }
        homeMobileScreen(
            actions = HomeActions(
                onResumeFilm = { filmId, sourceId, title, atMs ->
                    navController.navigate(
                        VodPlayerDestination.routeFor(filmId, sourceId, title, atMs),
                    )
                },
                onResumeEpisode = playEpisode,
                onOpenFilm = { filmId ->
                    navController.navigate(VodDetailDestination.routeFor(filmId))
                },
                onOpenSeries = { seriesId ->
                    navController.navigate(SeriesDetailDestination.routeFor(seriesId))
                },
                onPlayChannel = playChannel,
                // Moves of the bar rather than pushes: "See all" *is* the Library
                // tab. Landing there with a second Library entry stacked over Home
                // would make the bar and the back stack disagree about where one is.
                onOpenLibrary = { navController.switchTopLevelTo(FavoritesDestination) },
                onOpenLive = { navController.switchTopLevelTo(LiveDestination) },
                onOpenLiveChannels = { openLiveExplicit(DirectView.Channels) },
                onOpenLiveGuide = { openLiveExplicit(DirectView.Guide) },
                onOpenFilms = { navController.switchTopLevelTo(VodDestination) },
                onOpenSeriesCatalogue = { navController.switchTopLevelTo(SeriesDestination) },
                onAddSource = { navController.switchTopLevelTo(SourceDestination) },
                onOpenSources = { navController.switchTopLevelTo(SourceDestination) },
            ),
        )

        // Explore: one entry of the bar, three sections (US-017). A nested graph
        // and not a screen — the three catalogues are exactly what they were,
        // under the routes they always had. See `ExploreDestination`.
        navigation(
            route = ExploreDestination.route,
            startDestination = ExploreSections.first().route,
        ) {
            liveMobileScreen(onPlay = playChannel, onOpenSources = openSources)
            vodMobileScreen(
                onOpenFilm = { filmId ->
                    navController.navigate(VodDetailDestination.routeFor(filmId))
                },
                onOpenSources = openSources,
            )
            seriesMobileScreen(
                onOpenSeries = { seriesId ->
                    navController.navigate(SeriesDetailDestination.routeFor(seriesId))
                },
                onPlay = playEpisode,
                onOpenSources = openSources,
            )
        }

        // From a player, "My sources" *replaces* it: the stream was refused, there
        // is nothing to come back to, and BACK from the sources should land on the
        // grid or the detail screen the viewer came from.
        val leavePlayerForSources = {
            navController.popBackStack()
            openSources()
        }

        livePlayerMobileScreen(
            onBack = { navController.popBackStack() },
            onOpenSources = leavePlayerForSources,
        )
        // Same player, and the wire is held here rather than in either feature:
        // favourites has no business knowing that feature:live exists.
        favoritesMobileScreen(onPlay = playChannel)
        vodDetailMobileScreen(
            onPlay = { filmId, sourceId, title, atMs ->
                navController.navigate(
                    VodPlayerDestination.routeFor(filmId, sourceId, title, atMs),
                )
            },
            onBack = { navController.popBackStack() },
        )
        vodPlayerMobileScreen(
            onBack = { navController.popBackStack() },
            onOpenSources = leavePlayerForSources,
        )
        seriesDetailMobileScreen(
            onPlay = playEpisode,
            onBack = { navController.popBackStack() },
        )
        episodePlayerMobileScreen(
            onBack = { navController.popBackStack() },
            onOpenSources = leavePlayerForSources,
        )
        searchMobileScreen()
        settingsMobileScreen(
            // A push, unlike the bar's moves: "My sources" is opened *from*
            // Settings, and BACK from it returns there rather than to Home.
            onOpenSources = openSources,
        )
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
    // (US-06, US-07) rather than an empty home.
    AppStart.NeedsSource -> SourceDestination.route
    // The home screen, since US-017 — it used to be the channel list. Being the
    // start destination is also what makes Home the place BACK returns to from
    // every section, and the one screen BACK leaves the application from: the
    // bar pops to the start destination, whichever it is.
    AppStart.Ready -> HomeDestination.route
}

/**
 * What the bottom bar offers, in order: **Home, Explore, Library, Settings**
 * (US-017, decisions table).
 *
 * <h2>Four, where there were six</h2>
 *
 * Live, Films and Series moved under one entry, [ExploreDestination], and Source
 * left the bar: it is reached from Settings ("My sources") and from the source
 * switcher above every section, which is where somebody looks for it. Six entries
 * had stopped fitting a phone's width without scrolling — and a bar that scrolls
 * hides destinations as surely as one that omits them.
 *
 * <h2>What did not change is the rule about catalogues</h2>
 *
 * Films used to be conditional on the source having some, and **use disproved
 * it**: the owner of a panel carrying a hundred and forty thousand films could not
 * find them and reported the feature missing. An absence is indistinguishable from
 * a bug. The rule that came out of it — *an empty catalogue is a reply, an unbuilt
 * screen is a promise* — now applies to [ExploreSections], where the three
 * catalogues are always offered, and it still keeps Search out of the bar: its
 * screen is a placeholder until sprint 10. `adr/0010` carries the same reasoning
 * for the television.
 *
 * <h2>Library is the favourites screen, under the name it is growing into</h2>
 *
 * It sits beside the catalogue rather than inside it, which is the structural
 * point of US-12: a group belongs to the account and can hold channels from two
 * sources, so there is no source — and no section of Explore — under which it
 * could sit.
 *
 * Onboarding and authentication are absent for a different reason: they are not
 * places one returns to. They are the way in, and a tab that takes a signed-in
 * user back to a sign-up form is a tab that will be pressed by accident.
 */
val MobileDestinations: List<LumoDestination> = listOf(
    HomeDestination,
    ExploreDestination,
    FavoritesDestination,
    SettingsDestination,
)
