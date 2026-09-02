package tv.lumo.android.feature.vod

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.component.LumoAudioTrackSheet
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.core.player.ui.LumoVideoSurface
import tv.lumo.android.core.player.ui.asChoices

/**
 * Watching a film on the phone (US-13).
 *
 * The channel player's screen, with two additions and one subtraction, and each
 * of the three is a property of a film rather than a design choice:
 *
 * - **a scrubber**, because a film without one is not watchable;
 * - **a play/pause control**, for the same reason — pausing a live channel is a
 *   different act from pausing a film, and only one of them is expected;
 * - **no live indicator**, which would be a lie about a file.
 *
 * <h2>The scrubber has four states, and three of them are not a scrubber</h2>
 *
 * Seeking a progressive file needs the user's own server to answer HTTP `Range`
 * requests, and many IPTV panels do not. S5-08 is exact about what to do:
 * *"un curseur qui ne bouge pas sans dire pourquoi est un défaut ; un curseur
 * absent est une fonction qu'on croit ne pas avoir livrée."* So a refusal draws
 * the bar, disabled, **with the reason next to it** — never nothing, and never a
 * control that silently ignores a thumb.
 *
 * Media3 finds this out at the first attempt, which means the change happens
 * *during* playback: a scrubber that worked a second ago becomes disabled and
 * explains itself. That is the honest sequence, because it is the order in which
 * the server actually answers.
 */
@Composable
fun VodPlayerMobileScreen(
    filmId: String,
    sourceId: String,
    title: String?,
    resumeFromMs: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()

    // Local to the screen. Which sheet is open is not something the player or
    // the view model has an opinion about, and a choice that survived a rotation
    // would reopen a list over a picture somebody had come back to watch.
    var pickingAudio by remember { mutableStateOf(false) }

    // Keyed on the film: opening a different one restarts, turning the phone does
    // not — the Activity declares `configChanges` and the player is a singleton.
    LaunchedEffect(filmId) { viewModel.start(filmId, sourceId, title, resumeFromMs) }

    ImmersiveWhileVisible()

    DisposableEffect(Unit) {
        // Stop, not release: the player is shared by the whole process, and
        // stopping is what clears the credential-bearing URL out of it.
        onDispose { viewModel.stop() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
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
        // branch on purpose: a film that stopped playing has a position worth
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
            // resolved inside a plain lambda, which is not a composable scope.
            val resources = LocalContext.current.resources
            LumoAudioTrackSheet(
                title = stringResource(R.string.feature_vod_audio_track),
                tracks = audioTracks.asChoices(
                    unnamed = { position ->
                        resources.getString(R.string.feature_vod_audio_track_number, position)
                    },
                    unsupported = stringResource(R.string.feature_vod_audio_unsupported),
                ),
                onSelect = viewModel::selectAudioTrack,
                onDismiss = { pickingAudio = false },
            )
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
                    R.string.feature_vod_position,
                    (dragging?.toLong() ?: progress.positionMs).asClock(),
                    duration?.asClock() ?: stringResource(R.string.feature_vod_unknown_length),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
                onPickAudio?.let { pick ->
                    TextButton(onClick = pick) {
                        Text(
                            text = stringResource(R.string.feature_vod_audio_track),
                            color = Color.White,
                        )
                    }
                }

                TextButton(onClick = onTogglePlay) {
                    Text(
                        text = if (playing) {
                            stringResource(R.string.feature_vod_pause)
                        } else {
                            stringResource(R.string.feature_vod_resume)
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
                text = stringResource(R.string.feature_vod_seek_refused),
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
 * and `1:47:03` on a film needs it. The format follows the value.
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
    failure: VodPlayerFailure,
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

        // Only where it can help: a retry on a film this device cannot decode
        // costs a wait to learn what the sentence above already said.
        if (failure.isRetryable()) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.feature_vod_player_retry))
            }
        }

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.feature_vod_player_back))
        }
    }
}

@Composable
private fun VodPlayerFailure.message(): String = when (this) {
    is VodPlayerFailure.Unreachable -> stringResource(R.string.feature_vod_player_unreachable)

    is VodPlayerFailure.TooManyStreams -> if (allowed == null) {
        stringResource(R.string.feature_vod_player_too_many_streams)
    } else {
        stringResource(R.string.feature_vod_player_too_many_streams_count, allowed)
    }

    VodPlayerFailure.SourceNotReady -> stringResource(R.string.feature_vod_player_not_ready)
    VodPlayerFailure.SubscriptionExpired -> stringResource(R.string.feature_vod_player_expired)
    VodPlayerFailure.FilmGone -> stringResource(R.string.feature_vod_player_film_gone)
    VodPlayerFailure.Unplayable -> stringResource(R.string.feature_vod_player_unplayable)
    VodPlayerFailure.Unexpected -> stringResource(R.string.feature_vod_player_unexpected)
}

private const val SCRIM_ALPHA = 0.6f
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 3_600L
