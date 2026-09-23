package tv.lumo.android.feature.vod

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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.ResumableFilm
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.model.WatchProgress
import tv.lumo.android.core.data.notice
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.ContinueWatchingRepository
import tv.lumo.android.core.data.repository.ProgressRepository
import tv.lumo.android.core.data.repository.VodRepository
import tv.lumo.android.core.data.repository.onFailureNaming
import tv.lumo.android.core.data.sourceId

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
 *
 * <h2>A catalogue that exists is shown, in every status (lot C4)</h2>
 *
 * `LiveViewModel` again, for the reason written there: a source that refreshes,
 * or whose last attempt failed, keeps its grid and gets a [SourceNotice] above
 * it. Only a source whose **first** import never succeeded, with nothing cached,
 * has no grid — and then the screen says which: importing, or failed (US-024).
 *
 * <h2>The source is the active one, and it can change under the screen</h2>
 *
 * `LiveViewModel` again: the source is [ActiveSourceRepository]'s answer and it is
 * followed, not read once (US-018). Changing source from the shell keeps this
 * section open and shows the new source's films, with the category, the search
 * and the resume shelf of the old one dropped — see [VodState.browsing].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VodViewModel @Inject constructor(
    private val vod: VodRepository,
    private val activeSource: ActiveSourceRepository,
    private val continueWatching: ContinueWatchingRepository,
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
        .map { FilmQuery(it.sourceId, it.filter, it.query, it.continueWatching) }
        .distinctUntilChanged()
        .debounce { query -> if (query.text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .flatMapLatest { query -> filmsFor(query) }
        .cachedIn(viewModelScope)

    /**
     * The grid's contents, from whichever shelf is open.
     *
     * The resume shelf is served as a one-page [PagingData] rather than through a
     * list of its own, and that is what keeps the television's grid a single code
     * path — same cards, same focus handling, same return-to-the-film-you-opened.
     * The shapes underneath genuinely differ: thirty thousand films are read out
     * of SQLite in windows, while a dozen unfinished ones are already in memory.
     *
     * The same trick `LiveViewModel` plays for favourite groups, for the same
     * reason.
     */
    private fun filmsFor(query: FilmQuery): Flow<PagingData<VodItem>> = when {
        query.sourceId == null -> flowOf(PagingData.empty())
        query.text.isNotBlank() -> vod.search(query.sourceId, query.text)
        // Already in the server's order, most recently watched first. Re-sorting
        // it on anything would throw away the one thing this list knows and the
        // catalogue does not.
        query.filter is VodFilter.Resume -> flowOf(
            PagingData.from(query.resumable.map { it.film }),
        )
        else -> flowOf(Unit).let { _ ->
            vod.films(query.sourceId, (query.filter as? VodFilter.Category)?.id)
        }
    }

    init {
        observeActiveSource()
    }

    /**
     * Follows the active source for as long as the screen lives.
     *
     * `collectLatest`, so that what was started for the previous source — its
     * category observer, its resume rail still being resolved — is cancelled by
     * the switch instead of landing in the new source's screen.
     */
    private fun observeActiveSource() {
        // Resolved a while ago, so the status may have moved since — importing,
        // then ready. One short request per opening, which is what this screen
        // cost before. Still loading means the repository is already asking.
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

    private suspend fun open(source: CatalogueSource) {
        val sourceId = source.sourceId
        val cached = if (sourceId == null) 0 else vod.cachedFilmCount(sourceId)

        _state.update { it.browsing(source, cachedItems = cached) }

        if (sourceId == null || _state.value.step != VodStep.Browsing) return

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
        vod.categories(sourceId).collect { cached ->
            _state.update { it.copy(categories = cached.value, origin = cached.origin) }
        }
    }

    /**
     * The "continue watching" rail (S5-11).
     *
     * Assembled by [ContinueWatchingRepository] since the home screen needs the
     * same cards (US-017): saved positions resolved into films from the cache, in
     * the server's order — most recently updated first — with a film the cache no
     * longer holds dropped rather than drawn as a gap. The reasons are written
     * there and on `resumableFilms`.
     *
     * **Only the active source's films** (US-018). The progress list is the
     * account's; what is shown is the share that belongs to the catalogue on
     * screen, and the rest is untouched — it is there again when its source is.
     */
    private suspend fun loadContinueWatching(sourceId: String) {
        val films = continueWatching.films(sourceId)
        _state.update { it.copy(continueWatching = films) }
    }

    /** The user asking for the film catalogue to be pulled again. */
    fun refresh() {
        val sourceId = _state.value.sourceId ?: return
        if (_state.value.refreshing) return

        _state.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val result = vod.refresh(sourceId)

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

    /** Null is every film of the source, which is what the screen opens on. */
    fun onCategorySelected(categoryId: String?) = _state.update {
        it.copy(filter = categoryId?.let(VodFilter::Category) ?: VodFilter.All)
    }

    /** Filters the grid to what was started and not finished. The television's chip. */
    fun onResumeSelected() = _state.update { it.copy(filter = VodFilter.Resume) }

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
    val filter: VodFilter,
    val text: String,
    val resumable: List<ResumableFilm>,
)

/**
 * Which shelf the grid is showing.
 *
 * One value rather than a nullable category id beside a boolean: those two have a
 * state that means nothing — both set — and the screen would have to decide which
 * wins every time it drew. `CatalogueFilter` on the channel screen is the same
 * type for the same reason.
 */
sealed interface VodFilter {

    /** Every film of the source. What the screen opens on. */
    data object All : VodFilter

    data class Category(val id: String) : VodFilter

    /**
     * Films started and not finished (S5-11).
     *
     * A chip on the television, where the phone gets a rail. The strip has no
     * room for a second mechanism — the ruling `S4-08` made for the channels —
     * and a chip costs no new focus zone.
     */
    data object Resume : VodFilter
}

sealed interface VodStep {

    data object Loading : VodStep

    /** No source registered yet. */
    data object NoSource : VodStep

    /** Several sources and none chosen on this device. The shell is asking (US-018). */
    data object NeedsChoice : VodStep

    /** The **first** import is running, and this device holds no film of the source. */
    data object Importing : VodStep

    /** The first import failed, and this device holds no film of the source. */
    data object ImportFailed : VodStep

    /**
     * The server could not be reached and there is nothing cached to show
     * (US-024, "Indisponibilité et hors ligne"). Not [NoSource]: an outage
     * proves nothing about the account, and the screen says so.
     */
    data object Unreachable : VodStep

    data object Browsing : VodStep
}

data class VodState(
    val step: VodStep = VodStep.Loading,
    val sourceId: String? = null,
    val categories: List<Category> = emptyList(),
    /** Where the grid on screen came from. Said, not inferred — see US-08. */
    val origin: DataOrigin = DataOrigin.Cache,
    /** Which shelf the grid is showing: everything, one category, or the resume list. */
    val filter: VodFilter = VodFilter.All,
    val query: String = "",
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /**
     * What the active source is doing, said above the grid or in the first-import
     * message (US-024). Null when there is nothing to say.
     */
    val notice: SourceNotice? = null,
    /**
     * Films started and not finished, most recently watched first (S5-11).
     *
     * Empty offline, and that is stated rather than hidden: a position is written
     * on one device and read on another, so it lives on the server and nowhere
     * else — see `ProgressRepository`.
     */
    val continueWatching: List<ResumableFilm> = emptyList(),
) {

    /**
     * This screen, pointed at [source] (US-018).
     *
     * The same source keeps everything: a status that moves from importing to
     * ready must not cost somebody the word they were typing. Another source
     * starts from a blank state — the category, the search and the resume shelf
     * all belonged to the catalogue that just left, and a query kept across the
     * switch would open the new source on "no results" for a film it never had.
     */
    fun browsing(source: CatalogueSource, cachedItems: Int = 0): VodState {
        val step = when (source.face(cachedItems)) {
            CatalogueFace.Loading -> VodStep.Loading
            CatalogueFace.NoSource -> VodStep.NoSource
            CatalogueFace.NeedsChoice -> VodStep.NeedsChoice
            CatalogueFace.Importing -> VodStep.Importing
            CatalogueFace.ImportFailed -> VodStep.ImportFailed
            CatalogueFace.Unreachable -> VodStep.Unreachable
            CatalogueFace.Browsing -> VodStep.Browsing
        }

        return if (source.sourceId == sourceId) {
            copy(step = step)
        } else {
            // The notice is the active source's, written by its own collector:
            // whichever of the two runs first, it is never the old source's.
            VodState(step = step, sourceId = source.sourceId, notice = notice)
        }
    }

    /** The open category, for a screen that draws only category chips. */
    val selectedCategoryId: String?
        get() = (filter as? VodFilter.Category)?.id

    /** Whether the resume shelf is the one on screen. */
    val resumeSelected: Boolean
        get() = filter is VodFilter.Resume
}

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
    private val progress: ProgressRepository,
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
        viewModelScope.launch {
            // The source id is part of the progress key and it comes from the
            // cached row, so this waits for the first emission rather than racing
            // it. Passing it down the route instead would make a deep link carry
            // a value the cache already holds.
            val sourceId = vod.film(filmId).filterNotNull().first().sourceId

            // Once, on opening, with the three filters the contract says yield at
            // most one row. A finished film is offered no resume — that would be
            // "carry on from the credits" — so the screen shows one button.
            val row = progress.of(sourceId, filmId)
            _state.update { it.copy(resumeFrom = row?.takeUnless { it.finished }) }
        }
    }
}

data class VodDetailState(
    val film: VodItem? = null,
    /**
     * Where to offer to resume, or null.
     *
     * Null both when nothing was ever watched and when the film is finished:
     * both mean the screen offers one button rather than two. **Resuming is
     * offered, never imposed** — a player that started twenty minutes in on its
     * own is a good idea right up until somebody wants to see the beginning.
     */
    val resumeFrom: WatchProgress? = null,
    /**
     * Whether the synopsis is still being fetched.
     *
     * Only ever used to decide between "no synopsis yet" and "this film has
     * none": both are an absence of text, and only one of them is worth a
     * placeholder.
     */
    val loadingSynopsis: Boolean = false,
)
