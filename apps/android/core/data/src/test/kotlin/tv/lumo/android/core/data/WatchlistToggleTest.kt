package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.data.model.PendingIntention
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.data.model.WatchlistSlice
import tv.lumo.android.core.data.model.WatchlistTarget
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.core.data.model.WatchlistWriteOutcome
import tv.lumo.android.core.data.repository.WatchlistRepository
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * The sheet's "Add to my list" button (US-022, S11-E06): what it sends, with which
 * key, and what it shows after each outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistToggleTest {

    private val repo = FakeWatchlist()

    @Test
    fun `the membership is read before the button does anything`() = runTest {
        val toggle = toggle()
        assertThat(toggle.state.value.membership).isEqualTo(WatchlistMembership.Out)
        assertThat(toggle.state.value.enabled).isTrue()

        repo.member = entry("item-1")
        val other = toggle(contentId = "film-2")
        assertThat(other.state.value.membership).isEqualTo(WatchlistMembership.In("item-1"))
    }

    @Test
    fun `an add then a press on In my list removes, each with its own key`() = runTest {
        val toggle = toggle()

        repo.next = { repo.member = entry("item-1"); WatchlistWriteOutcome.Confirmed(entry("item-1")) }
        toggle.toggle()
        assertThat(toggle.state.value.membership).isEqualTo(WatchlistMembership.In("item-1"))

        repo.next = { repo.member = null; WatchlistWriteOutcome.Confirmed(null) }
        toggle.toggle()
        assertThat(toggle.state.value.membership).isEqualTo(WatchlistMembership.Out)

        val (add, remove) = repo.written
        assertThat(add.action).isEqualTo(WatchlistAction.Add)
        assertThat(remove.action).isEqualTo(WatchlistAction.Remove)
        assertThat(remove.target).isEqualTo(WatchlistTarget.Item("item-1"))
        assertThat(remove.key).isNotEqualTo(add.key)
    }

    @Test
    fun `an unknown result is not a success, re-reads, and its retry reuses the key`() = runTest {
        val toggle = toggle()
        val readsBefore = repo.reads

        repo.next = { WatchlistWriteOutcome.Unknown(LumoError.Offline(IOException("lost"))) }
        toggle.toggle()

        val state = toggle.state.value
        assertThat(state.unconfirmed).isTrue()
        assertThat(state.membership).isEqualTo(WatchlistMembership.Out)
        assertThat(repo.reads).isEqualTo(readsBefore + 1)
        assertThat(state.retryable).isTrue()

        repo.next = { repo.member = entry("item-1"); WatchlistWriteOutcome.Confirmed(entry("item-1")) }
        toggle.retry()

        assertThat(repo.written.map { it.key }.distinct()).hasSize(1)
        assertThat(toggle.state.value.unconfirmed).isFalse()
        assertThat(toggle.state.value.membership).isEqualTo(WatchlistMembership.In("item-1"))
    }

    @Test
    fun `pressing the button again on an unconfirmed add is the same intention`() = runTest {
        val toggle = toggle()
        repo.next = { WatchlistWriteOutcome.Unknown(LumoError.Unreadable(503, null)) }
        toggle.toggle()
        toggle.toggle()

        assertThat(repo.written).hasSize(2)
        assertThat(repo.written[0].key).isEqualTo(repo.written[1].key)
    }

    @Test
    fun `an unknown result the re-read shows in place is settled, and the next press is new`() = runTest {
        val toggle = toggle()

        // The answer was lost, but the server did add it.
        repo.next = { repo.member = entry("item-1"); WatchlistWriteOutcome.Unknown(LumoError.Unreadable(502, null)) }
        toggle.toggle()

        assertThat(toggle.state.value.unconfirmed).isFalse()
        assertThat(toggle.state.value.membership).isEqualTo(WatchlistMembership.In("item-1"))

        repo.next = { repo.member = null; WatchlistWriteOutcome.Confirmed(null) }
        toggle.toggle()
        assertThat(repo.written[1].key).isNotEqualTo(repo.written[0].key)
    }

    @Test
    fun `already in state is silent and re-reads`() = runTest {
        repo.member = entry("item-1")
        val toggle = toggle()

        // Removed elsewhere meanwhile: 404 WATCHLIST_ITEM_NOT_FOUND.
        repo.next = { repo.member = null; WatchlistWriteOutcome.AlreadyInState(null) }
        toggle.toggle()

        val state = toggle.state.value
        assertThat(state.membership).isEqualTo(WatchlistMembership.Out)
        assertThat(state.unconfirmed).isFalse()
        assertThat(state.rejected).isFalse()
    }

    @Test
    fun `a refusal is said, and the next press is a new intention`() = runTest {
        val toggle = toggle()
        repo.next = { WatchlistWriteOutcome.Rejected(LumoError.Api(ErrorCode.VOD_ITEM_NOT_FOUND, null)) }
        toggle.toggle()
        assertThat(toggle.state.value.rejected).isTrue()

        repo.next = { repo.member = entry("item-1"); WatchlistWriteOutcome.Confirmed(entry("item-1")) }
        toggle.toggle()
        assertThat(repo.written[1].key).isNotEqualTo(repo.written[0].key)
        assertThat(toggle.state.value.rejected).isFalse()
    }

    @Test
    fun `offline is read-only, and a reconnection re-reads before enabling`() = runTest {
        repo.readFailure = LumoError.Offline(IOException("no network"))
        val toggle = toggle()

        assertThat(toggle.state.value.offline).isTrue()
        assertThat(toggle.state.value.enabled).isFalse()
        toggle.toggle()
        assertThat(repo.written).isEmpty()

        repo.readFailure = null
        repo.reconnections.emit(Unit)

        assertThat(toggle.state.value.offline).isFalse()
        assertThat(toggle.state.value.enabled).isTrue()
    }

    // ---- helpers -------------------------------------------------------------

    private fun TestScope.toggle(contentId: String = "film-1"): WatchlistToggle =
        WatchlistToggle(repo, TestScope(UnconfinedTestDispatcher(testScheduler)))
            .also { it.start("source-1", WatchlistType.Film, contentId) }

    private fun entry(id: String) = WatchlistEntry(
        id = id,
        sourceId = "source-1",
        type = WatchlistType.Film,
        contentId = "film-1",
        available = true,
        title = "Titre",
        posterUrl = null,
        addedAt = Instant.EPOCH,
    )

    private class FakeWatchlist : WatchlistRepository {
        var member: WatchlistEntry? = null
        var readFailure: LumoError? = null
        var next: () -> WatchlistWriteOutcome = { WatchlistWriteOutcome.Confirmed(null) }
        val written = mutableListOf<PendingIntention>()
        var reads = 0
        override val reconnections = MutableSharedFlow<Unit>()

        override suspend fun page(sourceId: String, type: WatchlistType?, page: Int, size: Int) =
            LumoResult.Success(WatchlistSlice(listOfNotNull(member), 0, 1, 0, emptySet()))

        override suspend fun find(sourceId: String, type: WatchlistType, contentId: String): LumoResult<WatchlistEntry?> {
            reads++
            readFailure?.let { return LumoResult.Failure(it) }
            return LumoResult.Success(member)
        }

        override suspend fun write(intention: PendingIntention): WatchlistWriteOutcome {
            written += intention
            return next()
        }
    }
}
