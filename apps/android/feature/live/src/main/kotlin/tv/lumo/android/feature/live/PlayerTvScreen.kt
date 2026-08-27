package tv.lumo.android.feature.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.core.player.ui.LumoVideoSurface

/**
 * Watching a channel on a television (US-10).
 *
 * The focus map for this screen is `docs/design/tv-focus-map.md`.
 *
 * <h2>At rest there is nothing on the picture</h2>
 *
 * No bar, no gradient, no logo. That is the requirement and it is the right one:
 * this is the screen somebody sits in front of for an hour, and everything drawn
 * over the picture is drawn over the thing they came for.
 *
 * <h2>OK brings the bar, and five seconds of stillness takes it away</h2>
 *
 * Five seconds of **inactivity**, not five seconds: any key press restarts the
 * clock, so somebody reading the channel name slowly does not have it taken away
 * mid-sentence.
 *
 * <h2>The screen is focusable even though it holds no control</h2>
 *
 * It has to be: a television screen with no focus target leaves `BACK` as the only
 * key that does anything (US-10), and this one needs `OK` as well. So the surface
 * itself takes focus, and the D-pad has somewhere to be. When playback has failed
 * the retry button takes over as the target, because then there *is* something to
 * press.
 */
@Composable
fun PlayerTvScreen(
    channelId: String,
    channelName: String?,
    onBack: (channelId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(channelId) { viewModel.start(channelId) }

    DisposableEffect(Unit) {
        // Stop, not release: the player is the process's one codec, and stopping
        // is what clears the credential-bearing URL out of it.
        onDispose { viewModel.stop() }
    }

    // BACK returns to the grid **and says which channel was being watched**, so
    // the list comes back with the remote already on it rather than at the top of
    // a catalogue of fifteen thousand.
    BackHandler { onBack(channelId) }

    var infoVisible by remember { mutableStateOf(false) }
    var activityTick by remember { mutableIntStateOf(0) }

    // Keyed on the tick as well as on visibility: every press restarts the five
    // seconds instead of letting the first one run out under somebody's thumb.
    LaunchedEffect(infoVisible, activityTick) {
        if (!infoVisible) return@LaunchedEffect
        delay(INFO_BAR_TIMEOUT_MILLIS)
        infoVisible = false
    }

    val surface = remember { FocusRequester() }
    val retry = remember { FocusRequester() }
    val failed = state.failure != null

    LaunchedEffect(failed) {
        // Whichever of the two is the real target right now. Requesting focus on
        // a composable that is not there throws, which is why this follows the
        // state rather than running once.
        if (failed) retry.requestFocus() else surface.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(surface)
            // The picture is not a control, so `focusable` rather than
            // `clickable`: it takes the D-pad without pretending the whole screen
            // is a button.
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // The centre key. It only ever opens the bar — pressing it again
                // while the bar is up restarts its clock, which is what somebody
                // still reading expects.
                infoVisible = true
                activityTick++
            },
        contentAlignment = Alignment.Center,
    ) {
        LumoVideo(viewModel)

        when {
            failed -> Failure(
                failure = state.failure!!,
                channelName = channelName,
                onRetry = viewModel::retry,
                retryFocus = retry,
            )

            infoVisible -> InfoBar(channelName)
        }
    }
}

@Composable
private fun LumoVideo(viewModel: PlayerViewModel) {
    LumoVideoSurface(
        player = viewModel.player,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The information bar.
 *
 * Inside the overscan margin, unlike the picture: a television crops its edges,
 * and the video is *supposed* to reach them while a channel name is not. Anything
 * drawn outside that margin may simply not exist for some viewers.
 */
@Composable
private fun InfoBar(channelName: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvOverscan(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = channelName ?: stringResource(R.string.feature_live_tv_unknown_channel),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
            modifier = Modifier
                .clip(LumoShapes.medium)
                // Opaque rather than a scrim over the picture: a name read at
                // three metres against moving video is a name read twice.
                .background(LumoColors.SurfaceRaised)
                .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Playback failed, and there is something to say and possibly something to press.
 *
 * Same sentences as the phone — they come from the same [PlayerFailure] — at the
 * television scale and inside the overscan margin.
 */
@Composable
private fun Failure(
    failure: PlayerFailure,
    channelName: String?,
    onRetry: () -> Unit,
    retryFocus: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .tvOverscan()
            .padding(LumoSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md, Alignment.CenterVertically),
    ) {
        channelName?.let {
            Text(text = it, style = MaterialTheme.typography.titleLarge, color = LumoColors.OnDarkMuted)
        }

        Text(
            text = failure.tvMessage(),
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )

        Text(
            text = stringResource(R.string.feature_live_tv_back_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )

        if (failure.isRetryable()) {
            Text(
                text = stringResource(R.string.feature_live_player_retry),
                style = MaterialTheme.typography.titleLarge,
                color = if (focused) LumoColors.OnAccent else LumoColors.OnDark,
                modifier = Modifier
                    .focusRequester(retryFocus)
                    .onFocusChanged { focused = it.isFocused }
                    .lumoTvFocus(focused)
                    .clip(LumoShapes.medium)
                    .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRetry,
                    )
                    .padding(horizontal = LumoSpacing.xl, vertical = LumoSpacing.md)
                    .fillMaxWidth(fraction = 0.4f),
            )
        }
    }
}

/** The same wording as the phone, resolved here because this is the TV surface. */
@Composable
private fun PlayerFailure.tvMessage(): String = when (this) {
    is PlayerFailure.Unreachable -> stringResource(R.string.feature_live_player_unreachable)

    is PlayerFailure.TooManyStreams -> if (allowed == null) {
        stringResource(R.string.feature_live_player_too_many_streams)
    } else {
        stringResource(R.string.feature_live_player_too_many_streams_count, allowed)
    }

    PlayerFailure.SourceNotReady -> stringResource(R.string.feature_live_player_not_ready)
    PlayerFailure.SubscriptionExpired -> stringResource(R.string.feature_live_player_expired)
    PlayerFailure.ChannelGone -> stringResource(R.string.feature_live_player_channel_gone)
    PlayerFailure.Unplayable -> stringResource(R.string.feature_live_player_unplayable)
    PlayerFailure.Unexpected -> stringResource(R.string.feature_live_player_unexpected)
}

/** Five seconds of inactivity, as US-10 asks. */
private const val INFO_BAR_TIMEOUT_MILLIS = 5_000L
