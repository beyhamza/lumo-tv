package tv.lumo.android.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.CataloguePresence
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.data.model.SearchPage
import tv.lumo.android.core.data.model.SearchResults
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.SearchRepository
import tv.lumo.android.core.data.selectedSourceId

/**
 * One section of a search, and what is currently known about it.
 *
 * <h2>Four states, because a screen has to tell four situations apart</h2>
 *
 * - [Idle] — this type is not part of the current filter, or nothing has been
 *   asked yet. It is not "no results": that is [Loaded] with an empty list.
 * - [Loading] — asked for, nothing to show yet. Drawing it as empty tells somebody
 *   their source has nothing, which is a lie that lasts as long as the request.
 * - [Loaded] — results, and the total they were cut from. [Loaded.hasMore] is the
 *   only thing that decides whether "Afficher plus" (or "Voir tous" on the grouped
 *   view) is offered.
 * - [Failed] — asked for, and the request came back with an error. **Never an
 *   empty [Loaded].** Q9 is explicit that an error is not an absence, and a screen
 *   that merged them would offer "no results" to somebody whose server is down.
 *
 * [Loaded.pageError] is the fifth case inside the third: a failure of the *next*
 * page while the pages already fetched stay on screen. The retry then concerns that
 * page alone, which is Q9's rule for "Afficher plus".
 */
sealed interface SearchSection<out T> {

    data object Idle : SearchSection<Nothing>

    data object Loading : SearchSection<Nothing>

    data class Loaded<T>(
        val items: List<T>,
        /** Total hits of this type, from the contract's `total_elements`. */
        val totalElements: Long,
        /** The zero-based page these items are, and the size it was asked at. */
        val page: Int,
        val size: Int,
        /** A next page is being fetched; the pages already here stay. */
        val loadingMore: Boolean = false,
        /** The last next-page request failed. The pages already here stay. */
        val pageError: LumoError? = null,
    ) : SearchSection<T> {

        /** Whether there is a page after this one to offer. */
        val hasMore: Boolean get() = items.size.toLong() < totalElements
    }

    data class Failed(val error: LumoError) : SearchSection<Nothing>
}

/**
 * Everything the search screen draws (US-021, S10-01).
 *
 * The text and the filter are what the person set; the three sections are what the
 * last request answered. Nothing here is a history: a new session starts from
 * [SearchState] empty, and Q9 forbids persisting a search.
 */
data class SearchState(
    /** The raw text in the field. Not trimmed: that is what the person is typing. */
    val query: String = "",
    val filter: SearchFilter = SearchFilter.All,
    /**
     * The text is longer than the contract's `q` allows. Refused rather than
     * truncated, on the way out *and* on the way in (Q9, SR-05). A screen renders
     * a message next to the field; it never sends a shortened query.
     */
    val tooLong: Boolean = false,
    val channels: SearchSection<Channel> = SearchSection.Idle,
    val films: SearchSection<VodItem> = SearchSection.Idle,
    val series: SearchSection<Series> = SearchSection.Idle,
    /**
     * Which catalogues the current source carries, so a screen offers only the
     * filters that mean something here. Fail-open while the probe is unknown.
     */
    val presence: CataloguePresence = CataloguePresence.all,
) {

    /**
     * The empty field's invitation, and only that.
     *
     * Distinct from "no results for a query": this asks somebody to type, the
     * other tells them nothing matched. Q9 requires the empty field to load
     * nothing at all, so this is the state that must never become a full
     * catalogue.
     */
    val invitation: Boolean get() = query.isBlank() && !tooLong

    /**
     * The filters a screen offers: [SearchFilter.All] first, then only the types
     * the source carries (SR-11). A filter that is not listed is one the source
     * cannot answer, and offering it would open onto nothing.
     */
    val filters: List<SearchFilter>
        get() = buildList {
            add(SearchFilter.All)
            if (presence.channels) add(SearchFilter.Channels)
            if (presence.films) add(SearchFilter.Films)
            if (presence.series) add(SearchFilter.Series)
        }

    /** The section for one type, or null for [SearchFilter.All], which has three. */
    fun section(filter: SearchFilter): SearchSection<*>? = when (filter) {
        SearchFilter.All -> null
        SearchFilter.Channels -> channels
        SearchFilter.Films -> films
        SearchFilter.Series -> series
    }
}

/**
 * The search screen's engine (US-021, S10-01). No Compose: S10-02 draws it.
 *
 * <h2>Every answer is valid only for the context that asked for it</h2>
 *
 * A search is not one request but a stream of them, and the network reorders them
 * freely: a slow request for "fal" can land after a fast one for "falaise". Q9
 * states the rule — a response applies only to (account, source, text, type, page,
 * size) — and this class enforces it twice over:
 *
 * 1. every new request cancels the previous one, so the common case never gets an
 *    old answer at all;
 * 2. every answer is compared against [context] before it is applied, because
 *    cancellation is cooperative and a response already on its way can outlive the
 *    job. The second guard is the one SR-08 pins.
 *
 * <h2>The clock, and why the delay is here rather than in the screen</h2>
 *
 * [DEBOUNCE_MILLIS] after the last edit, and not a request per character (Q9,
 * SR-01). A Compose `TextField` cannot hold it: [viewModelScope] survives
 * rotation, and a timer living in a composable would restart on every
 * recomposition and fire a request the user did not finish typing. Settling the
 * rule here also makes it a thing a virtual clock can test, which is the
 * acceptance S10-01 asks for.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * It keeps no history (Q9), ranks nothing — the order is the server's — and it never
 * falls back to the local Room search. That fallback is S10-04's, and mixing it in
 * here would make the tested path differ from the shipped one.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val activeSource: ActiveSourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** The account part of the context identity. Read from the session, not guessed. */
    private var accountId: String? = null

    /** The source part of the context identity. */
    private var sourceId: String? = null

    /** The request the current screen describes. An answer with another is dropped. */
    private var context: SearchContext? = null

    private var searchJob: Job? = null
    private var debounceJob: Job? = null

    /**
     * The catalogues the current source carries, or null while the probe is
     * unknown. Null is what makes a search wait rather than ask a type the source
     * may not have; the screen shows the fail-open value meanwhile.
     */
    private var presence: CataloguePresence? = null
    private var presenceJob: Job? = null

    /**
     * A search is waiting for the presence probe to answer.
     *
     * Set when the text or the filter asks for a search while the probe is still
     * in flight, and cleared by every input change. It is what makes the probe's
     * answer run that search — and only that one, so a completion during
     * composition or before the debounce fires does not search early (S10-02).
     */
    private var searchWanted = false

    init {
        viewModelScope.launch {
            combine(activeSource.accountId, activeSource.state) { account, active ->
                account to active.selectedSourceId
            }
                .distinctUntilChanged()
                .collect { (account, source) -> onIdentityChanged(account, source) }
        }
    }

    // ---- input --------------------------------------------------------------

    /**
     * A change in the field.
     *
     * @param composing whether the platform's input method is still composing the
     *   text. While it is, no request is scheduled at all: composing a word fires
     *   this on every keystroke of the composition, and Q9 asks for the delay to
     *   start once the composition is *validated*. S10-02 passes
     *   `TextFieldValue.composition != null`.
     */
    fun onQueryChanged(text: String, composing: Boolean) {
        val tooLong = text.codePointCount(0, text.length) > MAX_QUERY_LENGTH
        _state.update { it.copy(query = text, tooLong = tooLong) }

        debounceJob?.cancel()

        // Refused, not truncated: the limit is the contract's, and sending a
        // shortened query would answer a question nobody asked while showing the
        // text of another (Q9, SR-05).
        if (tooLong) {
            dropPending()
            return
        }

        // Empty after trimming: cancel, clear and invite. Never load everything.
        if (text.trim().isEmpty()) {
            dropPending()
            return
        }

        // The text moved on, so nothing on screen can claim to match it any more
        // (Q9: never show an earlier search's results under a new text).
        dropPending()
        if (composing) return
        schedule()
    }

    /** Enter, or the "Search" action: run the current text now, and cancel the wait. */
    fun onSearchSubmitted() {
        debounceJob?.cancel()
        if (!canSearch()) return
        searchNow()
    }

    /**
     * A filter tab, and "Voir tous" for a preview section.
     *
     * Both mean the same thing at this layer: show one type, from its first page,
     * at the page size. "Voir tous" is deliberately **not** an index derived from
     * the preview of four — Q9 forbids that arithmetic and asks for a distinct
     * request at page 0, size 20.
     */
    fun onFilterSelected(filter: SearchFilter) {
        // A filter the source does not carry is not offered; asking for it anyway
        // leaves the screen on the one it can answer rather than on an empty tab.
        if (!_state.value.presence.carries(filter)) return
        debounceJob?.cancel()
        _state.update { it.copy(filter = filter) }
        if (!canSearch()) {
            dropPending()
            return
        }
        searchNow()
    }

    /** The "Afficher plus" of one type: the page after the last one, appended. */
    fun onLoadMore(filter: SearchFilter) {
        val loaded = _state.value.section(filter) as? SearchSection.Loaded ?: return
        if (!loaded.hasMore || loaded.loadingMore) return
        val present = presence ?: return
        request(present, requested = filter, page = loaded.page + 1, size = loaded.size, append = true)
    }

    /**
     * Retry for one section: the failed request, and only it.
     *
     * A section that never loaded restarts at its first page; a section whose
     * *next* page failed asks for that page again and keeps what is already there.
     * Q9 is explicit that a retry reaches one section or one page, never the whole
     * screen (SR-07, SR-10, SR-14).
     */
    fun onRetry(filter: SearchFilter) {
        if (filter == SearchFilter.All) {
            if (canSearch()) searchNow()
            return
        }

        val present = presence ?: return
        when (val section = _state.value.section(filter)) {
            is SearchSection.Failed ->
                request(present, requested = filter, page = 0, size = sizeFor(_state.value.filter), append = false)

            is SearchSection.Loaded ->
                if (section.pageError != null) {
                    request(present, requested = filter, page = section.page + 1, size = section.size, append = true)
                }

            else -> Unit
        }
    }

    // ---- identity -----------------------------------------------------------

    /**
     * The account or the source changed under the screen.
     *
     * Both are resolved by the same code path because both empty the context. Q9:
     * a source change keeps the text but returns to All and page 0, drops the old
     * source's results at once and searches again when the text is valid; a change
     * of account empties the context and must let nothing of the old one through.
     * Comparing the pair covers both without having to know which moved.
     */
    private fun onIdentityChanged(account: String?, source: String?) {
        if (account == accountId && source == sourceId) return
        accountId = account
        sourceId = source

        debounceJob?.cancel()
        dropPending()

        // A new identity's presence is unknown. Reset to the fail-open value so a
        // probe that never answers never hides a filter, and drop the previous
        // identity's presence so nothing of it can be applied (SR-08).
        presenceJob?.cancel()
        presenceJob = null
        presence = null
        _state.update { it.copy(filter = SearchFilter.All, presence = CataloguePresence.all) }

        if (source == null) return
        // A change of identity keeps the text and searches it again, but only once
        // the probe has answered (see [searchNow]); the answer runs that search.
        searchWanted = canSearch()
        loadPresence(account, source)
    }

    /**
     * Reads which catalogues the source carries, then searches if the text is ready.
     *
     * The probe is what lets the screen offer only real filters, and the first
     * request of a source waits for it so a type the source does not carry is never
     * asked for (S10-02). The answer is compared against the identity that asked for
     * it before it is applied, exactly as a search answer is: a probe that outlives
     * a change of account must not put the old account's filters on the new screen
     * (SR-08).
     */
    private fun loadPresence(account: String?, source: String) {
        presenceJob = viewModelScope.launch {
            val loaded = search.cataloguePresence(source)
            if (accountId != account || sourceId != source) return@launch
            presence = loaded
            _state.update { it.copy(presence = loaded) }
            if (searchWanted && canSearch()) searchNow()
        }
    }

    // ---- requesting ---------------------------------------------------------

    private fun schedule() {
        if (!canSearch()) return
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            searchNow()
        }
    }

    /** Searches the current filter from its first page, at the size that filter uses. */
    private fun searchNow() {
        val present = presence
        if (present == null) {
            // The probe decides which types exist. Remember that a search is
            // wanted, and [loadPresence] runs it once the answer is in.
            searchWanted = true
            return
        }
        searchWanted = false
        val filter = _state.value.filter
        request(present, requested = filter, page = 0, size = sizeFor(filter), append = false)
    }

    /**
     * Issues one request, and makes it the only one that may touch the state.
     *
     * The order matters: the context is published and the previous job cancelled
     * *before* the new state is marked loading, so an answer to the old request
     * cannot land between the two and be mistaken for the new one's.
     */
    private fun request(
        present: CataloguePresence,
        requested: SearchFilter,
        page: Int,
        size: Int,
        append: Boolean,
    ) {
        val source = sourceId ?: return

        // A type the source does not carry is never asked for, and never marked
        // loading: there is no request to wait for (S10-02). Its section stays
        // Idle and the screen offers no filter for it.
        if (!present.carries(requested)) return

        val query = _state.value.query.trim()
        if (query.isEmpty() || _state.value.tooLong) return

        val request = SearchContext(accountId, source, query, requested, page, size)
        context = request
        searchJob?.cancel()

        _state.update { if (append) it.markingMore(requested) else it.markingLoading(requested, present) }

        searchJob = viewModelScope.launch {
            val results = search.search(source, query, requested, page, size, present)
            // The explicit guard, and not a duplicate of the cancellation: a
            // repository that answered before it observed the cancellation
            // would otherwise have its answer applied to a text it no longer
            // belongs to (SR-08).
            if (context != request) return@launch
            _state.update { it.merge(page, size, results, append) }
        }
    }

    /** Cancels what is in flight, clears the sections and forgets the context. */
    private fun dropPending() {
        context = null
        searchJob?.cancel()
        // The text or the filter moved on: a search that was waiting for the
        // presence probe is no longer the one wanted.
        searchWanted = false
        _state.update {
            it.copy(
                channels = SearchSection.Idle,
                films = SearchSection.Idle,
                series = SearchSection.Idle,
            )
        }
    }

    private fun canSearch(): Boolean {
        val current = _state.value
        return sourceId != null && current.query.trim().isNotEmpty() && !current.tooLong
    }

    /** Four for the grouped view, twenty for a list. Never computed from the other. */
    private fun sizeFor(filter: SearchFilter): Int =
        if (filter == SearchFilter.All) PREVIEW_SIZE else PAGE_SIZE

    /**
     * The identity of one request: Q9's (account, source, text, type, page, size).
     *
     * A data class so the comparison that drops a stale answer is `!=` on the whole
     * thing. There is no way to forget a field: adding one here makes every answer
     * from a context without it fail to compare equal, which is the safe direction
     * to be wrong in.
     */
    private data class SearchContext(
        val accountId: String?,
        val sourceId: String,
        val query: String,
        val requested: SearchFilter,
        val page: Int,
        val size: Int,
    )

    companion object {
        /** Q9's delay, and Q9's sizes. Named here because they are the rule. */
        const val DEBOUNCE_MILLIS = 350L
        const val PREVIEW_SIZE = 4
        const val PAGE_SIZE = 20

        /**
         * The contract's `maxLength` for `q`, counted in Unicode code points.
         *
         * Code points and not `length`: `String.length` counts UTF-16 units, so a
         * name carrying an emoji or a non-BMP character would count twice and be
         * refused earlier than the server refuses it. The server's validator counts
         * characters, and the two must agree or the client refuses a request the
         * server would have accepted.
         */
        const val MAX_QUERY_LENGTH = 100
    }
}

// ---- state transitions ------------------------------------------------------

/**
 * Marks one request's sections as loading.
 *
 * Only the requested types are touched, and within the grouped view only the types
 * the source carries: an absent catalogue has no request to wait for, so marking it
 * loading would show a spinner for a filter the screen does not even offer
 * (S10-02). A type this request does not carry keeps its current value — a
 * single-type search must not clear the others, and an absent type stays Idle.
 */
private fun SearchState.markingLoading(
    filter: SearchFilter,
    present: CataloguePresence,
): SearchState = when (filter) {
    SearchFilter.All -> copy(
        channels = if (present.channels) SearchSection.Loading else channels,
        films = if (present.films) SearchSection.Loading else films,
        series = if (present.series) SearchSection.Loading else series,
    )
    SearchFilter.Channels -> copy(channels = SearchSection.Loading)
    SearchFilter.Films -> copy(films = SearchSection.Loading)
    SearchFilter.Series -> copy(series = SearchSection.Loading)
}

/** The same for a next page: the section already on screen stays, marked. */
private fun SearchState.markingMore(filter: SearchFilter): SearchState = when (filter) {
    SearchFilter.All -> this
    SearchFilter.Channels -> copy(channels = channels.markingMore())
    SearchFilter.Films -> copy(films = films.markingMore())
    SearchFilter.Series -> copy(series = series.markingMore())
}

private fun <T> SearchSection<T>.markingMore(): SearchSection<T> =
    if (this is SearchSection.Loaded) copy(loadingMore = true, pageError = null) else this

/**
 * Applies one request's answer, section by section.
 *
 * A null field is a type this request did not ask for, so it is left alone — which
 * is what keeps a next-page update from wiping the other two sections of the
 * grouped view.
 */
private fun SearchState.merge(
    page: Int,
    size: Int,
    results: SearchResults,
    append: Boolean,
): SearchState {
    var next = this
    results.channels?.let { next = next.copy(channels = it.toSection(next.channels, page, size, append)) }
    results.films?.let { next = next.copy(films = it.toSection(next.films, page, size, append)) }
    results.series?.let { next = next.copy(series = it.toSection(next.series, page, size, append)) }
    return next
}

/**
 * One section's next value.
 *
 * A failure on a next page keeps what is already there and records the error, which
 * is the difference between "the next page failed" and "there is nothing" — SR-07.
 * A failure on a first page is [SearchSection.Failed]: nothing to keep.
 */
private fun <T> LumoResult<SearchPage<T>>.toSection(
    previous: SearchSection<T>,
    page: Int,
    size: Int,
    append: Boolean,
): SearchSection<T> = when (this) {
    is LumoResult.Failure ->
        if (append && previous is SearchSection.Loaded) {
            previous.copy(loadingMore = false, pageError = error)
        } else {
            SearchSection.Failed(error)
        }

    is LumoResult.Success -> SearchSection.Loaded(
        items = if (append && previous is SearchSection.Loaded) {
            previous.items + value.items
        } else {
            value.items
        },
        totalElements = value.totalElements,
        page = page,
        size = size,
    )
}
