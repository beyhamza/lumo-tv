package tv.lumo.android.core.player.cast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A Cast receiver, as [CastingLumoPlayer] needs it (S16, ADR 0013).
 *
 * Implemented by `core:cast` over the Google Cast SDK, on the phone only: the
 * television application does not ship the SDK and has no implementation, so its
 * player is the local one and nothing else. Kept free of SDK types so that every
 * decision about switching between the phone and the receiver is a JVM test.
 *
 * Every member is called on the main thread, which is the SDK's rule.
 */
interface RemotePlayback {

    /** Google Play services are present and the Cast framework started. */
    val supported: StateFlow<Boolean>

    /** At least one Cast device is on the network (the SDK's `CastState`). */
    val devicesAvailable: StateFlow<Boolean>

    val session: StateFlow<RemoteSession>

    /** The receiver's media status, or null when it has none (no session, nothing loaded). */
    val status: StateFlow<RemoteStatus?>

    /** Signals 2 (detailed errors) and session start failures; see [CastSignals]. */
    val signals: Flow<CastSignal>

    /**
     * Sends a `LOAD` and suspends until the receiver answers it (signal 1).
     *
     * @return the SDK's status code: `0` when the receiver accepted the request.
     */
    suspend fun load(media: RemoteMedia, startPositionMs: Long): Int

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** 0..1. */
    fun setVolume(level: Float)

    /** Stops what the receiver plays; the session stays, and the next stream goes there. */
    fun stopMedia()

    /** Leaves the device and closes the receiver application. */
    fun endSession()
}

sealed interface RemoteSession {
    data object None : RemoteSession
    data object Connecting : RemoteSession
    data class Connected(val deviceName: String?) : RemoteSession
}

/**
 * What the receiver is given. [url] is the playback URL already obtained for the
 * phone — credential-bearing, never logged — hence [toString].
 */
data class RemoteMedia(
    val url: String,
    val container: CastContainer,
    val live: Boolean,
    val title: String?,
    val artworkUrl: String?,
) {
    override fun toString(): String = "RemoteMedia(container=$container, live=$live, title=$title)"
}

enum class RemotePlayerState { IDLE, BUFFERING, LOADING, PLAYING, PAUSED, UNKNOWN }

data class RemoteStatus(
    val playerState: RemotePlayerState,
    val idleReason: RemoteIdleReason = RemoteIdleReason.NONE,
    val positionMs: Long = 0L,
    /** Null when unknown, and for a live stream. */
    val durationMs: Long? = null,
    /** The receiver accepts a seek on this media (`COMMAND_SEEK`). */
    val seekable: Boolean = false,
    /** 0..1, or null when the device does not say. */
    val volume: Float? = null,
)
