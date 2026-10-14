package tv.lumo.android.core.player

/**
 * What playback does when the app leaves the screen, and when it comes back.
 *
 * Before S10B-03 the answer was "nothing": the six player screens stopped the
 * stream only when they were disposed, and HOME does not dispose anything. The
 * player is a process-wide singleton holding a network wake lock, so the
 * sound carried on behind the launcher — on a television, behind whatever the
 * family switched to.
 *
 * One object per player screen's ViewModel, so the rule is written once:
 *
 * - **Leaving:** a stream that is playing, or loading on its way to playing, is
 *   paused. One the viewer had already paused is left alone, and remembered as
 *   such, so coming back does not start what they stopped.
 * - **Coming back, live:** a channel paused by leaving resumes at the live edge.
 *   Somebody returning to the news wants the news now, not a recording of the
 *   minute they left.
 * - **Coming back, film or episode:** stays paused, where it was. Resuming on
 *   its own would play the next minute to a room that may still be empty.
 */
class BackgroundPlayback(private val player: LumoPlayer) {

    private var pausedByBackground = false

    /**
     * The screen is no longer visible.
     *
     * @return whether playback was paused now — the moment a caller saves a
     *         position, since this is the most likely last one of the evening.
     */
    fun onBackground(): Boolean {
        val state = player.state.value
        if (state !is PlaybackState.Playing && state !is PlaybackState.Buffering) return false
        player.pause()
        pausedByBackground = true
        return true
    }

    /**
     * The screen is visible again.
     *
     * @param live a channel resumes at its live edge; anything else stays paused.
     */
    fun onForeground(live: Boolean) {
        if (!pausedByBackground) return
        pausedByBackground = false
        if (live) player.resumeAtLiveEdge()
    }

    /** The stream was stopped or replaced: a pause from leaving no longer describes anything. */
    fun reset() {
        pausedByBackground = false
    }
}
