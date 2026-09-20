package tv.lumo.android.feature.favorites.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.favorites.FavoritesDestination
import tv.lumo.android.feature.favorites.FavoritesMobileScreen
import tv.lumo.android.feature.favorites.FavoritesTvScreen

/**
 * The phone's favourites tab.
 *
 * `onPlay` carries the same two values as the catalogue's, and points at the same
 * player: where a channel takes the user is the application's business, not this
 * feature's. A feature never navigates to another feature's route on its own
 * (docs/architecture.md §3), and this one would otherwise have to know
 * `feature:live` exists.
 *
 */
fun NavGraphBuilder.favoritesMobileScreen(onPlay: (channelId: String, name: String?) -> Unit) {
    composable(route = FavoritesDestination.route) { FavoritesMobileScreen(onPlay = onPlay) }
}

/**
 * The television's "My library" (US-017).
 *
 * **This used to say there would be no television surface**, and `S4-06` kept that
 * word: on a television a group is a chip in the channel grid's strip, because a
 * group filters a grid exactly as a category does. That is still true and those
 * chips are still there. What changed is the rail — it gained a "My library"
 * entry, and an entry needs a screen behind it.
 *
 * [onOpenLive] is what the empty state's button does: a television stars a channel
 * from the channel grid, so that is where somebody with no favourite is sent. The
 * application holds that wire, as it holds [onPlay].
 */
fun NavGraphBuilder.favoritesTvScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    onOpenLive: () -> Unit,
) {
    composable(route = FavoritesDestination.route) {
        FavoritesTvScreen(onPlay = onPlay, onOpenLive = onOpenLive)
    }
}
