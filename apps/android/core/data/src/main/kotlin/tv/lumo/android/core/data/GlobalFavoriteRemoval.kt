package tv.lumo.android.core.data

import tv.lumo.android.core.data.model.FavoriteMembership
import tv.lumo.android.core.data.model.FavoriteRemovalOutcome
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * "Remove from all my favourites" (US-022), planned and judged without a network.
 *
 * The contract has one removal per membership and no batch, so removing a channel
 * from three groups is three `DELETE /me/favorites/{id}`, which may not all
 * succeed (S11-00, G2). The rules this object holds:
 *
 * - **the plan** is the memberships the server lists for the channel when the
 *   removal starts — read, not taken from the cache (G3);
 * - **a retry** targets what remains of *that* plan and nothing more: a membership
 *   added meanwhile from another device is a new intention this one does not
 *   silently widen to (FO-12);
 * - **`404 FAVORITE_NOT_FOUND`** on one of them means it is already gone — a
 *   success, not a failure;
 * - **the verdict** comes from a re-read when there is one. Without it, only the
 *   removals that answered are known; the others are reported as unconfirmed,
 *   never as removed and never as still there (FO-08).
 */
object GlobalFavoriteRemoval {

    /**
     * The memberships to remove: those of the channel on the server, restricted to
     * [restrictTo] on a retry.
     */
    fun targets(onServer: List<FavoriteMembership>, restrictTo: Set<String>?): List<FavoriteMembership> =
        if (restrictTo == null) onServer else onServer.filter { it.favoriteId in restrictTo }

    /** A removal answered "no such favourite": somebody already removed it. */
    fun isAlreadyGone(error: LumoError): Boolean =
        error is LumoError.Api && error.code == ErrorCode.FAVORITE_NOT_FOUND

    /**
     * The verdict.
     *
     * @param failures the removals that did not succeed, by favourite id — a
     * `404 FAVORITE_NOT_FOUND` is not one of them.
     * @param presentAfter the favourite ids the server lists after the removals, or
     * null when that re-read failed.
     */
    fun outcome(
        targets: List<FavoriteMembership>,
        failures: Map<String, LumoError>,
        presentAfter: Set<String>?,
    ): FavoriteRemovalOutcome {
        if (presentAfter != null) {
            val remaining = targets.filter { it.favoriteId in presentAfter }
            return if (remaining.isEmpty()) {
                FavoriteRemovalOutcome.Removed
            } else {
                FavoriteRemovalOutcome.Incomplete(remaining, failures.values.firstOrNull())
            }
        }
        val unconfirmed = targets.filter { it.favoriteId in failures }
        return if (unconfirmed.isEmpty()) {
            // Every removal was answered with a success: known without a re-read.
            FavoriteRemovalOutcome.Removed
        } else {
            FavoriteRemovalOutcome.Unconfirmed(unconfirmed, failures.getValue(unconfirmed.first().favoriteId))
        }
    }
}
