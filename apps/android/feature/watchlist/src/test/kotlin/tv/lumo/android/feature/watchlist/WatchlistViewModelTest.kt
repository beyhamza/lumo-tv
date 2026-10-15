package tv.lumo.android.feature.watchlist

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.PendingIntention
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.data.model.WatchlistSlice
import tv.lumo.android.core.data.model.WatchlistTarget
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.core.data.model.WatchlistWriteOutcome
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.WatchlistRepository
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * "My list" (US-022, S11-E05) and the states `watchlist-states.md` retained: what
 * is drawn while loading, empty, failed or offline, the filters, and the removal
 * of an unavailable card with its idempotency key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val main = StandardTestDispatcher(scheduler)

    private val repo = FakeWatchlist()
    private val active = FakeActiveSource()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- loading, order, empty ---------------------------------------------

    @Test
    fun `loading is never drawn as an empty list`() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        assertThat(vm.state.value.phase).isEqualTo(WatchlistPhase.Loading)
        assertThat(vm.state.value.confirmedEmpty).isFalse()

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.state.value.confirmedEmpty).isTrue()
    }

    @Test
    fun `the server order is kept, for the active source only`() = runTest(scheduler) {
        repo.items = listOf(entry("b"), entry("a"), entry("c"))
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        assertThat(vm.state.value.items.map { it.id }).containsExactly("b", "a", "c").inOrder()
        assertThat(repo.sourcesAsked).containsExactly("source-1")
    }

    @Test
    fun `a change of source shows that source's list from scratch`() = runTest(scheduler) {
        repo.items = listOf(entry("a"))
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        repo.items = listOf(entry("z", source = "source-2"))
        active.active.value = ActiveSourceState.Selected("source-2", source = null, sources = emptyList())
        advanceUntilIdle()

        assertThat(vm.state.value.sourceId).isEqualTo("source-2")
        assertThat(vm.state.value.items.map { it.id }).containsExactly("z")
    }

    @Test
    fun `load more appends the next page after the first`() = runTest(scheduler) {
        repo.items = List(75) { entry("i$it") }
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()
        assertThat(vm.state.value.items).hasSize(50)
        assertThat(vm.state.value.hasMore).isTrue()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(vm.state.value.items.map { it.id }).isEqualTo(List(75) { "i$it" })
        assertThat(vm.state.value.hasMore).isFalse()
    }

    // ---- filters -----------------------------------------------------------

    @Test
    fun `no selector for fewer than two types, All Films Series from two`() {
        assertThat(filtersFor(emptySet())).isEmpty()
        assertThat(filtersFor(setOf(WatchlistType.Series))).isEmpty()
        assertThat(filtersFor(setOf(WatchlistType.Film, WatchlistType.Series)))
            .containsExactly(WatchlistFilter.All, WatchlistFilter.Films, WatchlistFilter.Series)
            .inOrder()
    }

    @Test
    fun `a filter with nothing in it keeps the filters and is not an empty list`() = runTest(scheduler) {
        repo.items = listOf(entry("a", WatchlistType.Film))
        // The list holds a series further on, or an unavailable one: the server says so.
        repo.types = setOf(WatchlistType.Film, WatchlistType.Series)
        repo.filterEmpty = true
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        vm.selectFilter(WatchlistFilter.Series)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.filterEmpty).isTrue()
        assertThat(state.confirmedEmpty).isFalse()
        assertThat(state.filters).isNotEmpty()
        assertThat(repo.typesAsked.last()).isEqualTo(WatchlistType.Series)
    }

    // ---- failures ----------------------------------------------------------

    @Test
    fun `a failed re-read keeps the items on screen and offers retry`() = runTest(scheduler) {
        repo.items = listOf(entry("a"))
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        repo.readFailure = LumoError.Unreadable(500, null)
        vm.retry()
        advanceUntilIdle()

        assertThat(vm.state.value.items.map { it.id }).containsExactly("a")
        assertThat(vm.state.value.refreshFailed).isTrue()
        assertThat(vm.state.value.phase).isEqualTo(WatchlistPhase.Ready)
    }

    @Test
    fun `offline with nothing loaded is a load failure, not an empty list`() = runTest(scheduler) {
        repo.readFailure = LumoError.Offline(IOException("no network"))
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        assertThat(vm.state.value.phase).isEqualTo(WatchlistPhase.LoadFailed(offline = true))
        assertThat(vm.state.value.confirmedEmpty).isFalse()
    }

    @Test
    fun `offline keeps the list read-only, and a reconnection re-reads before removal returns`() =
        runTest(scheduler) {
            repo.items = listOf(entry("a", available = false))
            val vm = WatchlistViewModel(repo, active)
            advanceUntilIdle()

            repo.readFailure = LumoError.Offline(IOException("no network"))
            vm.refresh()
            advanceUntilIdle()

            assertThat(vm.state.value.offline).isTrue()
            assertThat(vm.state.value.items).hasSize(1)
            assertThat(vm.state.value.canRemove).isFalse()

            vm.openPanel(vm.state.value.items.single())
            vm.remove(vm.state.value.items.single())
            advanceUntilIdle()
            // Nothing sent, nothing queued.
            assertThat(repo.written).isEmpty()

            repo.readFailure = null
            val readsBefore = repo.reads
            repo.reconnections.emit(Unit)
            advanceUntilIdle()

            assertThat(repo.reads).isGreaterThan(readsBefore)
            assertThat(vm.state.value.offline).isFalse()
            assertThat(vm.state.value.canRemove).isTrue()
        }

    // ---- a source deleted elsewhere (S11-05) ---------------------------------

    @Test
    fun `an empty list for a source deleted elsewhere falls back instead of claiming empty`() =
        runTest(scheduler) {
            // The server answers an empty 200 for a deleted source; the source list
            // no longer carries it, and one source is left.
            active.onRefresh = {
                active.active.value = ActiveSourceState.Selected("source-2", source = null, sources = emptyList())
            }
            repo.items = listOf(entry("z", source = "source-2"))
            repo.emptyFor = "source-1"
            val vm = WatchlistViewModel(repo, active)
            advanceUntilIdle()

            assertThat(active.refreshes).isAtLeast(1)
            assertThat(vm.state.value.sourceId).isEqualTo("source-2")
            assertThat(vm.state.value.items.map { it.id }).containsExactly("z")
        }

    @Test
    fun `an empty list for a source deleted with none left is never drawn as empty`() =
        runTest(scheduler) {
            active.onRefresh = { active.active.value = ActiveSourceState.None }
            val vm = WatchlistViewModel(repo, active)
            advanceUntilIdle()

            assertThat(vm.state.value.phase).isEqualTo(WatchlistPhase.NoSource)
            assertThat(vm.state.value.confirmedEmpty).isFalse()
        }

    @Test
    fun `an empty list for a source that still exists is a confirmed empty list`() = runTest(scheduler) {
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        assertThat(active.refreshes).isEqualTo(1)
        assertThat(vm.state.value.confirmedEmpty).isTrue()
    }

    @Test
    fun `SOURCE_NOT_FOUND on a read is handled as a deletion`() = runTest(scheduler) {
        repo.readFailure = LumoError.Api(ErrorCode.SOURCE_NOT_FOUND, null)
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        assertThat(active.gone).containsExactly("source-1")
        assertThat(vm.state.value.phase).isEqualTo(WatchlistPhase.NoSource)
    }

    @Test
    fun `a server failure re-reads the source list, offline does not`() = runTest(scheduler) {
        repo.readFailure = LumoError.Unreadable(500, null)
        WatchlistViewModel(repo, active)
        advanceUntilIdle()
        assertThat(active.refreshes).isEqualTo(1)

        repo.readFailure = LumoError.Offline(IOException("no network"))
        val offline = WatchlistViewModel(repo, active)
        advanceUntilIdle()
        assertThat(active.refreshes).isEqualTo(1)
        assertThat(offline.state.value.phase).isEqualTo(WatchlistPhase.LoadFailed(offline = true))
    }

    // ---- removal from the compact panel -------------------------------------

    @Test
    fun `a confirmed removal takes the card out and closes the panel`() = runTest(scheduler) {
        repo.items = listOf(entry("a"), entry("gone", available = false), entry("c"))
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()

        val gone = vm.state.value.items[1]
        vm.openPanel(gone)
        repo.next = { repo.items = repo.items.filterNot { it.id == "gone" }; WatchlistWriteOutcome.Confirmed(null) }
        vm.remove(gone)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.items.map { it.id }).containsExactly("a", "c").inOrder()
        assertThat(state.panel).isNull()
        assertThat(state.removedId).isEqualTo("gone")
        assertThat(repo.written.single().target).isEqualTo(WatchlistTarget.Item("gone"))
        assertThat(repo.written.single().action).isEqualTo(WatchlistAction.Remove)
    }

    @Test
    fun `an unknown removal is not announced, re-reads, and its retry reuses the key`() =
        runTest(scheduler) {
            repo.items = listOf(entry("gone", available = false))
            val vm = WatchlistViewModel(repo, active)
            advanceUntilIdle()
            val gone = vm.state.value.items.single()
            vm.openPanel(gone)

            repo.next = { WatchlistWriteOutcome.Unknown(LumoError.Offline(IOException("lost"))) }
            val readsBefore = repo.reads
            vm.remove(gone)
            advanceUntilIdle()

            val state = vm.state.value
            assertThat(state.removal?.unconfirmed).isTrue()
            assertThat(state.items.map { it.id }).containsExactly("gone")
            assertThat(state.panel).isEqualTo(gone)
            assertThat(repo.reads).isGreaterThan(readsBefore)

            repo.next = { repo.items = emptyList(); WatchlistWriteOutcome.Confirmed(null) }
            vm.remove(gone)
            advanceUntilIdle()

            assertThat(repo.written).hasSize(2)
            assertThat(repo.written[1].key).isEqualTo(repo.written[0].key)
            assertThat(vm.state.value.confirmedEmpty).isTrue()
        }

    @Test
    fun `an unknown removal the re-read shows done is settled without a retry`() = runTest(scheduler) {
        repo.items = listOf(entry("a"), entry("gone", available = false))
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()
        val gone = vm.state.value.items[1]
        vm.openPanel(gone)

        // The server removed it; only the answer was lost.
        repo.next = {
            repo.items = repo.items.filterNot { it.id == "gone" }
            WatchlistWriteOutcome.Unknown(LumoError.Unreadable(502, null))
        }
        vm.remove(gone)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.removal).isNull()
        assertThat(state.panel).isNull()
        assertThat(state.items.map { it.id }).containsExactly("a")
        assertThat(vm.pendingRemovalOf("gone")).isNull()
    }

    @Test
    fun `a refused removal says so and the next attempt is a new intention`() = runTest(scheduler) {
        repo.items = listOf(entry("gone", available = false))
        val vm = WatchlistViewModel(repo, active)
        advanceUntilIdle()
        val gone = vm.state.value.items.single()
        vm.openPanel(gone)

        repo.next = { WatchlistWriteOutcome.Rejected(LumoError.Api(ErrorCode.VALIDATION_FAILED, null)) }
        vm.remove(gone)
        advanceUntilIdle()
        assertThat(vm.state.value.removal?.rejected).isTrue()

        repo.next = { repo.items = emptyList(); WatchlistWriteOutcome.Confirmed(null) }
        vm.remove(gone)
        advanceUntilIdle()

        assertThat(repo.written[1].key).isNotEqualTo(repo.written[0].key)
    }

    // ---- selection ---------------------------------------------------------

    @Test
    fun `an available card opens its sheet, an unavailable one the panel`() {
        assertThat(entry("f", WatchlistType.Film).selection()).isEqualTo(CardSelection.OpenFilm("content-f"))
        assertThat(entry("s", WatchlistType.Series).selection()).isEqualTo(CardSelection.OpenSeries("content-s"))
        assertThat(entry("x", available = false).selection()).isEqualTo(CardSelection.OpenPanel)
    }

    // ---- doubles -----------------------------------------------------------

    private fun entry(
        id: String,
        type: WatchlistType = WatchlistType.Film,
        available: Boolean = true,
        source: String = "source-1",
    ) = WatchlistEntry(
        id = id,
        sourceId = source,
        type = type,
        contentId = "content-$id".takeIf { available },
        available = available,
        title = "Titre $id",
        posterUrl = null,
        addedAt = Instant.EPOCH,
    )

    private class FakeWatchlist : WatchlistRepository {
        var items: List<WatchlistEntry> = emptyList()
        var types: Set<WatchlistType>? = null
        var filterEmpty = false
        var readFailure: LumoError? = null

        /** A source the server answers an empty `200` for, as it does once deleted. */
        var emptyFor: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var next: () -> WatchlistWriteOutcome = { WatchlistWriteOutcome.Confirmed(null) }
        val written = mutableListOf<PendingIntention>()
        val sourcesAsked = mutableSetOf<String>()
        val typesAsked = mutableListOf<WatchlistType?>()
        var reads = 0
        override val reconnections = MutableSharedFlow<Unit>()

        override suspend fun page(
            sourceId: String,
            type: WatchlistType?,
            page: Int,
            size: Int,
        ): LumoResult<WatchlistSlice> {
            gate?.await()
            reads++
            sourcesAsked += sourceId
            typesAsked += type
            readFailure?.let { return LumoResult.Failure(it) }
            val matching = when {
                sourceId == emptyFor -> emptyList()
                type != null && filterEmpty -> emptyList()
                else -> items.filter { type == null || it.type == type }
            }
            val chunk = matching.drop(page * size).take(size)
            val totalPages = ((matching.size + size - 1) / size).coerceAtLeast(1)
            return LumoResult.Success(
                WatchlistSlice(
                    items = chunk,
                    page = page,
                    totalPages = totalPages,
                    totalElements = matching.size.toLong(),
                    types = types ?: items.mapTo(HashSet()) { it.type },
                ),
            )
        }

        override suspend fun find(sourceId: String, type: WatchlistType, contentId: String) =
            LumoResult.Success(items.firstOrNull { it.contentId == contentId })

        override suspend fun write(intention: PendingIntention): WatchlistWriteOutcome {
            written += intention
            return next()
        }
    }

    private class FakeActiveSource : ActiveSourceRepository {
        val active = MutableStateFlow<ActiveSourceState>(
            ActiveSourceState.Selected("source-1", source = null, sources = emptyList()),
        )
        override val state: StateFlow<ActiveSourceState> = active
        override val accountId: Flow<String?> = MutableStateFlow("account-1")
        override suspend fun select(sourceId: String) = Unit
        var refreshes = 0
        val gone = mutableListOf<String>()

        /** What a re-read of the source list concludes. Unchanged by default. */
        var onRefresh: () -> Unit = {}
        override suspend fun refresh() {
            refreshes++
            onRefresh()
        }

        override suspend fun onSourceGone(sourceId: String) {
            gone += sourceId
            active.value = ActiveSourceState.None
        }
    }
}
