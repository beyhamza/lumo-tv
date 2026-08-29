package tv.lumo.android.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.repository.VodRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * Browsing the films of a source (US-13).
 *
 * <h2>The mechanics are `LiveViewModel`'s, and that is the point</h2>
 *
 * Cache-first reads, a refresh the user asks for, an origin that is said rather
 * than guessed, and a filter that is one value instead of two nullable ones. A
 * film catalogue that behaved differently from a channel catalogue would be a
 * second set of rules to get right for no gain to anybody, so the divergences are
 * only the ones a film forces.
 *
 * <h2>What a film does force</h2>
 *
 * **There is a search box, and it searches the cache.** A grid of posters cannot
 * be scanned the way a list of channel names can, so a film catalogue needs one
 * where the channel list could do without. It is `VodRepository.search`, which is
 * a `LIKE` over what has been synchronised — the same floor the channels have.
 *
 * **It is a floor and not the ceiling, and that is worth saying plainly.** The
 * server owns the good search: a trigram index that tolerates a typo, which is
 * how somebody actually looks for a film they half remember. Reaching it means a
 * `PagingSource` over the API rather than over SQLite, and that belongs to
 * `feature:search`, which is still a placeholder. Until then a misspelt title
 * finds nothing here.
 *
 * **The synopsis is not in the listing.** [VodDetailViewModel] is what fetches
 * one, for the film somebody actually opened — on an Xtream panel it is a request
 * per film against the user's own server, so a grid must never trigger it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VodViewModel @Inject constructor(
    private val vod: VodRepository,
    private val sources: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VodState())
    val state: StateFlow<VodState> = _state

    /**
     * The grid's contents, paginated straight out of SQLite.
     *
     * `debounce` on the query and nothing else: a category chip is one tap and
     * should answer at once, while a search box emits on every letter and would
     * otherwise rebuild the pager six times for one word.
     */
    val films: Flow<PagingData<VodItem>> = _state
        .map { FilmQuery(it.sourceId, it.selectedCategoryId, it.query) }
        .distinctUntilChanged()
        .debounce { query -> if (query.text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .flatMapLatest { query -> filmsFor(query) }
        .cachedIn(viewModelScope)

    private fun filmsFor(query: FilmQuery): Flow<PagingData<VodItem>> = when {
        query.sourceId == null -> flowOf(PagingData.empty())
        query.text.isNotBlank() -> vod.search(query.sourceId, query.text)
        else -> vod.films(query.sourceId, query.categoryId)
    }

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val source = sources.sources().valueOrNull()?.firstOrNull()

            if (source == null) {
                _state.update { it.copy(step = VodStep.NoSource) }
                return@launch
            }

            val sourceId = source.id.toString()

            if (source.status != SourceStatus.READY) {
                _state.update { it.copy(sourceId = sourceId, step = VodStep.NotReadyYet) }
                return@launch
            }

            _state.update { it.copy(sourceId = sourceId, step = VodStep.Browsing) }
            observeCategories(sourceId)

            if (vod.cachedFilmCount(sourceId) == 0) {
                refresh()
            }
        }
    }

    private fun observeCategories(sourceId: String) {
        viewModelScope.launch {
            vod.categories(sourceId).collect { cached ->
                _state.update { it.copy(categories = cached.value, origin = cached.origin) }
            }
        }
    }

    /** The user asking for the film catalogue to be pulled again. */
    fun refresh() {
        val sourceId = _state.value.sourceId ?: return
        if (_state.value.refreshing) return

        _state.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val result = vod.refresh(sourceId)
            _state.update {
                it.copy(refreshing = false, refreshFailed = result is LumoResult.Failure)
            }
        }
    }

    /** Null is every film of the source, which is what the screen opens on. */
    fun onCategorySelected(categoryId: String?) =
        _state.update { it.copy(selectedCategoryId = categoryId) }

    fun onQueryChanged(query: String) = _state.update { it.copy(query = query) }

    private companion object {
        /**
         * Long enough that typing a title is one request, short enough that
         * stopping feels like an answer rather than a pause.
         */
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

/** What the grid depends on, gathered so the pager restarts on a real change. */
private data class FilmQuery(
    val sourceId: String?,
    val categoryId: String?,
    val text: String,
)

sealed interface VodStep {

    data object Loading : VodStep

    /** No source registered yet. */
    data object NoSource : VodStep

    /** A source exists but has not finished importing, or failed to. */
    data object NotReadyYet : VodStep

    data object Browsing : VodStep
}

data class VodState(
    val step: VodStep = VodStep.Loading,
    val sourceId: String? = null,
    val categories: List<Category> = emptyList(),
    /** Where the grid on screen came from. Said, not inferred — see US-08. */
    val origin: DataOrigin = DataOrigin.Cache,
    val selectedCategoryId: String? = null,
    val query: String = "",
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)

/**
 * One film's own screen (US-13).
 *
 * Its own view model rather than a field on [VodState], because it outlives the
 * grid: a film reached from a resume rail, or from a deep link, has no grid
 * behind it at all. Reading from [VodRepository.film] means the poster and the
 * title are on screen from the cache before any request is made, and the synopsis
 * arrives underneath them.
 */
@HiltViewModel
class VodDetailViewModel @Inject constructor(
    private val vod: VodRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VodDetailState())
    val state: StateFlow<VodDetailState> = _state

    private var loaded: String? = null

    /** Called once, with the film the screen was opened for. */
    fun start(filmId: String) {
        if (loaded == filmId) return
        loaded = filmId

        viewModelScope.launch {
            vod.film(filmId).collect { film -> _state.update { it.copy(film = film) } }
        }
        viewModelScope.launch {
            // Fetching, not blocking: whatever the cache holds is already on
            // screen, and this fills in the synopsis when it arrives. A failure
            // is a film without a synopsis, which is an ordinary film — many
            // sources carry none — so it is not said out loud.
            _state.update { it.copy(loadingSynopsis = true) }
            vod.refreshFilm(filmId)
            _state.update { it.copy(loadingSynopsis = false) }
        }
    }
}

data class VodDetailState(
    val film: VodItem? = null,
    /**
     * Whether the synopsis is still being fetched.
     *
     * Only ever used to decide between "no synopsis yet" and "this film has
     * none": both are an absence of text, and only one of them is worth a
     * placeholder.
     */
    val loadingSynopsis: Boolean = false,
)
