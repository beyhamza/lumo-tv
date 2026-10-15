package tv.lumo.android.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The time actually watched (S12-02, `adr/0012` ruling 1): pause, buffering, seek,
 * end and background are not watching; a save flushes; the contract caps at 20.
 */
class WatchedSegmentTrackerTest {

    private var now = 1_000_000L
    private val tracker = WatchedSegmentTracker { now }

    private fun advance(ms: Long) {
        now += ms
    }

    @Test
    fun `playing then pausing is one segment, and nothing before the first frame`() {
        advance(5_000) // opening, buffering: not watching
        tracker.onPlaying(true)
        val start = now
        advance(10_000)
        tracker.onPlaying(false)
        advance(60_000) // paused: CW-05

        val taken = tracker.take()
        assertEquals(listOf(WatchedSegment(start, start + 10_000)), taken.segments)
        assertEquals(start, taken.sessionStartedAtMs)
    }

    @Test
    fun `buffering in the middle splits the interval and is not counted`() {
        tracker.onPlaying(true)
        val a = now
        advance(8_000)
        tracker.onPlaying(false) // rebuffering
        advance(4_000)
        tracker.onPlaying(true)
        val b = now
        advance(6_000)
        tracker.onPlaying(false)

        val segments = tracker.take().segments
        assertEquals(listOf(WatchedSegment(a, a + 8_000), WatchedSegment(b, b + 6_000)), segments)
        assertEquals(14_000L, segments.sumOf { it.endedAtMs - it.startedAtMs })
    }

    @Test
    fun `a seek adds nothing and splits the open interval`() {
        tracker.onPlaying(true)
        val a = now
        advance(5_000)
        tracker.onSeek() // to twenty minutes in: CW-04
        advance(3_000)
        tracker.onPlaying(false)

        val segments = tracker.take().segments
        assertEquals(listOf(WatchedSegment(a, a + 5_000), WatchedSegment(a + 5_000, a + 8_000)), segments)
        assertEquals(8_000L, segments.sumOf { it.endedAtMs - it.startedAtMs })
    }

    @Test
    fun `a seek while paused opens nothing`() {
        tracker.onPlaying(true)
        advance(2_000)
        tracker.onPlaying(false)
        tracker.onSeek()
        advance(30_000)

        assertEquals(1, tracker.take().segments.size)
    }

    @Test
    fun `the end and the background close the segment like a pause`() {
        tracker.onPlaying(true)
        advance(7_000)
        tracker.onPlaying(false) // STATE_ENDED, or ON_STOP pausing the player
        advance(120_000)

        val segments = tracker.take().segments
        assertEquals(1, segments.size)
        assertEquals(7_000L, segments[0].endedAtMs - segments[0].startedAtMs)
    }

    @Test
    fun `a save flushes the open segment and keeps measuring after it`() {
        tracker.onPlaying(true)
        val a = now
        advance(30_000)
        val first = tracker.take()
        assertEquals(listOf(WatchedSegment(a, a + 30_000)), first.segments)

        advance(12_000)
        val second = tracker.take()
        // Continues from the flush, so the seam is neither lost nor counted twice.
        assertEquals(listOf(WatchedSegment(a + 30_000, a + 42_000)), second.segments)
        assertEquals(a, second.sessionStartedAtMs)
    }

    @Test
    fun `a save with nothing watched sends nothing and no session start`() {
        val taken = tracker.take()
        assertTrue(taken.segments.isEmpty())
        assertNull(taken.sessionStartedAtMs)
    }

    @Test
    fun `at most twenty per save, the rest kept oldest first`() {
        repeat(25) {
            tracker.onPlaying(true)
            advance(1_000)
            tracker.onPlaying(false)
            advance(500)
        }

        val first = tracker.take()
        assertEquals(WatchedSegmentTracker.MAX_PER_SAVE, first.segments.size)
        val second = tracker.take()
        assertEquals(5, second.segments.size)
        assertTrue(first.segments.last().endedAtMs <= second.segments.first().startedAtMs)
        assertTrue(tracker.take().segments.isEmpty())
    }

    @Test
    fun `pending intervals are bounded`() {
        repeat(WatchedSegmentTracker.MAX_PENDING + 10) {
            tracker.onPlaying(true)
            advance(1_000)
            tracker.onPlaying(false)
        }
        var total = 0
        while (true) {
            val batch = tracker.take().segments
            if (batch.isEmpty()) break
            total += batch.size
        }
        assertEquals(WatchedSegmentTracker.MAX_PENDING, total)
    }

    @Test
    fun `reset forgets the stream and its session start`() {
        tracker.onPlaying(true)
        advance(10_000)
        tracker.reset()
        advance(10_000)

        val taken = tracker.take()
        assertTrue(taken.segments.isEmpty())
        assertNull(taken.sessionStartedAtMs)

        tracker.onPlaying(true)
        assertEquals(now, tracker.sessionStartedAtMs)
    }

    @Test
    fun `the session start is the first real play, not a later resume`() {
        tracker.onPlaying(true)
        val first = now
        advance(1_000)
        tracker.onPlaying(false)
        advance(60_000)
        tracker.onPlaying(true)
        advance(1_000)

        assertEquals(first, tracker.take().sessionStartedAtMs)
    }

    @Test
    fun `repeated playing reports do not restart the interval`() {
        tracker.onPlaying(true)
        val a = now
        advance(3_000)
        tracker.onPlaying(true)
        advance(3_000)
        tracker.onPlaying(false)

        assertEquals(listOf(WatchedSegment(a, a + 6_000)), tracker.take().segments)
    }

    @Test
    fun `a clock that goes backwards produces no interval`() {
        tracker.onPlaying(true)
        advance(-5_000)
        tracker.onPlaying(false)

        assertTrue(tracker.take().segments.isEmpty())
    }
}
