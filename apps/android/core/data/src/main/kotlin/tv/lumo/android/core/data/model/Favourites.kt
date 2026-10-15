package tv.lumo.android.core.data.model

import tv.lumo.android.core.data.LumoError

/**
 * A group of favourites, as a screen wants one (US-12).
 *
 * A group belongs to the **account**, not to a source. That is what lets one
 * group hold channels from two subscriptions, and it is why the screen that shows
 * these does not sit under a source the way the catalogue does.
 */
data class FavoriteGroup(
    val id: String,
    val name: String,
    val position: Int,
    /**
     * True for the group an add without a group lands in.
     *
     * A screen renders its own translated wording while this is true **and** the
     * name is still the server's own — the server has to call that group
     * something and calls it `Favorites`, in English. Once the user has renamed
     * it, their name wins. The flag survives a rename; a name never could, which
     * is the whole reason the contract carries it.
     */
    val isDefault: Boolean,
)

/**
 * One channel filed in one group, ready to render.
 *
 * Carries the [Channel] rather than a channel id, because a screen that had to
 * resolve identifiers itself would do it once per list and get the offline case
 * wrong. The resolution is a join in SQLite, done by the repository.
 */
data class FavoriteChannel(
    val favoriteId: String,
    val groupId: String,
    val position: Int,
    val channel: Channel,
    /**
     * False when the channel could not be resolved on this device — an outage
     * during the resolution, typically (S11-05). [channel] then carries the ids
     * and nothing readable: the screens draw "Details unavailable", do not play
     * it, and still let it be removed. Never dropped from a list for that.
     */
    val resolved: Boolean = true,
)

/**
 * One membership of a channel: the favourite that files it in one group.
 *
 * A channel in three groups is three of these, each with its own identifier —
 * which is why "remove from all my favourites" is several calls, not one.
 */
data class FavoriteMembership(
    val favoriteId: String,
    val groupId: String,
)

/**
 * What a reorder of the visible favourites did (S11-00, G1/G2).
 *
 * Never a bare success after a failure: the sequence of moves is not atomic, so
 * an interruption leaves the group valid but partially permuted, and the screen
 * says so.
 */
sealed interface FavoriteReorderOutcome {

    /** Every planned move was accepted (or there was nothing to move). */
    data object Applied : FavoriteReorderOutcome

    /** Nothing was sent: the group could not be read first. Nothing changed. */
    data class NotApplied(val error: LumoError) : FavoriteReorderOutcome

    /**
     * [applied] of [planned] moves were accepted before [error]. The accepted ones
     * are kept — there is no rollback — and the list has been re-read.
     */
    data class NotFullyApplied(val applied: Int, val planned: Int, val error: LumoError) : FavoriteReorderOutcome
}

/**
 * What "remove from all my favourites" did (S11-00, G2; FO-05 → FO-08).
 *
 * Three different facts, never folded into one: everything targeted is gone; some
 * memberships are **known** to remain; or the result could not be confirmed.
 */
sealed interface FavoriteRemovalOutcome {

    /** Every targeted membership is confirmed gone. */
    data object Removed : FavoriteRemovalOutcome

    /** Nothing was sent: the memberships could not be read first. Nothing changed. */
    data class NotStarted(val error: LumoError) : FavoriteRemovalOutcome

    /**
     * The state was re-read and these memberships are still there. Removals that
     * succeeded are kept; a retry targets these and nothing else.
     */
    data class Incomplete(val remaining: List<FavoriteMembership>, val error: LumoError?) : FavoriteRemovalOutcome

    /**
     * Some removals got no usable answer and the state could not be re-read: these
     * may or may not be gone. Nothing is claimed either way; a retry re-reads first.
     */
    data class Unconfirmed(val unconfirmed: List<FavoriteMembership>, val error: LumoError) : FavoriteRemovalOutcome
}
