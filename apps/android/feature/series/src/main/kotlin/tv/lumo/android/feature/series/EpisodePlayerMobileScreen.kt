package tv.lumo.android.feature.series

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.component.LumoAudioTrackSheet
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.core.player.ui.LumoVideoSurface
import tv.lumo.android.core.player.ui.asChoices

/**
 * Watching an episode on the phone (US-15).
 *
 * `VodPlayerMobileScreen`, unchanged in everything a browser or a player can
 * tell apart: an episode is a progressive file, so it gets the scrubber, the
 * play/pause control, no live indicator, and the same four seek states — most of
 * all the one where the server refuses `Range` and the bar is drawn **disabled
 * with the reason beside it**.
 *
 * A copy rather than a shared screen because a feature module never depends on
 * another (`settings.gradle.kts`); the argument is on `EpisodePlayerViewModel`.
 *
 * <h2>The next episode, with a shorter countdown than the television (S6-06)</h2>
 *
 * The behaviour is written for the television, where it matters — somebody three
 * metres away with a remote in their lap — and it is the same behaviour here
 * because a viewer who owns both should not have to learn it twice.
 *
 * **The count is shorter**, and the reason is the distance: a phone is held, the
 * card is a thumb away, and ten seconds of looking at it is nine seconds of
 * waiting. Five is the number, and it is the only difference — the offer, the
 * cancellation and the end of a series are the view model's and are shared.
 *
 * **Any touch stops the count.** On a television that is a key press; here it is a
 * touch anywhere on the picture, which is the same gesture in the same spirit.
 */
@Composable
fun EpisodePlayerMobileScreen(
    episodeId: String,
    title: String?,
    resumeFromMs: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpisodePlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()

    // Local to the screen. Which sheet is open is not something the player or the
    // view model has an opinion about, and a choice that survived a rotation would
    // reopen a list over a picture somebody had come back to watch.
    var pickingAudio by remember { mutableStateOf(false) }

    // Keyed on the episode: opening a different one restarts, turning the phone
    // does not — the Activity declares `configChanges` and the player is a
    // singleton.
    LaunchedEffect(episodeId) {
        viewModel.start(
            episodeId = episodeId,
            title = title,
            autoAdvanceSeconds = AUTO_ADVANCE_SECONDS,
            resumeFromMs = resumeFromMs,
        )
    }

    // The last episode of the series has finished. Nothing is offered, so the
    // screen leaves rather than holding a frozen last frame.
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    ImmersiveWhileVisible()

    DisposableEffect(Unit) {
        // Stop, not release: the player is shared by the whole process, and
        // stopping is what clears the credential-bearing URL out of it.
        onDispose { viewModel.stop() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Every touch, in the initial pass, before the slider or a button
            // consumes it. Somebody touching the screen is somebody watching, and
            // an episode starting under their thumb is what this prevents — the
            // television does the same with a key press.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        viewModel.keepWatching()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        LumoVideoSurface(player = viewModel.player, modifier = Modifier.fillMaxSize())

        when {
            state.failure != null -> Failure(
                failure = state.failure!!,
                title = title,
                onRetry = viewModel::retry,
                onBack = onBack,
            )

            state.playback is PlaybackState.Buffering ||
                state.playback is PlaybackState.Idle -> CircularProgressIndicator()
        }

        // Drawn under the picture and over nothing else. Kept out of the failure
        // branch on purpose: an episode that stopped playing has a position worth
        // seeing, and hiding the bar with the error would take away the one thing
        // that says how far in the failure happened.
        if (state.failure == null) {
            Controls(
                progress = state.progress,
                playing = state.playback is PlaybackState.Playing,
                onSeek = viewModel::seekTo,
                onTogglePlay = viewModel::togglePlayPause,
                // Only when there is a choice to make. One track is not a
                // decision, and a control that opens a list of one wastes both a
                // tap and the width it sits in.
                onPickAudio = { pickingAudio = true }.takeIf { audioTracks.size > 1 },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (pickingAudio) {
            // `resources` rather than `stringResource`: the fallback name is
            // resolved inside a plain lambda, which is not a composable scope. The
            // position is only known while the list is being walked, so it cannot
            // be resolved before the call either.
            val resources = LocalContext.current.resources
            LumoAudioTrackSheet(
                title = stringResource(R.string.feature_series_audio_track),
                tracks = audioTracks.asChoices(
                    unnamed = { position ->
                        resources.getString(R.string.feature_series_audio_track_number, position)
                    },
                    unsupported = stringResource(R.string.feature_series_audio_unsupported),
                ),
                onSelect = viewModel::selectAudioTrack,
                onDismiss = { pickingAudio = false },
            )
        }

        state.upNext?.let { offer ->
            UpNextCard(
                offer = offer,
                onPlay = viewModel::playNext,
                onDismiss = onBack,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * The offer at the end of an episode.
 *
 * Over the picture rather than replacing it: the credits are part of what
 * somebody is watching, and a card that blacked them out would answer a question
 * nobody asked.
 *
 * **The count is shown, always**, and not only as a shrinking bar. A number is
 * what tells somebody how long they have to decide; an animation tells them
 * something is happening.
 *
 * Two controls, and the second is not a decoration: without a way to say no, the
 * only way out of an automatic advance is the back key, and on a phone that means
 * leaving the player entirely.
 */
@Composable
private fun UpNextCard(
    offer: UpNext,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = offer.episode.name
        ?: stringResource(R.string.feature_series_episode, offer.episode.episodeNumber)

    Column(
        modifier = modifier
            .padding(LumoSpacing.md)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = LumoShapes.medium,
            )
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Text(
            text = offer.secondsLeft
                ?.let { stringResource(R.string.feature_series_up_next_in, it) }
                ?: stringResource(R.string.feature_series_up_next),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
            Button(onClick = onPlay) {
                Text(stringResource(R.string.feature_series_play_next))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_series_player_back))
            }
        }
    }
}

/**
 * The transport controls.
 *
 * <h2>The bar follows the player, except while a thumb is on it</h2>
 *
 * `dragging` is not a nicety. Without it the position ticking in twice a second
 * fights the thumb, and the control jumps back under the finger — which reads as
 * a broken slider rather than as a race. The player is told once, when the thumb
 * lifts.
 */
@Composable
private fun Controls(
    progress: PlaybackProgress,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    /** Null when the stream carries one track or none — there is nothing to choose. */
    onPickAudio: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Nothing to draw for a live stream, and this screen never plays one — the
    // branch is here so a mis-wired call site shows no scrubber rather than a
    // scrubber over a channel.
    if (progress.seek == SeekAvailability.LIVE) return

    var dragging by remember { mutableStateOf<Float?>(null) }

    val duration = progress.durationMs
    val enabled = progress.seek == SeekAvailability.AVAILABLE && duration != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Slider(
            value = dragging ?: progress.positionMs.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            // Zero when the length is unknown, which with `enabled = false` is a
            // bar drawn empty rather than a bar drawn full.
            valueRange = 0f..(duration?.toFloat() ?: 0f),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.feature_series_position,
                    (dragging?.toLong() ?: progress.positionMs).asClock(),
                    duration?.asClock() ?: stringResource(R.string.feature_series_unknown_length),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
                onPickAudio?.let { pick ->
                    TextButton(onClick = pick) {
                        Text(
                            text = stringResource(R.string.feature_series_audio_track),
                            color = Color.White,
                        )
                    }
                }

                TextButton(onClick = onTogglePlay) {
                    Text(
                        text = if (playing) {
                            stringResource(R.string.feature_series_pause)
                        } else {
                            stringResource(R.string.feature_series_resume)
                        },
                        color = Color.White,
                    )
                }
            }
        }

        // The sentence S5-08 asks for, next to the control it explains. A bar
        // that does not move without saying why is the defect; this is the fix.
        if (progress.seek == SeekAvailability.REFUSED) {
            Text(
                text = stringResource(R.string.feature_series_seek_refused),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Milliseconds as a clock, and the hour only when there is one.
 *
 * `0:04:12` on a forty-minute episode wastes the width that matters on a phone,
 * and `1:47:03` on a long episode needs it. The format follows the value.
 */
private fun Long.asClock(): String {
    val totalSeconds = (this / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val minutes = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
    val hours = totalSeconds / SECONDS_PER_HOUR

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Hides the system bars while this screen is composed.
 *
 * The channel player's, unchanged — `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` so a
 * swipe brings them back rather than taking the back gesture away with them.
 */
@Composable
private fun ImmersiveWhileVisible() {
    val activity = LocalActivity.current ?: return

    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun Failure(
    failure: EpisodePlayerFailure,
    title: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        title?.let {
            Text(text = it, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }

        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )

        // Only where it can help: a retry on an episode this device cannot decode
        // costs a wait to learn what the sentence above already said.
        if (failure.isRetryable()) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.feature_series_player_retry))
            }
        }

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.feature_series_player_back))
        }
    }
}

@Composable
private fun EpisodePlayerFailure.message(): String = when (this) {
    is EpisodePlayerFailure.Unreachable -> stringResource(R.string.feature_series_player_unreachable)

    is EpisodePlayerFailure.TooManyStreams -> if (allowed == null) {
        stringResource(R.string.feature_series_player_too_many_streams)
    } else {
        stringResource(R.string.feature_series_player_too_many_streams_count, allowed)
    }

    EpisodePlayerFailure.SourceNotReady -> stringResource(R.string.feature_series_player_not_ready)
    EpisodePlayerFailure.SubscriptionExpired -> stringResource(R.string.feature_series_player_expired)
    EpisodePlayerFailure.EpisodeGone -> stringResource(R.string.feature_series_player_episode_gone)
    EpisodePlayerFailure.Unplayable -> stringResource(R.string.feature_series_player_unplayable)
    EpisodePlayerFailure.Unexpected -> stringResource(R.string.feature_series_player_unexpected)
}

private const val SCRIM_ALPHA = 0.6f
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 3_600L

/**
 * Five seconds, against the television's ten.
 *
 * A phone is held: the card is a thumb away and the decision is instant. Ten
 * seconds at arm's length is nine seconds of waiting for something already
 * decided — and the same ten seconds across a room is barely enough to find the
 * remote.
 */
private const val AUTO_ADVANCE_SECONDS = 5
