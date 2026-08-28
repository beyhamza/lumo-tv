package tv.lumo.android.feature.favorites

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * Where favourites sit in either application's navigation.
 *
 * **A module of its own, and that is the structural point of US-12.** Every other
 * catalogue screen hangs off a source — `LiveMobileScreen` will not draw until one
 * is `READY`, and the web puts the source id in the URL. A group of favourites has
 * no source to name: it belongs to the account and can hold channels from two
 * subscriptions at once. So it sits *beside* the catalogue rather than inside it.
 *
 * It also cannot live in `feature:live` for a second reason, which
 * `settings.gradle.kts` states outright: a feature never depends on another. The
 * two would have had to share a view model.
 */
object FavoritesDestination : LumoDestination {
    override val route: String = "favorites"
    override val titleRes: Int = R.string.feature_favorites_title
}
