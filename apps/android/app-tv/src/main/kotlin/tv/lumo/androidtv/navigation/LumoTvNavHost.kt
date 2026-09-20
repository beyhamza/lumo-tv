package tv.lumo.androidtv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.feature.auth.AuthDestination
import tv.lumo.android.feature.auth.navigation.authTvScreen
import tv.lumo.android.feature.favorites.FavoritesTvDestination
import tv.lumo.android.feature.favorites.navigation.favoritesTvScreen
import tv.lumo.android.feature.home.HomeDestination
import tv.lumo.android.feature.home.navigation.HomeActions
import tv.lumo.android.feature.home.navigation.homeTvScreen
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.live.PlayerDestination
import tv.lumo.android.feature.live.navigation.KEY_RETURNED_CHANNEL
import tv.lumo.android.feature.live.navigation.livePlayerTvScreen
import tv.lumo.android.feature.live.navigation.liveTvScreen
import tv.lumo.android.feature.onboarding.navigation.onboardingTvScreen
import tv.lumo.android.feature.search.navigation.searchTvScreen
import tv.lumo.android.feature.series.EpisodePlayerDestination
import tv.lumo.android.feature.series.SeriesDestination
import tv.lumo.android.feature.series.SeriesDetailDestination
import tv.lumo.android.feature.series.navigation.KEY_RETURNED_SERIES
import tv.lumo.android.feature.series.navigation.episodePlayerTvScreen
import tv.lumo.android.feature.series.navigation.seriesDetailTvScreen
import tv.lumo.android.feature.series.navigation.seriesTvScreen
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.settings.navigation.settingsTvScreen
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.source.navigation.sourceTvScreen
import tv.lumo.android.feature.vod.VodDestination
import tv.lumo.android.feature.vod.VodDetailDestination
import tv.lumo.android.feature.vod.VodPlayerDestination
import tv.lumo.android.feature.vod.navigation.KEY_RETURNED_FILM
import tv.lumo.android.feature.vod.navigation.vodDetailTvScreen
import tv.lumo.android.feature.vod.navigation.vodPlayerTvScreen
import tv.lumo.android.feature.vod.navigation.vodTvScreen

/**
 * The same features as the phone, each contributing its television surface
 * instead of its phone one.
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

        // "My sources", from a notice over a grid or from a player whose stream
        // was refused. A push, so that BACK returns to where the viewer was — the
        // rail has no entry for it to move to (US-024).
        val openSources = {
            navController.navigate(SourceDestination.route) { launchSingleTop = true }
        }
        // From a player it *replaces* the player: the stream was refused, there is
        // nothing to come back to, and BACK from the sources lands on the grid or
        // the detail screen underneath.
        val leavePlayerForSources = {
            navController.popBackStack()
            openSources()
        }

        // The same player for the three screens that start a channel: the home
        // screen, the channel grid and the library.
        val playChannel = { channelId: String, name: String? ->
            navController.navigate(PlayerDestination.routeFor(channelId, name))
        }

        // What a signed-in set lands on (US-017). Every way out of it leads into
        // another feature, so every one of them is a wire held here.
        homeTvScreen(
            actions = HomeActions(
                onResumeFilm = { filmId, sourceId, title, atMs ->
                    navController.navigate(
                        VodPlayerDestination.routeFor(filmId, sourceId, title, atMs),
                    )
                },
                onResumeEpisode = { episodeId, title, atMs ->
                    navController.navigate(
                        EpisodePlayerDestination.routeFor(episodeId, title, atMs),
                    )
                },
                onOpenFilm = { filmId ->
                    navController.navigate(VodDetailDestination.routeFor(filmId))
                },
                onOpenSeries = { seriesId ->
                    navController.navigate(SeriesDetailDestination.routeFor(seriesId))
                },
                onPlayChannel = playChannel,
                // Moves of the rail rather than pushes: "See all" *is* the rail's
                // "My library". A second library stacked over Home would make BACK
                // walk through a screen the rail says one never left.
                onOpenLibrary = { navController.switchTopLevelTo(FavoritesTvDestination) },
                onOpenLive = { navController.switchTopLevelTo(LiveDestination) },
                onOpenFilms = { navController.switchTopLevelTo(VodDestination) },
                onOpenSeriesCatalogue = { navController.switchTopLevelTo(SeriesDestination) },
                // A television never adds a source, and its home screen never
                // calls this. Wired to the screen that says where one is added,
                // so that the day it is called it still leads somewhere true.
                onAddSource = { navController.switchTopLevelTo(SourceDestination) },
                onOpenSources = { navController.switchTopLevelTo(SourceDestination) },
            ),
        )
        liveTvScreen(onPlay = playChannel, onOpenSources = openSources)
        // "My library": the favourites of the active source (US-017). Its empty
        // state sends somebody to where a television stars a channel.
        favoritesTvScreen(
            onPlay = playChannel,
            onOpenLive = { navController.switchTopLevelTo(LiveDestination) },
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
            onOpenSources = leavePlayerForSources,
        )
        vodTvScreen(
            onOpenFilm = { filmId ->
                navController.navigate(VodDetailDestination.routeFor(filmId))
            },
            onOpenSources = openSources,
        )
        vodDetailTvScreen(
            onPlay = { filmId, sourceId, title, atMs ->
                navController.navigate(
                    VodPlayerDestination.routeFor(filmId, sourceId, title, atMs),
                )
            },
            onBack = { filmId ->
                // The same rule as the channel player, one screen further out:
                // the grid comes back on the film that was being looked at. The
                // id is left on the entry underneath before popping, which is the
                // one moment both entries exist.
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_RETURNED_FILM, filmId)
                navController.popBackStack()
            },
        )
        vodPlayerTvScreen(
            onBack = { navController.popBackStack() },
            onOpenSources = leavePlayerForSources,
        )
        seriesTvScreen(
            onOpenSeries = { seriesId ->
                navController.navigate(SeriesDetailDestination.routeFor(seriesId))
            },
            onOpenSources = openSources,
        )
        seriesDetailTvScreen(
            onPlay = { episodeId, title, atMs ->
                navController.navigate(
                    EpisodePlayerDestination.routeFor(episodeId, title, atMs),
                )
            },
            onBack = { seriesId ->
                // The rule the film screen already follows, one catalogue over:
                // the grid comes back on the series that was being looked at. The
                // id is left on the entry underneath before popping, which is the
                // one moment both entries exist.
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_RETURNED_SERIES, seriesId)
                navController.popBackStack()
            },
        )
        episodePlayerTvScreen(
            onBack = { navController.popBackStack() },
            onOpenSources = leavePlayerForSources,
        )
        searchTvScreen()
        settingsTvScreen(
            // A push, unlike the rail's moves: "My sources" is opened *from*
            // Settings, and BACK from it returns there rather than to Home.
            onOpenSources = openSources,
        )
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
    // The home screen, since US-017 — it used to be the channel grid. Being the
    // start destination is also what makes Home the place BACK returns to from
    // every other entry of the rail, and the one screen BACK leaves the
    // application from.
    AppStart.Ready -> HomeDestination.route
}

/**
 * The rail's contents, in D-pad order: **Home, Live, Films, Series, My library,
 * Settings** (US-017, decisions table — the side menu the web shares).
 *
 * <h2>Home first, and Live is still one press from it</h2>
 *
 * The rail used to open on Live, on the argument that it is what a television is
 * for and should be the shortest journey. Home takes the head of the rail because
 * it is now where a session starts — and it *is* the shorter journey: the channels
 * somebody actually watches are on it, one `RIGHT` away, without a grid to cross.
 *
 * <h2>Six entries, and what paid for the sixth</h2>
 *
 * Every rail entry is a mandatory stop on the way down, so the rail does not grow
 * for free. **Source left it**: it is reached from the switcher at the foot of the
 * rail ("My sources") and from Settings, which is where somebody looks for it —
 * and it was the entry visited least, on a surface that cannot edit a source
 * anyway. "My library" took its place, with a screen built for it.
 *
 * <h2>What did not change is the rule about catalogues</h2>
 *
 * Films and Series are always there, whatever the source holds. The entry was once
 * conditional, and hiding it is what made somebody with a hundred and forty
 * thousand films conclude the feature did not exist: **an absence is
 * indistinguishable from a bug**, and on a television there is nowhere else to go
 * and look. An *empty* catalogue is a reply, an *unbuilt* screen is a promise, and
 * only the first belongs in a rail (`adr/0010`). That is also what still keeps
 * Search out: its screen is a placeholder until sprint 10.
 *
 * The rail's own rule is unchanged: `RIGHT` enters the content, `LEFT` comes back,
 * and nothing here is reachable only by travelling through everything else (US-10).
 */
val TvDestinations: List<LumoDestination> = listOf(
    HomeDestination,
    LiveDestination,
    VodDestination,
    SeriesDestination,
    FavoritesTvDestination,
    SettingsDestination,
)

/**
 * Moves between the entries of the rail without stacking them.
 *
 * Same behaviour as the phone's bar, for the same reason: BACK from any entry
 * returns to the start — Home — rather than replaying every rail item the viewer
 * has opened, and BACK on Home leaves the application. On a television that
 * matters more: BACK is a physical key people press repeatedly to get out (US-10).
 *
 * Shared by the rail and by the home screen's own ways onward ("See all", "All
 * channels"), so that reaching the library from a rail of favourites and reaching
 * it from the side menu leave the same back stack behind.
 */
internal fun NavHostController.switchTopLevelTo(destination: LumoDestination) {
    val alreadyThere = currentBackStackEntry?.destination?.hierarchy
        ?.any { it.route == destination.route } == true
    if (alreadyThere) return

    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
