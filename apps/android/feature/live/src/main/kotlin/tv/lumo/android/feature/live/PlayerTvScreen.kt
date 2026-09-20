package tv.lumo.android.feature.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.designsystem.component.LumoMockMissingData
import tv.lumo.android.core.designsystem.component.LumoMockNotImplemented
import tv.lumo.android.core.designsystem.component.LumoTvAcknowledgeDialog
import tv.lumo.android.core.designsystem.component.LumoTvAudioTrackSheet
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.core.player.ui.LumoVideoSurface
import tv.lumo.android.core.player.ui.asChoices

/**
 * The television player (US-09, US-10), laid out as `TV4 — Lecteur`.
 *
 * <h2>At rest, nothing but the picture</h2>
 *
 * Full screen, no chrome, and the rail is gone (`LumoTvApp` hides it on this
 * route). The picture itself takes the focus, so the D-pad has somewhere to be:
 * `OK` opens the bar, `UP` the audio picker when there is more than one track,
 * `BACK` leaves. The bar closes on its own after five seconds without a press.
 *
 * <h2>The bar, as the canvas draws it</h2>
 *
 * Number, name, what is on and what is next, a progress bar, `DIRECT`, and a row
 * of pills: pause, subtitles, quality, guide. Of those, the programme and its
 * progress are the guide the server does not serve yet, and subtitles, quality
 * and the guide are screens that do not exist: each is on the bar and says so
 * with the shared `[mock]` badge, rather than being left off and looking like a
 * decision.
 *
 * Pause is real. It is not timeshift (v2): a paused live stream resumes at the
 * live edge or thereabouts, which is what the button honestly does.
 *
 * <h2>When playback has failed</h2>
 *
 * The picture is replaced by `TV6 — erreur de flux`: what happened, and two
 * answers, `Réessayer` and `Chaîne suivante`. The focus lands on the first, so
 * one press of OK is the obvious recovery. A silent black screen is exactly what
 * an unhandled player error looks like, and US-09 forbids it.
 */
@Composable
fun PlayerTvScreen(
    channelId: String,
    channelName: String?,
    onBack: (channelId: String) -> Unit,
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

    LaunchedEffect(channelId) { viewModel.start(channelId) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    var infoVisible by remember { mutableStateOf(false) }
    var pickingAudio by remember { mutableStateOf(false) }
    // Which pill said "[mock]" last, so the notice sits next to the bar rather
    // than interrupting it.
    var mockNotice by remember { mutableStateOf(false) }
    val canPickAudio = audioTracks.size > 1
    var activityTick by remember { mutableIntStateOf(0) }

    // The channel the player knows beats the one the route named: after
    // "next channel" they differ, and the bar must say where we are now.
    val currentId = state.channel?.id ?: channelId
    val currentName = state.channel?.name ?: channelName

    BackHandler {
        if (infoVisible) infoVisible = false else onBack(currentId)
    }

    // Keyed on the tick as well as on visibility: every press restarts the five
    // seconds instead of letting the first one run out under somebody's thumb.
    LaunchedEffect(infoVisible, activityTick) {
        if (!infoVisible) return@LaunchedEffect
        delay(INFO_BAR_TIMEOUT_MILLIS)
        infoVisible = false
        mockNotice = false
    }

    val surface = remember { FocusRequester() }
    val retry = remember { FocusRequester() }
    val pause = remember { FocusRequester() }
    // Not while the deleted-source dialog is up: it is a window of its own, it
    // holds the focus, and a failure panel drawn under it would ask for the
    // focus of a button nobody can reach.
    val failed = state.failure != null && !sourceDeleted

    LaunchedEffect(failed, infoVisible) {
        // Whichever of the three is the real target right now. Requesting focus
        // on a composable that is not there throws, which is why this follows
        // the state rather than running once.
        runCatching {
            when {
                failed -> retry.requestFocus()
                infoVisible -> pause.requestFocus()
                else -> surface.requestFocus()
            }
        }
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
            // Keys are read here, on the way down, rather than on whichever
            // child holds the focus: the video surface is an Android view that
            // takes the focus for itself on some sets, and OK pressed on it
            // would otherwise reach nothing.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Any key while the bar is up restarts its five seconds: moving
                // along the pills is activity, and a bar that vanishes under a
                // thumb halfway to "Guide" is a bar that was not listening.
                if (infoVisible) activityTick++
                when (event.key) {
                    Key.DirectionUp -> {
                        if (!canPickAudio || infoVisible || failed) return@onPreviewKeyEvent false
                        pickingAudio = true
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        // The centre key on the picture opens the bar. Once the
                        // bar is up the focus is on its pills, and OK is theirs.
                        if (infoVisible || failed || pickingAudio) return@onPreviewKeyEvent false
                        infoVisible = true
                        activityTick++
                        true
                    }

                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // The centre key on the picture. It only ever opens the bar —
                // once the bar is up the focus is on its pills, and OK is theirs.
                infoVisible = true
                activityTick++
            },
        contentAlignment = Alignment.Center,
    ) {
        LumoVideo(viewModel)

        // The source was deleted from another device, and the server has
        // confirmed it (US-024). Playback is already stopped. One sentence, one
        // button that takes the focus, and `BACK` does what the button does —
        // leave the player; what is browsed next is the shell's to settle.
        if (sourceDeleted) {
            LumoTvAcknowledgeDialog(
                message = stringResource(DataR.string.core_data_source_deleted_message),
                actionLabel = stringResource(DataR.string.core_data_source_deleted_continue),
                onAcknowledge = { viewModel.onSourceDeletedAcknowledged { onBack(currentId) } },
            )
        }

        when {
            sourceDeleted -> Unit

            failed -> Failure(
                failure = state.failure!!,
                channelName = currentName,
                onRetry = viewModel::retry,
                onNext = viewModel::next,
                onOpenSources = onOpenSources,
                retryFocus = retry,
            )

            infoVisible -> InfoBar(
                channelName = currentName,
                channelNumber = state.channel?.number,
                quality = state.channel?.quality,
                paused = state.paused,
                audioHint = canPickAudio,
                mockNotice = mockNotice,
                pauseFocus = pause,
                onPause = {
                    viewModel.togglePause()
                    activityTick++
                },
                onMock = {
                    mockNotice = true
                    activityTick++
                },
            )
        }

        if (pickingAudio) {
            val resources = LocalContext.current.resources
            LumoTvAudioTrackSheet(
                title = stringResource(R.string.feature_live_audio_track),
                tracks = audioTracks.asChoices(
                    unnamed = { position ->
                        resources.getString(R.string.feature_live_audio_track_number, position)
                    },
                    unsupported = stringResource(R.string.feature_live_audio_unsupported),
                ),
                onSelect = viewModel::selectAudioTrack,
                onDismiss = {
                    pickingAudio = false
                    runCatching { surface.requestFocus() }
                },
            )
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

@Composable
private fun InfoBar(
    channelName: String?,
    channelNumber: Int?,
    quality: String?,
    paused: Boolean,
    audioHint: Boolean,
    mockNotice: Boolean,
    pauseFocus: FocusRequester,
    onPause: () -> Unit,
    onMock: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Ink fading up from the bottom, so the bar reads over any picture
            // without an opaque slab across a third of it.
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to LumoColors.Ink.copy(alpha = 0.92f),
                ),
            )
            .tvOverscan(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The number, in the monospaced face the canvas gives every
                // technical figure.
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(LumoTvShapes.small)
                        .background(LumoColors.SurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = channelNumber?.let { "%03d".format(it) } ?: "—",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                        color = LumoColors.OnDarkMuted,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
                ) {
                    Text(
                        text = channelName ?: stringResource(R.string.feature_live_tv_unknown_channel),
                        style = MaterialTheme.typography.titleLarge,
                        color = LumoColors.OnDark,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    // « 21:00 – 22:00 · Généralistes · HD · Ensuite : Météo » —
                    // the guide is not served yet; the quality is real.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LumoMockMissingData(scale = TV_TYPE_SCALE)
                        quality?.let {
                            Text(
                                text = "· $it",
                                style = MaterialTheme.typography.labelLarge,
                                color = LumoColors.OnDarkMuted,
                            )
                        }
                    }
                }

                LiveBadge()
            }

            // The programme's progress: track only, the guide being what it is.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(LumoTvShapes.pill)
                    .background(LumoColors.SurfaceRaised),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LumoTvButton(
                    text = stringResource(
                        if (paused) R.string.feature_live_tv_resume else R.string.feature_live_tv_pause,
                    ),
                    onClick = onPause,
                    primary = true,
                    focusRequester = pauseFocus,
                )
                LumoTvButton(text = stringResource(R.string.feature_live_tv_subtitles), onClick = onMock)
                LumoTvButton(text = stringResource(R.string.feature_live_tv_quality), onClick = onMock)
                LumoTvButton(text = stringResource(R.string.feature_live_tv_guide), onClick = onMock)

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
                ) {
                    if (mockNotice) LumoMockNotImplemented(scale = TV_TYPE_SCALE)
                    Text(
                        text = if (audioHint) {
                            stringResource(R.string.feature_live_tv_player_hints)
                        } else {
                            stringResource(R.string.feature_live_tv_back_hint)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = LumoColors.OnDarkMuted,
                    )
                }
            }
        }
    }
}

/** « ● DIRECT » — the canvas's red pill, in the charter's `danger`. */
@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier
            .border(2.dp, LumoColors.Error.copy(alpha = 0.5f), LumoTvShapes.pill)
            .background(LumoColors.Error.copy(alpha = 0.12f), LumoTvShapes.pill)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(LumoColors.Error),
        )
        Text(
            text = stringResource(R.string.feature_live_tv_live_badge),
            style = MaterialTheme.typography.labelLarge,
            color = LumoColors.Error,
        )
    }
}

/**
 * Playback failed, and there is something to say and two things to press.
 */
@Composable
private fun Failure(
    failure: PlayerFailure,
    channelName: String?,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onOpenSources: () -> Unit,
    retryFocus: FocusRequester,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscan(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(FAILURE_WIDTH)
                .clip(LumoTvShapes.large)
                .background(LumoColors.Surface)
                .padding(LumoSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Text(
                text = "⚠",
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.Error,
            )
            channelName?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = LumoColors.OnDarkMuted)
            }
            Text(
                text = stringResource(R.string.feature_live_tv_failure_title),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Text(
                text = failure.tvMessage(),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
            )
            Spacer(modifier = Modifier.height(LumoSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
                if (failure == PlayerFailure.CredentialsRefused) {
                    // The provider refused the credentials (C4, D2): every channel
                    // of this source would be refused alike, so "next channel" is
                    // not an offer. The one target is the screen that says where
                    // a source is corrected.
                    LumoTvButton(
                        text = stringResource(R.string.feature_live_player_open_sources),
                        onClick = onOpenSources,
                        primary = true,
                        focusRequester = retryFocus,
                    )
                } else if (failure.isRetryable()) {
                    LumoTvButton(
                        text = stringResource(R.string.feature_live_player_retry),
                        onClick = onRetry,
                        primary = true,
                        focusRequester = retryFocus,
                    )
                    LumoTvButton(
                        text = stringResource(R.string.feature_live_tv_next_channel),
                        onClick = onNext,
                    )
                } else {
                    LumoTvButton(
                        text = stringResource(R.string.feature_live_tv_next_channel),
                        onClick = onNext,
                        primary = true,
                        focusRequester = retryFocus,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerFailure.tvMessage(): String = when (this) {
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

private const val INFO_BAR_TIMEOUT_MILLIS = 5_000L
private val FAILURE_WIDTH = 760.dp

/** `platforms.tv.typeScale` — what the mock badge grows by on a television. */
private const val TV_TYPE_SCALE = 1.75f
