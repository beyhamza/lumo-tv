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

    /**
     * Where playback has got to, and whether it can be moved.
     *
     * Separate from [state] and not folded into [PlaybackState.Playing], because
     * the two change on completely different clocks: the state changes a handful
     * of times per stream, this changes several times a second. One flow would
     * recompose every screen that watches playback at the tick rate of a
     * progress bar.
     *
     * Idle between streams, and it says [SeekAvailability.UNKNOWN] rather than
     * zero — a scrubber drawn at the start of a film nobody has loaded is a
     * scrubber that lies for one frame.
     */
    val progress: StateFlow<PlaybackProgress>

    /** Loads a stream and starts playing it. Replaces whatever was playing. */
    fun play(request: PlaybackRequest)

    /**
     * Moves within the current stream.
     *
     * Ignored when [PlaybackProgress.seek] is not [SeekAvailability.AVAILABLE] —
     * a caller that draws a disabled scrubber and one that does not both end up
     * doing the right thing, and neither can put the player somewhere it cannot
     * come back from.
     */
    fun seekTo(positionMs: Long)

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

/**
 * How far into the stream playback is, and whether that can be changed.
 *
 * <h2>Why seeking is a state and not a boolean</h2>
 *
 * A film that cannot be seeked and a live channel that cannot be seeked are the
 * same `false` and two completely different sentences. On a channel, seeking is
 * meaningless and the control should not exist. On a film, it is a thing the
 * user's own server will not do, and S5-08 is explicit about the difference:
 * *"un curseur qui ne bouge pas sans dire pourquoi est un défaut ; un curseur
 * absent est une fonction qu'on croit ne pas avoir livrée."*
 *
 * So the screen gets four answers, and only one of them draws a working scrubber.
 */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    /** Null while unknown, and for a live stream, which has no meaningful end. */
    val durationMs: Long? = null,
    val seek: SeekAvailability = SeekAvailability.UNKNOWN,
)

enum class SeekAvailability {
    /** Nothing loaded yet. A scrubber drawn now would be drawing a guess. */
    UNKNOWN,

    /** A live stream. Seeking has no meaning, so no control is offered at all. */
    LIVE,

    AVAILABLE,

    /**
     * The stream is a file, and the user's server will not serve part of it.
     *
     * Seeking a progressive file needs HTTP `Range` requests, and a great many
     * IPTV panels answer the whole body regardless. Media3 finds that out at the
     * first attempt, not before — so this state is reached *during* playback, and
     * a screen that has already drawn a working scrubber has to change it in
     * place rather than pretend it was never there.
     *
     * **One case is not detected, and is stated rather than hidden.** A server
     * that ignores `Range` and answers `200` with the whole file from byte zero
     * produces no error at all: Media3 reads and discards its way forward, which
     * looks exactly like a slow connection. Nothing here can tell those two
     * apart, and inventing a timeout that called one of them a refusal would be
     * guessing at somebody else's server.
     */
    REFUSED,
}
