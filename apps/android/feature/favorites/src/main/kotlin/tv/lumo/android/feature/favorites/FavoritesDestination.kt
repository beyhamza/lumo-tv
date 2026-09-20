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

    /**
     * "Library", since US-017. The bar's fourth entry is where what somebody keeps
     * lives, and today that is their favourites; the screen underneath still says
     * "Favourites" because that is what it lists.
     */
    override val titleRes: Int = R.string.feature_favorites_library_title
}

/**
 * The same place, as the television's rail names it: **"My library"**.
 *
 * The decisions table gives the two surfaces two labels — *Bibliothèque* in the
 * phone's bar, *Ma bibliothèque* in the side menu of the television and the web —
 * and a [LumoDestination] carries one. So there are two objects and **one route**:
 * the vocabulary of routes stays shared between the applications, which is what
 * lets a deep link or a bug report read the same wherever it came from.
 */
object FavoritesTvDestination : LumoDestination {
    override val route: String = FavoritesDestination.route
    override val titleRes: Int = R.string.feature_favorites_tv_title
}
