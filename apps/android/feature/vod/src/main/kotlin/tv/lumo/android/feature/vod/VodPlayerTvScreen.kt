package tv.lumo.android.feature.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.designsystem.component.LumoTvAcknowledgeDialog
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.core.designsystem.component.LumoTvAudioTrackSheet
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.core.player.ui.LumoVideoSurface
import tv.lumo.android.core.player.ui.asChoices

/**
 * Watching a film on a television (US-13).
 *
 * The focus map is `docs/design/tv-focus-map.md`. This is `PlayerTvScreen` with
 * the one thing a film needs and a channel cannot have, and everything else is
 * deliberately unchanged: nothing drawn on the picture at rest, the surface
 * focusable so `OK` has somewhere to arrive, five seconds of *inactivity* before
 * an overlay leaves.
 *
 * <h2>`LEFT` and `RIGHT` move through the film, and open no information bar</h2>
 *
 * This task is explicit about it, and the reason is that the two are a single
 * gesture away from each other on every remote ever made. So the two overlays are
 * genuinely two:
 *
 * - **`OK` opens the information bar** — the film's title, as on the channel
 *   player, with the progress bar under it;
 * - **`LEFT`/`RIGHT` seek, and open the progress bar alone.** No title, no panel.
 *   Somebody skipping a scene has not asked to be told what they are watching.
 *
 * A key that did both would put a title over the picture every time somebody
 * nudged ten seconds forward, which is precisely the accident the task names.
 *
 * <h2>When the server will not seek, the keys say so rather than doing nothing</h2>
 *
 * Seeking a progressive file needs the user's own server to answer HTTP `Range`
 * requests, and a great many panels do not (`core:player` reports it as
 * `SeekAvailability.REFUSED`). `LEFT` and `RIGHT` then still open the bar — drawn
 * where it is, disabled, **with the sentence beside it**. A key that appears dead
 * is a remote somebody thinks has stopped working.
 */
@Composable
fun VodPlayerTvScreen(
    filmId: String,
    sourceId: String,
    title: String?,
    resumeFromMs: Long,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val sourceDeleted by viewModel.sourceDeleted.collectAsStateWithLifecycle()

    // Coming back to the foreground is one of the moments the product names for
    // noticing a source deleted elsewhere (C4, D5). The `ON_START` of opening
    // the player reaches nobody: the watch has not begun, and need not have.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    LaunchedEffect(filmId) { viewModel.start(filmId, sourceId, title, resumeFromMs) }

    DisposableEffect(Unit) {
        // Stop, not release: the player is the process's one codec, and stopping
        // is what clears the credential-bearing URL out of it.
        onDispose { viewModel.stop() }
    }

    // BACK returns to the film's own screen, which is the entry underneath — and
    // that screen's own BACK is what puts the grid back on the right card.
    BackHandler(onBack = onBack)

    var overlay by remember { mutableStateOf(Overlay.None) }

    // A layer of its own rather than a third `Overlay` value: this one takes
    // focus and the bar never does, so the two cannot be the same kind of thing.
    var pickingAudio by remember { mutableStateOf(false) }
    val canPickAudio = audioTracks.size > 1
    var activityTick by remember { mutableIntStateOf(0) }

    // Keyed on the tick as well as on the overlay: every press restarts the five
    // seconds instead of letting the first one run out under somebody's thumb.
    LaunchedEffect(overlay, activityTick) {
        if (overlay == Overlay.None) return@LaunchedEffect
        delay(OVERLAY_TIMEOUT_MILLIS)
        overlay = Overlay.None
    }

    val surface = remember { FocusRequester() }
    val retry = remember { FocusRequester() }
    // Not while the deleted-source dialog is up: it is a window of its own, it
    // holds the focus, and a failure panel drawn under it would ask for the
    // focus of a button nobody can reach.
    val failed = state.failure != null && !sourceDeleted

    LaunchedEffect(failed) {
        // Whichever of the two is the real target right now. Requesting focus on
        // a composable that is not there throws.
        runCatching { if (failed) retry.requestFocus() else surface.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(surface)
            // The picture is not a control, so `focusable` rather than
            // `clickable`: it takes the D-pad without pretending to be a button.
            .focusable()
            // Preview, so the two arrow keys are taken before Compose's focus
            // search sees them. Without it they would look for something to focus,
            // find nothing on a full-screen picture, and be silently dropped.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // UP opens the audio tracks, and only when there is a choice to
                // make. It is the one direction this screen has never used —
                // LEFT and RIGHT seek, CENTRE opens the bar — so nothing is taken
                // away from anybody, and the bar names the key so it is not a
                // gesture somebody has to be told about.
                if (event.key == Key.DirectionUp && canPickAudio) {
                    pickingAudio = true
                    overlay = Overlay.None
                    return@onPreviewKeyEvent true
                }

                val step = when (event.key) {
                    Key.DirectionLeft -> -SEEK_STEP_MILLIS
                    Key.DirectionRight -> SEEK_STEP_MILLIS
                    else -> return@onPreviewKeyEvent false
                }

                // The bar opens either way. When the server refuses to seek it
                // opens carrying the reason, which is the difference between a
                // limitation and a remote that has stopped working.
                overlay = Overlay.Progress
                activityTick++
                viewModel.seekTo(state.progress.positionMs + step)
                true
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // The centre key, and it only ever opens the information bar.
                overlay = Overlay.Info
                activityTick++
            },
        contentAlignment = Alignment.Center,
    ) {
        LumoVideoSurface(player = viewModel.player, modifier = Modifier.fillMaxSize())

        // The source was deleted from another device, and the server has
        // confirmed it (US-024). Playback is already stopped. One sentence, one
        // button that takes the focus, and `BACK` does what the button does —
        // leave the player; what is browsed next is the shell's to settle.
        if (sourceDeleted) {
            LumoTvAcknowledgeDialog(
                message = stringResource(DataR.string.core_data_source_deleted_message),
                actionLabel = stringResource(DataR.string.core_data_source_deleted_continue),
                onAcknowledge = { viewModel.onSourceDeletedAcknowledged(onBack) },
            )
        }

        when {
            sourceDeleted -> Unit

            failed -> TvFailure(
                failure = state.failure!!,
                title = title,
                onRetry = viewModel::retry,
                onOpenSources = onOpenSources,
                retryFocus = retry,
            )

            overlay != Overlay.None -> OverlayBar(
                title = title.takeIf { overlay == Overlay.Info },
                progress = state.progress,
                audioHint = canPickAudio,
            )
        }

        if (pickingAudio) {
            val resources = LocalContext.current.resources
            LumoTvAudioTrackSheet(
                title = stringResource(R.string.feature_vod_audio_track),
                tracks = audioTracks.asChoices(
                    unnamed = { position ->
                        resources.getString(R.string.feature_vod_audio_track_number, position)
                    },
                    unsupported = stringResource(R.string.feature_vod_audio_unsupported),
                ),
                onSelect = viewModel::selectAudioTrack,
                onDismiss = {
                    pickingAudio = false
                    // The picture takes the keys back. Without this the remote
                    // would be pointing at a panel that no longer exists, and the
                    // next press would go nowhere.
                    runCatching { surface.requestFocus() }
                },
            )
        }
    }
}

/**
 * Which of the two layers is up.
 *
 * An enum rather than two booleans: "both visible" is a state that means nothing,
 * and a screen holding two flags has to decide which one wins on every frame it
 * draws.
 */
private enum class Overlay { None, Info, Progress }

/**
 * The overlay, in its two forms.
 *
 * Inside the overscan margin, unlike the picture: a television crops its edges,
 * the video is *supposed* to reach them, and a position nobody can see is a
 * position nobody has.
 */
@Composable
private fun OverlayBar(title: String?, progress: PlaybackProgress, audioHint: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvOverscan(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LumoTvShapes.medium)
                // Opaque rather than a scrim over the picture: text read at three
                // metres against moving video is text read twice.
                .background(LumoColors.SurfaceRaised)
                .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDark,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            ProgressBar(progress)

            Text(
                text = stringResource(
                    R.string.feature_vod_position,
                    progress.positionMs.asTvClock(),
                    progress.durationMs?.asTvClock()
                        ?: stringResource(R.string.feature_vod_unknown_length),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )

            // Only where there is something to open. A television has no key
            // labelled "audio", so the one that does it has to be named — and
            // naming a key that does nothing would be worse than saying nothing.
            if (audioHint) {
                Text(
                    text = stringResource(R.string.feature_vod_audio_track_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            // The sentence this task asks for, next to the bar it explains. A bar
            // that does not move without saying why is the defect; a bar that is
            // absent is a feature people think we did not ship.
            if (progress.seek == SeekAvailability.REFUSED) {
                Text(
                    text = stringResource(R.string.feature_vod_seek_refused),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumoColors.OnDarkMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * The bar itself: drawn, never focusable.
 *
 * It is a **read-out**, not a control. `LEFT` and `RIGHT` are bound to the screen
 * and work whether or not the bar is up, so a focusable slider here would be a
 * second target for keys that already do the job — and it would take the D-pad
 * away from a picture that should only be listening for those two, `OK` and
 * `BACK`.
 *
 * Muted rather than the accent when the server refuses to seek: the same shape,
 * visibly inert, which is what "disabled and explained" looks like at three
 * metres.
 */
@Composable
private fun ProgressBar(progress: PlaybackProgress) {
    val duration = progress.durationMs
    val fraction = if (duration != null && duration > 0L) {
        (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        // Unknown length: an empty track rather than a full one. A bar that draws
        // itself complete because the end is unknown is a bar that lies.
        0f
    }
    val enabled = progress.seek == SeekAvailability.AVAILABLE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(LumoTvShapes.small)
            .background(LumoColors.Surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(if (enabled) LumoColors.Accent else LumoColors.OnDarkMuted),
        )
    }
}

/**
 * Playback failed. Same sentences as the phone, at the television scale.
 *
 * The retry button becomes the focus target, because now there *is* something to
 * press — the surface has nothing left to open.
 */
@Composable
private fun TvFailure(
    failure: VodPlayerFailure,
    title: String?,
    onRetry: () -> Unit,
    onOpenSources: () -> Unit,
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
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDarkMuted,
            )
        }

        Text(
            text = failure.tvMessage(),
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )

        Text(
            text = stringResource(R.string.feature_vod_tv_back_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )

        // The provider refused the credentials (C4, D2): retrying cannot help, and
        // the one target is the screen that says where a source is corrected.
        if (failure == VodPlayerFailure.CredentialsRefused) {
            LumoTvButton(
                text = stringResource(R.string.feature_vod_player_open_sources),
                onClick = onOpenSources,
                primary = true,
                focusRequester = retryFocus,
            )
        }

        if (failure.isRetryable()) {
            Text(
                text = stringResource(R.string.feature_vod_player_retry),
                style = MaterialTheme.typography.titleLarge,
                color = if (focused) LumoColors.OnAccent else LumoColors.OnDark,
                modifier = Modifier
                    .focusRequester(retryFocus)
                    .onFocusChanged { focused = it.isFocused }
                    .lumoTvFocus(focused)
                    .clip(LumoTvShapes.medium)
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

@Composable
private fun VodPlayerFailure.tvMessage(): String = when (this) {
    is VodPlayerFailure.Unreachable -> stringResource(R.string.feature_vod_player_unreachable)

    is VodPlayerFailure.TooManyStreams -> if (allowed == null) {
        stringResource(R.string.feature_vod_player_too_many_streams)
    } else {
        stringResource(R.string.feature_vod_player_too_many_streams_count, allowed)
    }

    VodPlayerFailure.SourceNotReady -> stringResource(R.string.feature_vod_player_not_ready)
    VodPlayerFailure.CredentialsRefused -> stringResource(R.string.feature_vod_player_auth_failed)
    VodPlayerFailure.SubscriptionExpired -> stringResource(R.string.feature_vod_player_expired)
    VodPlayerFailure.FilmGone -> stringResource(R.string.feature_vod_player_film_gone)
    VodPlayerFailure.Unplayable -> stringResource(R.string.feature_vod_player_unplayable)
    VodPlayerFailure.Unexpected -> stringResource(R.string.feature_vod_player_unexpected)
}

/**
 * Milliseconds as a clock, the hour only when there is one.
 *
 * The phone's formatter, repeated rather than shared: it is six lines, and the
 * alternative is a `core:` module holding one string function that two screens of
 * the same feature call.
 */
private fun Long.asTvClock(): String {
    val totalSeconds = (this / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val seconds = totalSeconds % SECONDS_PER_MINUTE_L
    val minutes = (totalSeconds / SECONDS_PER_MINUTE_L) % MINUTES_PER_HOUR
    val hours = totalSeconds / SECONDS_PER_HOUR

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Ten seconds a press.
 *
 * The step every television player has settled on, and the reason is that a
 * remote has no scrubber: the unit has to be small enough to land on a line of
 * dialogue and large enough that crossing an advert break is a handful of presses
 * rather than a minute of them.
 */
private const val SEEK_STEP_MILLIS = 10_000L

/** Five seconds of inactivity, as US-10 asks of the channel player. */
private const val OVERLAY_TIMEOUT_MILLIS = 5_000L

private val BAR_HEIGHT = 8.dp

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE_L = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 3_600L
