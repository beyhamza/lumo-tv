package tv.lumo.android.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.aggregatedFavorites
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.model.FavoriteMembership
import tv.lumo.android.core.data.model.FavoriteRemovalOutcome
import tv.lumo.android.core.data.model.FavoriteReorderOutcome
import tv.lumo.android.core.data.ofSource
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.FavoriteLibrary
import tv.lumo.android.core.data.selectedSourceId
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Favourite channels, the first section of the library (US-12, US-022:
 * S11-E01 → S11-E04). One state for the phone and the television.
 *
 * <h2>Everything drawn here comes out of Room</h2>
 *
 * [refresh] fills the cache; it never answers the screen. Refreshing is cheap —
 * two requests — so it happens on open, and again at each `ON_START` of the
 * screens (S11-05): this is a list somebody edits from several devices, and
 * coming back to the application is when the other device's change is expected.
 *
 * <h2>The account's groups, the active source's favourites</h2>
 *
 * A group belongs to the account and may hold channels from two subscriptions;
 * what is *shown* is the share of the source being browsed (US-018). "All
 * favourites" walks the groups in their order, then the channels in theirs, and
 * keeps each channel's first occurrence — `aggregatedFavorites`, the same rule as
 * the home rail (US-020).
 *
 * <h2>The server decides (S11-00, G3)</h2>
 *
 * No optimistic state. Every write goes to the server and the cache follows its
 * answer or a re-read; a failure leaves what the server holds on screen and says
 * so. A reorder is a sequence of single moves that may stop halfway (G1/G2), and
 * "remove from all my favourites" is one removal per membership that may partly
 * fail: neither is ever announced as a success unless it was one.
 *
 * <h2>Offline: nothing changes, nothing is queued</h2>
 *
 * Writes need the network. A write that fails for lack of one marks the screen
 * offline: the list stays readable, organising is refused with an explanation,
 * and the next successful refresh lifts it. There is no write queue.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favorites: FavoriteLibrary,
    private val activeSource: ActiveSourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state

    init {
        observe()
        refresh()
    }

    private fun observe() {
        viewModelScope.launch {
            favorites.groups().collect { groups ->
                _state.update { state ->
                    state.copy(
                        groups = groups,
                        // Keeps the open group across an edit, and falls back to
                        // "All favourites" when it is gone — deleted from another
                        // device, or on this one.
                        selectedGroupId = state.selectedGroupId?.takeIf { id -> groups.any { it.id == id } },
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            favorites.favorites().collect { rows -> _state.update { it.copy(favorites = rows) } }
        }
        viewModelScope.launch {
            // The identifier and nothing more: a source that finishes importing is
            // no reason to redraw this list.
            activeSource.state
                .map { it.selectedSourceId }
                .distinctUntilChanged()
                .collect { sourceId -> _state.update { it.copy(activeSourceId = sourceId) } }
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val error = (favorites.refresh() as? LumoResult.Failure)?.error
            _state.update {
                it.copy(
                    refreshing = false,
                    loading = false,
                    // Only a failure is worth saying. A success is the list itself.
                    error = error,
                    // A failed write's notice described a state this read has
                    // just replaced: it does not outlive it (BUG-S11-06-01).
                    writeError = if (error == null) null else it.writeError,
                    // A successful read is the only way back from offline.
                    offline = if (error == null) false else it.offline || error is LumoError.Offline,
                )
            }
        }
    }

    /** A group, or null for "All favourites". */
    fun onGroupSelected(groupId: String?) = _state.update { it.copy(selectedGroupId = groupId) }

    fun onErrorShown() = _state.update { it.copy(error = null, writeError = null) }

    // ---- groups (S11-E03) ----------------------------------------------------

    fun onCreateGroupRequested() = whenWritable { _state.update { it.copy(creatingGroup = true) } }

    fun onCreateGroupConfirmed(name: String) {
        _state.update { it.copy(creatingGroup = false) }
        write { favorites.createGroup(name.trim()) }
    }

    fun onRenameRequested(group: FavoriteGroup) = whenWritable { _state.update { it.copy(renaming = group) } }

    fun onRenameConfirmed(name: String) {
        val group = _state.value.renaming ?: return
        _state.update { it.copy(renaming = null) }
        write { favorites.renameGroup(group.id, name.trim()) }
    }

    /**
     * Opens the confirmation, which is where the count comes from: the server
     * moves the group's favourites into the default group, and somebody has to be
     * able to predict that. The default group is never offered.
     */
    fun onDeleteRequested(group: FavoriteGroup) {
        if (group.isDefault) return
        whenWritable { _state.update { it.copy(deleting = group) } }
    }

    fun onDeleteConfirmed() {
        val group = _state.value.deleting ?: return
        _state.update { it.copy(deleting = null) }
        write { favorites.deleteGroup(group.id) }
    }

    fun onDialogDismissed() =
        _state.update { it.copy(renaming = null, deleting = null, creatingGroup = false) }

    /**
     * Moves a group one place. Out of range is a no-op rather than a clamp: the
     * entry is not offered at the ends, and a silent clamp would make a mis-fired
     * call look like it worked.
     */
    fun onGroupMoved(group: FavoriteGroup, delta: Int) {
        val ordered = _state.value.groups
        val target = ordered.indexOfFirst { it.id == group.id } + delta
        if (target !in ordered.indices) return
        write { favorites.moveGroup(group.id, target) }
    }

    // ---- reorder inside a group (S11-E02, G1/G2) ------------------------------

    /**
     * "Move up" / "Move down" on a phone, "Move before" / "Move after" on a
     * television: one place in the **visible** list of the open group.
     *
     * The visible slot is never sent as a position — the group may interleave
     * another source — so this goes through [reorder], like a drag.
     */
    fun onFavoriteMoved(favorite: FavoriteChannel, delta: Int) {
        val visible = _state.value.visible.map { it.favoriteId }.toMutableList()
        val from = visible.indexOf(favorite.favoriteId)
        val to = from + delta
        if (from < 0 || to !in visible.indices) return
        visible.add(to, visible.removeAt(from))
        reorder(visible)
    }

    /** A drag that ended: the visible list of the open group in its new order. */
    fun onFavoritesReordered(visibleOrder: List<String>) {
        if (visibleOrder == _state.value.visible.map { it.favoriteId }) return
        reorder(visibleOrder)
    }

    private fun reorder(visibleOrder: List<String>) {
        val groupId = _state.value.selectedGroupId ?: return
        whenWritable {
            _state.update { it.copy(busy = true, reorderNotice = null) }
            viewModelScope.launch {
                val outcome = favorites.reorderVisible(groupId, visibleOrder)
                _state.update {
                    it.copy(
                        busy = false,
                        reorderNotice = when (outcome) {
                            FavoriteReorderOutcome.Applied -> null
                            is FavoriteReorderOutcome.NotApplied -> ReorderNotice.NotApplied
                            // Never a success: some moves are in, some are not,
                            // and the list on screen is the re-read one.
                            is FavoriteReorderOutcome.NotFullyApplied ->
                                if (outcome.applied == 0) ReorderNotice.NotApplied else ReorderNotice.NotFullyApplied
                        },
                        offline = it.offline || outcome.errorOrNull() is LumoError.Offline,
                    )
                }
            }
        }
    }

    fun onReorderNoticeShown() = _state.update { it.copy(reorderNotice = null) }

    // ---- organise: memberships (S11-E02) -------------------------------------

    fun onOrganizeRequested(favorite: FavoriteChannel) =
        whenWritable { _state.update { it.copy(organizing = favorite.channel, organizeNotice = null) } }

    /**
     * Adds the channel to a group, or removes it from one.
     *
     * Organise keeps a channel in at least one group: unticking the last one is
     * refused with a word, because removing it from every group is a different
     * gesture with its own confirmation (US-022).
     */
    fun onMembershipToggled(groupId: String, checked: Boolean) {
        val channel = _state.value.organizing ?: return
        val memberships = _state.value.membershipsOf(channel.id)
        if (checked) {
            if (memberships.any { it.groupId == groupId }) return
            write(onAlreadyThere = true) { favorites.add(channel.id, groupId) }
        } else {
            val membership = memberships.firstOrNull { it.groupId == groupId } ?: return
            if (memberships.size == 1) {
                _state.update { it.copy(organizeNotice = OrganizeNotice.LastGroup) }
                return
            }
            write { favorites.remove(membership.favoriteId) }
        }
    }

    /** "New group" from Organise: created, then the channel filed in it. */
    fun onOrganizeGroupCreated(name: String) {
        val channel = _state.value.organizing ?: return
        write {
            when (val created = favorites.createGroup(name.trim())) {
                is LumoResult.Failure -> created
                is LumoResult.Success -> favorites.add(channel.id, created.value.id)
            }
        }
    }

    fun onOrganizeDismissed() = _state.update { it.copy(organizing = null, organizeNotice = null) }

    // ---- removal --------------------------------------------------------------

    /** From a group: that membership only, no confirmation (FO-09). */
    fun onRemoveFromGroup(favorite: FavoriteChannel) = write { favorites.remove(favorite.favoriteId) }

    /** From "All favourites": asks first (S11-E04). */
    fun onRemoveEverywhereRequested(favorite: FavoriteChannel) = whenWritable {
        _state.update {
            it.copy(removal = GlobalRemoval(favorite.channel, RemovalPhase.Confirming), removalDone = null)
        }
    }

    fun onRemoveEverywhereConfirmed() {
        val removal = _state.value.removal ?: return
        if (removal.phase != RemovalPhase.Confirming) return
        runRemoval(removal.channel, restrictTo = null)
    }

    /**
     * "Retry" after a partial or unconfirmed removal: **only** what remains of the
     * first attempt, after a re-read (FO-06, FO-08). Nothing is replayed that
     * already succeeded, and a membership added meanwhile is not swept in (FO-12).
     */
    fun onRemovalRetried() {
        val removal = _state.value.removal ?: return
        val restrictTo = when (val phase = removal.phase) {
            is RemovalPhase.Incomplete -> phase.remaining.mapTo(HashSet()) { it.favoriteId }
            is RemovalPhase.Unconfirmed -> phase.unconfirmed.mapTo(HashSet()) { it.favoriteId }
            is RemovalPhase.NotStarted -> null
            RemovalPhase.Confirming, RemovalPhase.Running -> return
        }
        runRemoval(removal.channel, restrictTo)
    }

    /**
     * Closing the dialog. Before the removal, a cancel; after a partial one, just a
     * close — never presented as a success (FO-07): the list shows what the server
     * holds. Not while the removals are on the wire.
     */
    fun onRemovalDismissed() {
        if (_state.value.removal?.phase == RemovalPhase.Running) return
        _state.update { it.copy(removal = null) }
    }

    /** The screen has accounted for a completed removal (announced it, moved the focus). */
    fun onRemovalShown() = _state.update { it.copy(removalDone = null) }

    private fun runRemoval(channel: Channel, restrictTo: Set<String>?) {
        if (_state.value.offline) {
            _state.update { it.copy(removal = GlobalRemoval(channel, RemovalPhase.NotStarted(offline = true))) }
            refresh()
            return
        }
        _state.update { it.copy(busy = true, removal = GlobalRemoval(channel, RemovalPhase.Running)) }
        viewModelScope.launch {
            val outcome = favorites.removeEverywhere(channel.id, restrictTo)
            _state.update {
                when (outcome) {
                    FavoriteRemovalOutcome.Removed ->
                        it.copy(busy = false, removal = null, removalDone = channel)
                    is FavoriteRemovalOutcome.Incomplete -> it.copy(
                        busy = false,
                        removal = GlobalRemoval(channel, RemovalPhase.Incomplete(outcome.remaining)),
                        offline = it.offline || outcome.error is LumoError.Offline,
                    )
                    is FavoriteRemovalOutcome.Unconfirmed -> it.copy(
                        busy = false,
                        removal = GlobalRemoval(channel, RemovalPhase.Unconfirmed(outcome.unconfirmed)),
                        offline = it.offline || outcome.error is LumoError.Offline,
                    )
                    is FavoriteRemovalOutcome.NotStarted -> it.copy(
                        busy = false,
                        removal = GlobalRemoval(
                            channel,
                            RemovalPhase.NotStarted(offline = outcome.error is LumoError.Offline),
                        ),
                        offline = it.offline || outcome.error is LumoError.Offline,
                    )
                }
            }
        }
    }

    // ---- plumbing -------------------------------------------------------------

    /** Runs [block] when a write may start; offline, re-reads instead (nothing is queued). */
    private inline fun whenWritable(block: () -> Unit) {
        val state = _state.value
        if (state.busy) return
        if (state.offline) {
            refresh()
            return
        }
        block()
    }

    /**
     * Runs one write and reports what it did.
     *
     * A refusal other than "offline" is followed by a re-read: the server said no
     * because its state differs from this screen's — a group deleted elsewhere, a
     * favourite already moved — and the screen must show the server's.
     *
     * @param onAlreadyThere `409 FAVORITE_ALREADY_EXISTS` counts as done: the
     * channel is in that group, which is what was asked.
     */
    private fun write(onAlreadyThere: Boolean = false, block: suspend () -> LumoResult<*>) {
        whenWritable {
            _state.update { it.copy(busy = true) }
            viewModelScope.launch {
                val refused = (block() as? LumoResult.Failure)?.error
                val error = refused?.takeUnless { onAlreadyThere && it.isAlreadyThere() }
                _state.update {
                    it.copy(
                        busy = false,
                        writeError = error?.takeUnless { e -> e is LumoError.Offline },
                        offline = it.offline || error is LumoError.Offline,
                    )
                }
                if (refused != null && refused !is LumoError.Offline) favorites.refresh()
            }
        }
    }
}

private fun LumoError.isAlreadyThere(): Boolean =
    this is LumoError.Api && code == ErrorCode.FAVORITE_ALREADY_EXISTS

private fun FavoriteReorderOutcome.errorOrNull(): LumoError? = when (this) {
    FavoriteReorderOutcome.Applied -> null
    is FavoriteReorderOutcome.NotApplied -> error
    is FavoriteReorderOutcome.NotFullyApplied -> error
}

/** What a reorder left behind, when it was not everything that was asked. */
enum class ReorderNotice {
    /** Nothing moved. */
    NotApplied,

    /** Some moves are in, some are not; the list shows the order actually saved. */
    NotFullyApplied,
}

enum class OrganizeNotice {
    /** The last group cannot be unticked here: that is "remove from all my favourites". */
    LastGroup,
}

/** "Remove from all my favourites", from its question to its verdict (S11-E04, G2). */
data class GlobalRemoval(val channel: Channel, val phase: RemovalPhase)

sealed interface RemovalPhase {
    data object Confirming : RemovalPhase

    data object Running : RemovalPhase

    /** Re-read: these memberships are still there. Retry targets them only. */
    data class Incomplete(val remaining: List<FavoriteMembership>) : RemovalPhase

    /** No usable answer and no re-read: these may or may not be gone. */
    data class Unconfirmed(val unconfirmed: List<FavoriteMembership>) : RemovalPhase

    /** Nothing was sent. */
    data class NotStarted(val offline: Boolean) : RemovalPhase
}

data class FavoritesState(
    /** True until the first Room emission. Distinct from "no favourites". */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val groups: List<FavoriteGroup> = emptyList(),
    /** The account's favourites, every source. Everything drawn reads [ofActiveSource]. */
    val favorites: List<FavoriteChannel> = emptyList(),
    /** The open group, or null for "All favourites" — what the library opens on. */
    val selectedGroupId: String? = null,
    /** The last refresh failed: the list shown is this device's. */
    val error: LumoError? = null,
    /** A write the server refused. The list has been re-read since. */
    val writeError: LumoError? = null,
    /**
     * The source being browsed, or null when there is none (US-018). With no
     * active source nothing is shown rather than everything.
     */
    val activeSourceId: String? = null,
    /** A write failed for lack of network: organising is off until a refresh succeeds. */
    val offline: Boolean = false,

    /** One write at a time. Two reorders in flight would race on the server. */
    val busy: Boolean = false,
    val creatingGroup: Boolean = false,
    val renaming: FavoriteGroup? = null,
    val deleting: FavoriteGroup? = null,
    /** The channel whose groups are being chosen. */
    val organizing: Channel? = null,
    val organizeNotice: OrganizeNotice? = null,
    val removal: GlobalRemoval? = null,
    /** A channel just removed from all favourites, for the screen to announce. */
    val removalDone: Channel? = null,
    val reorderNotice: ReorderNotice? = null,
) {

    val isAllView: Boolean get() = selectedGroupId == null

    val selectedGroup: FavoriteGroup? get() = groups.firstOrNull { it.id == selectedGroupId }

    /**
     * What the open view lists: "All favourites", each channel once in the
     * library's order; or the open group, in **the user's** order — `position`,
     * never the channel name, or reordering would change nothing visible.
     */
    val visible: List<FavoriteChannel>
        get() = if (selectedGroupId == null) {
            aggregated
        } else {
            ofActiveSource.filter { it.groupId == selectedGroupId }.sortedBy { it.position }
        }

    /** The favourites of the source being browsed (US-018). */
    val ofActiveSource: List<FavoriteChannel>
        get() = favorites.ofSource(activeSourceId)

    /**
     * Every favourite of the source being browsed, **each channel once** (US-020):
     * the same `core:data` function as the home rail, so the two cannot disagree
     * about where a channel sits.
     */
    val aggregated: List<FavoriteChannel>
        get() = aggregatedFavorites(groups, favorites, activeSourceId)

    /**
     * Some favourites of this source could not be resolved on this device (S11-05):
     * listed as "Details unavailable", not playable, removable — and said, with a
     * Retry, rather than dropped.
     */
    val partial: Boolean
        get() = ofActiveSource.any { !it.resolved }

    /** Nothing starred at all in this source — as opposed to an empty group. */
    val nothingAtAll: Boolean
        get() = !loading && ofActiveSource.isEmpty()

    /** Organising may start: online and nothing on the wire. */
    val canWrite: Boolean get() = !offline && !busy

    /**
     * How many favourites a deletion is about to move — **counted over the
     * account**, every source: the server moves them all, and a smaller number
     * would be a promise the deletion does not keep.
     */
    fun countIn(group: FavoriteGroup): Int = favorites.count { it.groupId == group.id }

    fun canMoveGroup(group: FavoriteGroup, delta: Int): Boolean =
        groups.indexOfFirst { it.id == group.id } + delta in groups.indices

    /** Within the open group's visible list; never in "All favourites", which has no order of its own. */
    fun canMoveFavorite(favorite: FavoriteChannel, delta: Int): Boolean =
        !isAllView && visible.indexOfFirst { it.favoriteId == favorite.favoriteId } + delta in visible.indices

    /** Every group a channel is filed in, across the account. */
    fun membershipsOf(channelId: String): List<FavoriteMembership> =
        favorites.filter { it.channel.id == channelId }.map { FavoriteMembership(it.favoriteId, it.groupId) }

    /** The groups of [memberships], in the groups' order, for a dialog that names them. */
    fun groupsOf(memberships: List<FavoriteMembership>): List<FavoriteGroup> {
        val ids = memberships.mapTo(HashSet()) { it.groupId }
        return groups.filter { it.id in ids }
    }
}
