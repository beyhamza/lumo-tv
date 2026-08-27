package tv.lumo.android.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.PlaybackTarget
import tv.lumo.android.core.data.repository.PlaybackRepository
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.PlaybackError
import tv.lumo.android.core.player.PlaybackRequest
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Playing one channel (US-09).
 *
 * <h2>The URL is fetched per playback and kept nowhere</h2>
 *
 * `GET /channels/{id}/playback` is the only operation in the contract that emits
 * a stream URL, and on most Xtream panels that URL carries the user's own
 * credentials in its path. It is asked for at the moment of playing, handed
 * straight to the player, and never written to Room, to a log, or to saved state
 * (AGENTS.md §5). [PlaybackTarget] overrides `toString` for the same reason.
 *
 * <h2>Two failures, two sentences, and neither is a black rectangle</h2>
 *
 * US-09 names them. A stream that will not open gets a message and a retry —
 * "the application does not crash and does not sit on a silent black screen" is
 * the acceptance criterion, and a silent black screen is exactly what an
 * unhandled player error looks like. A subscription already streaming as much as
 * it allows gets a sentence naming **the user's own** limit, which
 * `PlaybackInfo.max_connections` is carried all the way here to make possible.
 *
 * <h2>The player is a singleton, so this stops it rather than releasing it</h2>
 *
 * A codec is scarce on the cheap boxes this product runs on. Releasing it here
 * would leave the next screen without a player at all; stopping clears the media
 * item, which is what drops the credential-bearing URL out of memory.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackRepository,
    /** Exposed for the video surface, which needs the instance rather than its state. */
    val player: LumoPlayer,
) : ViewModel() {

    private val _failure = MutableStateFlow<PlayerFailure?>(null)
    private val _target = MutableStateFlow<PlaybackTarget?>(null)
    private var channelId: String? = null
    private var recorded = false

    val state: StateFlow<PlayerUiState> =
        combine(player.state, _failure, _target) { playback, failure, target ->
            PlayerUiState(
                playback = playback,
                // A failure this screen produced outranks the player's own: it
                // has a code behind it, and the player's is always UNKNOWN when
                // the stream never started.
                failure = failure ?: (playback as? PlaybackState.Failed)?.error?.asPlayerFailure(target),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlayerUiState(),
        )

    /** Called once, with the channel the screen was opened for. */
    fun start(channelId: String) {
        if (this.channelId == channelId) return
        this.channelId = channelId
        open(channelId)
    }

    fun retry() {
        val channelId = channelId ?: return
        open(channelId)
    }

    private fun open(channelId: String) {
        _failure.value = null
        recorded = false

        viewModelScope.launch {
            when (val result = playback.playbackTarget(channelId)) {
                is LumoResult.Success -> {
                    val target = result.value
                    _target.value = target
                    player.play(
                        PlaybackRequest(
                            streamUrl = target.streamUrl,
                            title = null,
                            isLive = true,
                        ),
                    )
                    recordWatched(channelId)
                }

                is LumoResult.Failure -> _failure.value = result.error.asPlayerFailure()
            }
        }
    }

    /**
     * Records that a channel was actually watched.
     *
     * Sent when playback is started, never when a channel is focused or scrolled
     * past: a "recently watched" rail built from what a finger flicked over is
     * noise, and it is the user's own history being made worse. Failure is
     * ignored on purpose — this is a side effect of watching television, not a
     * step of it, and a player that interrupted itself to report a failed
     * bookkeeping call would be worse than a rail that misses an entry.
     */
    private fun recordWatched(channelId: String) {
        if (recorded) return
        recorded = true
        viewModelScope.launch { playback.recordWatched(channelId) }
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

data class PlayerUiState(
    val playback: PlaybackState = PlaybackState.Idle,
    val failure: PlayerFailure? = null,
)

/** What this screen can say, and what it offers when it says it. */
sealed interface PlayerFailure {

    /** Worth trying again unchanged: the server did not answer, or the app is offline. */
    data class Unreachable(val retryable: Boolean = true) : PlayerFailure

    /**
     * The subscription is already streaming as much as it allows.
     *
     * [allowed] is the user's own ceiling, echoed from their panel by
     * `PlaybackInfo.max_connections`. Null when the panel did not say — the
     * sentence then names the limit without a number rather than inventing one.
     */
    data class TooManyStreams(val allowed: Int?) : PlayerFailure

    /** The catalogue is not ready yet — the import has not finished. */
    data object SourceNotReady : PlayerFailure

    /** The user's subscription with their provider has expired. */
    data object SubscriptionExpired : PlayerFailure

    /** The channel is gone — dropped by a re-synchronisation, most likely. */
    data object ChannelGone : PlayerFailure

    /** Reached, and not decodable on this device. Retrying will not help. */
    data object Unplayable : PlayerFailure

    data object Unexpected : PlayerFailure
}

/** The contract's refusals. All three of the `409`s are different sentences. */
internal fun LumoError.asPlayerFailure(): PlayerFailure = when (this) {
    is LumoError.Offline -> PlayerFailure.Unreachable()
    is LumoError.Api -> when (code) {
        ErrorCode.SOURCE_MAX_CONNECTIONS -> PlayerFailure.TooManyStreams(null)
        ErrorCode.SOURCE_NOT_READY -> PlayerFailure.SourceNotReady
        ErrorCode.SOURCE_EXPIRED -> PlayerFailure.SubscriptionExpired
        ErrorCode.CHANNEL_NOT_FOUND -> PlayerFailure.ChannelGone
        else -> PlayerFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> PlayerFailure.Unexpected
}

/**
 * The player's own failures.
 *
 * `REFUSED` is the interesting one: a panel out of allowed connections answers
 * with an HTTP error rather than a network failure, so this is the case US-09
 * asks to be worded as a subscription limit rather than as an outage — and the
 * number comes from the target the server already handed us.
 */
internal fun PlaybackError.asPlayerFailure(target: PlaybackTarget?): PlayerFailure = when (this) {
    PlaybackError.UNREACHABLE -> PlayerFailure.Unreachable()
    PlaybackError.REFUSED -> PlayerFailure.TooManyStreams(target?.maxConnections)
    PlaybackError.UNPLAYABLE -> PlayerFailure.Unplayable
    PlaybackError.UNKNOWN -> PlayerFailure.Unexpected
}
