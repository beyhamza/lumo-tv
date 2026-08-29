package tv.lumo.android.core.data.model

import tv.lumo.android.core.common.log.Redact

/**
 * A series, as a screen wants one.
 *
 * The reasons this is neither the generated model nor the Room entity are the ones
 * written on [Channel], and they are not repeated.
 *
 * **A [VodItem] minus one field and plus one.** No playback of its own — a series
 * is not played, its episodes are — and an [episodeRunTime] the panel offers as
 * indicative.
 */
data class Series(
    val id: String,
    val sourceId: String,
    val categoryId: String?,
    val name: String,
    /** The poster the user's own source advertises, or null. Lumo ships no artwork. */
    val posterUrl: String?,
    val year: Int?,
    /**
     * Typical episode length in minutes, as the panel states it.
     *
     * **Never used as a duration.** Whether an episode was watched to the end is
     * decided against [Episode.durationSeconds]; using this would test a threshold
     * against a number belonging to no episode in particular.
     */
    val episodeRunTime: Int?,
    /** Whatever the source calls a rating, verbatim. */
    val rating: String?,
    /**
     * The synopsis, and **null until a tree has been fetched**.
     *
     * Unlike a film's, it costs nothing extra: it arrives with the tree, from the
     * single call that loads the seasons.
     */
    val plot: String?,
    val isAdult: Boolean,
)

/** A season: a number, a claim, and its episodes. */
data class Season(
    val seasonNumber: Int,
    /**
     * How many episodes the panel claims this season has.
     *
     * **It can disagree with [episodes], and the list wins.** A panel announcing
     * twenty-four and returning twenty-two has twenty-two episodes somebody can
     * watch. Carried because it is occasionally the only hint that a season is
     * incomplete.
     */
    val episodeCount: Int?,
    /** Season artwork when the panel has some. Null falls back to the series poster. */
    val posterUrl: String?,
    val episodes: List<Episode>,
)

/** One episode: the thing that is played, and the thing a saved position points at. */
data class Episode(
    val id: String,
    val seriesId: String,
    val sourceId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    /** Title when the panel has one. Null far more often than a film's. */
    val name: String?,
    /** Length of *this* episode. What decides whether it was watched to the end. */
    val durationSeconds: Long?,
    val plot: String?,
)

/**
 * The state of one series' tree (S6-04).
 *
 * <h2>Three states, and collapsing any two of them breaks a screen</h2>
 *
 * This is the piece the film work did not need, and the sprint names it as the new
 * thing. A film's detail screen has one question — is the synopsis here yet — and
 * the answer is a nullable string. A series has three answers and they look alike
 * from a distance:
 *
 * - **[Loaded]** with no seasons is a series whose panel genuinely lists none. Rare
 *   and real.
 * - **[Loading]** is a tree on its way. Drawing it as an empty series says the
 *   provider has nothing, which is a lie that lasts as long as the request.
 * - **[Unavailable]** is a provider that did not answer. Drawing it as an empty
 *   series sends somebody looking for episodes their provider still has; drawing it
 *   as a spinner leaves them watching one for ever.
 *
 * A nullable `List<Season>` would have merged the first two, and an exception would
 * have merged the last with a bug.
 */
sealed interface SeriesTree {

    /** Nothing has been asked yet. What a screen shows for the first frame only. */
    data object Idle : SeriesTree

    /** A request is in flight and nothing is cached. This is the spinner. */
    data object Loading : SeriesTree

    /**
     * A tree is here.
     *
     * @param stale whether it is old enough that a refresh was started behind it.
     *   Kept as a value rather than hidden, because a screen may want to say so —
     *   and because a refresh that fails leaves this true, which is the honest
     *   thing to be able to render.
     */
    data class Loaded(val seasons: List<Season>, val stale: Boolean = false) : SeriesTree

    /** Nothing cached, and the provider could not supply one. Worth retrying. */
    data object Unavailable : SeriesTree
}

/**
 * What the player needs to open one episode, and nothing that outlives it.
 *
 * A third type beside [PlaybackTarget] and [VodPlaybackTarget] rather than one with
 * a renamed identifier: the three point at three different tables, and a player
 * handed "an id" it cannot name is how an episode gets looked up among the films.
 *
 * The [toString] is not decoration — the whole argument is on [PlaybackTarget] and
 * applies here word for word.
 *
 * **An episode plays like a film**: a progressive file, so seeking works only if
 * the user's server answers `Range` requests.
 */
data class EpisodePlaybackTarget(
    val episodeId: String,
    /** **Sensitive.** Opened by the player, never logged, never persisted. */
    val streamUrl: String,
    val userAgent: String?,
    /** The *user's own* subscription ceiling. An episode counts against it. */
    val maxConnections: Int?,
    val expiresAtMillis: Long?,
) {
    override fun toString(): String =
        "EpisodePlaybackTarget(episodeId=$episodeId, streamUrl=${Redact.url(streamUrl)}, " +
            "userAgent=$userAgent, maxConnections=$maxConnections, " +
            "expiresAtMillis=$expiresAtMillis)"
}
