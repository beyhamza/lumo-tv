package tv.lumo.android.feature.series

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.core.player.ui.LumoVideoSurface

/**
 * Watching an episode on a television (US-15, S6-06).
 *
 * `VodPlayerTvScreen` with one thing added, and the added thing is what makes an
 * application a series application. Everything else is deliberately identical:
 * nothing drawn on the picture at rest, the surface focusable so `OK` has somewhere
 * to arrive, `OK` opening the information bar and `LEFT`/`RIGHT` opening the
 * progress bar **alone** — because the two keys are one gesture apart on every
 * remote ever made, and a title appearing every time somebody nudges ten seconds
 * forward is precisely the accident that rule exists to prevent.
 *
 * <h2>"Next episode", and the default behaviour is the whole of the argument</h2>
 *
 * At the end of an episode a card offers the next one and counts ten seconds down.
 * `OK` starts it now, `BACK` returns to the series.
 *
 * **The countdown stops at the first key press, and the card stays.** Somebody
 * pressing a key is somebody watching — they reached for the remote *because* the
 * credits started — and beginning the next episode under their thumb is the kind of
 * thing that is not forgiven. What they lose is the automatic part; the offer is
 * still there, and `OK` still takes it.
 *
 * **Advancing does not navigate.** The episode is replaced inside this screen, so
 * six of them leave one back stack entry and `BACK` returns to the series rather
 * than walking backwards through an evening. Chaining across a season boundary and
 * stopping at the end of a series are `EpisodePlayerViewModel`'s, shared with the
 * phone, and the rule itself is tested in `core:data`.
 *
 * **Ten seconds**, against the phone's five. A remote may be on the arm of the
 * sofa; a phone is already in the hand.
 *
 * <h2>A third focus target, and only while the card is up</h2>
 *
 * The card is focusable and takes the focus when it appears, so `OK` means "play it
 * now" without anybody having to travel to it. It gives the focus back to the
 * picture when it goes, which is the part a focus map exists to catch: a card that
 * left with the focus still on it would leave a screen where `OK` did nothing.
 */
@Composable
fun EpisodePlayerTvScreen(
    episodeId: String,
    title: String?,
    resumeFromMs: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpisodePlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(episodeId) {
        viewModel.start(
            episodeId = episodeId,
            title = title,
            autoAdvanceSeconds = AUTO_ADVANCE_SECONDS,
            resumeFromMs = resumeFromMs,
        )
    }

    DisposableEffect(Unit) {
        // Stop, not release: the player is the process's one codec, and stopping
        // is what clears the credential-bearing URL out of it.
        onDispose { viewModel.stop() }
    }

    // BACK returns to the series, which is the entry underneath — and that screen's
    // own BACK is what puts the grid back on the right card.
    BackHandler(onBack = onBack)

    // The last episode of the series has finished and nothing is offered. The
    // screen leaves rather than holding a frozen last frame, which at three metres
    // is indistinguishable from a set that has stopped answering.
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    var overlay by remember { mutableStateOf(Overlay.None) }
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
    val upNext = remember { FocusRequester() }
    val failed = state.failure != null
    val offering = state.upNext != null

    // Whichever of the three is the real target right now. Requesting focus on a
    // composable that is not there throws, and the card appearing and leaving is
    // exactly when that would happen.
    LaunchedEffect(failed, offering) {
        runCatching {
            when {
                failed -> retry.requestFocus()
                offering -> upNext.requestFocus()
                else -> surface.requestFocus()
            }
        }
    }

    // What the player is called right now, and it changes when an episode advances.
    // The route's title is only the answer for the first one.
    val playingTitle = state.episode
        ?.let { episode ->
            episode.name
                ?: stringResource(R.string.feature_series_episode, episode.episodeNumber)
        }
        ?: title

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(surface)
            // The picture is not a control, so `focusable` rather than `clickable`:
            // it takes the D-pad without pretending to be a button.
            .focusable()
            // Preview, so the arrow keys are taken before Compose's focus search
            // sees them. Without it they would look for something to focus, find
            // nothing on a full-screen picture, and be silently dropped.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Any key at all, before anything else this screen does with it.
                // The offer stops counting; it does not go away. See the class
                // documentation — this is the half of the feature that has to be
                // right, and it has to be right for keys this screen ignores too.
                viewModel.keepWatching()

                // Nothing seeks while the offer is up. The episode is over; the
                // keys belong to the card, and moving inside a finished file is
                // not what somebody pressing a key at that moment wants.
                if (state.upNext != null) return@onPreviewKeyEvent false

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

        when {
            failed -> TvFailure(
                failure = state.failure!!,
                title = playingTitle,
                onRetry = viewModel::retry,
                retryFocus = retry,
            )

            overlay != Overlay.None -> OverlayBar(
                title = playingTitle.takeIf { overlay == Overlay.Info },
                progress = state.progress,
            )
        }

        state.upNext?.let { offer ->
            UpNextCard(
                offer = offer,
                focusRequester = upNext,
                onPlay = viewModel::playNext,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * Which of the two layers is up.
 *
 * An enum rather than two booleans: "both visible" is a state that means nothing,
 * and a screen holding two flags has to decide which one wins on every frame.
 */
private enum class Overlay { None, Info, Progress }

/**
 * The offer at the end of an episode.
 *
 * Over the picture rather than replacing it: the credits are part of what somebody
 * is watching, and a card that blacked them out would answer a question nobody
 * asked.
 *
 * **The count is a number, always**, and not only a shrinking bar. A number is what
 * tells somebody how long they have to decide; an animation tells them that
 * something is happening.
 *
 * There is no "cancel" control, and that is deliberate rather than an omission: on
 * a television `BACK` is a physical key that already returns to the series, and
 * drawing a second target for it would be a control the focus map would have to
 * describe as the long way round to a key press. The phone gets one because its
 * back gesture leaves the player entirely.
 */
@Composable
private fun UpNextCard(
    offer: UpNext,
    focusRequester: FocusRequester,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = offer.episode.name
        ?: stringResource(R.string.feature_series_episode, offer.episode.episodeNumber)
    var focused by remember { mutableStateOf(false) }

    Box(modifier = modifier.tvOverscan()) {
        Column(
            modifier = Modifier
                .width(CARD_WIDTH)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .lumoTvFocus(focused)
                .clip(LumoShapes.medium)
                // Opaque rather than a scrim: text read at three metres against
                // moving video is text read twice.
                .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlay,
                )
                .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            Text(
                text = offer.secondsLeft
                    ?.let { stringResource(R.string.feature_series_up_next_in, it) }
                    ?: stringResource(R.string.feature_series_up_next),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) LumoColors.OnAccent else LumoColors.OnDarkMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = if (focused) LumoColors.OnAccent else LumoColors.OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The overlay, in its two forms.
 *
 * Inside the overscan margin, unlike the picture: a television crops its edges, the
 * video is *supposed* to reach them, and a position nobody can see is a position
 * nobody has.
 */
@Composable
private fun OverlayBar(title: String?, progress: PlaybackProgress) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvOverscan(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LumoShapes.medium)
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
                    R.string.feature_series_position,
                    progress.positionMs.asTvClock(),
                    progress.durationMs?.asTvClock()
                        ?: stringResource(R.string.feature_series_unknown_length),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )

            // The sentence beside the bar it explains. A bar that does not move
            // without saying why is the defect; a bar that is absent is a feature
            // people think we did not ship.
            if (progress.seek == SeekAvailability.REFUSED) {
                Text(
                    text = stringResource(R.string.feature_series_seek_refused),
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
 * second target for keys that already do the job.
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
            .clip(LumoShapes.small)
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
    failure: EpisodePlayerFailure,
    title: String?,
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
            text = stringResource(R.string.feature_series_tv_back_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )

        if (failure.isRetryable()) {
            Text(
                text = stringResource(R.string.feature_series_player_retry),
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
                    .fillMaxWidth(fraction = RETRY_WIDTH_FRACTION),
            )
        }
    }
}

@Composable
private fun EpisodePlayerFailure.tvMessage(): String = when (this) {
    is EpisodePlayerFailure.Unreachable ->
        stringResource(R.string.feature_series_player_unreachable)

    is EpisodePlayerFailure.TooManyStreams -> if (allowed == null) {
        stringResource(R.string.feature_series_player_too_many_streams)
    } else {
        stringResource(R.string.feature_series_player_too_many_streams_count, allowed)
    }

    EpisodePlayerFailure.SourceNotReady ->
        stringResource(R.string.feature_series_player_not_ready)

    EpisodePlayerFailure.SubscriptionExpired ->
        stringResource(R.string.feature_series_player_expired)

    EpisodePlayerFailure.EpisodeGone ->
        stringResource(R.string.feature_series_player_episode_gone)

    EpisodePlayerFailure.Unplayable ->
        stringResource(R.string.feature_series_player_unplayable)

    EpisodePlayerFailure.Unexpected ->
        stringResource(R.string.feature_series_player_unexpected)
}

/** Milliseconds as a clock, the hour only when there is one. The film player's. */
private fun Long.asTvClock(): String {
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
 * Ten seconds, against the phone's five.
 *
 * A remote may be on the arm of the sofa or under a cushion; a phone is already in
 * the hand. The number has to be long enough to reach for the remote and short
 * enough that somebody who wants the next episode is not waiting for permission.
 */
private const val AUTO_ADVANCE_SECONDS = 10

/** Ten seconds a press. The film player's step, for the film player's reason. */
private const val SEEK_STEP_MILLIS = 10_000L

/** Five seconds of inactivity, as US-10 asks of the channel player. */
private const val OVERLAY_TIMEOUT_MILLIS = 5_000L

/** Wide enough to read at three metres, narrow enough not to cover the picture. */
private val CARD_WIDTH = 420.dp

private val BAR_HEIGHT = 8.dp

private const val RETRY_WIDTH_FRACTION = 0.4f

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 3_600L
