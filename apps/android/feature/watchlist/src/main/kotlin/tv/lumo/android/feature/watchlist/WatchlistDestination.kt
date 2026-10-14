package tv.lumo.android.feature.watchlist

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * "My list" — *À regarder* (US-022, S11-E05).
 *
 * A module of its own beside `feature:favorites`, not inside it: the two halves of
 * the library share no state, no model and no server resource (ADR 0011, ruling 1),
 * and a feature never depends on another. The applications put them side by side:
 * the phone as the second section of Library, the television as the rail entry
 * right after "My library".
 */
object WatchlistDestination : LumoDestination {
    override val route: String = "watchlist"
    override val titleRes: Int = R.string.feature_watchlist_title
}
