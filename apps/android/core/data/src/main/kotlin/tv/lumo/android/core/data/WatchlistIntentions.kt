package tv.lumo.android.core.data

import java.util.UUID
import tv.lumo.android.core.data.model.PendingIntention
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistTarget

/**
 * The idempotency keys of the watchlist writes still waiting for a confirmed
 * outcome — one per user intention (ADR 0011, ruling 6).
 *
 * <h2>The rule, in two lines</h2>
 *
 * - **A retry is not a new intention.** Asking again for the change that is still
 *   pending — the automatic retry, the "Retry" of an unconfirmed result, or a
 *   second press of a button that would ask for that very change — hands back the
 *   pending intention, **with its key**. The server recognises the key and replays
 *   what it recorded instead of acting twice (`WL-11`).
 * - **A press after the state is confirmed is a new intention.** Once [settle]
 *   has been called — the server confirmed, or a re-read showed the change is in
 *   place — the next request for that target gets a fresh key.
 *
 * Asking for the *opposite* change while one is pending is also a new intention:
 * it replaces the pending one, and the old key is never sent again.
 *
 * <h2>Held in memory, by the screen's view model</h2>
 *
 * Not persisted: a key outlives a retry, not a process. Nothing is queued either
 * — this class never sends anything on its own (ADR 0011, ruling 4).
 *
 * Not thread-safe: it is touched from the main thread of one view model.
 */
class WatchlistIntentions(
    private val newKey: () -> UUID = UUID::randomUUID,
) {

    private val pending = mutableMapOf<Any, PendingIntention>()

    /**
     * The intention to send for [action] on [target]: the pending one when it asks
     * for the same thing — a retry — otherwise a new one with a new key.
     *
     * @param subject what the intention is *about*, when that is not [target]
     *   itself. A film's sheet adds by content and removes by item id, and the two
     *   are still one subject: a removal asked while an add is pending replaces
     *   it rather than living beside it.
     */
    fun intend(
        action: WatchlistAction,
        target: WatchlistTarget,
        subject: Any = target,
    ): PendingIntention {
        val current = pending[subject]
        if (current != null && current.action == action && current.target == target) return current

        return PendingIntention(key = newKey(), action = action, target = target)
            .also { pending[subject] = it }
    }

    /** The pending intention about [subject], if one is still unconfirmed. */
    fun pendingFor(subject: Any): PendingIntention? = pending[subject]

    /** The state of [subject] is confirmed: the next request about it is a new intention. */
    fun settle(subject: Any) {
        pending.remove(subject)
    }

    /** Forgets everything — the source changed, the screen is somewhere else. */
    fun clear() = pending.clear()
}
