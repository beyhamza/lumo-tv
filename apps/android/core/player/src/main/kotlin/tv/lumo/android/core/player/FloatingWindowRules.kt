package tv.lumo.android.core.player

/**
 * The floating window's decisions, without the Activity (S15-01/04, ADR 0013).
 *
 * The platform part — parameters, remote actions, the mode-change callback —
 * is in `ui/FloatingWindow.kt`, and needs a device to run. Everything it decides
 * is here, as functions a JVM test can hold.
 *
 * <h2>Leaving opens a window only over something playing</h2>
 *
 * HOME during a playback is "keep it in a corner"; HOME over a paused film, a
 * failure or a spinner that never ended is "I am done", and a window there would
 * be a black square to swipe away. So the window opens on leaving only when:
 *
 * - the setting "Floating window when leaving the application" is on (S15-02);
 * - the device has a window and the user has not refused it in Android's settings;
 * - the stream is playing, or loading on its way to playing.
 *
 * Otherwise S10B-03 applies unchanged: the screen stops, playback pauses
 * ([BackgroundPlayback]).
 *
 * The player's own "Floating window" button is not subject to the setting: the
 * setting is about leaving, the button is somebody asking.
 */
object FloatingWindowRules {

    fun enterOnLeave(settingOn: Boolean, available: Boolean, playing: Boolean): Boolean =
        settingOn && available && playing

    /** Playing, or loading on its way to playing: what [BackgroundPlayback] would pause. */
    fun isPlaying(state: PlaybackState): Boolean =
        state is PlaybackState.Playing || state is PlaybackState.Buffering

    /**
     * The window's buttons, left to right.
     *
     * One toggle rather than two buttons: the window is a few centimetres wide and
     * the system allows only a handful of actions. A channel adds *Direct*, which
     * rejoins the live edge after a pause — the only way back to "now" from a
     * window with no seek bar.
     */
    fun actions(live: Boolean, playing: Boolean): List<FloatingWindowAction> = buildList {
        add(if (playing) FloatingWindowAction.PAUSE else FloatingWindowAction.PLAY)
        if (live) add(FloatingWindowAction.LIVE)
    }

    /**
     * How the window ended, from the Activity's state when the platform says it did.
     *
     * Expanding it brings the Activity back in front: it is still started. Closing
     * it (the cross, or a swipe away) stops the Activity first — so a window that
     * ends on a stopped Activity was closed, and the playback is stopped and its
     * position saved.
     */
    fun exitOf(activityStarted: Boolean): FloatingWindowExit =
        if (activityStarted) FloatingWindowExit.EXPANDED else FloatingWindowExit.CLOSED

    /**
     * The window's shape, clamped to what the platform accepts.
     *
     * Android refuses a ratio outside 1:2.39 to 2.39:1 with an exception, and a
     * stream reports whatever its encoder wrote — including a 0×0 before the first
     * frame. Unknown or degenerate is 16:9, what nearly everything is.
     */
    fun aspectRatio(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return DEFAULT_RATIO
        val ratio = width.toFloat() / height
        return when {
            ratio > MAX_RATIO -> MAX_RATIO_FRACTION
            ratio < 1f / MAX_RATIO -> MIN_RATIO_FRACTION
            else -> width to height
        }
    }

    private const val MAX_RATIO = 2.39f
    private val DEFAULT_RATIO = 16 to 9
    private val MAX_RATIO_FRACTION = 239 to 100
    private val MIN_RATIO_FRACTION = 100 to 239
}

/** A button of the floating window. */
enum class FloatingWindowAction { PLAY, PAUSE, LIVE }

/** How a floating window ended. */
enum class FloatingWindowExit { EXPANDED, CLOSED }
