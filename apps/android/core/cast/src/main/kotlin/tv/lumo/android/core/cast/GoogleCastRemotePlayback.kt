package tv.lumo.android.core.cast

import android.content.Context
import androidx.core.net.toUri
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import tv.lumo.android.core.player.cast.CastSignal
import tv.lumo.android.core.player.cast.RemoteIdleReason
import tv.lumo.android.core.player.cast.RemoteMedia
import tv.lumo.android.core.player.cast.RemotePlayback
import tv.lumo.android.core.player.cast.RemotePlayerState
import tv.lumo.android.core.player.cast.RemoteSession
import tv.lumo.android.core.player.cast.RemoteStatus

/**
 * [RemotePlayback] over the Google Cast SDK (S16-01).
 *
 * A translation layer and nothing more: every decision — when to send, what a
 * refusal is, where to resume — is in `core:player`, where a JVM test can hold it.
 *
 * <h2>No Google Play services, no Cast</h2>
 *
 * Checked before the framework is touched: on a device without them
 * [supported] stays false, no button is ever drawn, and the phone plays as it
 * always did. A framework that fails to start says the same.
 *
 * <h2>Nothing sensitive leaves through here</h2>
 *
 * The URL goes to the receiver — that is the feature, and the S15-00 report notes
 * it is then visible to other senders on the local network — and nowhere else: it
 * is never logged, and [RemoteMedia] does not print it.
 */
@Singleton
internal class GoogleCastRemotePlayback @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RemotePlayback {

    private val _supported = MutableStateFlow(false)
    override val supported: StateFlow<Boolean> = _supported.asStateFlow()

    private val _devicesAvailable = MutableStateFlow(false)
    override val devicesAvailable: StateFlow<Boolean> = _devicesAvailable.asStateFlow()

    private val _session = MutableStateFlow<RemoteSession>(RemoteSession.None)
    override val session: StateFlow<RemoteSession> = _session.asStateFlow()

    private val _status = MutableStateFlow<RemoteStatus?>(null)
    override val status: StateFlow<RemoteStatus?> = _status.asStateFlow()

    private val _signals = MutableSharedFlow<CastSignal>(extraBufferCapacity = SIGNAL_BUFFER)
    override val signals: Flow<CastSignal> = _signals.asSharedFlow()

    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var client: RemoteMediaClient? = null

    init {
        start()
    }

    private fun start() {
        val services = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (services != ConnectionResult.SUCCESS) {
            Log.i(TAG, "cast unavailable: play services $services")
            return
        }
        try {
            CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
                .addOnSuccessListener(::attach)
                .addOnFailureListener { Log.i(TAG, "cast unavailable: ${it.javaClass.simpleName}") }
        } catch (e: RuntimeException) {
            // A missing or broken framework module: the phone plays locally, as before.
            Log.i(TAG, "cast unavailable: ${e.javaClass.simpleName}")
        }
    }

    private fun attach(cast: CastContext) {
        castContext = cast
        _supported.value = true
        _devicesAvailable.value = cast.castState != CastState.NO_DEVICES_AVAILABLE
        cast.addCastStateListener { state -> _devicesAvailable.value = state != CastState.NO_DEVICES_AVAILABLE }
        cast.sessionManager.addSessionManagerListener(sessions, CastSession::class.java)
        cast.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::connected)
    }

    // ---- RemotePlayback -----------------------------------------------------

    override suspend fun load(media: RemoteMedia, startPositionMs: Long): Int {
        val client = client ?: return CastStatusCodes.FAILED
        val metadata = MediaMetadata(
            if (media.live) MediaMetadata.MEDIA_TYPE_GENERIC else MediaMetadata.MEDIA_TYPE_MOVIE,
        ).apply {
            media.title?.let { putString(MediaMetadata.KEY_TITLE, it) }
            media.artworkUrl?.let { addImage(WebImage(it.toUri())) }
        }
        val info = MediaInfo.Builder(media.url)
            .setStreamType(if (media.live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(media.container.mimeType)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(startPositionMs.coerceAtLeast(0L))
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.load(request).setResultCallback { result ->
                if (continuation.isActive) continuation.resume(result.status.statusCode)
            }
        }
    }

    override fun play() {
        client?.play()
    }

    override fun pause() {
        client?.pause()
    }

    override fun seekTo(positionMs: Long) {
        client?.seek(MediaSeekOptions.Builder().setPosition(positionMs).build())
    }

    override fun setVolume(level: Float) {
        try {
            castSession?.volume = level.toDouble()
        } catch (e: IOException) {
            Log.i(TAG, "volume not applied: ${e.javaClass.simpleName}")
        } catch (e: IllegalStateException) {
            Log.i(TAG, "volume not applied: ${e.javaClass.simpleName}")
        }
    }

    override fun stopMedia() {
        client?.stop()
    }

    override fun endSession() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    // ---- SDK callbacks ------------------------------------------------------

    private fun connected(session: CastSession) {
        if (castSession !== session) {
            detachClient()
            castSession = session
            session.addCastListener(volumeListener)
            client = session.remoteMediaClient?.also {
                it.registerCallback(mediaCallback)
                it.addProgressListener(progressListener, PROGRESS_INTERVAL_MS)
            }
        }
        _session.value = RemoteSession.Connected(session.castDevice?.friendlyName)
        publishStatus()
    }

    private fun disconnected() {
        detachClient()
        _session.value = RemoteSession.None
        _status.value = null
    }

    private fun detachClient() {
        client?.unregisterCallback(mediaCallback)
        client?.removeProgressListener(progressListener)
        castSession?.removeCastListener(volumeListener)
        client = null
        castSession = null
    }

    private fun publishStatus() {
        val client = client
        val status = client?.mediaStatus
        if (client == null || status == null) {
            _status.value = null
            return
        }
        _status.value = RemoteStatus(
            playerState = when (status.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING -> RemotePlayerState.PLAYING
                MediaStatus.PLAYER_STATE_PAUSED -> RemotePlayerState.PAUSED
                MediaStatus.PLAYER_STATE_BUFFERING -> RemotePlayerState.BUFFERING
                MediaStatus.PLAYER_STATE_LOADING -> RemotePlayerState.LOADING
                MediaStatus.PLAYER_STATE_IDLE -> RemotePlayerState.IDLE
                else -> RemotePlayerState.UNKNOWN
            },
            idleReason = when (status.idleReason) {
                MediaStatus.IDLE_REASON_FINISHED -> RemoteIdleReason.FINISHED
                MediaStatus.IDLE_REASON_CANCELED -> RemoteIdleReason.CANCELED
                MediaStatus.IDLE_REASON_INTERRUPTED -> RemoteIdleReason.INTERRUPTED
                MediaStatus.IDLE_REASON_ERROR -> RemoteIdleReason.ERROR
                else -> RemoteIdleReason.NONE
            },
            positionMs = client.approximateStreamPosition,
            durationMs = client.streamDuration.takeIf { it > 0L },
            seekable = status.isMediaCommandSupported(MediaStatus.COMMAND_SEEK),
            volume = castSession?.let { session ->
                runCatching { session.volume.toFloat() }.getOrNull()
            },
        )
    }

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishStatus()

        override fun onMediaError(error: MediaError) {
            // The detailed code for the S17 campaign; the URL is not in a MediaError.
            Log.i(TAG, "receiver error: ${error.type} ${error.detailedErrorCode} ${error.reason}")
            _signals.tryEmit(CastSignal.MediaError(error.detailedErrorCode, error.type))
        }
    }

    private val progressListener = RemoteMediaClient.ProgressListener { _, _ -> publishStatus() }

    private val volumeListener = object : Cast.Listener() {
        override fun onVolumeChanged() = publishStatus()
    }

    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _session.value = RemoteSession.Connecting
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) = connected(session)

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            // Includes the picker closed without a choice: not a refusal (S16-05).
            Log.i(TAG, "session not started: $error")
            disconnected()
            _signals.tryEmit(CastSignal.SessionStartFailed(error))
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) = disconnected()

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _session.value = RemoteSession.Connecting
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = connected(session)

        override fun onSessionResumeFailed(session: CastSession, error: Int) = disconnected()

        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    private companion object {
        const val TAG = "LumoCast"
        const val PROGRESS_INTERVAL_MS = 1_000L
        const val SIGNAL_BUFFER = 8
    }
}
