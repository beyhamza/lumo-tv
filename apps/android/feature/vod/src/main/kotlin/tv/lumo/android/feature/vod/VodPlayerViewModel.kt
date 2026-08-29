package tv.lumo.android.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.VodPlaybackTarget
import tv.lumo.android.core.data.repository.PlaybackRepository
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.PlaybackError
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackRequest
import tv.lumo.android.core.player.PlaybackState
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
 * **Nothing is recorded yet.** A film's position is worth keeping and a channel's
 * is not, but that is S5-11 — and writing half of it here would leave a position
 * saved that no screen reads.
 */
@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    private val playback: PlaybackRepository,
    /** Exposed for the video surface, which needs the instance rather than its state. */
    val player: LumoPlayer,
) : ViewModel() {

    private val _failure = MutableStateFlow<VodPlayerFailure?>(null)
    private val _target = MutableStateFlow<VodPlaybackTarget?>(null)
    private var filmId: String? = null

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

    /** Called once, with the film the screen was opened for. */
    fun start(filmId: String, title: String?) {
        if (this.filmId == filmId) return
        this.filmId = filmId
        open(filmId, title)
    }

    fun retry() {
        val filmId = filmId ?: return
        open(filmId, currentTitle)
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun togglePlayPause() {
        if (player.state.value is PlaybackState.Playing) player.pause() else player.resume()
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
                }

                is LumoResult.Failure -> _failure.value = result.error.asVodFailure()
            }
        }
    }

    /** Leaving the screen. The player survives; the stream and its URL do not. */
    fun stop() {
        player.stop()
        _target.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
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
