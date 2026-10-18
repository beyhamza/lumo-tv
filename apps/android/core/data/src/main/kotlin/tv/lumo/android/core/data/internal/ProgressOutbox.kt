package tv.lumo.android.core.data.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.ProgressItemType

/**
 * Which progress row a save writes: the key `PUT /me/progress` upserts on.
 */
internal data class ProgressKey(val sourceId: String, val type: ProgressItemType, val ref: String)

/**
 * One save, as it is kept until the server has it (S12-05, carry-over of S12-02).
 *
 * @property observedAtMs when the position was read — **the original reading**, kept
 *   through every resend: the server keeps the latest observation, and a resend
 *   stamped with its own sending time would win over a newer reading made on
 *   another device in the meantime (CW-32, `adr/0012` ruling 2).
 * @property segments wall-clock intervals of actual playback not yet acknowledged,
 *   sorted and coalesced. Resending one is harmless: the server keeps a union
 *   (`adr/0012` ruling 1, CW-33).
 */
internal data class PendingSave(
    val key: ProgressKey,
    val positionMs: Long,
    val durationMs: Long?,
    val observedAtMs: Long,
    val sessionStartedAtMs: Long?,
    val segments: List<Pair<Long, Long>>,
)

/**
 * Keeps the latest unsent save of each item in memory, and sends it again (S12-05).
 *
 * <h2>What it fixes</h2>
 *
 * A player hands each save the watched segments **since the previous save**, and
 * forgets them. A save lost to a tunnel used to take those seconds with it: thirty
 * seconds watched offline never counted towards the card (CW-01, CW-17).
 *
 * <h2>The rule</h2>
 *
 * - A failed save stays here, per item. The next save of that item **supersedes
 *   its position** (the most recent reading) and **keeps all its segments**; any
 *   save that goes through also sends the others; so does the network coming back.
 * - At most [MAX_SEGMENTS_PER_SAVE] segments per request, the contract's bound: more
 *   are sent in several requests carrying the same position and observation.
 * - Bounded: [MAX_PENDING_SEGMENTS] segments per item (the oldest go first) and
 *   [MAX_PENDING_ITEMS] items (the oldest observation goes first). Memory only, no
 *   table: a killed process loses what was pending, as it did before.
 * - A refusal that sending again cannot change — a validation error, a source that
 *   no longer exists — is dropped rather than retried forever.
 *
 * Everything runs under one lock, sending included, so that a reconnection and a
 * save arriving together cannot send the same segments twice from this side. The
 * server's union would absorb it anyway; the lock is about not *relying* on that.
 */
internal class ProgressOutbox(private val now: () -> Long = System::currentTimeMillis) {

    private val lock = Mutex()
    private val pending = LinkedHashMap<ProgressKey, PendingSave>()

    /** What is waiting, for tests and nothing else. */
    suspend fun pending(): List<PendingSave> = lock.withLock { pending.values.toList() }

    /**
     * Sends [save], merged with whatever its item still had pending, then anything
     * else pending once the network has proved it works.
     *
     * @param observedAtMs null for a caller that did not read the clock: stamped now,
     *   once, and kept through every resend.
     */
    suspend fun submit(
        key: ProgressKey,
        positionMs: Long,
        durationMs: Long?,
        observedAtMs: Long?,
        sessionStartedAtMs: Long?,
        segments: List<Pair<Long, Long>>,
        send: suspend (PendingSave) -> LumoResult<Unit>,
    ): LumoResult<Unit> = lock.withLock {
        val save = PendingSave(
            key = key,
            positionMs = positionMs,
            durationMs = durationMs,
            observedAtMs = observedAtMs ?: now(),
            sessionStartedAtMs = sessionStartedAtMs,
            segments = coalesce(segments),
        )
        keep(pending[key]?.let { merge(it, save) } ?: save)

        val result = deliver(key, send)
        if (result is LumoResult.Success) deliverAll(send)
        result
    }

    /** The network came back: everything pending, once. */
    suspend fun flush(send: suspend (PendingSave) -> LumoResult<Unit>) = lock.withLock { deliverAll(send) }

    private suspend fun deliverAll(send: suspend (PendingSave) -> LumoResult<Unit>) {
        for (key in pending.keys.toList()) {
            // Offline for one is offline for all: stop at the first failure.
            if (deliver(key, send) is LumoResult.Failure) return
        }
    }

    /**
     * Sends what [key] has pending, in chunks of [MAX_SEGMENTS_PER_SAVE] segments.
     *
     * The entry stays in [pending] **while** it is being sent and leaves only what the
     * server acknowledged: a save cancelled mid-flight — the screen that launched it
     * was closed — is still here for the next one.
     */
    private suspend fun deliver(key: ProgressKey, send: suspend (PendingSave) -> LumoResult<Unit>): LumoResult<Unit> {
        while (true) {
            val current = pending[key] ?: return LumoResult.Success(Unit)
            val chunk = current.copy(segments = current.segments.take(MAX_SEGMENTS_PER_SAVE))
            val result = send(chunk)
            if (result is LumoResult.Failure) {
                if (!result.error.worthResending()) pending.remove(key)
                return result
            }
            // Re-read: nothing else can write under the lock, but say so in code.
            val after = pending[key] ?: return result
            val left = after.segments.drop(chunk.segments.size)
            if (left.isEmpty()) {
                pending.remove(key)
                return result
            }
            pending[key] = after.copy(segments = left)
        }
    }

    private fun keep(save: PendingSave) {
        pending[save.key] = save.copy(segments = save.segments.takeLast(MAX_PENDING_SEGMENTS))
        while (pending.size > MAX_PENDING_ITEMS) {
            val oldest = pending.values.minBy { it.observedAtMs }
            pending.remove(oldest.key)
        }
    }

    internal companion object {
        /** `SaveProgressRequest.watched_segments.maxItems`. */
        const val MAX_SEGMENTS_PER_SAVE = 20

        /** Per item: hours of offline playback, at one segment per thirty-second save. */
        const val MAX_PENDING_SEGMENTS = 200

        /** Items with something pending. Far more than one evening offline produces. */
        const val MAX_PENDING_ITEMS = 50
    }
}

/**
 * [newer] supersedes [older]'s position — the most recent reading wins, as on the
 * server — and every segment of both is kept.
 */
internal fun merge(older: PendingSave, newer: PendingSave): PendingSave {
    val (first, last) = if (newer.observedAtMs >= older.observedAtMs) older to newer else newer to older
    return last.copy(
        sessionStartedAtMs = last.sessionStartedAtMs ?: first.sessionStartedAtMs,
        segments = coalesce(older.segments + newer.segments),
    )
}

/**
 * Sorted, with overlapping or touching intervals joined. Safe to do here because
 * the server keeps a union: joining changes the number of segments, never the time
 * they cover. Empty or reversed intervals are dropped.
 */
internal fun coalesce(segments: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
    val sorted = segments.filter { (start, end) -> end > start }.sortedBy { it.first }
    val out = ArrayList<Pair<Long, Long>>(sorted.size)
    for (segment in sorted) {
        val last = out.lastOrNull()
        if (last != null && segment.first <= last.second) {
            out[out.lastIndex] = last.first to maxOf(last.second, segment.second)
        } else {
            out += segment
        }
    }
    return out
}

/**
 * Whether sending the same save again can succeed: the network, the server having
 * a bad moment, a rate limit. Anything the server refused on its merits cannot.
 */
internal fun LumoError.worthResending(): Boolean = when (this) {
    is LumoError.Offline -> true
    is LumoError.Unreadable -> status == null || status >= SERVER_ERROR
    is LumoError.Api -> code == ErrorCode.INTERNAL_ERROR || code == ErrorCode.RATE_LIMITED
    is LumoError.UnknownCode -> false
}

private const val SERVER_ERROR = 500
