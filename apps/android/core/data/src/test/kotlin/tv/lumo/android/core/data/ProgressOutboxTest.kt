package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.data.internal.PendingSave
import tv.lumo.android.core.data.internal.ProgressKey
import tv.lumo.android.core.data.internal.ProgressOutbox
import tv.lumo.android.core.data.internal.coalesce
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.ProgressItemType

/**
 * S12-05: a failed save keeps its watched segments, and they go exactly once from
 * this side; a newer save supersedes the position and keeps every pending segment.
 */
class ProgressOutboxTest {

    private val film = ProgressKey("source", ProgressItemType.VOD, "film")
    private val other = ProgressKey("source", ProgressItemType.EPISODE, "episode")

    /** A server that records what it was sent, and fails while [online] is false. */
    private class FakeServer {
        var online = true
        var error: LumoError = LumoError.Offline(IOException("offline"))
        val received = mutableListOf<PendingSave>()

        suspend fun send(save: PendingSave): LumoResult<Unit> =
            if (online) {
                received += save
                LumoResult.Success(Unit)
            } else {
                LumoResult.Failure(error)
            }
    }

    private suspend fun ProgressOutbox.save(
        server: FakeServer,
        positionMs: Long,
        observedAtMs: Long,
        segments: List<Pair<Long, Long>>,
        key: ProgressKey = film,
        sessionStartedAtMs: Long? = 1_000L,
    ) = submit(key, positionMs, 6_000_000L, observedAtMs, sessionStartedAtMs, segments, server::send)

    @Test
    fun `a failed save is resent once on reconnection, and not again afterwards`() = runTest {
        val server = FakeServer().apply { online = false }
        val outbox = ProgressOutbox()

        val failed = outbox.save(server, positionMs = 30_000, observedAtMs = 31_000, segments = listOf(1_000L to 31_000L))
        assertThat(failed).isInstanceOf(LumoResult.Failure::class.java)
        assertThat(outbox.pending()).hasSize(1)

        server.online = true
        outbox.flush(server::send)
        outbox.flush(server::send)

        assertThat(server.received).hasSize(1)
        val resent = server.received.single()
        assertThat(resent.segments).containsExactly(1_000L to 31_000L)
        // The original reading, not the time of the resend (CW-32).
        assertThat(resent.observedAtMs).isEqualTo(31_000L)
        assertThat(resent.positionMs).isEqualTo(30_000L)
        assertThat(outbox.pending()).isEmpty()

        // The next save carries only its own segments.
        outbox.save(server, positionMs = 60_000, observedAtMs = 61_000, segments = listOf(40_000L to 61_000L))
        assertThat(server.received.last().segments).containsExactly(40_000L to 61_000L)
    }

    @Test
    fun `a newer save supersedes the pending position and keeps every pending segment`() = runTest {
        val server = FakeServer().apply { online = false }
        val outbox = ProgressOutbox()

        outbox.save(server, positionMs = 30_000, observedAtMs = 31_000, segments = listOf(1_000L to 31_000L))
        outbox.save(server, positionMs = 50_000, observedAtMs = 61_000, segments = listOf(41_000L to 61_000L))

        val pending = outbox.pending().single()
        assertThat(pending.positionMs).isEqualTo(50_000L)
        assertThat(pending.observedAtMs).isEqualTo(61_000L)
        assertThat(pending.segments).containsExactly(1_000L to 31_000L, 41_000L to 61_000L).inOrder()

        server.online = true
        outbox.save(server, positionMs = 80_000, observedAtMs = 91_000, segments = listOf(61_000L to 91_000L))

        // One request: the touching segments joined, the latest reading.
        val sent = server.received.single()
        assertThat(sent.positionMs).isEqualTo(80_000L)
        assertThat(sent.observedAtMs).isEqualTo(91_000L)
        assertThat(sent.segments).containsExactly(1_000L to 31_000L, 41_000L to 91_000L).inOrder()
        assertThat(outbox.pending()).isEmpty()
    }

    @Test
    fun `an older reading arriving late does not take the position back`() = runTest {
        val server = FakeServer().apply { online = false }
        val outbox = ProgressOutbox()

        outbox.save(server, positionMs = 50_000, observedAtMs = 61_000, segments = emptyList())
        outbox.save(server, positionMs = 30_000, observedAtMs = 31_000, segments = listOf(1_000L to 31_000L))

        val pending = outbox.pending().single()
        assertThat(pending.positionMs).isEqualTo(50_000L)
        assertThat(pending.segments).containsExactly(1_000L to 31_000L)
    }

    @Test
    fun `a save that goes through also sends the other items pending`() = runTest {
        val server = FakeServer().apply { online = false }
        val outbox = ProgressOutbox()

        outbox.save(server, positionMs = 30_000, observedAtMs = 31_000, segments = listOf(1_000L to 31_000L), key = other)
        server.online = true
        outbox.save(server, positionMs = 10_000, observedAtMs = 100_000, segments = listOf(90_000L to 100_000L))

        assertThat(server.received.map { it.key }).containsExactly(film, other).inOrder()
        assertThat(outbox.pending()).isEmpty()
    }

    @Test
    fun `more than twenty segments go in several requests, none twice`() = runTest {
        val server = FakeServer().apply { online = false }
        val outbox = ProgressOutbox()
        // 25 separate intervals: paused a second between each.
        val segments = (0 until 25).map { i -> (i * 10_000L) to (i * 10_000L + 9_000L) }

        outbox.save(server, positionMs = 250_000, observedAtMs = 300_000, segments = segments)
        server.online = true
        outbox.flush(server::send)

        assertThat(server.received.map { it.segments.size }).containsExactly(20, 5).inOrder()
        assertThat(server.received.flatMap { it.segments }).containsExactlyElementsIn(segments).inOrder()
        assertThat(server.received.map { it.observedAtMs }.toSet()).containsExactly(300_000L)
    }

    @Test
    fun `a refusal a resend cannot fix is dropped`() = runTest {
        val server = FakeServer().apply {
            online = false
            error = LumoError.Api(ErrorCode.SOURCE_NOT_FOUND, detail = null)
        }
        val outbox = ProgressOutbox()

        outbox.save(server, positionMs = 30_000, observedAtMs = 31_000, segments = listOf(1_000L to 31_000L))

        assertThat(outbox.pending()).isEmpty()
    }

    @Test
    fun `a server error is kept`() = runTest {
        val server = FakeServer().apply {
            online = false
            error = LumoError.Unreadable(status = 503, cause = null)
        }
        val outbox = ProgressOutbox()

        outbox.save(server, positionMs = 30_000, observedAtMs = 31_000, segments = listOf(1_000L to 31_000L))

        assertThat(outbox.pending()).hasSize(1)
    }

    @Test
    fun `pending segments are bounded per item, the oldest dropped`() = runTest {
        val server = FakeServer().apply { online = false }
        val outbox = ProgressOutbox()
        val many = (0 until ProgressOutbox.MAX_PENDING_SEGMENTS + 10).map { i -> (i * 10L) to (i * 10L + 5L) }

        outbox.save(server, positionMs = 1, observedAtMs = 1, segments = many)

        val kept = outbox.pending().single().segments
        assertThat(kept).hasSize(ProgressOutbox.MAX_PENDING_SEGMENTS)
        assertThat(kept.last()).isEqualTo(many.last())
    }

    @Test
    fun `a missing observation is stamped once and kept`() = runTest {
        val server = FakeServer().apply { online = false }
        var clock = 5_000L
        val outbox = ProgressOutbox(now = { clock })

        outbox.submit(film, 1_000L, null, null, null, emptyList(), server::send)
        clock = 9_000L
        server.online = true
        outbox.flush(server::send)

        assertThat(server.received.single().observedAtMs).isEqualTo(5_000L)
    }

    @Test
    fun `coalesce joins overlapping and touching intervals and drops empty ones`() {
        assertThat(coalesce(listOf(20L to 30L, 0L to 10L, 10L to 15L, 25L to 40L, 50L to 50L)))
            .containsExactly(0L to 15L, 20L to 40L).inOrder()
    }
}
