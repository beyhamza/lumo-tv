package tv.lumo.android.core.data.internal

import java.time.Duration
import java.time.Instant

/**
 * How a grouped guide request is cut down when the server answers
 * `422 EPG_WINDOW_TOO_LARGE` (C1 D2, C1-11).
 *
 * <h2>Bounded, and the bound is the point</h2>
 *
 * The server refuses a batch that would exceed one of its two ceilings — 5 000
 * programme occurrences or 4 MiB — and tells the client to ask for less at a
 * time. Left to itself a client would halve until something fits, which with a
 * single programme too large for the ceiling never ends. So: **channels first,
 * then the window**, and **at most [MAX_LEVELS] levels** of splitting. Past
 * that the error is shown as an error, with a reduction offered to the user,
 * and no request is made.
 *
 * Channels before the window because a channel batch splits cleanly — each half
 * is a complete answer for its channels — while a window split produces
 * programmes straddling the cut, present in both halves, that have to be merged
 * back by id. Doing the clean cut first means the merge is only needed when a
 * single channel is too large on its own.
 *
 * A pure function of the request, so that the fan-out can be read in a test
 * without a server: [split] says what to ask next, and never asks.
 */
internal object EpgSplitPlanner {

    /** Three levels of halving: one request can become at most eight. */
    const val MAX_LEVELS = 3

    /**
     * One request of the plan.
     *
     * @param level how many splits produced it. The initial request is level 0.
     */
    data class Request(
        val channelIds: List<String>,
        val from: Instant,
        val to: Instant,
        val level: Int = 0,
    )

    /**
     * The two requests to make instead of [request], or null when the plan is
     * exhausted — the level cap is reached, or a single channel's window cannot
     * be halved any further.
     */
    fun split(request: Request): List<Request>? {
        if (request.level >= MAX_LEVELS) return null

        val level = request.level + 1
        val ids = request.channelIds

        if (ids.size > 1) {
            val middle = (ids.size + 1) / 2
            return listOf(
                request.copy(channelIds = ids.subList(0, middle), level = level),
                request.copy(channelIds = ids.subList(middle, ids.size), level = level),
            )
        }

        val half = Duration.between(request.from, request.to).dividedBy(2)
        if (half.isZero || half.isNegative) return null
        val middle = request.from.plus(half)

        return listOf(
            request.copy(to = middle, level = level),
            request.copy(from = middle, level = level),
        )
    }
}
