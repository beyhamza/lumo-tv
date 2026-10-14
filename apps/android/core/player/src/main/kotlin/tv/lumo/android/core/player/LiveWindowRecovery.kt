package tv.lumo.android.core.player

import androidx.media3.common.PlaybackException

/**
 * Whether an error is a live stream that fell behind its window, and should
 * rejoin the edge rather than fail.
 *
 * An HLS live playlist keeps a sliding window of segments. A box that stalls —
 * a slow network, a paused channel left for a minute — can find that the
 * segment it wanted next has already slid out. Media3 reports that as
 * `ERROR_CODE_BEHIND_LIVE_WINDOW`, and before S10B-04 it reached the viewer as
 * "unknown error" on a channel that was perfectly fine: the documented answer
 * is to seek to the default position, which is the edge, and prepare again.
 *
 * Bounded, because a stream whose window is broken would otherwise loop for
 * ever between "behind" and "rejoin". [attempts] counts consecutive recoveries
 * and goes back to zero once the stream is ready again.
 */
internal fun shouldRejoinLiveEdge(errorCode: Int, isLive: Boolean, attempts: Int): Boolean =
    isLive &&
        errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
        attempts < MAX_LIVE_EDGE_REJOINS

/** Consecutive rejoins before the error is shown. Three covers a stall; more is a broken stream. */
internal const val MAX_LIVE_EDGE_REJOINS = 3
