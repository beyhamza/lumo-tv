package tv.lumo.android.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.CatalogueFace
import tv.lumo.android.core.data.CatalogueSource
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.asCatalogueSource
import tv.lumo.android.core.data.face
import tv.lumo.android.core.data.isPlaylist
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.SeriesTree
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.ResumableSeries
import tv.lumo.android.core.data.notice
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.ContinueWatchingRepository
import tv.lumo.android.core.data.repository.ProgressRepository
import tv.lumo.android.core.data.repository.SeriesRepository
import tv.lumo.android.core.data.repository.onFailureNaming
import tv.lumo.android.core.data.sourceId

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
 *
 * The source is the active one and it is followed, not read once (US-018): a
 * change made from the shell keeps this section open, shows the new source's
 * series and drops the old one's filters — see [SeriesState.browsing].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val series: SeriesRepository,
    private val activeSource: ActiveSourceRepository,
    private val continueWatching: ContinueWatchingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state

    val items: Flow<PagingData<Series>> = _state
        .map { SeriesQuery(it.sourceId, it.filter, it.query, it.continueWatching) }
        .distinctUntilChanged()
        .debounce { query -> if (query.text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .flatMapLatest { query -> seriesFor(query) }
        .cachedIn(viewModelScope)

    /**
     * The grid, from whichever shelf is open.
     *
     * The resume shelf is served as a one-page [PagingData] rather than as a list
     * of its own, which is what keeps the television's grid a single code path —
     * same cards, same focus handling, same return-to-what-you-opened. `S5-11` does
     * exactly this for films, and the shapes underneath genuinely differ: fifty
     * thousand series are read out of SQLite in windows, while a dozen started ones
     * are already in memory.
     */
    private fun seriesFor(query: SeriesQuery): Flow<PagingData<Series>> = when {
        query.sourceId == null -> flowOf(PagingData.empty())
        query.text.isNotBlank() -> series.search(query.sourceId, query.text)
        // Already in the server's order, most recently watched first. Re-sorting it
        // on anything would throw away the one thing this list knows and the
        // catalogue does not.
        query.filter is SeriesFilter.Resume -> flowOf(
            PagingData.from(query.resumable.map { it.series }),
        )
        else -> series.series(query.sourceId, (query.filter as? SeriesFilter.Category)?.id)
    }

    init {
        observeActiveSource()
    }

    /**
     * Follows the active source for as long as the screen lives.
     *
     * `collectLatest`, so that what was started for the previous source is
     * cancelled by the switch instead of landing in the new source's screen.
     */
    private fun observeActiveSource() {
        // Resolved a while ago, so the status may have moved since. One short
        // request per opening, which is what this screen cost before. Still
        // loading means the repository is already asking.
        if (activeSource.state.value !is ActiveSourceState.Loading) {
            viewModelScope.launch { activeSource.refresh() }
        }

        viewModelScope.launch {
            activeSource.state
                .map { it.asCatalogueSource() }
                .distinctUntilChanged()
                .collectLatest { source -> open(source) }
        }

        // Apart from the collector above, and that is the point: the notice
        // changes with every step of a synchronisation, and none of those steps
        // may restart the grid.
        viewModelScope.launch {
            activeSource.state
                .map { it.notice() }
                .distinctUntilChanged()
                .collect { notice -> _state.update { it.copy(notice = notice) } }
        }
    }

    /**
     * A catalogue that exists is shown, in every status (lot C4) — `LiveViewModel`
     * says why. Only a source whose **first** import never succeeded, with nothing
     * cached, has no grid, and then the screen says which: importing, or failed.
     */
    private suspend fun open(source: CatalogueSource) {
        val sourceId = source.sourceId
        val cached = if (sourceId == null) 0 else series.cachedSeriesCount(sourceId)

        _state.update { it.browsing(source, cachedItems = cached) }

        if (sourceId == null || _state.value.step != SeriesStep.Browsing) return

        coroutineScope {
            launch { observeCategories(sourceId) }
            launch { loadContinueWatching(sourceId) }

            // Only a source the server can list: one that never finished an
            // ingestion answers `409`, and asking would put a "refresh failed"
            // banner over a cache that is merely waiting.
            if (cached == 0 && source is CatalogueSource.Ready) {
                refresh()
            }
        }
    }

    /**
     * Asks for the list of sources again.
     *
     * What the screens call every few seconds **while a refresh is on display** —
     * from the composition, so that it stops with the screen: the step of a
     * synchronisation is only worth showing if it moves, and the notice has to go
     * away when the refresh ends.
     */
    fun refreshSource() {
        if (activeSource.state.value is ActiveSourceState.Loading) return
        viewModelScope.launch { activeSource.refresh() }
    }

    private suspend fun observeCategories(sourceId: String) {
        series.categories(sourceId).collect { cached ->
            _state.update { it.copy(categories = cached.value, origin = cached.origin) }
        }
    }

    /**
     * The "continue watching" rail (S6-08).
     *
     * Two calls, and the second is what makes it a rail of **series** rather than a
     * list of episodes: `GET /me/progress` carries identifiers and positions, and
     * going from an episode back to its series needs the tree. `resumable` is where
     * that translation lives, and where the threshold decides between "this episode
     * again" and "the next one".
     *
     * A series whose tree this device does not hold drops out rather than rendering
     * as a gap — see `SeriesRepository.resumable`, which states the trade.
     *
     * **Only the active source's episodes** (US-018). The progress list is the
     * account's; the rest of it is untouched and is there again with its source.
     */
    private suspend fun loadContinueWatching(sourceId: String) {
        // Through the repository the home screen reads too (US-017), so the two
        // cannot come to disagree about which series somebody is watching.
        val cards = continueWatching.series(sourceId)
        _state.update { it.copy(continueWatching = cards) }
    }

    fun refresh() {
        val sourceId = _state.value.sourceId ?: return
        if (_state.value.refreshing) return

        _state.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val result = series.refresh(sourceId)

            // `SOURCE_NOT_FOUND` is a deletion made elsewhere, not a refresh that
            // failed: the repository decides what is browsed next and this screen
            // follows. A dead network proves nothing and stays a banner (US-018).
            val gone = result is LumoResult.Failure &&
                activeSource.onFailureNaming(sourceId, result.error)

            _state.update {
                // The outcome belongs to the catalogue that asked for it, which
                // may no longer be the one on screen.
                if (it.sourceId != sourceId) {
                    it
                } else {
                    it.copy(
                        refreshing = false,
                        refreshFailed = result is LumoResult.Failure && !gone,
                    )
                }
            }
        }
    }

    /** Null is every series of the source, which is what the screen opens on. */
    fun onCategorySelected(categoryId: String?) = _state.update {
        it.copy(filter = categoryId?.let(SeriesFilter::Category) ?: SeriesFilter.All)
    }

    /**
     * Filters the grid to what was started. The television's chip (S6-08).
     *
     * A chip and not a rail, which is `S4-08`'s ruling applied for the third time:
     * a rail above the grid is a **second focus zone**, and this strip has no height
     * for a second mechanism. The phone, which has the room and no D-pad, gets the
     * rail — where a card plays directly.
     *
     * On a television the chip filters, and `OK` on one of those cards opens the
     * series, where the focus lands on the episode to resume. Two presses, and no
     * new zone in the focus map.
     */
    fun onResumeSelected() = _state.update { it.copy(filter = SeriesFilter.Resume) }

    fun onQueryChanged(query: String) = _state.update { it.copy(query = query) }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

private data class SeriesQuery(
    val sourceId: String?,
    val filter: SeriesFilter,
    val text: String,
    val resumable: List<ResumableSeries>,
)

/** Which shelf the grid is showing. The film screen's three, one catalogue over. */
sealed interface SeriesFilter {
    data object All : SeriesFilter
    data class Category(val id: String) : SeriesFilter
    data object Resume : SeriesFilter
}

sealed interface SeriesStep {
    data object Loading : SeriesStep
    data object NoSource : SeriesStep

    /** Several sources and none chosen on this device. The shell is asking (US-018). */
    data object NeedsChoice : SeriesStep

    /** The **first** import is running, and this device holds no series of the source. */
    data object Importing : SeriesStep

    /** The first import failed, and this device holds no series of the source. */
    data object ImportFailed : SeriesStep
    data object Browsing : SeriesStep
}

data class SeriesState(
    val step: SeriesStep = SeriesStep.Loading,
    val sourceId: String? = null,
    val categories: List<Category> = emptyList(),
    val origin: DataOrigin = DataOrigin.Cache,
    val filter: SeriesFilter = SeriesFilter.All,
    val query: String = "",
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /**
     * What the active source is doing, said above the grid or in the first-import
     * message (US-024). Null when there is nothing to say.
     */
    val notice: SourceNotice? = null,
    /**
     * One card per series, already carrying what pressing it does (S6-08).
     *
     * Empty until the two calls behind it answer, and empty for ever on a device
     * that has not opened any of these series — which is honest, and stated on
     * `SeriesRepository.resumable`.
     */
    val continueWatching: List<ResumableSeries> = emptyList(),
    /**
     * Whether the source is a playlist rather than a panel.
     *
     * Only ever used to pick which sentence an empty grid shows. An M3U playlist
     * *cannot* carry series; an Xtream panel that has none simply does not offer
     * them, and telling an Xtream user their panel cannot do something it can
     * would be the worse of the two mistakes.
     */
    val isPlaylist: Boolean = false,
) {

    /**
     * This screen, pointed at [source] (US-018).
     *
     * The same source keeps everything but its status. Another source starts from
     * a blank state: the category, the search and the resume shelf belonged to the
     * catalogue that just left.
     *
     * [isPlaylist] follows the source either way. A playlist declares no season
     * and no episode (adr/0010), and the empty grid says which of the two
     * absences this is — a format that cannot carry series, or a panel that
     * offers none.
     */
    fun browsing(source: CatalogueSource, cachedItems: Int = 0): SeriesState {
        val step = when (source.face(cachedItems)) {
            CatalogueFace.Loading -> SeriesStep.Loading
            CatalogueFace.NoSource -> SeriesStep.NoSource
            CatalogueFace.NeedsChoice -> SeriesStep.NeedsChoice
            CatalogueFace.Importing -> SeriesStep.Importing
            CatalogueFace.ImportFailed -> SeriesStep.ImportFailed
            CatalogueFace.Browsing -> SeriesStep.Browsing
        }
        val playlist = source.isPlaylist

        return if (source.sourceId == sourceId) {
            copy(step = step, isPlaylist = playlist)
        } else {
            // The notice is the active source's, written by its own collector:
            // whichever of the two runs first, it is never the old source's.
            SeriesState(
                step = step,
                sourceId = source.sourceId,
                isPlaylist = playlist,
                notice = notice,
            )
        }
    }

    /** What the category strip should draw as selected. */
    val selectedCategoryId: String?
        get() = (filter as? SeriesFilter.Category)?.id

    val resumeSelected: Boolean
        get() = filter is SeriesFilter.Resume
}

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
    private val progress: ProgressRepository,
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
        loadProgress()
    }

    /**
     * Where the viewer is in each episode of this series (S6-08).
     *
     * Asked once, when the screen opens, and never again: it is a number the
     * screen already has by the time anything can change it, and a screen that
     * re-asked on every frame would be polling the server for its own state.
     *
     * Filtered to this series here rather than by the server, because the request
     * cannot express it — `item_ref` is opaque and a series is not one. The list is
     * a dozen rows.
     */
    private fun loadProgress() {
        viewModelScope.launch {
            val rows = progress.episodesInProgress().associateBy { it.episodeId }
            _state.update { it.copy(progress = rows) }
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
    /**
     * Saved positions, by episode id (S6-08).
     *
     * A map rather than a list, because every episode row asks the same question
     * about itself and a list would be a scan per row down a fifty-episode season.
     */
    val progress: Map<String, EpisodeProgress> = emptyMap(),
) {

    /**
     * The season on screen.
     *
     * **The first one that has episodes, until somebody picks another.** A selector
     * that opened on nothing would be a decision imposed on somebody who has not
     * asked for one — they came to see the episodes.
     *
     * <h2>Why "that has episodes" and not simply "the first"</h2>
     *
     * Panels declare a **season 0** — specials, pilots, a bucket they never filled —
     * and it sorts before season 1. A real series with eight full seasons opened on
     * an empty season 0, which reads exactly like the series having no episodes at
     * all. Found on a real catalogue, not in a test.
     *
     * The empty season is still **listed**: a season the panel declares is a season
     * a viewer should see, and hiding it would be deciding on their behalf. It is
     * just not what the screen opens on.
     *
     * A season number in [openedSeason] is honoured **even when empty** — that one
     * is a choice somebody made, and overriding it would be the screen arguing.
     */
    val openSeason: tv.lumo.android.core.data.model.Season?
        get() {
            val seasons = (tree as? SeriesTree.Loaded)?.seasons.orEmpty()
            return seasons.firstOrNull { it.seasonNumber == openedSeason }
                ?: seasons.firstOrNull { it.episodes.isNotEmpty() }
                ?: seasons.firstOrNull()
        }

    /**
     * The episode the remote should land on (S6-08).
     *
     * **The one being watched, then the first not started, then the first.** That is
     * S6-06's statement, finally answerable now that positions exist — and it is
     * the whole of the ten per cent that task was short.
     *
     * Within the open season only. A viewer who chose season 3 is looking at season
     * 3, and moving the focus to season 1 because that is where they stopped would
     * be the screen arguing with them.
     */
    val resumeEpisodeId: String?
        get() {
            val episodes = openSeason?.episodes.orEmpty()
            val started = episodes.firstOrNull {
                progress[it.id]?.finished == false
            }
            return started?.id ?: episodes.firstOrNull { progress[it.id] == null }?.id
        }
}
