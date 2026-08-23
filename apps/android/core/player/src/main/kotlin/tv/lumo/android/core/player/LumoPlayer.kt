package tv.lumo.android.core.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Playback, with no opinion about the shape of the device.
 *
 * The phone and the television share this interface and its implementation; only
 * the controls around it differ (touch versus D-pad, docs/architecture.md §3).
 * Nothing outside this module imports `androidx.media3` — the Compose surface
 * that draws the video lives here too, for exactly that reason. That is what
 * would make replacing the engine a change to one module rather than to every
 * screen that plays something.
 */
interface LumoPlayer {

    val state: StateFlow<PlaybackState>

    /** Loads a stream and starts playing it. Replaces whatever was playing. */
    fun play(request: PlaybackRequest)

    fun pause()

    fun resume()

    /** Stops and clears the current stream, keeping the player alive. */
    fun stop()

    /** Frees the codec and the socket. After this the instance is unusable. */
    fun release()
}

/**
 * What to play.
 *
 * [streamUrl] is obtained per playback from `GET /channels/{id}/playback` and is
 * credential-bearing: it is never cached, never logged, and never leaves this
 * object (AGENTS.md §5). [toString] is overridden so it cannot reach a log line
 * through a data class's default.
 */
data class PlaybackRequest(
    val streamUrl: String,
    /** Shown in the info bar and in the notification. Never the URL. */
    val title: String?,
    /** Live streams have no meaningful duration and cannot be seeked. */
    val isLive: Boolean = true,
) {
    override fun toString(): String = "PlaybackRequest(title=$title, isLive=$isLive)"
}

sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** Loading. The UI shows a spinner; US-09 budgets five seconds for this. */
    data object Buffering : PlaybackState

    data class Playing(val title: String?) : PlaybackState

    data object Paused : PlaybackState

    data object Ended : PlaybackState

    /**
     * Playback failed. [error] is a stable code the UI maps to a localised
     * string — never a raw player message, which would be untranslated and
     * would leak the URL.
     */
    data class Failed(val error: PlaybackError) : PlaybackState
}

enum class PlaybackError {
    /** The user's server could not be reached at all. */
    UNREACHABLE,

    /**
     * The server answered, and refused.
     *
     * On IPTV panels this is most often the simultaneous-connection limit rather
     * than a real authorisation problem, which is why US-09 asks for a message
     * about the subscription's stream limit rather than "access denied".
     */
    REFUSED,

    /** The stream was reached but could not be decoded on this device. */
    UNPLAYABLE,

    UNKNOWN,
}
