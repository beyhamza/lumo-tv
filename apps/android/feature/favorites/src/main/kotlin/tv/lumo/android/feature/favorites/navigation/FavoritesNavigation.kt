package tv.lumo.android.feature.favorites.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.favorites.FavoritesMobileScreen

/**
 * The phone's favourites tab.
 *
 * `onPlay` carries the same two values as the catalogue's, and points at the same
 * player: where a channel takes the user is the application's business, not this
 * feature's. A feature never navigates to another feature's route on its own
 * (docs/architecture.md §3), and this one would otherwise have to know
 * `feature:live` exists.
 *
 * No television surface yet — that is S4-06, and it is not a screen: on a
 * television a group is a chip in the grid's existing strip, because `S2-13` ruled
 * against rails and a group filters a grid exactly as a category does.
 */
fun NavGraphBuilder.favoritesMobileScreen(onPlay: (channelId: String, name: String?) -> Unit) {
    composable(route = FavoritesDestination.route) { FavoritesMobileScreen(onPlay = onPlay) }
}
