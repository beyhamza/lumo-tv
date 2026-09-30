package tv.lumo.android.feature.search

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import tv.lumo.android.core.data.ActiveSourceState
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

/**
 * The engine of the search screen (US-021, S10-01).
 *
 * Every rule here is Q9's, and every one of them is about *when* a request is made
 * and *whether* an answer may land. So the clock is virtual and the answers are
 * released by hand: [FakeSearch] keeps the requests it was given and lets a test
 * complete them in any order, which is the only way SR-08 is provable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val main = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- SR-01 / SR-02: the delay, and the immediate subject -----------------

    @Test
    fun `no request before the delay, and one after it`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertThat(search.calls).isEmpty()

        advanceTimeBy(1)
        runCurrent()
        assertThat(search.calls).hasSize(1)
        assertThat(search.calls.single().query).isEqualTo("falaise")
    }

    @Test
    fun `a rapid spelling issues one request for the final text`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("f", composing = false)
        advanceTimeBy(100)
        viewModel.onQueryChanged("fa", composing = false)
        advanceTimeBy(100)
        viewModel.onQueryChanged("fal", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertThat(search.calls.map { it.query }).containsExactly("fal")
    }

    @Test
    fun `submitting runs now and the elapsed delay adds no second request`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(100)
        viewModel.onSearchSubmitted()
        runCurrent()
        assertThat(search.calls).hasSize(1)

        // The debounce would have fired here; it must not, or a person who pressed
        // Enter would pay for two identical searches.
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertThat(search.calls).hasSize(1)
    }

    @Test
    fun `no request while the input method is still composing`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falai", composing = true)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS * 4)
        runCurrent()
        assertThat(search.calls).isEmpty()

        // Validation of the composition is what starts the delay.
        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertThat(search.calls.map { it.query }).containsExactly("falaise")
    }

    // ---- SR-04 / SR-05: trim, empty and the hundred-character limit ----------

    @Test
    fun `spaces alone invite and load nothing`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("   ", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertThat(search.calls).isEmpty()
        assertThat(viewModel.state.value.invitation).isTrue()
        assertThat(viewModel.state.value.channels).isEqualTo(SearchSection.Idle)
        assertThat(viewModel.state.value.films).isEqualTo(SearchSection.Idle)
        assertThat(viewModel.state.value.series).isEqualTo(SearchSection.Idle)
    }

    @Test
    fun `the query is trimmed before it goes out`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("  falaise  ", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertThat(search.calls.single().query).isEqualTo("falaise")
    }

    @Test
    fun `emptying the field cancels what is in flight`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertThat(search.calls).hasSize(1)

        viewModel.onQueryChanged("", composing = false)
        search.answer(0, allThree(channels = listOf(channel("c1", "Falaise 1"))))
        runCurrent()

        assertThat(viewModel.state.value.invitation).isTrue()
        assertThat(viewModel.state.value.channels).isEqualTo(SearchSection.Idle)
    }

    @Test
    fun `a hundred and one characters are refused, never truncated`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)
        val tooLong = "a".repeat(SearchViewModel.MAX_QUERY_LENGTH + 1)

        viewModel.onQueryChanged(tooLong, composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertThat(search.calls).isEmpty()
        assertThat(viewModel.state.value.tooLong).isTrue()
    }

    @Test
    fun `a hundred code points are accepted, even with a non-BMP character`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        // 99 ASCII characters plus one emoji: 100 code points but 101 UTF-16 units.
        // The server counts characters, so the client must too (SR-05).
        val accepted = "a".repeat(99) + "\uD83D\uDE00"
        assertThat(accepted.length).isEqualTo(SearchViewModel.MAX_QUERY_LENGTH + 1)

        viewModel.onQueryChanged(accepted, composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertThat(viewModel.state.value.tooLong).isFalse()
        assertThat(search.calls.map { it.query }).containsExactly(accepted)
    }

    // ---- SR-06 / SR-07: four and twenty are distinct requests ----------------

    @Test
    fun `the grouped view asks four, and a type asks twenty from page zero`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        val preview = search.calls.single()
        assertThat(preview.filter).isEqualTo(SearchFilter.All)
        assertThat(preview.page).isEqualTo(0)
        assertThat(preview.size).isEqualTo(SearchViewModel.PREVIEW_SIZE)

        // "Voir tous" / the Channels tab: a distinct request, not an index computed
        // for a size of four (Q9).
        viewModel.onFilterSelected(SearchFilter.Channels)
        runCurrent()

        val channels = search.calls.last()
        assertThat(channels.filter).isEqualTo(SearchFilter.Channels)
        assertThat(channels.page).isEqualTo(0)
        assertThat(channels.size).isEqualTo(SearchViewModel.PAGE_SIZE)
    }

    @Test
    fun `a next page keeps what is on screen and appends`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        search.answer(0, allThree(channels = (1..20).map { channel("c$it", "Falaise $it") }))
        runCurrent()
        viewModel.onFilterSelected(SearchFilter.Channels)
        runCurrent()

        // The first page of the type: 20 of 40, so there is a next one.
        search.answer(1, onlyChannels((1..20).map { channel("c$it", "Falaise $it") }, total = 40L))
        runCurrent()
        assertThat(loaded(viewModel.state.value.channels).hasMore).isTrue()

        viewModel.onLoadMore(SearchFilter.Channels)
        runCurrent()
        val next = search.calls.last()
        assertThat(next.page).isEqualTo(1)
        assertThat(next.size).isEqualTo(SearchViewModel.PAGE_SIZE)

        search.answer(2, onlyChannels((21..40).map { channel("c$it", "Falaise $it") }, total = 40L))
        runCurrent()
        assertThat(loaded(viewModel.state.value.channels).items).hasSize(40)
    }

    // ---- SR-08: a late answer never replaces the current context -------------

    @Test
    fun `a first request landing after the second is ignored`() = runTest(main) {
        // `nonCancellable` makes the fake's answer survive the job cancellation, so
        // the context guard — and not the cancellation alone — is what the test
        // exercises.
        val search = FakeSearch(nonCancellable = true)
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("abc", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        viewModel.onQueryChanged("abcd", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertThat(search.calls.map { it.query }).containsExactly("abc", "abcd")

        // The newest answer arrives first and is kept.
        search.answer(1, allThree(channels = listOf(channel("new", "Nouveau"))))
        runCurrent()
        assertThat(loaded(viewModel.state.value.channels).items.map { it.id }).containsExactly("new")

        // The old one arrives afterwards. It must not put "Ancien" back on screen.
        search.answer(0, allThree(channels = listOf(channel("old", "Ancien"))))
        runCurrent()
        assertThat(loaded(viewModel.state.value.channels).items.map { it.id }).containsExactly("new")
    }

    // ---- SR-09 / SR-15: a change of source, and a change of account ----------

    @Test
    fun `a change of source keeps the text, drops the old results and searches again`() = runTest(main) {
        val search = FakeSearch()
        val active = FakeActiveSource()
        val viewModel = viewModel(search, active)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        search.answer(0, allThree(channels = listOf(channel("a1", "Source A", sourceId = "source-1"))))
        runCurrent()
        assertThat(loaded(viewModel.state.value.channels).items.map { it.id }).containsExactly("a1")

        // The source changes under the screen.
        active.active.value = selected("source-2")
        runCurrent()

        // Text kept, back to All, and the old source's results gone at once.
        assertThat(viewModel.state.value.query).isEqualTo("falaise")
        assertThat(viewModel.state.value.filter).isEqualTo(SearchFilter.All)
        assertThat(search.calls.map { it.sourceId }).containsExactly("source-1", "source-2")
        assertThat(search.calls.last().query).isEqualTo("falaise")

        // The old source's answer cannot come back over the new source's.
        search.answer(0, allThree(channels = listOf(channel("a2", "Source A again", sourceId = "source-1"))))
        search.answer(1, allThree(channels = listOf(channel("b1", "Source B", sourceId = "source-2"))))
        runCurrent()
        assertThat(loaded(viewModel.state.value.channels).items.map { it.sourceId }).containsExactly("source-2")
    }

    @Test
    fun `a change of account empties the context and hides the old answer`() = runTest(main) {
        val search = FakeSearch(nonCancellable = true)
        val active = FakeActiveSource()
        val viewModel = viewModel(search, active)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        // Another account signs in, with its own source.
        active.account.value = "account-2"
        active.active.value = selected("source-2")
        runCurrent()

        assertThat(viewModel.state.value.channels).isEqualTo(SearchSection.Loading)
        assertThat(search.calls.map { it.sourceId }).containsExactly("source-1", "source-2")

        // The first account's answer arrives late: it must not be shown.
        search.answer(0, allThree(channels = listOf(channel("a1", "Compte A", sourceId = "source-1"))))
        runCurrent()
        assertThat(viewModel.state.value.channels).isEqualTo(SearchSection.Loading)

        search.answer(1, allThree(channels = listOf(channel("b1", "Compte B", sourceId = "source-2"))))
        runCurrent()
        assertThat(loaded(viewModel.state.value.channels).items.map { it.sourceId }).containsExactly("source-2")
    }

    // ---- S10-02: only the catalogues the source carries ----------------------

    @Test
    fun `a source without films never asks for films and offers no films filter`() = runTest(main) {
        val search = FakeSearch()
        search.presence = CataloguePresence(channels = true, films = false, series = false)
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        // The grouped view is composed for the present type only: the absent types
        // are not sent to the repository at all (S10-02).
        val call = search.calls.single()
        assertThat(call.present.channels).isTrue()
        assertThat(call.present.films).isFalse()
        assertThat(call.present.series).isFalse()

        // Absent is Idle, not Loaded(empty): nothing was asked, so nothing can be
        // claimed about the source.
        assertThat(viewModel.state.value.films).isEqualTo(SearchSection.Idle)
        assertThat(viewModel.state.value.series).isEqualTo(SearchSection.Idle)

        // The screen offers only the present filters.
        assertThat(viewModel.state.value.filters)
            .containsExactly(SearchFilter.All, SearchFilter.Channels)
    }

    @Test
    fun `a present type with no match stays offered, with an empty section`() = runTest(main) {
        val search = FakeSearch()
        search.presence = CataloguePresence(channels = true, films = false, series = false)
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        search.answer(0, onlyChannels(emptyList(), total = 0L))
        runCurrent()

        // Present without a match: an empty section under a filter that stays. It
        // is not the same as the type being absent (S10-02).
        assertThat(viewModel.state.value.channels).isEqualTo(
            SearchSection.Loaded(
                emptyList<Channel>(),
                totalElements = 0L,
                page = 0,
                size = SearchViewModel.PREVIEW_SIZE,
            ),
        )
        assertThat(viewModel.state.value.filters).contains(SearchFilter.Channels)
    }

    @Test
    fun `a change of source re-reads the presence`() = runTest(main) {
        val search = FakeSearch()
        val active = FakeActiveSource()
        val viewModel = viewModel(search, active)
        assertThat(search.presenceCalls).containsExactly("source-1")

        // The new source carries films, not channels.
        search.presence = CataloguePresence(channels = false, films = true, series = false)
        active.active.value = selected("source-2")
        runCurrent()

        assertThat(search.presenceCalls).containsExactly("source-1", "source-2")
        assertThat(viewModel.state.value.filters)
            .containsExactly(SearchFilter.All, SearchFilter.Films)
    }

    @Test
    fun `a presence answer for the old account is ignored`() = runTest(main) {
        val search = FakeSearch(nonCancellable = true, deferPresence = true)
        val active = FakeActiveSource()
        val viewModel = viewModel(search, active)
        assertThat(search.presenceCalls).containsExactly("source-1")

        // Another account signs in, with its own source.
        active.account.value = "account-2"
        active.active.value = selected("source-2")
        runCurrent()
        assertThat(search.presenceCalls).containsExactly("source-1", "source-2")

        // The first account's probe arrives late: it must not set the filters.
        search.answerPresence(0, CataloguePresence(channels = false, films = false, series = false))
        runCurrent()
        assertThat(viewModel.state.value.presence).isEqualTo(CataloguePresence.all)

        // The new account's presence is the one that applies.
        search.answerPresence(1, CataloguePresence(channels = false, films = true, series = false))
        runCurrent()
        assertThat(viewModel.state.value.filters)
            .containsExactly(SearchFilter.All, SearchFilter.Films)
    }

    @Test
    fun `a search typed while the probe is in flight runs once, after the probe`() = runTest(main) {
        val search = FakeSearch(deferPresence = true)
        val viewModel = viewModel(search)
        assertThat(search.presenceCalls).containsExactly("source-1")

        // Typed while the probe is still out: the debounce fires but no request is
        // issued, because the types the source carries are not known yet.
        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertThat(search.calls).isEmpty()

        // The probe answers: the wanted search runs now, exactly once, with the
        // presence it just learned.
        search.answerPresence(0, CataloguePresence(channels = true, films = false, series = false))
        runCurrent()
        assertThat(search.calls).hasSize(1)
        assertThat(search.calls.single().present.films).isFalse()

        // The debounce already fired; it must not add a second request.
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertThat(search.calls).hasSize(1)
    }

    @Test
    fun `a probe answer during composition does not search early`() = runTest(main) {
        val search = FakeSearch(deferPresence = true)
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falai", composing = true)
        runCurrent()
        search.answerPresence(0, CataloguePresence(channels = true, films = false, series = false))
        runCurrent()

        // Still composing: no request, even though the probe is now known.
        assertThat(search.calls).isEmpty()

        // Validating the composition starts the delay, then the single search runs.
        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertThat(search.calls.map { it.query }).containsExactly("falaise")
    }

    // ---- SR-10: a section fails, the others stay ----------------------------

    @Test
    fun `one failing section leaves the other two, and the failure is not empty`() = runTest(main) {
        val search = FakeSearch()
        val viewModel = viewModel(search)

        viewModel.onQueryChanged("falaise", composing = false)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        val offline = LumoError.Offline(IOException("no network"))
        search.answer(
            0,
            SearchResults(
                channels = LumoResult.Failure(offline),
                films = LumoResult.Success(SearchPage(listOf(vod("v1", "Falaise")), totalElements = 1L)),
                series = LumoResult.Success(SearchPage(listOf(series("s1", "Falaises")), totalElements = 1L)),
            ),
        )
        runCurrent()

        assertThat(viewModel.state.value.channels).isEqualTo(SearchSection.Failed(offline))
        assertThat(loaded(viewModel.state.value.films).items.map { it.id }).containsExactly("v1")
        assertThat(loaded(viewModel.state.value.series).items.map { it.id }).containsExactly("s1")
    }

    // ---- helpers -------------------------------------------------------------

    private fun TestScope.viewModel(
        search: SearchRepository,
        active: FakeActiveSource = FakeActiveSource(),
    ): SearchViewModel {
        val viewModel = SearchViewModel(search, active)
        // Lets the identity collector read the first state before a test acts.
        runCurrent()
        return viewModel
    }

    private fun selected(sourceId: String) =
        ActiveSourceState.Selected(sourceId, source = null, sources = emptyList())

    @Suppress("UNCHECKED_CAST")
    private fun <T> loaded(section: SearchSection<T>): SearchSection.Loaded<T> {
        assertThat(section).isInstanceOf(SearchSection.Loaded::class.java)
        return section as SearchSection.Loaded<T>
    }

    private fun channel(id: String, name: String, sourceId: String = "source-1") = Channel(
        id = id,
        sourceId = sourceId,
        categoryId = null,
        name = name,
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun vod(id: String, name: String) = VodItem(
        id = id,
        sourceId = "source-1",
        categoryId = null,
        name = name,
        posterUrl = null,
        year = null,
        durationSeconds = null,
        rating = null,
        plot = null,
        isAdult = false,
    )

    private fun series(id: String, name: String) = Series(
        id = id,
        sourceId = "source-1",
        categoryId = null,
        name = name,
        posterUrl = null,
        year = null,
        episodeRunTime = null,
        rating = null,
        plot = null,
        isAdult = false,
    )

    private fun allThree(channels: List<Channel> = emptyList()) = SearchResults(
        channels = LumoResult.Success(SearchPage(channels, channels.size.toLong())),
        films = LumoResult.Success(SearchPage(emptyList(), 0L)),
        series = LumoResult.Success(SearchPage(emptyList(), 0L)),
    )

    private fun onlyChannels(channels: List<Channel>, total: Long) = SearchResults(
        channels = LumoResult.Success(SearchPage(channels, total)),
    )

    /** A search that records its requests and answers when the test says so. */
    private class FakeSearch(
        private val nonCancellable: Boolean = false,
        private val deferPresence: Boolean = false,
    ) : SearchRepository {

        /** What the source carries, set by a test before it changes identity. */
        var presence: CataloguePresence = CataloguePresence.all

        data class Call(
            val sourceId: String,
            val query: String,
            val filter: SearchFilter,
            val page: Int,
            val size: Int,
            val present: CataloguePresence,
        )

        val calls = mutableListOf<Call>()
        val presenceCalls = mutableListOf<String>()
        private val answers = mutableListOf<kotlinx.coroutines.CompletableDeferred<SearchResults>>()
        private val presenceAnswers =
            mutableListOf<kotlinx.coroutines.CompletableDeferred<CataloguePresence>>()

        override suspend fun search(
            sourceId: String,
            query: String,
            filter: SearchFilter,
            page: Int,
            size: Int,
            present: CataloguePresence,
        ): SearchResults {
            calls += Call(sourceId, query, filter, page, size, present)
            val answer = kotlinx.coroutines.CompletableDeferred<SearchResults>()
            answers += answer
            // Non-cancellable for the stale test: the answer outlives the job so the
            // state guard, not the cancellation, is what refuses it.
            return if (nonCancellable) withContext(NonCancellable) { answer.await() } else answer.await()
        }

        override suspend fun cataloguePresence(sourceId: String): CataloguePresence {
            presenceCalls += sourceId
            if (!deferPresence) return presence
            val answer = kotlinx.coroutines.CompletableDeferred<CataloguePresence>()
            presenceAnswers += answer
            // Non-cancellable for the old-account probe: it outlives its job so the
            // identity guard, not the cancellation, is what refuses it.
            return if (nonCancellable) withContext(NonCancellable) { answer.await() } else answer.await()
        }

        fun answer(index: Int, results: SearchResults) {
            answers[index].complete(results)
        }

        fun answerPresence(index: Int, value: CataloguePresence) {
            presenceAnswers[index].complete(value)
        }
    }

    /** The account and the source under the screen, both movable by a test. */
    private class FakeActiveSource : ActiveSourceRepository {

        val account = MutableStateFlow<String?>("account-1")
        val active = MutableStateFlow<ActiveSourceState>(selectedSource("source-1"))

        override val state: StateFlow<ActiveSourceState> = active
        override val accountId: Flow<String?> = account

        override suspend fun select(sourceId: String) = Unit
        override suspend fun refresh() = Unit
        override suspend fun onSourceGone(sourceId: String) = Unit

        private companion object {
            fun selectedSource(id: String) =
                ActiveSourceState.Selected(id, source = null, sources = emptyList())
        }
    }
}
