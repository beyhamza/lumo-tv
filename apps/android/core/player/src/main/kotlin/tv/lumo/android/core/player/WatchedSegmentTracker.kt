package tv.lumo.android.core.player

/**
 * One interval of actual playback, in wall-clock milliseconds since the epoch
 * (`adr/0012`, ruling 1).
 *
 * Wall-clock and not media time, on purpose: the server keeps the **union** of the
 * intervals every device sends, so two devices playing during the same twenty
 * seconds count twenty (CW-29), and a save sent twice counts once (CW-33). Media
 * time could do neither — and a seek, which moves media time by twenty minutes in
 * no time at all, would count as twenty minutes watched (CW-04).
 */
data class WatchedSegment(val startedAtMs: Long, val endedAtMs: Long)

/**
 * What [LumoPlayer.takeWatched] hands to a save.
 *
 * @property segments the intervals closed since the previous call, oldest first,
 *   at most [WatchedSegmentTracker.MAX_PER_SAVE].
 * @property sessionStartedAtMs when this stream first actually played, or null
 *   while it never has. Not the moment the screen opened: a launch that failed is
 *   not a start (continue-watching-cases, C3 inputs), and a hidden card comes back
 *   only for a start after the hide (CW-18, CW-31).
 */
data class WatchedTime(
    val segments: List<WatchedSegment> = emptyList(),
    val sessionStartedAtMs: Long? = null,
) {
    companion object {
        val NONE = WatchedTime()
    }
}

/**
 * Records when a player is actually playing, as wall-clock intervals (S12-02).
 *
 * <h2>Only `isPlaying` opens a segment</h2>
 *
 * Paused, buffering, ended, failed, sent to the background — every one of them
 * arrives as "not playing", and every one of them closes the open segment (CW-05).
 * Nothing else here interprets a state: the player says whether frames are being
 * shown, and that is the only question the rule asks.
 *
 * <h2>A seek closes the segment</h2>
 *
 * The interval up to the seek is kept, and a new one starts after it if the player
 * is still playing. The jump itself adds nothing (CW-04) — wall-clock time does not
 * move during it — and closing there keeps one segment from straddling two places
 * in the film, which is what lets a reader of the log believe it.
 *
 * <h2>Flushed at each save</h2>
 *
 * [take] closes the open segment at "now", hands over what is pending, and opens
 * a new segment at the same instant if playback goes on. Nothing is counted twice
 * and nothing between two saves is lost. At most [MAX_PER_SAVE] segments leave per
 * call — the contract's cap — and the rest stay for the next save, oldest first.
 *
 * Pure, with an injectable clock, so that every sequence above is a JVM test.
 */
class WatchedSegmentTracker(private val clock: () -> Long = System::currentTimeMillis) {

    private var openSince: Long? = null
    private var playing = false
    private val pending = ArrayDeque<WatchedSegment>()

    /** When this stream first actually played; see [WatchedTime.sessionStartedAtMs]. */
    var sessionStartedAtMs: Long? = null
        private set

    /** The player's `isPlaying`, each time it changes. Repeats are harmless. */
    fun onPlaying(isPlaying: Boolean) {
        playing = isPlaying
        if (isPlaying) open() else close()
    }

    /** A seek was asked for. Closes the interval; a new one opens if still playing. */
    fun onSeek() {
        close()
        if (playing) open()
    }

    /** A new stream, or none: forgets everything, including the session's start. */
    fun reset() {
        openSince = null
        playing = false
        pending.clear()
        sessionStartedAtMs = null
    }

    /** Closes the open interval at now, and hands over at most [max] pending ones. */
    fun take(max: Int = MAX_PER_SAVE): WatchedTime {
        if (openSince != null) {
            close()
            if (playing) open()
        }
        val out = ArrayList<WatchedSegment>(minOf(max, pending.size))
        while (out.size < max && pending.isNotEmpty()) out += pending.removeFirst()
        return WatchedTime(segments = out, sessionStartedAtMs = sessionStartedAtMs)
    }

    private fun open() {
        if (openSince != null) return
        val now = clock()
        openSince = now
        if (sessionStartedAtMs == null) sessionStartedAtMs = now
    }

    private fun close() {
        val start = openSince ?: return
        openSince = null
        val end = clock()
        // A zero-length interval is nothing watched; a clock that went backwards
        // (the user changed the time) is not an interval either.
        if (end <= start) return
        pending += WatchedSegment(start, end)
        // Bounded: a device that cannot save for an hour of pause-and-play must not
        // grow without end. The oldest go first — the server has the least use for
        // them, the position having moved on since.
        while (pending.size > MAX_PENDING) pending.removeFirst()
    }

    companion object {
        /** `SaveProgressRequest.watched_segments.maxItems`. */
        const val MAX_PER_SAVE = 20

        /** What is kept between saves at most. Five saves' worth. */
        const val MAX_PENDING = 100
    }
}
