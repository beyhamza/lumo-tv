package tv.lumo.android.feature.series

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
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.SeriesTree
import tv.lumo.android.core.data.repository.SeriesRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * Browsing the series of a source (US-15).
 *
 * `VodViewModel` again, and deliberately not a line more: same cache-first reads,
 * same debounced local search, same origin said rather than guessed. A series
 * catalogue that behaved differently from a film catalogue would be a second set
 * of rules to get right for no gain to anybody.
 *
 * The tree is not here. It belongs to one series, it costs a call to the user's own
 * panel, and a grid that loaded one per card would be a request per poster — see
 * [SeriesDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val series: SeriesRepository,
    private val sources: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state

    val items: Flow<PagingData<Series>> = _state
        .map { SeriesQuery(it.sourceId, it.selectedCategoryId, it.query) }
        .distinctUntilChanged()
        .debounce { query -> if (query.text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .flatMapLatest { query -> seriesFor(query) }
        .cachedIn(viewModelScope)

    private fun seriesFor(query: SeriesQuery): Flow<PagingData<Series>> = when {
        query.sourceId == null -> flowOf(PagingData.empty())
        query.text.isNotBlank() -> series.search(query.sourceId, query.text)
        else -> series.series(query.sourceId, query.categoryId)
    }

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val source = sources.sources().valueOrNull()?.firstOrNull()

            if (source == null) {
                _state.update { it.copy(step = SeriesStep.NoSource) }
                return@launch
            }

            val sourceId = source.id.toString()

            if (source.status != SourceStatus.READY) {
                _state.update { it.copy(sourceId = sourceId, step = SeriesStep.NotReadyYet) }
                return@launch
            }

            _state.update {
                it.copy(
                    sourceId = sourceId,
                    step = SeriesStep.Browsing,
                    // A playlist declares no season and no episode (adr/0010). The
                    // empty grid says which of the two absences this is, and the
                    // two are different facts: a format that cannot carry series,
                    // and a panel that offers none.
                    isPlaylist = source.kind != tv.lumo.android.network.generated.model.SourceKind.XTREAM,
                )
            }
            observeCategories(sourceId)

            if (series.cachedSeriesCount(sourceId) == 0) {
                refresh()
            }
        }
    }

    private fun observeCategories(sourceId: String) {
        viewModelScope.launch {
            series.categories(sourceId).collect { cached ->
                _state.update { it.copy(categories = cached.value, origin = cached.origin) }
            }
        }
    }

    fun refresh() {
        val sourceId = _state.value.sourceId ?: return
        if (_state.value.refreshing) return

        _state.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val result = series.refresh(sourceId)
            _state.update {
                it.copy(refreshing = false, refreshFailed = result is LumoResult.Failure)
            }
        }
    }

    fun onCategorySelected(categoryId: String?) =
        _state.update { it.copy(selectedCategoryId = categoryId) }

    fun onQueryChanged(query: String) = _state.update { it.copy(query = query) }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

private data class SeriesQuery(val sourceId: String?, val categoryId: String?, val text: String)

sealed interface SeriesStep {
    data object Loading : SeriesStep
    data object NoSource : SeriesStep
    data object NotReadyYet : SeriesStep
    data object Browsing : SeriesStep
}

data class SeriesState(
    val step: SeriesStep = SeriesStep.Loading,
    val sourceId: String? = null,
    val categories: List<Category> = emptyList(),
    val origin: DataOrigin = DataOrigin.Cache,
    val selectedCategoryId: String? = null,
    val query: String = "",
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /**
     * Whether the source is a playlist rather than a panel.
     *
     * Only ever used to pick which sentence an empty grid shows. An M3U playlist
     * *cannot* carry series; an Xtream panel that has none simply does not offer
     * them, and telling an Xtream user their panel cannot do something it can
     * would be the worse of the two mistakes.
     */
    val isPlaylist: Boolean = false,
)

/**
 * One series, its tree, and the three answers a screen has to tell apart (US-15).
 *
 * Its own view model rather than a field on [SeriesState], for the reason
 * `VodDetailViewModel` has one: it outlives the grid. A series reached from a
 * resume rail or a deep link has no grid behind it.
 *
 * <h2>What it adds over the film's, and it is the whole of S6-04's new surface</h2>
 *
 * A film's detail screen asks one question — is the synopsis here — and a nullable
 * string answers it. This one renders a [SeriesTree], which is four states, and
 * collapsing any two of them breaks the screen: a panel that lists no season, a
 * tree on its way, and a provider that did not answer look alike from a distance
 * and mean completely different things.
 *
 * The repository decides which; this only asks once and holds the season somebody
 * opened.
 */
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val series: SeriesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesDetailState())
    val state: StateFlow<SeriesDetailState> = _state

    private var loaded: String? = null

    /** Called once, with the series the screen was opened for. */
    fun start(seriesId: String) {
        if (loaded == seriesId) return
        loaded = seriesId

        viewModelScope.launch {
            series.one(seriesId).collect { row -> _state.update { it.copy(series = row) } }
        }
        viewModelScope.launch {
            series.tree(seriesId).collect { tree -> _state.update { it.copy(tree = tree) } }
        }
        viewModelScope.launch {
            // Once, on opening. The repository serves what is cached first and
            // decides on its own whether this call is worth making.
            series.loadTree(seriesId)
        }
    }

    /** Retrying after a provider that did not answer. The only failure worth a button. */
    fun retry() {
        val seriesId = loaded ?: return
        viewModelScope.launch { series.loadTree(seriesId) }
    }

    fun onSeasonSelected(seasonNumber: Int) =
        _state.update { it.copy(openedSeason = seasonNumber) }
}

data class SeriesDetailState(
    val series: Series? = null,
    val tree: SeriesTree = SeriesTree.Idle,
    /**
     * The season somebody chose, or null for "whichever is first".
     *
     * Null rather than a number defaulted at construction, because the tree is not
     * loaded yet when this screen first draws and there is no first season to name.
     * See [openSeason], which resolves the two.
     */
    val openedSeason: Int? = null,
) {

    /**
     * The season on screen.
     *
     * **The first one, until somebody picks another.** A selector that opened on
     * nothing would be a decision imposed on somebody who has not asked for one —
     * they came to see the episodes, and the first season is the answer that needs
     * no input.
     *
     * A season number in [openedSeason] that the tree no longer lists falls back to
     * the first rather than to an empty episode list that reads as a broken series.
     */
    val openSeason: tv.lumo.android.core.data.model.Season?
        get() {
            val seasons = (tree as? SeriesTree.Loaded)?.seasons.orEmpty()
            return seasons.firstOrNull { it.seasonNumber == openedSeason } ?: seasons.firstOrNull()
        }
}
