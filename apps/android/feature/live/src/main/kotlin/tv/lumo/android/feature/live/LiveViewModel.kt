package tv.lumo.android.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.Cached
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.repository.CatalogueRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * Browsing the channels of a source (US-08).
 *
 * <h2>Everything on this screen comes out of the cache</h2>
 *
 * Categories and channels are read from Room, always, and the network only ever
 * *fills* it. That is what makes the offline scenario ordinary rather than
 * special: the same code path renders the same list, and the only difference is
 * what [Cached.origin] says about it.
 *
 * <h2>Refreshing is not free, so it is not automatic</h2>
 *
 * A refresh walks the source's whole catalogue — fifteen thousand channels is an
 * ordinary source — and doing that every time this screen opens would be a
 * minute of somebody's data for a list that has not changed. So it happens on the
 * first open of an empty cache, and afterwards only when the user asks.
 *
 * The one exception is a source that is still importing: there is nothing to
 * cache yet, and the screen says so rather than showing an empty list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val catalogue: CatalogueRepository,
    private val sources: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    /**
     * The channels of the chosen category, paginated straight out of SQLite.
     *
     * `cachedIn` keeps the loaded windows across a rotation; without it, turning
     * the phone re-reads the first page and jumps the user back to the top.
     */
    val channels: Flow<PagingData<Channel>> = _state
        .map { it.sourceId to it.selectedCategoryId }
        .flatMapLatest { (sourceId, categoryId) ->
            if (sourceId == null) {
                flowOf(PagingData.empty())
            } else {
                catalogue.channels(sourceId, categoryId)
            }
        }
        .cachedIn(viewModelScope)

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val source = sources.sources().valueOrNull()?.firstOrNull()

            if (source == null) {
                _state.update { it.copy(step = LiveStep.NoSource) }
                return@launch
            }

            val sourceId = source.id.toString()

            if (source.status != SourceStatus.READY) {
                // Nothing has been ingested yet. An empty channel list here would
                // read as "this source has no channels", which is a different and
                // much more alarming thing than "it is still importing".
                _state.update { it.copy(sourceId = sourceId, step = LiveStep.NotReadyYet) }
                return@launch
            }

            _state.update { it.copy(sourceId = sourceId, step = LiveStep.Browsing) }
            observeCategories(sourceId)

            if (catalogue.cachedChannelCount(sourceId) == 0) {
                refresh()
            }
        }
    }

    private fun observeCategories(sourceId: String) {
        viewModelScope.launch {
            catalogue.categories(sourceId).collect { cached ->
                _state.update {
                    it.copy(categories = cached.value, origin = cached.origin)
                }
            }
        }
    }

    /** The user asking for the catalogue to be pulled again. */
    fun refresh() {
        val sourceId = _state.value.sourceId ?: return
        if (_state.value.refreshing) return

        _state.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val result = catalogue.refresh(sourceId)
            _state.update {
                it.copy(
                    refreshing = false,
                    // Only a failure is worth saying out loud. A success is
                    // visible in the list itself and in the origin indicator.
                    refreshFailed = result is LumoResult.Failure,
                )
            }
        }
    }

    /** Null is every channel of the source, which is what the screen opens on. */
    fun onCategorySelected(categoryId: String?) =
        _state.update { it.copy(selectedCategoryId = categoryId) }
}

sealed interface LiveStep {

    data object Loading : LiveStep

    /** No source registered yet. The source tab is where that starts. */
    data object NoSource : LiveStep

    /** A source exists but has not finished importing, or failed to. */
    data object NotReadyYet : LiveStep

    data object Browsing : LiveStep
}

data class LiveState(
    val step: LiveStep = LiveStep.Loading,
    val sourceId: String? = null,
    val categories: List<Category> = emptyList(),
    /**
     * Where the list on screen came from.
     *
     * US-08 asks for the offline case to be *said*, discreetly. It cannot be
     * inferred from the data — Room has rows either way — so the repository, which
     * is the only thing that knows whether the last refresh succeeded, says it.
     */
    val origin: DataOrigin = DataOrigin.Cache,
    val selectedCategoryId: String? = null,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)
