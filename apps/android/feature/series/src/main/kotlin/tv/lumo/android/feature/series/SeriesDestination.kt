package tv.lumo.android.feature.series

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * Where this feature sits in either application's navigation.
 *
 * The feature owns its route and its label; the application decides where the
 * destination appears (docs/architecture.md §3).
 */
object SeriesDestination : LumoDestination {
    override val route: String = "series"
    override val titleRes: Int = R.string.feature_series_title
}

/**
 * One series' own screen.
 *
 * Not a top-level destination — reached by choosing a series, left by pressing
 * back. It implements [LumoDestination] so its route sits beside the feature's
 * others and stays the same string on both applications.
 */
object SeriesDetailDestination : LumoDestination {
    override val route: String = "series/{seriesId}"
    override val titleRes: Int = R.string.feature_series_title

    fun routeFor(seriesId: String): String = "series/$seriesId"

    const val ARG_SERIES_ID = "seriesId"
}

/**
 * Watching one episode.
 *
 * The title travels beside the id for the reason `VodPlayerDestination` gives: the
 * player has something to call what it is playing before any request answers, and
 * the screen that sent the viewer here already knew it.
 */
object EpisodePlayerDestination : LumoDestination {
    override val route: String = "series/player/{episodeId}?title={title}"
    override val titleRes: Int = R.string.feature_series_title

    fun routeFor(episodeId: String, title: String?): String =
        "series/player/$episodeId?title=${title.orEmpty()}"

    const val ARG_EPISODE_ID = "episodeId"
    const val ARG_TITLE = "title"
}
