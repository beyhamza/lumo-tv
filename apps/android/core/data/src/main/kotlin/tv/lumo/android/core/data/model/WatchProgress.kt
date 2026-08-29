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
        get() = durationMs != null &&
            durationMs > 0L &&
            positionMs >= durationMs * FINISHED_FRACTION

    private companion object {
        /**
         * 95 %.
         *
         * The last few minutes of a film are credits often enough that treating
         * them as unwatched puts a finished film back in the rail on most of
         * them.
         */
        const val FINISHED_FRACTION = 0.95
    }
}
