package tv.lumo.android.feature.favorites

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
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
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.model.FavoriteMembership
import tv.lumo.android.core.data.model.FavoriteRemovalOutcome
import tv.lumo.android.core.data.model.FavoriteReorderOutcome
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.FavoriteLibrary
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * The favourites view model's transitions for S11-03: the global removal's
 * confirmation, its partial failure and the retry of what remains only; a reorder
 * that stops midway; Organise's last group; offline.
 *
 * Over a fake [FavoriteLibrary]: what is under test is what the screen says and
 * what it sends, not the HTTP (that is `FavoriteRepositoryTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val main = StandardTestDispatcher(scheduler)

    private val docs = FavoriteGroup("docs", "Docs", position = 0, isDefault = true)
    private val news = FavoriteGroup("news", "News", position = 1, isDefault = false)
    private val kids = FavoriteGroup("kids", "Kids", position = 2, isDefault = false)

    // A* in the active source, B* in another — abstract bench entries.
    private val a1 = channel("a1", "source-a")
    private val a2 = channel("a2", "source-a")
    private val b1 = channel("b1", "source-b")

    private val library = FakeLibrary()
    private val active = FakeActiveSource()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- global removal (S11-E04, G2) ----------------------------------------

    @Test
    fun `remove from all my favourites asks first, and cancelling sends nothing`() = runTest(scheduler) {
        val vm = ready()
        val favorite = vm.state.value.visible.first { it.channel.id == "a1" }

        vm.onRemoveEverywhereRequested(favorite)
        assertThat(vm.state.value.removal).isEqualTo(GlobalRemoval(a1, RemovalPhase.Confirming))

        vm.onRemovalDismissed()
        advanceUntilIdle()

        assertThat(vm.state.value.removal).isNull()
        assertThat(library.removals).isEmpty()
    }

    @Test
    fun `a confirmed removal that completes is announced, once`() = runTest(scheduler) {
        val vm = ready()
        library.removalOutcomes += FavoriteRemovalOutcome.Removed

        vm.onRemoveEverywhereRequested(vm.state.value.visible.first { it.channel.id == "a1" })
        vm.onRemoveEverywhereConfirmed()
        assertThat(vm.state.value.removal?.phase).isEqualTo(RemovalPhase.Running)
        advanceUntilIdle()

        assertThat(library.removals).containsExactly("a1" to null)
        assertThat(vm.state.value.removal).isNull()
        assertThat(vm.state.value.removalDone).isEqualTo(a1)
    }

    @Test
    fun `FO-05 and FO-06 - a partial removal names what remains and the retry targets only that`() =
        runTest(scheduler) {
            val vm = ready()
            val remaining = FavoriteMembership("f-a1-kids", "kids")
            library.removalOutcomes += FavoriteRemovalOutcome.Incomplete(listOf(remaining), error = refused)
            library.removalOutcomes += FavoriteRemovalOutcome.Removed

            vm.onRemoveEverywhereRequested(vm.state.value.visible.first { it.channel.id == "a1" })
            vm.onRemoveEverywhereConfirmed()
            advanceUntilIdle()

            // Never a success: the dialog stays, with the group still holding it.
            assertThat(vm.state.value.removal?.phase).isEqualTo(RemovalPhase.Incomplete(listOf(remaining)))
            assertThat(vm.state.value.removalDone).isNull()
            assertThat(vm.state.value.groupsOf(listOf(remaining))).containsExactly(kids)

            vm.onRemovalRetried()
            advanceUntilIdle()

            // Only the remaining membership; nothing already removed is replayed.
            assertThat(library.removals).containsExactly("a1" to null, "a1" to setOf("f-a1-kids")).inOrder()
            assertThat(vm.state.value.removal).isNull()
            assertThat(vm.state.value.removalDone).isEqualTo(a1)
        }

    @Test
    fun `FO-07 - closing after a partial removal is a close, not a success`() = runTest(scheduler) {
        val vm = ready()
        library.removalOutcomes += FavoriteRemovalOutcome.Incomplete(listOf(FavoriteMembership("f", "kids")), refused)

        vm.onRemoveEverywhereRequested(vm.state.value.visible.first())
        vm.onRemoveEverywhereConfirmed()
        advanceUntilIdle()
        vm.onRemovalDismissed()

        assertThat(vm.state.value.removal).isNull()
        assertThat(vm.state.value.removalDone).isNull()
    }

    @Test
    fun `FO-08 - an unconfirmed removal retries the unconfirmed memberships, after a re-read`() =
        runTest(scheduler) {
            val vm = ready()
            val unknown = FavoriteMembership("f-a1-news", "news")
            library.removalOutcomes += FavoriteRemovalOutcome.Unconfirmed(listOf(unknown), lost)
            library.removalOutcomes += FavoriteRemovalOutcome.Removed

            vm.onRemoveEverywhereRequested(vm.state.value.visible.first { it.channel.id == "a1" })
            vm.onRemoveEverywhereConfirmed()
            advanceUntilIdle()

            assertThat(vm.state.value.removal?.phase).isEqualTo(RemovalPhase.Unconfirmed(listOf(unknown)))
            // Lost answers are not proof of being offline for the list, but the
            // screen now refuses writes until a read succeeds.
            assertThat(vm.state.value.offline).isTrue()

            library.refreshResult = LumoResult.Success(Unit)
            vm.refresh()
            advanceUntilIdle()
            vm.onRemovalRetried()
            advanceUntilIdle()

            assertThat(library.removals.last()).isEqualTo("a1" to setOf("f-a1-news"))
            assertThat(vm.state.value.removal).isNull()
        }

    @Test
    fun `remove from a group removes that membership only, without asking`() = runTest(scheduler) {
        val vm = ready()
        vm.onGroupSelected("docs")

        vm.onRemoveFromGroup(vm.state.value.visible.first { it.channel.id == "a1" })
        advanceUntilIdle()

        assertThat(library.removedFavorites).containsExactly("f-a1-docs")
        assertThat(library.removals).isEmpty()
        assertThat(vm.state.value.removal).isNull()
    }

    // ---- reorder (G1/G2) -----------------------------------------------------

    @Test
    fun `move before sends the visible order of the open group, never a visible slot`() = runTest(scheduler) {
        val vm = ready()
        vm.onGroupSelected("docs")
        // docs: A1, B1, A2 — the visible list is A1, A2.
        assertThat(vm.state.value.visible.map { it.channel.id }).containsExactly("a1", "a2").inOrder()

        vm.onFavoriteMoved(vm.state.value.visible.last(), -1)
        advanceUntilIdle()

        assertThat(library.reorders).containsExactly("docs" to listOf("f-a2-docs", "f-a1-docs"))
        assertThat(vm.state.value.reorderNotice).isNull()
    }

    @Test
    fun `a reorder that fails midway says it was not fully applied`() = runTest(scheduler) {
        val vm = ready()
        vm.onGroupSelected("docs")
        library.reorderOutcome = FavoriteReorderOutcome.NotFullyApplied(applied = 1, planned = 2, error = refused)

        vm.onFavoritesReordered(listOf("f-a2-docs", "f-a1-docs"))
        assertThat(vm.state.value.busy).isTrue()
        advanceUntilIdle()

        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.reorderNotice).isEqualTo(ReorderNotice.NotFullyApplied)
    }

    @Test
    fun `a reorder that moved nothing says so`() = runTest(scheduler) {
        val vm = ready()
        vm.onGroupSelected("docs")
        library.reorderOutcome = FavoriteReorderOutcome.NotFullyApplied(applied = 0, planned = 2, error = refused)

        vm.onFavoritesReordered(listOf("f-a2-docs", "f-a1-docs"))
        advanceUntilIdle()

        assertThat(vm.state.value.reorderNotice).isEqualTo(ReorderNotice.NotApplied)
    }

    @Test
    fun `All favourites has no order to change`() = runTest(scheduler) {
        val vm = ready()

        vm.onFavoriteMoved(vm.state.value.visible.last(), -1)
        vm.onFavoritesReordered(vm.state.value.visible.reversed().map { it.favoriteId })
        advanceUntilIdle()

        assertThat(library.reorders).isEmpty()
    }

    // ---- organise ------------------------------------------------------------

    @Test
    fun `organise adds to a group, removes one membership, and refuses the last one`() = runTest(scheduler) {
        val vm = ready()
        val a2InDocs = vm.state.value.visible.first { it.channel.id == "a2" }

        vm.onOrganizeRequested(a2InDocs)
        vm.onMembershipToggled("news", checked = true)
        advanceUntilIdle()
        assertThat(library.adds).containsExactly("a2" to "news")

        // a2 is only in docs on the fake's side: unticking it is refused here.
        vm.onMembershipToggled("docs", checked = false)
        advanceUntilIdle()
        assertThat(library.removedFavorites).isEmpty()
        assertThat(vm.state.value.organizeNotice).isEqualTo(OrganizeNotice.LastGroup)
    }

    @Test
    fun `the default group is never deleted`() = runTest(scheduler) {
        val vm = ready()

        vm.onDeleteRequested(docs)

        assertThat(vm.state.value.deleting).isNull()
    }

    // ---- offline ---------------------------------------------------------------

    @Test
    fun `offline, writes are refused and a read is tried instead - nothing is queued`() = runTest(scheduler) {
        val vm = ready()
        library.writeResult = LumoResult.Failure(lost)
        vm.onRenameRequested(news)
        vm.onRenameConfirmed("Infos")
        advanceUntilIdle()
        assertThat(vm.state.value.offline).isTrue()

        val refreshesBefore = library.refreshes
        library.refreshResult = LumoResult.Failure(lost)
        vm.onRemoveEverywhereRequested(vm.state.value.visible.first())
        advanceUntilIdle()

        assertThat(vm.state.value.removal).isNull()
        assertThat(library.refreshes).isEqualTo(refreshesBefore + 1)

        library.refreshResult = LumoResult.Success(Unit)
        vm.refresh()
        advanceUntilIdle()
        assertThat(vm.state.value.offline).isFalse()
    }

    // ---- helpers -------------------------------------------------------------

    private fun TestScope.ready(): FavoritesViewModel {
        library.groups.value = listOf(docs, news, kids)
        library.favorites.value = listOf(
            favorite("f-a1-docs", "docs", a1, 0),
            favorite("f-b1-docs", "docs", b1, 1),
            favorite("f-a2-docs", "docs", a2, 2),
            favorite("f-a1-kids", "kids", a1, 0),
        )
        val vm = FavoritesViewModel(library, active)
        advanceUntilIdle()
        return vm
    }

    private val refused = LumoError.Api(ErrorCode.INTERNAL_ERROR, detail = null)
    private val lost = LumoError.Offline(IOException("lost"))

    private fun channel(id: String, sourceId: String) = Channel(
        id = id,
        sourceId = sourceId,
        categoryId = null,
        name = "Entry $id",
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun favorite(id: String, groupId: String, channel: Channel, position: Int) =
        FavoriteChannel(favoriteId = id, groupId = groupId, position = position, channel = channel)

    private class FakeLibrary : FavoriteLibrary {
        val groups = MutableStateFlow<List<FavoriteGroup>>(emptyList())
        val favorites = MutableStateFlow<List<FavoriteChannel>>(emptyList())

        var refreshResult: LumoResult<Unit> = LumoResult.Success(Unit)
        var writeResult: LumoResult<Unit> = LumoResult.Success(Unit)
        var reorderOutcome: FavoriteReorderOutcome = FavoriteReorderOutcome.Applied
        val removalOutcomes = ArrayDeque<FavoriteRemovalOutcome>()

        var refreshes = 0
        val adds = mutableListOf<Pair<String, String?>>()
        val removedFavorites = mutableListOf<String>()
        val reorders = mutableListOf<Pair<String, List<String>>>()
        val removals = mutableListOf<Pair<String, Set<String>?>>()

        override fun groups(): Flow<List<FavoriteGroup>> = groups
        override fun favorites(groupId: String?): Flow<List<FavoriteChannel>> = favorites

        override suspend fun refresh(): LumoResult<Unit> {
            refreshes++
            return refreshResult
        }

        override suspend fun add(channelId: String, groupId: String?): LumoResult<Unit> {
            adds += channelId to groupId
            return writeResult
        }

        override suspend fun remove(favoriteId: String): LumoResult<Unit> {
            removedFavorites += favoriteId
            return writeResult
        }

        override suspend fun createGroup(name: String): LumoResult<FavoriteGroup> =
            LumoResult.Success(FavoriteGroup("new", name, position = 3, isDefault = false))

        override suspend fun renameGroup(groupId: String, name: String): LumoResult<Unit> = writeResult
        override suspend fun moveGroup(groupId: String, position: Int): LumoResult<Unit> = writeResult
        override suspend fun deleteGroup(groupId: String): LumoResult<Unit> = writeResult

        override suspend fun reorderVisible(groupId: String, visibleOrder: List<String>): FavoriteReorderOutcome {
            reorders += groupId to visibleOrder
            return reorderOutcome
        }

        override suspend fun removeEverywhere(channelId: String, restrictTo: Set<String>?): FavoriteRemovalOutcome {
            removals += channelId to restrictTo
            return removalOutcomes.removeFirst()
        }
    }

    private class FakeActiveSource : ActiveSourceRepository {
        override val state: StateFlow<ActiveSourceState> = MutableStateFlow(
            ActiveSourceState.Selected("source-a", source = null, sources = emptyList()),
        )
        override val accountId: Flow<String?> = MutableStateFlow("account-1")
        override suspend fun select(sourceId: String) = Unit
        override suspend fun refresh() = Unit
        override suspend fun onSourceGone(sourceId: String) = Unit
    }
}
