package tv.lumo.android.core.data.model

/**
 * Where somebody stopped watching one film (S5-11).
 *
 * [filmId] rather than `itemRef`: the contract's field is opaque and this one is
 * not — for `VOD` it is a `VodItem.id`, which is what lets a rail resolve these
 * rows into films. Naming it for what it holds is what stops a screen passing it
 * where a channel id was expected.
 */
data class WatchProgress(
    val sourceId: String,
    val filmId: String,
    val positionMs: Long,
    /** Total length, when the source states one. Null far more often than not. */
    val durationMs: Long?,
) {

    /**
     * Whether this film counts as watched.
     *
     * **A threshold, not an event.** Nothing tells us a film ended — a player
     * that is killed, a set-top box unplugged and a viewer who watched the
     * credits all stop writing at some position — so "finished" is the position
     * being close enough to the end that offering to resume it would be absurd.
     *
     * **With no duration it is never finished, and that default is the whole
     * decision.** Many panels state no running time at all. A film that lingers
     * in "continue watching" is an annoyance somebody dismisses; a film that
     * vanishes from it before the end is a loss they cannot recover, because
     * nothing else records where they were.
     */
    val finished: Boolean
        get() = watched(positionMs, durationMs)
}

/**
 * Where somebody stopped watching one episode (S6-08).
 *
 * [episodeId] rather than `itemRef`, and a second type rather than a field added
 * to [WatchProgress], for the reason written there: the contract's field is
 * opaque, ours are not, and an id named for what it holds is what stops a screen
 * resolving an episode among the films. The two ids point at two tables.
 *
 * **The threshold is shared and the meaning is not.** A film past 95 % leaves the
 * rail; an *episode* past 95 % is what puts the **next** one in it. Same number,
 * two different answers, which is exactly why [watched] is one function and these
 * are two types.
 */
data class EpisodeProgress(
    val sourceId: String,
    val episodeId: String,
    val positionMs: Long,
    /** Length of *this* episode, when the source states one. */
    val durationMs: Long?,
) {

    /** Close enough to the end that resuming it would be absurd. See [watched]. */
    val finished: Boolean
        get() = watched(positionMs, durationMs)
}

/**
 * Whether a position counts as "watched to the end".
 *
 * **A threshold, not an event.** Nothing tells us a film or an episode ended — a
 * player that is killed, a set-top box unplugged and a viewer who sits through the
 * credits all stop writing at some position — so "finished" is the position being
 * close enough to the end that offering to resume it would be absurd.
 *
 * **With no duration it is never finished, and that default is the whole
 * decision.** Many panels state no running time at all. Something that lingers in
 * "continue watching" is an annoyance somebody dismisses; something that vanishes
 * from it before the end is a loss they cannot recover, because nothing else
 * records where they were.
 *
 * One function rather than one per type, because the day the number moves it has
 * to move for both — a rail that dropped films at 95 % and advanced series at 90 %
 * would be two products.
 */
internal fun watched(positionMs: Long, durationMs: Long?): Boolean =
    durationMs != null &&
        durationMs > 0L &&
        positionMs >= durationMs * FINISHED_FRACTION

/**
 * 95 %.
 *
 * The last few minutes are credits often enough that treating them as unwatched
 * puts a finished film back in the rail on most of them — and, for a series, stops
 * the one thing somebody actually wants next.
 */
private const val FINISHED_FRACTION = 0.95
