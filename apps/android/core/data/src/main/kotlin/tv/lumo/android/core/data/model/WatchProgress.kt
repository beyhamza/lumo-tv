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
    /**
     * When the server last wrote this row — the contract's `updated_at`.
     *
     * Carried since the home screen (US-017): its "Continue" rail merges films and
     * episodes, and two lists that are each "most recent first" can only be merged
     * on the value they were sorted by. The **server's** clock, so two devices
     * cannot disagree about which of them was watched last.
     */
    val updatedAtMillis: Long = 0L,
    /**
     * Whether the server puts this film in "Continue watching" (`adr/0012`,
     * ruling 4): not hidden, not finished, and 30 seconds actually watched or
     * restarted after a hide. The rail reads this and nothing else; it no longer
     * re-derives the 30-second or the 95 % rule (S12-04).
     */
    val inContinue: Boolean = false,
    /** Removed from the rail by the user, position kept (CW-10). The sheet still resumes. */
    val hidden: Boolean = false,
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
    /** When the server last wrote this row. See [WatchProgress.updatedAtMillis]. */
    val updatedAtMillis: Long = 0L,
    /** The server's verdict for the rail. See [WatchProgress.inContinue]. */
    val inContinue: Boolean = false,
    /** Hidden from the rail. For an episode, the server hides every row of its series. */
    val hidden: Boolean = false,
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
        // Strictly past (CW-06): exactly 95 minutes of a 100-minute film is not
        // finished yet. The server makes the same comparison for `in_continue`.
        positionMs * 100 > durationMs * FINISHED_PERCENT

/**
 * 95 %.
 *
 * The last few minutes are credits often enough that treating them as unwatched
 * puts a finished film back in the rail on most of them — and, for a series, stops
 * the one thing somebody actually wants next.
 */
private const val FINISHED_PERCENT = 95L
