package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * A source deleted elsewhere while something plays (US-024, C4 decision D5).
 *
 * Two halves, as in the code. **The decision**: one answer in the world may stop
 * playback, and every other one — a train tunnel included — must not. **The
 * timing**: every sixty seconds, plus the foreground and the network coming back,
 * on a virtual clock so that "sixty seconds" is asserted rather than slept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSourceWatcherTest {

    // ---- the decision --------------------------------------------------------

    @Test
    fun `only a 404 naming the source proves it was deleted`() {
        assertThat(api(ErrorCode.SOURCE_NOT_FOUND).asSourcePresence()).isEqualTo(SourcePresence.Deleted)
    }

    @Test
    fun `a source that answers is present`() {
        assertThat(LumoResult.Success(benchSource("Source A")).asSourcePresence())
            .isEqualTo(SourcePresence.Present)
    }

    @Test
    fun `a network error, a timeout, a 5xx and the unknown prove nothing`() {
        val inconclusive = listOf(
            LumoResult.Failure(LumoError.Offline(IOException("timeout"))),
            api(ErrorCode.INTERNAL_ERROR),
            api(ErrorCode.UNAUTHENTICATED),
            api(ErrorCode.RATE_LIMITED),
            // Another resource being gone says nothing about the source.
            api(ErrorCode.CHANNEL_NOT_FOUND),
            LumoResult.Failure(LumoError.UnknownCode("SOMETHING_NEWER")),
            LumoResult.Failure(LumoError.Unreadable(status = 502, cause = null)),
        )

        inconclusive.forEach { result ->
            assertThat(result.asSourcePresence()).isEqualTo(SourcePresence.Unknown)
        }
    }

    // ---- the timing ----------------------------------------------------------

    @Test
    fun `the source is checked every sixty seconds, and not before`() = runTest {
        val server = ScriptedPresence()
        val watching = watch(server)

        advanceTimeBy(59_999)
        runCurrent()
        assertThat(server.checks).isEqualTo(0)

        advanceTimeBy(1)
        runCurrent()
        assertThat(server.checks).isEqualTo(1)

        advanceTimeBy(120_000)
        runCurrent()
        assertThat(server.checks).isEqualTo(3)
        assertThat(watching.deleted).isFalse()
    }

    @Test
    fun `a proven deletion ends the watch, and nothing is asked afterwards`() = runTest {
        val server = ScriptedPresence()
        val watching = watch(server)

        advanceTimeBy(60_000)
        runCurrent()
        assertThat(watching.deleted).isFalse()

        server.answer = api(ErrorCode.SOURCE_NOT_FOUND)
        advanceTimeBy(60_000)
        runCurrent()

        assertThat(watching.deleted).isTrue()
        val checksAtDeletion = server.checks

        advanceTimeBy(600_000)
        runCurrent()
        assertThat(server.checks).isEqualTo(checksAtDeletion)
    }

    @Test
    fun `an unreachable server never stops playback, however long it lasts`() = runTest {
        val server = ScriptedPresence(LumoResult.Failure(LumoError.Offline(IOException("no route"))))
        val watching = watch(server)

        advanceTimeBy(3_600_000)
        runCurrent()

        assertThat(server.checks).isEqualTo(60)
        assertThat(watching.deleted).isFalse()
    }

    @Test
    fun `a deletion confirmed after the network returns does stop playback`() = runTest {
        val server = ScriptedPresence(LumoResult.Failure(LumoError.Offline(IOException("no route"))))
        val reconnections = MutableSharedFlow<Unit>()
        val watching = watch(server, reconnections = reconnections)

        advanceTimeBy(90_000)
        runCurrent()
        assertThat(watching.deleted).isFalse()

        // The network is back, and the server can finally say it.
        server.answer = api(ErrorCode.SOURCE_NOT_FOUND)
        reconnections.emit(Unit)
        runCurrent()

        assertThat(watching.deleted).isTrue()
    }

    @Test
    fun `coming back to the foreground checks at once, then the clock starts again`() = runTest {
        val server = ScriptedPresence()
        val foregrounds = MutableSharedFlow<Unit>()
        watch(server, foregrounds = foregrounds)

        advanceTimeBy(20_000)
        foregrounds.emit(Unit)
        runCurrent()
        assertThat(server.checks).isEqualTo(1)

        // Sixty seconds from that check, not from the opening.
        advanceTimeBy(59_999)
        runCurrent()
        assertThat(server.checks).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(server.checks).isEqualTo(2)
    }

    @Test
    fun `leaving the player stops the questions`() = runTest {
        val server = ScriptedPresence()
        val watching = watch(server)

        advanceTimeBy(60_000)
        runCurrent()
        watching.job.cancel()

        advanceTimeBy(600_000)
        runCurrent()
        assertThat(server.checks).isEqualTo(1)
    }

    // ---- helpers -----------------------------------------------------------

    private class ScriptedPresence(
        var answer: LumoResult<*> = LumoResult.Success(Unit),
    ) {
        var checks = 0
            private set

        fun check(): LumoResult<*> {
            checks++
            return answer
        }
    }

    private class Watching(val job: kotlinx.coroutines.Job) {
        var deleted = false
    }

    private fun TestScope.watch(
        server: ScriptedPresence,
        reconnections: MutableSharedFlow<Unit> = MutableSharedFlow(),
        foregrounds: MutableSharedFlow<Unit> = MutableSharedFlow(),
    ): Watching {
        val watcher = PlaybackSourceWatcher(
            check = { server.check() },
            reconnections = reconnections,
        )
        lateinit var watching: Watching
        val job = backgroundScope.launch {
            watcher.awaitDeletion("source-a", foregrounds)
            watching.deleted = true
        }
        watching = Watching(job)
        // Lets the watcher subscribe to the two flows before a test emits.
        runCurrent()
        return watching
    }

    private fun api(code: ErrorCode) = LumoResult.Failure(LumoError.Api(code, detail = null))
}
