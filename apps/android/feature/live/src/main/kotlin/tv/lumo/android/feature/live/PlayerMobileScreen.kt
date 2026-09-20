package tv.lumo.android.feature.live

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.designsystem.component.LumoAcknowledgeDialog
import tv.lumo.android.core.designsystem.component.LumoAudioTrackSheet
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.core.player.ui.LumoVideoSurface
import tv.lumo.android.core.player.ui.asChoices

/**
 * Watching a channel on the phone (US-09).
 *
 * <h2>Rotation does not interrupt anything, and that is not this file's doing</h2>
 *
 * Three things have to be true together, and only the third is visible here: the
 * Activity declares `configChanges` for orientation, so it is not recreated; the
 * player is a process-wide singleton, so it is not rebuilt; and this screen never
 * restarts playback for a channel it is already playing. Miss any one and the
 * stream restarts from the buffer's beginning on every turn of the wrist.
 *
 * <h2>Full screen means the bars go away, and come back</h2>
 *
 * The video is full-bleed and the system bars are hidden while this screen is up.
 * `DisposableEffect` puts them back on the way out — a player that leaves a phone
 * without a status bar is a player people force-quit.
 *
 * <h2>Never a silent black rectangle</h2>
 *
 * The acceptance criterion says it in as many words. Every failure this screen
 * can meet ends in a sentence, and the two that are worth trying again end in a
 * button.
 */
@Composable
fun PlayerMobileScreen(
    channelId: String,
    channelName: String?,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val sourceDeleted by viewModel.sourceDeleted.collectAsStateWithLifecycle()

    // Coming back to the foreground is one of the moments the product names for
    // noticing a source deleted elsewhere (C4, D5). The `ON_START` of opening
    // the player reaches nobody: the watch has not begun, and need not have.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    // Local to the screen. Which sheet is open is not something the player or the
    // view model has an opinion about, and a choice that survived a rotation would
    // reopen a list over a picture somebody had come back to watch.
    var pickingAudio by remember { mutableStateOf(false) }

    // Keyed on the channel: opening a different one restarts, turning the phone
    // does not.
    LaunchedEffect(channelId) { viewModel.start(channelId) }

    ImmersiveWhileVisible()

    DisposableEffect(Unit) {
        onDispose {
            // Stop, not release: the player is shared by the whole process, and
            // stopping is what clears the credential-bearing URL out of it.
            viewModel.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Black rather than the theme's background: it is what sits around a
            // 16:9 picture on a taller screen, and any other colour reads as a
            // letterbox somebody forgot.
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        LumoVideoSurface(
            player = viewModel.player,
            modifier = Modifier.fillMaxSize(),
        )

        when {
            // The source was deleted from another device, and the server has
            // confirmed it (US-024). Playback is already stopped; one sentence,
            // one way on, and nothing else is started. Back is that same way on.
            sourceDeleted -> LumoAcknowledgeDialog(
                message = stringResource(DataR.string.core_data_source_deleted_message),
                actionLabel = stringResource(DataR.string.core_data_source_deleted_continue),
                onAcknowledge = { viewModel.onSourceDeletedAcknowledged(onBack) },
            )

            state.failure != null -> Failure(
                failure = state.failure!!,
                channelName = channelName,
                onRetry = viewModel::retry,
                onBack = onBack,
                onOpenSources = onOpenSources,
            )

            // US-09 budgets five seconds for the picture to appear. A spinner is
            // what makes that wait look like a wait rather than a failure.
            state.playback is PlaybackState.Buffering ||
                state.playback is PlaybackState.Idle -> CircularProgressIndicator()
        }

        // The one control on a screen that deliberately has none, and it appears
        // only for a channel that carries a choice — which is why it is drawn
        // rather than hidden behind a tap that would have to be taught. A channel
        // with one audio track shows nothing and the screen is what it was.
        if (audioTracks.size > 1 && state.failure == null && !sourceDeleted) {
            TextButton(
                onClick = { pickingAudio = true },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Text(
                    text = stringResource(R.string.feature_live_audio_track),
                    color = Color.White,
                )
            }
        }

        if (pickingAudio) {
            // `resources` rather than `stringResource`: the fallback name is
            // resolved inside a plain lambda, which is not a composable scope.
            val resources = LocalContext.current.resources
            LumoAudioTrackSheet(
                title = stringResource(R.string.feature_live_audio_track),
                tracks = audioTracks.asChoices(
                    unnamed = { position ->
                        resources.getString(R.string.feature_live_audio_track_number, position)
                    },
                    unsupported = stringResource(R.string.feature_live_audio_unsupported),
                ),
                onSelect = viewModel::selectAudioTrack,
                onDismiss = { pickingAudio = false },
            )
        }
    }
}

/**
 * Hides the system bars for as long as this screen is composed.
 *
 * Swipe from an edge brings them back temporarily, which is the behaviour people
 * expect from a video player and the reason `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
 * exists — hiding them outright would take the back gesture with them.
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
    failure: PlayerFailure,
    channelName: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        channelName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }

        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )

        // Only where it can help. A retry on a channel this device cannot decode,
        // or on a subscription that has expired, costs a wait to learn what the
        // sentence above already said.
        if (failure.isRetryable()) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.feature_live_player_retry))
            }
        }

        // The provider refused the credentials: the one thing that helps is the
        // screen where they are corrected, so that is the button (C4, D2).
        if (failure == PlayerFailure.CredentialsRefused) {
            Button(onClick = onOpenSources) {
                Text(stringResource(R.string.feature_live_player_open_sources))
            }
        }

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.feature_live_player_back))
        }
    }
}

/**
 * Whether trying the same thing again can help.
 *
 * Shared by both surfaces, because the answer is a property of the failure and
 * not of the screen: a stream this device cannot decode will not decode on the
 * second press, on a phone or on a television.
 */
internal fun PlayerFailure.isRetryable(): Boolean = when (this) {
    is PlayerFailure.Unreachable -> retryable
    // Worth one more try: something else may have stopped playing since.
    is PlayerFailure.TooManyStreams -> true
    // A refresh is running, and it ends (C4): the same press works afterwards.
    PlayerFailure.SourceNotReady -> true
    PlayerFailure.CredentialsRefused -> false
    PlayerFailure.SubscriptionExpired -> false
    PlayerFailure.ChannelGone -> false
    PlayerFailure.Unplayable -> false
    PlayerFailure.Unexpected -> true
}

@Composable
private fun PlayerFailure.message(): String = when (this) {
    is PlayerFailure.Unreachable -> stringResource(R.string.feature_live_player_unreachable)

    is PlayerFailure.TooManyStreams -> if (allowed == null) {
        stringResource(R.string.feature_live_player_too_many_streams)
    } else {
        stringResource(R.string.feature_live_player_too_many_streams_count, allowed)
    }

    PlayerFailure.SourceNotReady -> stringResource(R.string.feature_live_player_not_ready)
    PlayerFailure.CredentialsRefused -> stringResource(R.string.feature_live_player_auth_failed)
    PlayerFailure.SubscriptionExpired -> stringResource(R.string.feature_live_player_expired)
    PlayerFailure.ChannelGone -> stringResource(R.string.feature_live_player_channel_gone)
    PlayerFailure.Unplayable -> stringResource(R.string.feature_live_player_unplayable)
    PlayerFailure.Unexpected -> stringResource(R.string.feature_live_player_unexpected)
}
