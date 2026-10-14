package tv.lumo.android.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.model.PendingIntention
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistTarget
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.core.data.model.WatchlistWriteOutcome
import tv.lumo.android.core.data.repository.WatchlistRepository

/**
 * The "Add to my list" / "In my list" button of a film or series sheet (US-022,
 * S11-E06), shared by the four sheets — film and series, phone and television.
 *
 * <h2>What it guarantees</h2>
 *
 * - **One key per intention** ([WatchlistIntentions]). A press that asks again for
 *   the change still unconfirmed is a retry and re-sends the same key; a press
 *   after the state was confirmed is a new intention.
 * - **An unknown result is never a success.** No answer, a `5xx`, a reused key:
 *   the button says the change is not confirmed, the membership is **re-read**
 *   before anything else is offered, and the retry uses the same key. Nothing is
 *   retried on the user's behalf.
 * - **Offline is read-only.** A membership that cannot be read, or a device without
 *   network, disables the button with an explanation; nothing is queued (ADR 0011,
 *   ruling 4). When the network comes back the membership is re-read **before** the
 *   button is enabled again (`WL-08`).
 * - Removing never touches playback progress: it is a different resource.
 *
 * A plain class rather than a view model: two features own the sheets and may not
 * share one (AGENTS.md §2). Each sheet's view model holds one, on its own scope.
 */
class WatchlistToggle(
    private val repository: WatchlistRepository,
    private val scope: CoroutineScope,
    private val intentions: WatchlistIntentions = WatchlistIntentions(),
) {

    private val _state = MutableStateFlow(WatchlistToggleState())
    val state: StateFlow<WatchlistToggleState> = _state.asStateFlow()

    private var subject: WatchlistTarget.Content? = null
    private var sourceId: String? = null
    private var work: Job? = null
    private var listening: Job? = null

    /**
     * Reads the membership of [contentId] in [sourceId]'s list. Once per content;
     * a second call with the same arguments does nothing.
     */
    fun start(sourceId: String, type: WatchlistType, contentId: String) {
        val content = WatchlistTarget.Content(type, contentId)
        if (subject == content && this.sourceId == sourceId) return
        subject = content
        this.sourceId = sourceId
        intentions.clear()
        _state.value = WatchlistToggleState()

        work?.cancel()
        work = scope.launch { reread() }

        listening?.cancel()
        listening = scope.launch {
            // A reconnection is a reason to ask again, and the only way back from
            // "offline" to an enabled button.
            repository.reconnections.collect {
                if (_state.value.offline && work?.isActive != true) {
                    work = scope.launch { reread() }
                }
            }
        }
    }

    /**
     * The button was pressed: add when out, remove when in.
     *
     * Ignored while something is in flight, while offline, or before the
     * membership is known — the button is disabled then, and a stray key press
     * must not get past that.
     */
    fun toggle() {
        val content = subject ?: return
        val current = _state.value
        if (!current.enabled) return

        val intention = when (val membership = current.membership) {
            WatchlistMembership.Out -> intentions.intend(WatchlistAction.Add, content, subject = content)
            is WatchlistMembership.In -> intentions.intend(
                WatchlistAction.Remove,
                WatchlistTarget.Item(membership.itemId),
                subject = content,
            )
            WatchlistMembership.Unknown -> return
        }
        send(intention)
    }

    /**
     * "Retry", offered next to "Change not confirmed": the **same** intention with
     * the **same** key. Without a pending intention it re-reads the membership —
     * which is what a retry after a failed read means.
     */
    fun retry() {
        val content = subject ?: return
        if (_state.value.busy) return
        val pending = intentions.pendingFor(content)
        if (pending != null && !_state.value.offline) {
            send(pending)
        } else {
            work = scope.launch { reread() }
        }
    }

    private fun send(intention: PendingIntention) {
        _state.update { it.copy(busy = true, rejected = false) }
        work = scope.launch {
            val outcome = repository.write(intention)
            val content = subject ?: return@launch
            when (outcome) {
                is WatchlistWriteOutcome.Confirmed,
                is WatchlistWriteOutcome.AlreadyInState,
                -> {
                    intentions.settle(content)
                    val item = (outcome as? WatchlistWriteOutcome.Confirmed)?.item
                        ?: (outcome as? WatchlistWriteOutcome.AlreadyInState)?.item
                    _state.update {
                        it.copy(
                            unconfirmed = false,
                            membership = when (intention.action) {
                                WatchlistAction.Add ->
                                    item?.let { entry -> WatchlistMembership.In(entry.id) } ?: it.membership
                                WatchlistAction.Remove -> WatchlistMembership.Out
                            },
                        )
                    }
                }

                is WatchlistWriteOutcome.Unknown -> _state.update { it.copy(unconfirmed = true) }

                is WatchlistWriteOutcome.Rejected -> {
                    // Refused, so nothing was recorded: the next press is a new intention.
                    intentions.settle(content)
                    _state.update { it.copy(unconfirmed = false, rejected = true) }
                }
            }
            // Always: a replayed answer describes the moment it was accepted, not
            // now (`S11-00` G7), and an unknown one describes nothing at all.
            reread()
        }
    }

    private suspend fun reread() {
        val content = subject ?: return
        val source = sourceId ?: return
        _state.update { it.copy(busy = true) }

        when (val result = repository.find(source, content.type, content.contentId)) {
            is LumoResult.Success -> {
                val membership = result.value
                    ?.let { WatchlistMembership.In(it.id) }
                    ?: WatchlistMembership.Out

                // A pending change the list now shows in place was accepted after
                // all: it is settled, and the next press is a new intention.
                val pending = intentions.pendingFor(content)
                val reached = pending != null && when (pending.action) {
                    WatchlistAction.Add -> membership is WatchlistMembership.In
                    WatchlistAction.Remove -> membership == WatchlistMembership.Out
                }
                if (reached) intentions.settle(content)

                _state.update {
                    it.copy(
                        membership = membership,
                        busy = false,
                        offline = false,
                        checkFailed = false,
                        unconfirmed = it.unconfirmed && !reached,
                    )
                }
            }

            is LumoResult.Failure -> _state.update {
                it.copy(
                    busy = false,
                    offline = result.error is LumoError.Offline,
                    checkFailed = result.error !is LumoError.Offline,
                )
            }
        }
    }
}

/** Whether the content is in the list, as last read from the server. */
sealed interface WatchlistMembership {
    /** Never read yet, or the read failed: the button cannot say anything true. */
    data object Unknown : WatchlistMembership
    data object Out : WatchlistMembership
    data class In(val itemId: String) : WatchlistMembership
}

data class WatchlistToggleState(
    val membership: WatchlistMembership = WatchlistMembership.Unknown,
    /** A read or a write is in flight. */
    val busy: Boolean = false,
    /** The last read failed for want of a network: read-only, with an explanation. */
    val offline: Boolean = false,
    /** The last read failed for another reason. */
    val checkFailed: Boolean = false,
    /** A change was sent and nobody knows whether it was accepted ("Change not confirmed"). */
    val unconfirmed: Boolean = false,
    /** The server refused the last change. */
    val rejected: Boolean = false,
) {
    /** Whether pressing the button does anything. */
    val enabled: Boolean
        get() = !busy && !offline && membership != WatchlistMembership.Unknown

    /** Whether to offer "Retry" beside the button. */
    val retryable: Boolean
        get() = !busy && (unconfirmed || checkFailed || offline)
}
