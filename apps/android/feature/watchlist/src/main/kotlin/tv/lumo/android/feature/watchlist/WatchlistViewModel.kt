package tv.lumo.android.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.WatchlistIntentions
import tv.lumo.android.core.data.model.PendingIntention
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.data.model.WatchlistSlice
import tv.lumo.android.core.data.model.WatchlistTarget
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.core.data.model.WatchlistWriteOutcome
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.onFailureNaming
import tv.lumo.android.core.data.selectedSourceId
import tv.lumo.android.core.data.repository.WatchlistRepository

/**
 * "My list" (US-022, S11-E05): the films and series saved for the **active source**,
 * most recently added first, in the server's order and never re-sorted here.
 *
 * <h2>In memory, and only in memory</h2>
 *
 * The list lives in this view model and nowhere else: no Room table, no file
 * (ADR 0011, ruling 4). Offline, the screen keeps showing **what it already
 * loaded**, says it is offline, and refuses changes; nothing is queued. When the
 * network comes back the list is **re-read before** removal is offered again.
 *
 * <h2>A removal is an intention with a key</h2>
 *
 * The compact panel of an unavailable card removes it by item id (`WL-03`). The key
 * comes from [WatchlistIntentions]: "Retry" after an unconfirmed result sends the
 * same key, a removal after a confirmed state is a new one.
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlist: WatchlistRepository,
    private val activeSource: ActiveSourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchlistState())
    val state: StateFlow<WatchlistState> = _state.asStateFlow()

    private val intentions = WatchlistIntentions()
    private var reading: Job? = null
    private var writing: Job? = null

    init {
        viewModelScope.launch {
            activeSource.state
                .map { it.sourceSelection() }
                .distinctUntilChanged()
                .collect(::onSourceChanged)
        }
        viewModelScope.launch {
            // The only way back from "offline" to a list that can be changed.
            watchlist.reconnections.collect { if (_state.value.offline) read() }
        }
    }

    // ---- reads -------------------------------------------------------------

    /** Re-reads quietly, keeping what is on screen — on a return to the screen, say. */
    fun refresh() {
        if (reading?.isActive == true) return
        if (_state.value.sourceId == null) return
        read()
    }

    /** "Retry" after a failed load: the same quiet re-read, the items kept. */
    fun retry() = read()

    fun loadMore() {
        val current = _state.value
        val sourceId = current.sourceId ?: return
        if (!current.hasMore || current.loadingMore || reading?.isActive == true) return

        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        reading = viewModelScope.launch {
            val result = watchlist.page(sourceId, current.filter.type, current.loadedPages)
            _state.update { state ->
                if (state.sourceId != sourceId || state.filter != current.filter) return@update state
                when (result) {
                    is LumoResult.Success -> {
                        val known = state.items.mapTo(HashSet()) { it.id }
                        state.copy(
                            items = state.items + result.value.items.filter { it.id !in known },
                            types = result.value.types,
                            loadedPages = state.loadedPages + 1,
                            hasMore = result.value.hasMore,
                            loadingMore = false,
                        )
                    }
                    is LumoResult.Failure -> state.copy(
                        loadingMore = false,
                        loadMoreFailed = true,
                        offline = state.offline || result.error is LumoError.Offline,
                    )
                }
            }
        }
    }

    fun selectFilter(filter: WatchlistFilter) {
        val current = _state.value
        if (filter == current.filter || current.sourceId == null) return
        // A filter is another list: what was shown does not belong on screen while
        // the new one loads, so the screen goes back to loading — with its filters.
        _state.update {
            it.copy(
                filter = filter,
                items = emptyList(),
                loadedPages = 0,
                hasMore = false,
                phase = WatchlistPhase.Loading,
                loadMoreFailed = false,
            )
        }
        read()
    }

    private fun onSourceChanged(selection: SourceSelection) {
        reading?.cancel()
        writing?.cancel()
        intentions.clear()
        when (selection) {
            SourceSelection.Pending -> _state.value = WatchlistState()
            SourceSelection.None -> _state.value = WatchlistState(phase = WatchlistPhase.NoSource)
            is SourceSelection.Selected -> {
                _state.value = WatchlistState(sourceId = selection.sourceId)
                read()
            }
        }
    }

    /**
     * Reads every page already on screen again, from the top, for the current filter.
     *
     * While it runs, removal is off (`rereading`): a change is offered on a list
     * the server has just confirmed, never on one it may have moved past.
     */
    private fun read() {
        val start = _state.value
        val sourceId = start.sourceId ?: return
        val filter = start.filter

        reading?.cancel()
        _state.update { it.copy(rereading = true) }
        reading = viewModelScope.launch {
            val pages = start.loadedPages.coerceAtLeast(1)
            val items = mutableListOf<WatchlistEntry>()
            var failure: LumoError? = null
            var last: WatchlistSlice? = null
            for (index in 0 until pages) {
                when (val result = watchlist.page(sourceId, filter.type, index)) {
                    is LumoResult.Failure -> {
                        failure = result.error
                        break
                    }
                    is LumoResult.Success -> {
                        last = result.value
                        val known = items.mapTo(HashSet()) { it.id }
                        items += result.value.items.filter { it.id !in known }
                        if (!result.value.hasMore) break
                    }
                }
            }

            // An empty list or a failure may be a source deleted from another
            // device: `GET /me/watchlist` answers an empty `200` for a source that
            // no longer exists, and "Your list is empty" would be a lie about a
            // source that is gone. The source is checked first; when it has gone,
            // the change of source re-draws this screen (S11-05).
            if ((items.isEmpty() || failure != null) && !sourceStillActive(sourceId, failure)) {
                _state.update { if (it.sourceId == sourceId) it.copy(rereading = false) else it }
                return@launch
            }

            val slice = last
            if (failure != null || slice == null) {
                val offline = failure is LumoError.Offline
                _state.update { state ->
                    if (state.phase == WatchlistPhase.Ready) {
                        // Data on screen stays on screen (S11-E07).
                        state.copy(rereading = false, offline = offline, refreshFailed = !offline)
                    } else {
                        state.copy(rereading = false, phase = WatchlistPhase.LoadFailed(offline), offline = offline)
                    }
                }
                return@launch
            }

            // A filter on a type the list no longer holds — the last series was
            // removed elsewhere — falls back to All rather than to an empty filter.
            if (filter != WatchlistFilter.All && filter !in filtersFor(slice.types)) {
                _state.update { it.copy(filter = WatchlistFilter.All, loadedPages = 0, rereading = false) }
                read()
                return@launch
            }

            _state.update { state ->
                val pending = state.removal
                // An unconfirmed removal the list no longer shows was accepted after
                // all: settled, and the next removal is a new intention.
                val removalSettled = pending != null && !pending.busy && items.none { it.id == pending.itemId }
                if (removalSettled) intentions.settle(WatchlistTarget.Item(pending!!.itemId))
                state.copy(
                    phase = WatchlistPhase.Ready,
                    items = items,
                    types = slice.types,
                    loadedPages = (slice.page + 1).coerceAtLeast(1),
                    hasMore = slice.hasMore,
                    rereading = false,
                    offline = false,
                    refreshFailed = false,
                    loadMoreFailed = false,
                    removal = if (removalSettled) null else pending,
                    panel = if (removalSettled) null else state.panel,
                    removedId = if (removalSettled) pending!!.itemId else state.removedId,
                )
            }
        }
    }

    /**
     * Whether [sourceId] is still the active source, asked of the server.
     *
     * A `404 SOURCE_NOT_FOUND` is a proof on its own; otherwise the source list is
     * re-read, and its absence from a successful list is the other proof. Offline,
     * nothing can be proven and the source is presumed still there (US-018).
     */
    private suspend fun sourceStillActive(sourceId: String, failure: LumoError?): Boolean {
        if (failure != null && activeSource.onFailureNaming(sourceId, failure)) return false
        if (failure is LumoError.Offline) return true
        activeSource.refresh()
        return activeSource.state.value.selectedSourceId == sourceId
    }

    // ---- the compact panel of an unavailable card ---------------------------

    /** Opens the compact panel (WL-03). Never a fake sheet: the content is gone. */
    fun openPanel(entry: WatchlistEntry) = _state.update { it.copy(panel = entry, removal = null) }

    fun closePanel() {
        if (_state.value.removal?.busy == true) return
        _state.update { it.copy(panel = null, removal = null) }
    }

    /**
     * "Remove from my list", or "Retry" after an unconfirmed removal — the same call,
     * and the same key while the removal is unconfirmed.
     *
     * Offline it re-reads instead: nothing is sent, nothing is queued, and the
     * button comes back once the list has been confirmed.
     */
    fun remove(entry: WatchlistEntry) {
        val current = _state.value
        if (current.removal?.busy == true) return
        if (current.offline || current.rereading) {
            if (current.offline) read()
            return
        }
        send(intentions.intend(WatchlistAction.Remove, WatchlistTarget.Item(entry.id)))
    }

    /** The idempotency key the panel's removal would send now. For tests. */
    internal fun pendingRemovalOf(itemId: String): PendingIntention? =
        intentions.pendingFor(WatchlistTarget.Item(itemId))

    /** The removed card has been accounted for by the screen (focus moved). */
    fun onRemovalShown() = _state.update { it.copy(removedId = null) }

    private fun send(intention: PendingIntention) {
        val itemId = (intention.target as WatchlistTarget.Item).itemId
        _state.update { it.copy(removal = RemovalState(itemId, busy = true)) }

        writing = viewModelScope.launch {
            when (watchlist.write(intention)) {
                is WatchlistWriteOutcome.Confirmed,
                is WatchlistWriteOutcome.AlreadyInState,
                -> {
                    intentions.settle(intention.target)
                    _state.update { state ->
                        state.copy(
                            items = state.items.filterNot { it.id == itemId },
                            removal = null,
                            panel = null,
                            removedId = itemId,
                        )
                    }
                }

                is WatchlistWriteOutcome.Unknown -> _state.update {
                    // "Change not confirmed": never announced as done, never
                    // re-sent under a new key. The list is re-read below.
                    it.copy(removal = RemovalState(itemId, busy = false, unconfirmed = true))
                }

                is WatchlistWriteOutcome.Rejected -> {
                    intentions.settle(intention.target)
                    _state.update {
                        it.copy(removal = RemovalState(itemId, busy = false, rejected = true))
                    }
                }
            }
            // A replayed answer describes the moment it was accepted (S11-00 G7):
            // what the list looks like now is a question for the server.
            read()
        }
    }
}

// ---- state ------------------------------------------------------------------

/** The All / Films / Series selector. */
enum class WatchlistFilter(val type: WatchlistType?) {
    All(null),
    Films(WatchlistType.Film),
    Series(WatchlistType.Series),
}

/**
 * The filters to offer for the types the list holds (`WL-17`): none below two
 * types — a selector with one choice says nothing — and all three from two.
 * Unavailable items count: [types] comes from the server and includes them.
 */
fun filtersFor(types: Set<WatchlistType>): List<WatchlistFilter> =
    if (types.size < 2) emptyList() else WatchlistFilter.entries

sealed interface WatchlistPhase {
    /** Nothing to show yet. Never drawn as an empty list. */
    data object Loading : WatchlistPhase

    /** The list as the server last confirmed it (possibly empty). */
    data object Ready : WatchlistPhase

    /** Nothing could be loaded. Offline says so; neither claims an empty list (WL-07). */
    data class LoadFailed(val offline: Boolean) : WatchlistPhase

    /** No source selected: there is no list to scope. */
    data object NoSource : WatchlistPhase
}

/** A removal from the compact panel, while it is not settled. */
data class RemovalState(
    val itemId: String,
    val busy: Boolean = false,
    /** Sent, outcome unknown: "Change not confirmed" and Retry with the same key. */
    val unconfirmed: Boolean = false,
    /** The server refused it. */
    val rejected: Boolean = false,
)

data class WatchlistState(
    val sourceId: String? = null,
    val phase: WatchlistPhase = WatchlistPhase.Loading,
    /** In the server's order, most recently added first. Never re-sorted. */
    val items: List<WatchlistEntry> = emptyList(),
    /** The types the whole list holds for the source, unavailable items included. */
    val types: Set<WatchlistType> = emptySet(),
    val filter: WatchlistFilter = WatchlistFilter.All,
    val loadedPages: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** A re-read is in flight; removal waits for it. */
    val rereading: Boolean = false,
    /** The last read found no network: read-only, with a notice (WL-06). */
    val offline: Boolean = false,
    /** The last re-read failed for another reason; the items on screen are kept. */
    val refreshFailed: Boolean = false,
    /** The unavailable card whose compact panel is open. */
    val panel: WatchlistEntry? = null,
    val removal: RemovalState? = null,
    /** The last card removed, until the screen has moved the focus off it. */
    val removedId: String? = null,
) {
    val filters: List<WatchlistFilter> get() = filtersFor(types)

    /** Whether a removal may be sent now. */
    val canRemove: Boolean
        get() = phase == WatchlistPhase.Ready && !offline && !rereading && removal?.busy != true

    /** The list is empty, confirmed by the server, with no filter to blame. */
    val confirmedEmpty: Boolean
        get() = phase == WatchlistPhase.Ready && items.isEmpty() && filter == WatchlistFilter.All

    /** A filter with nothing in it: the filters stay and "All" is offered. */
    val filterEmpty: Boolean
        get() = phase == WatchlistPhase.Ready && items.isEmpty() && filter != WatchlistFilter.All
}

private sealed interface SourceSelection {
    data object Pending : SourceSelection
    data object None : SourceSelection
    data class Selected(val sourceId: String) : SourceSelection
}

private fun ActiveSourceState.sourceSelection(): SourceSelection = when (this) {
    ActiveSourceState.Loading -> SourceSelection.Pending
    is ActiveSourceState.Selected -> SourceSelection.Selected(sourceId)
    else -> SourceSelection.None
}
