package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * What a player does once its source is proven gone (US-024).
 *
 * [PlaybackSourceWatcherTest] holds the proof and the clock. This holds the
 * order of what follows, which is the part a viewer sees: the picture stops
 * **before** the sentence appears, and the active source is re-decided on
 * *Continue* — not before, or the shell's chooser would open over the sentence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSourceGuardTest {

    private var answer: LumoResult<*> = LumoResult.Success(Unit)
    private val activeSource = RecordingActiveSource()
    private val events = mutableListOf<String>()

    private fun guard() = PlaybackSourceGuard(
        watcher = PlaybackSourceWatcher(check = { answer }, reconnections = emptyFlow()),
        activeSource = activeSource,
    )

    @Test
    fun `a proven deletion stops the player, then shows the sentence, and decides nothing yet`() = runTest {
        val guard = guard()
        guard.watch(backgroundScope, "source-a") {
            events += "stopped while deleted=${guard.deleted.value}"
        }

        answer = gone()
        advanceTimeBy(60_000)
        runCurrent()

        assertThat(events).containsExactly("stopped while deleted=false")
        assertThat(guard.deleted.value).isTrue()
        // Not before Continue: the chooser must not cover the explanation.
        assertThat(activeSource.gone).isEmpty()
    }

    @Test
    fun `continue hands the proof to the active source, once`() = runTest {
        val guard = guard()
        guard.watch(backgroundScope, "source-a") {}
        answer = gone()
        advanceTimeBy(60_000)
        runCurrent()

        guard.acknowledge()

        assertThat(activeSource.gone).containsExactly("source-a")
    }

    @Test
    fun `a network failure shows nothing and decides nothing`() = runTest {
        val guard = guard()
        answer = LumoResult.Failure(LumoError.Offline(IOException("no route")))
        guard.watch(backgroundScope, "source-a") { events += "stopped" }

        advanceTimeBy(600_000)
        runCurrent()
        guard.acknowledge()

        assertThat(guard.deleted.value).isFalse()
        assertThat(events).isEmpty()
        assertThat(activeSource.gone).isEmpty()
    }

    @Test
    fun `the next episode of the same source does not restart the clock`() = runTest {
        var checks = 0
        val counting = PlaybackSourceGuard(
            watcher = PlaybackSourceWatcher(
                check = { checks++; LumoResult.Success(Unit) },
                reconnections = emptyFlow(),
            ),
            activeSource = activeSource,
        )

        counting.watch(backgroundScope, "source-a") {}
        advanceTimeBy(45_000)
        // Advancing to the next episode: same source, same watch.
        counting.watch(backgroundScope, "source-a") {}
        advanceTimeBy(15_000)
        runCurrent()

        assertThat(checks).isEqualTo(1)
    }

    @Test
    fun `a player opened without a source watches nothing`() = runTest {
        val guard = guard()
        answer = gone()
        guard.watch(backgroundScope, "") { events += "stopped" }

        advanceTimeBy(120_000)
        runCurrent()

        assertThat(guard.deleted.value).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun `leaving the player ends the watch`() = runTest {
        val guard = guard()
        guard.watch(backgroundScope, "source-a") { events += "stopped" }
        guard.stop()

        answer = gone()
        advanceTimeBy(120_000)
        runCurrent()

        assertThat(guard.deleted.value).isFalse()
        assertThat(events).isEmpty()
    }

    private fun gone() = LumoResult.Failure(LumoError.Api(ErrorCode.SOURCE_NOT_FOUND, detail = null))

    /** Records the one call this guard may make, and fails on the others. */
    private class RecordingActiveSource : ActiveSourceRepository {
        val gone = mutableListOf<String>()

        override val state: StateFlow<ActiveSourceState> = MutableStateFlow(ActiveSourceState.Loading)

        override suspend fun select(sourceId: String): Unit = throw AssertionError("never auto-selects")

        override suspend fun refresh(): Unit = throw AssertionError("a list proves less than the 404 did")

        override suspend fun onSourceGone(sourceId: String) {
            gone += sourceId
        }
    }
}
