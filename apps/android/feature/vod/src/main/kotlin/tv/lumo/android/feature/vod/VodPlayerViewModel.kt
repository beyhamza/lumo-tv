package tv.lumo.android.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.VodPlaybackTarget
import tv.lumo.android.core.data.repository.PlaybackRepository
import tv.lumo.android.core.data.repository.ProgressRepository
import tv.lumo.android.core.player.AudioTrack
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.PlaybackError
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackRequest
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Playing one film (US-13).
 *
 * <h2>The same player, and the same rules about its URL</h2>
 *
 * `GET /vod/{id}/playback` is the film half of the one operation family that
 * emits a stream URL, and everything written on `PlayerViewModel` applies
 * unchanged: fetched per playback, handed straight to the player, never written
 * to Room, to a log or to saved state (AGENTS.md §5). [VodPlaybackTarget]
 * overrides `toString` for the same reason its channel counterpart does.
 *
 * <h2>Three differences, and all three are what a film is</h2>
 *
 * **It is not live.** No live indicator, and `isLive = false` on the request —
 * which is what makes the player treat the stream as a file with an end rather
 * than as an edge to join.
 *
 * **It can be moved through, sometimes.** Seeking a progressive file needs the
 * user's own server to answer HTTP `Range` requests, and a great many panels do
 * not. `core:player` reports which of the four states applies, and this screen
 * draws a working scrubber only for one of them.
 *
 * **Its position is written, and a channel's is not.** Every thirty seconds while
 * playing, on pause, and on the way out — never per frame: `PUT /me/progress` is
 * an idempotent upsert, not a stream, and thirty seconds is the trade between
 * losing a minute when the process is killed and writing two thousand times a
 * film.
 *
 * The guard against saving a *live* position is [savable], and it is a function
 * rather than a comment because the risk is real: this view model and the channel
 * player share one [LumoPlayer], and a shared player is exactly the thing that
 * ends up writing a position for a continuous stream on its own.
 */
@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    private val playback: PlaybackRepository,
    private val progress: ProgressRepository,
    /** Exposed for the video surface, which needs the instance rather than its state. */
    val player: LumoPlayer,
) : ViewModel() {

    private val _failure = MutableStateFlow<VodPlayerFailure?>(null)
    private val _target = MutableStateFlow<VodPlaybackTarget?>(null)
    private var filmId: String? = null
    private var sourceId: String? = null
    private var resumeFromMs: Long = 0L
    private var saver: Job? = null

    val state: StateFlow<VodPlayerUiState> =
        combine(
            player.state,
            player.progress,
            _failure,
            _target,
        ) { playbackState, progress, failure, target ->
            VodPlayerUiState(
                playback = playbackState,
                progress = progress,
                // A failure this screen produced outranks the player's own: it
                // carries a contract code, and the player's is UNKNOWN whenever
                // the stream never started at all.
                failure = failure
                    ?: (playbackState as? PlaybackState.Failed)?.error?.asVodFailure(target),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = VodPlayerUiState(),
        )

    /**
     * Called once, with the film the screen was opened for.
     *
     * @param sourceId needed to save a position: it is part of the key the
     *   contract upserts on. The screen has it because the film's own screen
     *   read it from the cache before opening this one.
     * @param resumeFromMs where the viewer chose to start. Zero is the
     *   beginning, and that is a choice somebody made on the previous screen —
     *   never a default this player applied on their behalf.
     */
    fun start(filmId: String, sourceId: String, title: String?, resumeFromMs: Long) {
        if (this.filmId == filmId) return
        this.filmId = filmId
        this.sourceId = sourceId
        this.resumeFromMs = resumeFromMs
        open(filmId, title)
    }

    fun retry() {
        val filmId = filmId ?: return
        open(filmId, currentTitle)
    }

    /**
     * The audio tracks of what is playing, and the way to change which one plays.
     *
     * Passed straight through rather than folded into the screen's state: the
     * list changes when a container header is read and when a choice is made,
     * which is a handful of times per stream, while the state above changes
     * several times a second. One flow would recompose the picker at the tick
     * rate of a progress bar.
     */
    val audioTracks: StateFlow<List<AudioTrack>> = player.audioTracks

    fun selectAudioTrack(id: String) = player.selectAudioTrack(id)
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun togglePlayPause() {
        if (player.state.value is PlaybackState.Playing) {
            player.pause()
            onPaused()
        } else {
            player.resume()
        }
    }

    private var currentTitle: String? = null

    private fun open(filmId: String, title: String?) {
        _failure.value = null
        currentTitle = title

        viewModelScope.launch {
            when (val result = playback.vodPlaybackTarget(filmId)) {
                is LumoResult.Success -> {
                    val target = result.value
                    _target.value = target
                    player.play(
                        PlaybackRequest(
                            streamUrl = target.streamUrl,
                            title = title,
                            // The whole reason a film gets a scrubber and a
                            // channel does not.
                            isLive = false,
                        ),
                    )
                    // After `play`, because the player has no timeline before it.
                    // Ignored by `LumoPlayer` until the stream turns out to be
                    // seekable, which is the honest outcome: a server that will
                    // not serve part of a file cannot resume one either.
                    if (resumeFromMs > 0L) player.seekTo(resumeFromMs)
                    startSaving()
                }

                is LumoResult.Failure -> _failure.value = result.error.asVodFailure()
            }
        }
    }

    /**
     * Leaving the screen. The player survives; the stream and its URL do not.
     *
     * The last save happens **before** the player is stopped, because stopping
     * resets the position to zero — saving after it would write a viewer back to
     * the beginning of every film they leave.
     */
    fun stop() {
        saveNow()
        saver?.cancel()
        saver = null
        player.stop()
        _target.value = null
    }

    /**
     * The thirty-second loop.
     *
     * A loop rather than a listener, because nothing in the player fires as time
     * passes — the position advances with the clock. It runs while a stream is
     * loaded, including while paused: pausing is the single most likely moment
     * for somebody to walk away, and a paused position is the one most worth
     * having.
     */
    private fun startSaving() {
        saver?.cancel()
        saver = viewModelScope.launch {
            while (isActive) {
                delay(SAVE_EVERY_MILLIS)
                saveNow()
            }
        }
    }

    /** A tap on pause. The moment a position is most likely to matter. */
    private fun onPaused() = saveNow()

    private fun saveNow() {
        val filmId = filmId ?: return
        val sourceId = sourceId ?: return
        val snapshot = player.progress.value
        if (!snapshot.savable()) return

        viewModelScope.launch {
            progress.save(
                sourceId = sourceId,
                filmId = filmId,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** See the class documentation: an upsert, not a stream. */
        const val SAVE_EVERY_MILLIS = 30_000L
    }
}

data class VodPlayerUiState(
    val playback: PlaybackState = PlaybackState.Idle,
    val progress: PlaybackProgress = PlaybackProgress(),
    val failure: VodPlayerFailure? = null,
)

/**
 * What this screen can say.
 *
 * Its own type rather than `feature:live`'s, and not out of preference: a feature
 * module never depends on another feature module (`settings.gradle.kts`), and the
 * two lists genuinely differ — a film cannot be `SOURCE_MAX_CONNECTIONS`-free and
 * a channel cannot be `VOD_ITEM_NOT_FOUND`. The day a third surface needs them
 * both, the shared half moves down into `core:`; duplicating two enums is a
 * smaller debt than a dependency the architecture forbids.
 */
sealed interface VodPlayerFailure {

    /** Worth trying again unchanged: the server did not answer, or we are offline. */
    data class Unreachable(val retryable: Boolean = true) : VodPlayerFailure

    /**
     * The subscription is already streaming as much as it allows.
     *
     * A film counts against that ceiling exactly as a channel does, and the
     * sentence has to name **the user's own** provider's rule rather than imply
     * ours (US-09).
     */
    data class TooManyStreams(val allowed: Int?) : VodPlayerFailure

    /** The import has not finished. */
    data object SourceNotReady : VodPlayerFailure

    /** The user's subscription with their provider has expired. */
    data object SubscriptionExpired : VodPlayerFailure

    /** The film is gone — dropped by a re-synchronisation, most likely. */
    data object FilmGone : VodPlayerFailure

    /** Reached, and not decodable on this device. Retrying will not help. */
    data object Unplayable : VodPlayerFailure

    data object Unexpected : VodPlayerFailure
}

internal fun LumoError.asVodFailure(): VodPlayerFailure = when (this) {
    is LumoError.Offline -> VodPlayerFailure.Unreachable()
    is LumoError.Api -> when (code) {
        ErrorCode.SOURCE_MAX_CONNECTIONS -> VodPlayerFailure.TooManyStreams(null)
        ErrorCode.SOURCE_NOT_READY -> VodPlayerFailure.SourceNotReady
        ErrorCode.SOURCE_EXPIRED -> VodPlayerFailure.SubscriptionExpired
        ErrorCode.VOD_ITEM_NOT_FOUND -> VodPlayerFailure.FilmGone
        else -> VodPlayerFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> VodPlayerFailure.Unexpected
}

internal fun PlaybackError.asVodFailure(
    target: VodPlaybackTarget?,
): VodPlayerFailure = when (this) {
    PlaybackError.UNREACHABLE -> VodPlayerFailure.Unreachable()
    PlaybackError.REFUSED -> VodPlayerFailure.TooManyStreams(target?.maxConnections)
    PlaybackError.UNPLAYABLE -> VodPlayerFailure.Unplayable
    PlaybackError.UNKNOWN -> VodPlayerFailure.Unexpected
}

/** Whether trying the same thing again can help. Same answers as the channel player. */
internal fun VodPlayerFailure.isRetryable(): Boolean = when (this) {
    is VodPlayerFailure.Unreachable -> retryable
    is VodPlayerFailure.TooManyStreams -> true
    VodPlayerFailure.SourceNotReady -> true
    VodPlayerFailure.SubscriptionExpired -> false
    VodPlayerFailure.FilmGone -> false
    VodPlayerFailure.Unplayable -> false
    VodPlayerFailure.Unexpected -> true
}

/**
 * Whether this position is worth sending to the server.
 *
 * <h2>Two guards, and only one of them is an optimisation</h2>
 *
 * **A live stream is never saved.** `ProgressItemType` has no `LIVE` value, so
 * the contract cannot express it — but a type system does not stop a shared
 * player from being handed a channel and this view model from writing what it
 * reports. The sprint asks for a test on each client for exactly that reason,
 * and this function is what a test can hold.
 *
 * **A position of zero is not saved either**, and that one is not thrift: a save
 * at zero overwrites a real position with the beginning of the film. It happens
 * on every open — the player reports zero for the frames before the first one
 * decodes — so without this guard opening a film and closing it immediately
 * would lose where somebody was.
 *
 * `UNKNOWN` is refused with the same reasoning: nothing has loaded, so whatever
 * the position says is not a position.
 */
internal fun PlaybackProgress.savable(): Boolean = when (seek) {
    SeekAvailability.LIVE, SeekAvailability.UNKNOWN -> false
    SeekAvailability.AVAILABLE, SeekAvailability.REFUSED -> positionMs > 0L
}
