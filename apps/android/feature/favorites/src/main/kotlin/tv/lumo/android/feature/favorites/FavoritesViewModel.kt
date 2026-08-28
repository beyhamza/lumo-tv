package tv.lumo.android.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.repository.FavoriteRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.valueOrNull

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
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favorites: FavoriteRepository,
    private val sources: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state

    init {
        observe()
        refresh()
        loadSourceNames()
    }

    /**
     * The names of the account's sources, once, for the whole screen.
     *
     * Not cached anywhere — `core:database` holds channels and categories, not
     * sources — so this is a network read, and it is allowed to fail: offline, the
     * rows simply do not say which subscription they came from. A favourites
     * screen that needed the network to draw a channel name would defeat the point
     * of the cache underneath it.
     */
    private fun loadSourceNames() {
        viewModelScope.launch {
            val loaded = sources.sources().valueOrNull() ?: return@launch
            _state.update { state ->
                state.copy(sourceNames = loaded.associate { it.id.toString() to it.label })
            }
        }
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
}

data class FavoritesState(
    /** True until the first Room emission. Distinct from "no favourites". */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val groups: List<FavoriteGroup> = emptyList(),
    val favorites: List<FavoriteChannel> = emptyList(),
    val selectedGroupId: String? = null,
    val error: LumoError? = null,
    /** Source id to name. Empty offline, and that is a supported state. */
    val sourceNames: Map<String, String> = emptyMap(),
) {

    /**
     * The favourites of the open tab, in **the user's** order.
     *
     * Their order and not the provider's: `position` is what S4-05 lets them
     * change, and sorting by channel name here would make that whole task
     * invisible.
     */
    val visible: List<FavoriteChannel>
        get() = favorites
            .filter { it.groupId == selectedGroupId }
            .sortedBy { it.position }

    /**
     * Nothing starred at all — as opposed to an empty tab in an account that has
     * favourites elsewhere. The two want different words: one names the gesture
     * that starts, the other says this shelf is empty.
     */
    val nothingAtAll: Boolean
        get() = !loading && favorites.isEmpty()

    /**
     * Which subscription a favourite came from, or null when it should not be said.
     *
     * Null on an account with one source, because "from My playlist" under every
     * single row is noise, not information. It earns its line only when a group
     * genuinely mixes two subscriptions — which is the case this whole feature
     * exists for. Null too when the names could not be fetched, which is what
     * being offline looks like here.
     */
    fun sourceLabel(favorite: FavoriteChannel): String? =
        if (sourceNames.size > 1) sourceNames[favorite.channel.sourceId] else null
}
