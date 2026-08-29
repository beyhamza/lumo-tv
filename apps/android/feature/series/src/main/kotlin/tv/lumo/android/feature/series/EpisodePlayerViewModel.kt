package tv.lumo.android.feature.series

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
import tv.lumo.android.core.data.model.EpisodePlaybackTarget
import tv.lumo.android.core.data.repository.PlaybackRepository
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.PlaybackError
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackRequest
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Playing one episode (US-15).
 *
 * <h2>`VodPlayerViewModel`, one table over</h2>
 *
 * Everything written there applies here unchanged: the URL is fetched per
 * playback from the one operation family that emits one, handed straight to the
 * player, and never written to Room, to a log or to saved state (AGENTS.md §5).
 * An episode is a progressive file exactly as a film is, so it gets the same
 * scrubber and the same four seek states.
 *
 * <h2>Why it is a copy rather than a shared class</h2>
 *
 * A feature module never depends on another feature module
 * (`settings.gradle.kts`), and the two failure lists genuinely differ — an
 * episode cannot be `VOD_ITEM_NOT_FOUND` and a film cannot be
 * `EPISODE_NOT_FOUND`. The day a third surface needs both, the shared half moves
 * down into `core:`; duplicating two enums is a smaller debt than a dependency
 * the architecture forbids.
 *
 * <h2>Nothing is saved here, and that is a decision rather than an omission</h2>
 *
 * The film's player runs a thirty-second loop that upserts a position. This one
 * runs none, and `ProgressRepository.save` would refuse an episode if it tried:
 * its `itemType` is `VOD` and nothing else.
 *
 * Saving belongs to `S6-08`, which carries a ruling this file must not pre-empt:
 * what a viewer resumes is a **series**, not an episode — they remember having
 * got to episode four, not an identifier — and turning one into the other needs
 * the tree. Writing half of it here would leave rows saved that no screen reads,
 * and a rail that resumed an episode with no series around it.
 *
 * Seeking still works. Moving inside a file one is watching is playback; coming
 * back to it tomorrow is the feature that is not built yet.
 */
@HiltViewModel
class EpisodePlayerViewModel @Inject constructor(
    private val playback: PlaybackRepository,
    /** Exposed for the video surface, which needs the instance rather than its state. */
    val player: LumoPlayer,
) : ViewModel() {

    private val _failure = MutableStateFlow<EpisodePlayerFailure?>(null)
    private val _target = MutableStateFlow<EpisodePlaybackTarget?>(null)
    private var episodeId: String? = null
    private var currentTitle: String? = null

    val state: StateFlow<EpisodePlayerUiState> =
        combine(
            player.state,
            player.progress,
            _failure,
            _target,
        ) { playbackState, progress, failure, target ->
            EpisodePlayerUiState(
                playback = playbackState,
                progress = progress,
                // A failure this screen produced outranks the player's own: it
                // carries a contract code, and the player's is UNKNOWN whenever
                // the stream never started at all.
                failure = failure
                    ?: (playbackState as? PlaybackState.Failed)?.error?.asEpisodeFailure(target),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EpisodePlayerUiState(),
        )

    /**
     * Called once, with the episode the screen was opened for.
     *
     * @param title what the previous screen already knew to call it, so the
     *   player has a name to show before any request answers. Null is honest —
     *   a panel that numbers an episode without naming it exists, and the screen
     *   says "Episode 4" rather than inventing a title.
     */
    fun start(episodeId: String, title: String?) {
        if (this.episodeId == episodeId) return
        this.episodeId = episodeId
        open(episodeId, title)
    }

    fun retry() {
        val episodeId = episodeId ?: return
        open(episodeId, currentTitle)
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun togglePlayPause() {
        if (player.state.value is PlaybackState.Playing) player.pause() else player.resume()
    }

    private fun open(episodeId: String, title: String?) {
        _failure.value = null
        currentTitle = title

        viewModelScope.launch {
            when (val result = playback.episodePlaybackTarget(episodeId)) {
                is LumoResult.Success -> {
                    val target = result.value
                    _target.value = target
                    player.play(
                        PlaybackRequest(
                            streamUrl = target.streamUrl,
                            title = title,
                            // The whole reason an episode gets a scrubber and a
                            // channel does not.
                            isLive = false,
                        ),
                    )
                }

                is LumoResult.Failure -> _failure.value = result.error.asEpisodeFailure()
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

data class EpisodePlayerUiState(
    val playback: PlaybackState = PlaybackState.Idle,
    val progress: PlaybackProgress = PlaybackProgress(),
    val failure: EpisodePlayerFailure? = null,
)

/**
 * What this screen can say.
 *
 * Its own type rather than `feature:vod`'s, and not out of preference: a feature
 * module never depends on another feature module (`settings.gradle.kts`), and the
 * two lists genuinely differ — an episode cannot be `VOD_ITEM_NOT_FOUND` and a
 * film cannot be `EPISODE_NOT_FOUND`. The day a third surface needs them both,
 * the shared half moves down into `core:`; duplicating two enums is a smaller
 * debt than a dependency the architecture forbids.
 */
sealed interface EpisodePlayerFailure {

    /** Worth trying again unchanged: the server did not answer, or we are offline. */
    data class Unreachable(val retryable: Boolean = true) : EpisodePlayerFailure

    /**
     * The subscription is already streaming as much as it allows.
     *
     * An episode counts against that ceiling exactly as a channel does, and the
     * sentence has to name **the user's own** provider's rule rather than imply
     * ours (US-09).
     */
    data class TooManyStreams(val allowed: Int?) : EpisodePlayerFailure

    /** The import has not finished. */
    data object SourceNotReady : EpisodePlayerFailure

    /** The user's subscription with their provider has expired. */
    data object SubscriptionExpired : EpisodePlayerFailure

    /** The episode is gone — the tree was refetched and it is no longer in it. */
    data object EpisodeGone : EpisodePlayerFailure

    /** Reached, and not decodable on this device. Retrying will not help. */
    data object Unplayable : EpisodePlayerFailure

    data object Unexpected : EpisodePlayerFailure
}

internal fun LumoError.asEpisodeFailure(): EpisodePlayerFailure = when (this) {
    is LumoError.Offline -> EpisodePlayerFailure.Unreachable()
    is LumoError.Api -> when (code) {
        ErrorCode.SOURCE_MAX_CONNECTIONS -> EpisodePlayerFailure.TooManyStreams(null)
        ErrorCode.SOURCE_NOT_READY -> EpisodePlayerFailure.SourceNotReady
        ErrorCode.SOURCE_EXPIRED -> EpisodePlayerFailure.SubscriptionExpired
        ErrorCode.EPISODE_NOT_FOUND -> EpisodePlayerFailure.EpisodeGone
        else -> EpisodePlayerFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> EpisodePlayerFailure.Unexpected
}

internal fun PlaybackError.asEpisodeFailure(
    target: EpisodePlaybackTarget?,
): EpisodePlayerFailure = when (this) {
    PlaybackError.UNREACHABLE -> EpisodePlayerFailure.Unreachable()
    PlaybackError.REFUSED -> EpisodePlayerFailure.TooManyStreams(target?.maxConnections)
    PlaybackError.UNPLAYABLE -> EpisodePlayerFailure.Unplayable
    PlaybackError.UNKNOWN -> EpisodePlayerFailure.Unexpected
}

/** Whether trying the same thing again can help. Same answers as the film player. */
internal fun EpisodePlayerFailure.isRetryable(): Boolean = when (this) {
    is EpisodePlayerFailure.Unreachable -> retryable
    is EpisodePlayerFailure.TooManyStreams -> true
    EpisodePlayerFailure.SourceNotReady -> true
    EpisodePlayerFailure.SubscriptionExpired -> false
    EpisodePlayerFailure.EpisodeGone -> false
    EpisodePlayerFailure.Unplayable -> false
    EpisodePlayerFailure.Unexpected -> true
}
