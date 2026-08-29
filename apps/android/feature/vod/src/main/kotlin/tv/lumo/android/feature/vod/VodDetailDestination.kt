package tv.lumo.android.feature.vod

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * One film's own screen.
 *
 * Not a top-level destination — it is reached by choosing a film and left by
 * pressing back. It implements [LumoDestination] anyway so its route sits beside
 * the feature's others and stays the same string on both applications.
 */
object VodDetailDestination : LumoDestination {
    override val route: String = "vod/{filmId}"
    override val titleRes: Int = R.string.feature_vod_title

    fun routeFor(filmId: String): String = "vod/$filmId"

    const val ARG_FILM_ID = "filmId"
}

/**
 * Watching one film.
 *
 * The title travels in the route beside the id, for the reason `PlayerDestination`
 * gives for a channel's name: the player has something to *call* what it is
 * playing before any request answers, and the screen that sent the user here
 * already knew it.
 *
 * Unlike a channel, the id alone would in fact be enough — `GET /vod/{id}` exists
 * and the cache holds the row. It is still passed, because a player that shows a
 * title only once the cache emits is a player with a blank heading for a frame.
 */
object VodPlayerDestination : LumoDestination {
    override val route: String =
        "vod/player/{filmId}?title={title}&sourceId={sourceId}&at={at}"
    override val titleRes: Int = R.string.feature_vod_title

    /**
     * @param sourceId part of the key `PUT /me/progress` upserts on. Carried in
     *   the route rather than looked up here: the screen that sends the viewer
     *   has the film in hand, and a player that had to read the cache before it
     *   could save a position would be one more thing between a press and a
     *   picture.
     * @param atMs where to start. **A value somebody chose on the previous
     *   screen**, never a resume this player applied on their own — see S5-11:
     *   resuming is offered, not imposed.
     */
    fun routeFor(filmId: String, sourceId: String, title: String?, atMs: Long): String =
        "vod/player/$filmId?title=${title.orEmpty()}&sourceId=$sourceId&at=$atMs"

    const val ARG_FILM_ID = "filmId"
    const val ARG_TITLE = "title"
    const val ARG_SOURCE_ID = "sourceId"
    const val ARG_AT = "at"
}
