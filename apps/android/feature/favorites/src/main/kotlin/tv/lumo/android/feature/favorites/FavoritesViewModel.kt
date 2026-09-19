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
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.ofSource
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.FavoriteRepository
import tv.lumo.android.core.data.selectedSourceId

/**
 * The favourites screen (US-12).
 *
 * <h2>Everything drawn here comes out of Room</h2>
 *
 * Same rule as the catalogue, and it matters more: this is the list somebody
 * curated by hand, and it is the one they open on a train. [refresh] fills the
 * cache; it never answers the screen.
 *
 * <h2>Refreshing here is cheap, so it happens on open</h2>
 *
 * And that is the difference with `LiveViewModel`, which refuses to: a catalogue
 * refresh walks fifteen thousand channels, while this is two requests and a list
 * somebody edits from several devices. Opening the tab is exactly the moment they
 * expect to see what the television did.
 *
 * <h2>The account's groups, the active source's favourites</h2>
 *
 * A group belongs to the account and may hold channels from two subscriptions —
 * that has not changed. What has (US-018) is what is *shown*: the favourites of
 * the source being browsed, and nothing else. They are filtered on the device,
 * since the API has no such filter, and only filtered: switching back to a source
 * shows its favourites exactly as they were left.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favorites: FavoriteRepository,
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
                        // Keeps the open tab across an edit, and falls back to the
                        // first group when the selected one is gone — deleted from
                        // another device, or by S4-05 on this one.
                        selectedGroupId = state.selectedGroupId
                            ?.takeIf { id -> groups.any { it.id == id } }
                            ?: groups.firstOrNull()?.id,
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            favorites.favorites().collect { rows ->
                _state.update { it.copy(favorites = rows) }
            }
        }
        viewModelScope.launch {
            // The identifier and nothing more: a source that finishes importing,
            // or a sibling that gets renamed, is no reason to redraw this list.
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
            val result = favorites.refresh()
            _state.update {
                it.copy(
                    refreshing = false,
                    loading = false,
                    // Only a failure is worth saying. A success is the list itself.
                    error = (result as? LumoResult.Failure)?.error,
                )
            }
        }
    }

    fun onGroupSelected(groupId: String) =
        _state.update { it.copy(selectedGroupId = groupId) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    // ---- organising (S4-05) -------------------------------------------------

    fun onRenameRequested(group: FavoriteGroup) =
        _state.update { it.copy(renaming = group) }

    fun onRenameConfirmed(name: String) {
        val group = _state.value.renaming ?: return
        _state.update { it.copy(renaming = null) }
        write { favorites.renameGroup(group.id, name.trim()) }
    }

    /**
     * Opens the confirmation, which is where the count comes from.
     *
     * Asked before the deletion rather than after, and with the number in it: the
     * server moves the group's favourites into the default group, and somebody has
     * to be able to predict that. "Are you sure?" tells them nothing they did not
     * already know.
     */
    fun onDeleteRequested(group: FavoriteGroup) =
        _state.update { it.copy(deleting = group) }

    fun onDeleteConfirmed() {
        val group = _state.value.deleting ?: return
        _state.update { it.copy(deleting = null) }
        write { favorites.deleteGroup(group.id) }
    }

    fun onDialogDismissed() =
        _state.update { it.copy(renaming = null, deleting = null, moving = null) }

    /**
     * Moves a group one place, in the list's own order.
     *
     * `delta` rather than a target index, because that is the gesture: the menu
     * offers "move up" and "move down". Out of range is a no-op rather than a
     * clamp — the menu entry is not offered at the ends, and a silent clamp would
     * make a mis-fired call look like it worked.
     */
    fun onGroupMoved(group: FavoriteGroup, delta: Int) {
        val ordered = _state.value.groups
        val target = ordered.indexOfFirst { it.id == group.id } + delta
        if (target !in ordered.indices) return
        write { favorites.moveGroup(group.id, target) }
    }

    /** The same, for one favourite inside its group. */
    fun onFavoriteMoved(favorite: FavoriteChannel, delta: Int) {
        val ordered = _state.value.visible
        val target = ordered.indexOfFirst { it.favoriteId == favorite.favoriteId } + delta
        if (target !in ordered.indices) return
        write { favorites.move(favorite.favoriteId, position = target) }
    }

    fun onMoveRequested(favorite: FavoriteChannel) =
        _state.update { it.copy(moving = favorite) }

    fun onMoveConfirmed(groupId: String) {
        val favorite = _state.value.moving ?: return
        _state.update { it.copy(moving = null) }
        // One call, not a remove then an add: that pair loses the position, and a
        // connection dropped between the two loses the favourite.
        write { favorites.move(favorite.favoriteId, groupId = groupId) }
    }

    fun onFavoriteRemoved(favorite: FavoriteChannel) =
        write { favorites.remove(favorite.favoriteId) }

    /**
     * Runs one write and reports what it did.
     *
     * No optimistic state here, unlike the heart in the channel list. The
     * difference is the gesture: starring is frequent and wants an instant answer,
     * while renaming a group happens once and its result is a word changing on
     * screen. Guessing at it would mean predicting a renumbering the server owns —
     * which is the very thing `FavoriteRepository` refuses to do.
     */
    private fun write(block: suspend () -> LumoResult<*>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }

        viewModelScope.launch {
            val result = block()
            _state.update {
                it.copy(busy = false, error = (result as? LumoResult.Failure)?.error)
            }
        }
    }
}

data class FavoritesState(
    /** True until the first Room emission. Distinct from "no favourites". */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val groups: List<FavoriteGroup> = emptyList(),
    val favorites: List<FavoriteChannel> = emptyList(),
    val selectedGroupId: String? = null,
    val error: LumoError? = null,
    /**
     * The source being browsed, or null when there is none — no source at all, or
     * several and no choice yet (US-018).
     *
     * [favorites] stays the account's list, untouched, and everything drawn reads
     * it through [ofActiveSource]. Keeping the whole list is what makes a switch
     * of source instant here, and what keeps [countIn] honest.
     */
    val activeSourceId: String? = null,

    // ---- organising (S4-05) ------------------------------------------------

    /** One write at a time. Two reorders in flight would race on the server. */
    val busy: Boolean = false,
    val renaming: FavoriteGroup? = null,
    val deleting: FavoriteGroup? = null,
    val moving: FavoriteChannel? = null,
) {

    /**
     * The favourites of the open tab, in **the user's** order.
     *
     * Their order and not the provider's: `position` is what S4-05 lets them
     * change, and sorting by channel name here would make that whole task
     * invisible.
     */
    val visible: List<FavoriteChannel>
        get() = ofActiveSource
            .filter { it.groupId == selectedGroupId }
            .sortedBy { it.position }

    /**
     * The favourites of the source being browsed (US-018).
     *
     * With no active source this is empty rather than everything: there is no
     * catalogue on screen for a favourite to belong to.
     */
    val ofActiveSource: List<FavoriteChannel>
        get() = favorites.ofSource(activeSourceId)

    /**
     * Nothing starred at all — as opposed to an empty tab in an account that has
     * favourites elsewhere. The two want different words: one names the gesture
     * that starts, the other says this shelf is empty.
     *
     * "At all" is within the source being browsed. Somebody with forty favourites
     * in another source and none here is, on this screen, somebody who has not
     * pressed a heart yet — and the gesture is the useful thing to name.
     */
    val nothingAtAll: Boolean
        get() = !loading && ofActiveSource.isEmpty()

    /**
     * How many favourites a deletion is about to move, for the confirmation.
     *
     * The number is the whole point of asking: it lets somebody predict the state
     * they will be in. Without it the dialog says "are you sure" about something
     * they cannot picture.
     *
     * **Counted over the account, not over the active source.** Deleting a group
     * moves every favourite it holds, whichever source it came from, and the
     * number has to be the one the server is about to act on — a smaller one
     * would be a promise the deletion does not keep.
     */
    fun countIn(group: FavoriteGroup): Int = favorites.count { it.groupId == group.id }

    /** Where a group can go, so the menu offers only moves that exist. */
    fun canMoveGroup(group: FavoriteGroup, delta: Int): Boolean =
        groups.indexOfFirst { it.id == group.id } + delta in groups.indices

    /** The same for a favourite, within the open tab. */
    fun canMoveFavorite(favorite: FavoriteChannel, delta: Int): Boolean =
        visible.indexOfFirst { it.favoriteId == favorite.favoriteId } + delta in visible.indices

    /**
     * The groups a favourite could be moved into: every group but its own.
     *
     * Its own is absent rather than disabled — an entry that does nothing is an
     * entry somebody presses once to find out.
     */
    fun moveTargets(favorite: FavoriteChannel): List<FavoriteGroup> =
        groups.filter { it.id != favorite.groupId }
}
