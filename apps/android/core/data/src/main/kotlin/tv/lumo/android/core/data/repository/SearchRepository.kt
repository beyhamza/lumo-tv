package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.CataloguePresence
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.data.model.SearchPage
import tv.lumo.android.core.data.model.SearchResults
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.VodItem

/**
 * One search over the active source's three catalogues (US-021, S10-01).
 *
 * <h2>An interface, for the screen's sake</h2>
 *
 * The search view model must be testable without three repositories, a session
 * and a server — the whole of S10-01's interesting behaviour is the request it
 * makes and the answer it keeps, and both are decided by what arrives and when.
 * An interface lets a test hand it answers out of order, which is the only way
 * SR-08 is provable. The same argument is written on `ActiveSourceRepository`.
 *
 * <h2>The composition, and why it lives here</h2>
 *
 * The three listings already exist and already share a `q`. Composing them is not
 * a new endpoint and not a new query language: it is one call per requested type,
 * issued together, each with its own outcome. A section that fails does not fail
 * the search — Q9 is explicit that an error is never an empty result and that the
 * sections are independent.
 */
interface SearchRepository {

    /**
     * Searches [filter]'s types in [sourceId] for [query], one page.
     *
     * @param page zero-based, and fresh for every request: the caller never derives
     *   a page index for a size from another size (Q9 — four and twenty are distinct
     *   requests).
     * @param size the contract's page size. [SearchFilter.All] is asked at the
     *   preview size, a single type at the page size.
     * @param present the types the source carries. A type it does not carry is not
     *   asked for, even in the grouped view (S10-02). Defaults to the fail-open
     *   value, so a caller that has not probed still asks for everything.
     * @return one field per requested type, each a success or a failure. The fields
     *   of types [filter] does not ask for are null.
     */
    suspend fun search(
        sourceId: String,
        query: String,
        filter: SearchFilter,
        page: Int,
        size: Int,
        present: CataloguePresence = CataloguePresence.all,
    ): SearchResults

    /**
     * Which catalogues [sourceId] carries, so the screens list only real filters.
     *
     * Never fails: a probe that did not answer leaves its type present (fail-open),
     * so an outage never hides a filter. Read once per source, before its first
     * search.
     */
    suspend fun cataloguePresence(sourceId: String): CataloguePresence
}

/**
 * [SearchRepository] over the three server-side searches.
 *
 * <h2>One dispatcher, and it is injected</h2>
 *
 * The three searches each switch to the IO dispatcher inside their own repository;
 * this one wraps the composition in the same dispatcher so that cancelling the
 * caller cancels the three together. Nothing here creates a thread or a scope of
 * its own (AGENTS.md §4): [coroutineScope] is structured concurrency, not a pool.
 */
@Singleton
internal class DefaultSearchRepository @Inject constructor(
    private val channels: CatalogueRepository,
    private val films: VodRepository,
    private val series: SeriesRepository,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun search(
        sourceId: String,
        query: String,
        filter: SearchFilter,
        page: Int,
        size: Int,
        present: CataloguePresence,
    ): SearchResults = withContext(io) {
        searchSections(
            filter = filter,
            present = present,
            channels = { channels.searchPage(sourceId, query, page, size) },
            films = { films.searchPage(sourceId, query, page, size) },
            series = { series.searchPage(sourceId, query, page, size) },
        )
    }

    override suspend fun cataloguePresence(sourceId: String): CataloguePresence = withContext(io) {
        cataloguePresenceOf(
            channels = { channels.hasItems(sourceId) },
            films = { films.hasItems(sourceId) },
            series = { series.hasItems(sourceId) },
        )
    }
}

/**
 * Runs the requested sections together and keeps their outcomes apart.
 *
 * <h2>Together, because one slow type must not hold up the others</h2>
 *
 * The three listings are independent. Asked in sequence, a channel search that
 * takes two seconds would put the two sections that were ready behind it; asked
 * concurrently, each section is on screen as soon as its own answer is. The
 * results are awaited in a fixed order, so the *assembly* is deterministic even
 * though the network is not.
 *
 * <h2>Pulled out so that the rule is testable without a server</h2>
 *
 * "One section failing leaves the other two" is the sentence worth pinning, and it
 * is a property of this function rather than of Retrofit. The lambdas stand in for
 * the three repositories, and a test hands it a failure for one and successes for
 * the others without a session or a network.
 *
 * <h2>An absent type is not asked for</h2>
 *
 * [present] is what the source carries. A type it does not carry is skipped even by
 * the grouped view, so the search never spends a request on a catalogue the source
 * does not have (S10-02). The section stays null, exactly as for a type the filter
 * did not ask for.
 */
internal suspend fun searchSections(
    filter: SearchFilter,
    present: CataloguePresence = CataloguePresence.all,
    channels: suspend () -> LumoResult<SearchPage<Channel>>,
    films: suspend () -> LumoResult<SearchPage<VodItem>>,
    series: suspend () -> LumoResult<SearchPage<Series>>,
): SearchResults = coroutineScope {
    val wantsChannels = present.channels && (filter == SearchFilter.All || filter == SearchFilter.Channels)
    val wantsFilms = present.films && (filter == SearchFilter.All || filter == SearchFilter.Films)
    val wantsSeries = present.series && (filter == SearchFilter.All || filter == SearchFilter.Series)

    // Started before any of them is awaited, so the three are genuinely in
    // flight together and not merely declared together.
    val channelJob = if (wantsChannels) async { channels() } else null
    val filmJob = if (wantsFilms) async { films() } else null
    val seriesJob = if (wantsSeries) async { series() } else null

    SearchResults(
        channels = channelJob?.await(),
        films = filmJob?.await(),
        series = seriesJob?.await(),
    )
}

/**
 * Probes the three catalogues together, and keeps a failed probe present.
 *
 * The three questions are independent, so they are asked concurrently and awaited
 * in a fixed order for a deterministic assembly. Extracted from
 * [DefaultSearchRepository.cataloguePresence] for the same reason as
 * [searchSections]: "a probe that did not answer keeps its type" is the rule worth
 * testing, and it is a property of this function rather than of the three
 * repositories.
 */
internal suspend fun cataloguePresenceOf(
    channels: suspend () -> LumoResult<Boolean>,
    films: suspend () -> LumoResult<Boolean>,
    series: suspend () -> LumoResult<Boolean>,
): CataloguePresence = coroutineScope {
    val channelProbe = async { channels() }
    val filmProbe = async { films() }
    val seriesProbe = async { series() }

    CataloguePresence(
        channels = channelProbe.await().presentOrFailOpen(),
        films = filmProbe.await().presentOrFailOpen(),
        series = seriesProbe.await().presentOrFailOpen(),
    )
}

/**
 * A probe's answer as a presence.
 *
 * A failure is "present": an outage must never shorten the list of filters
 * (US-021, SR-11). See [CataloguePresence].
 */
private fun LumoResult<Boolean>.presentOrFailOpen(): Boolean = when (this) {
    is LumoResult.Success -> value
    is LumoResult.Failure -> true
}
