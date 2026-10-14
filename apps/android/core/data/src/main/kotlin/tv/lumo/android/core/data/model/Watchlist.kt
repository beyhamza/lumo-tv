package tv.lumo.android.core.data.model

import java.time.Instant
import java.util.UUID
import tv.lumo.android.core.data.LumoError

/**
 * What a watchlist item points at (US-022). Films and series only: a series is
 * saved as a whole, never episode by episode.
 */
enum class WatchlistType { Film, Series }

/**
 * One film or series in "My list", as the server ordered it.
 *
 * Carries its own display snapshot — [title], [posterUrl] — so an item whose
 * content disappeared from the catalogue still renders (ADR 0011, ruling 3):
 * [available] false, [contentId] null, and the card says *Unavailable* instead
 * of opening a sheet that no longer exists.
 */
data class WatchlistEntry(
    /** The item's own id: what a removal addresses, available or not (`WL-03`). */
    val id: String,
    val sourceId: String,
    val type: WatchlistType,
    /** `VodItem.id` or `Series.id` to open the sheet. Null exactly when not [available]. */
    val contentId: String?,
    val available: Boolean,
    val title: String,
    val posterUrl: String?,
    /** The server's acceptance time. The list's order; never re-sorted on a device. */
    val addedAt: Instant,
)

/**
 * One page of the list, plus the types the **whole** list holds for the source.
 *
 * [types] is what the All / Films / Series selector is built from (`WL-17`): a
 * page alone cannot say whether the other type exists further on.
 */
data class WatchlistSlice(
    val items: List<WatchlistEntry>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
    val types: Set<WatchlistType>,
) {
    /** Whether another page follows this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/** The two writes, as a user intends them. */
enum class WatchlistAction { Add, Remove }

/**
 * What a write is about.
 *
 * An add names the content (there is no item yet); a removal names the item,
 * which is what makes removing an unavailable card possible without its sheet.
 */
sealed interface WatchlistTarget {
    data class Content(val type: WatchlistType, val contentId: String) : WatchlistTarget
    data class Item(val itemId: String) : WatchlistTarget
}

/**
 * One user intention, and the idempotency key that travels with it (ADR 0011,
 * ruling 6).
 *
 * The key is generated **once**, when the user initiates the change, and every
 * retry of that same change — automatic or the "Retry" of an unconfirmed result —
 * sends the same key. The server then recognises the retry and replays the outcome
 * it recorded instead of acting twice. A press after the state was confirmed is a
 * new intention and gets a new key: see [WatchlistIntentions].
 */
data class PendingIntention(
    val key: UUID,
    val action: WatchlistAction,
    val target: WatchlistTarget,
)

/**
 * What became of one write. Four outcomes, and the screen says something different
 * for each.
 */
sealed interface WatchlistWriteOutcome {

    /** `201` on an add, `204` on a removal: the server did what was asked. */
    data class Confirmed(val item: WatchlistEntry?) : WatchlistWriteOutcome

    /**
     * The list was already in the requested state: `200` on an add, `404
     * WATCHLIST_ITEM_NOT_FOUND` on a removal. Not an error — re-read silently.
     */
    data class AlreadyInState(val item: WatchlistEntry?) : WatchlistWriteOutcome

    /**
     * Nobody knows whether the server accepted it: no answer (network, timeout),
     * a `5xx`, an unreadable success, or `409 IDEMPOTENCY_KEY_REUSED` (a client
     * bug, treated as unknown). **Never** reported as a success, never retried
     * under a new key; the list is re-read and the same key is offered again.
     */
    data class Unknown(val cause: LumoError) : WatchlistWriteOutcome

    /** The server answered and refused, for a reason a retry will not change. */
    data class Rejected(val cause: LumoError) : WatchlistWriteOutcome
}
