package tv.lumo.android.feature.home

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * Where a signed-in user lands (US-017).
 *
 * First in the phone's bar and first in the television's rail, and the start
 * destination of both graphs once a source exists — so it is also where `BACK`
 * returns to from every other section, and the one screen `BACK` leaves the
 * application from.
 *
 * **A module of its own rather than a corner of `feature:live`.** The home screen
 * shows films, series, favourites and channels side by side, and a feature may not
 * depend on another: whichever of the four had hosted it would have needed the
 * other three. Everything it shares with them lives in `core:data` instead.
 */
object HomeDestination : LumoDestination {
    override val route: String = "home"
    override val titleRes: Int = R.string.feature_home_title
}
