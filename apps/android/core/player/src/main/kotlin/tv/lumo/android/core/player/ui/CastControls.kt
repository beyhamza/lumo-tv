package tv.lumo.android.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.R
import tv.lumo.android.core.player.cast.CastControl
import tv.lumo.android.core.player.cast.CastNotice
import tv.lumo.android.core.player.cast.CastUi

/**
 * The Cast button, provided by the phone application (`core:cast`, a
 * `MediaRouteButton`). Null on the television, which has no Cast SDK: the screens
 * compose [CastButton] either way and get nothing there.
 */
val LocalCastButton = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

private val NO_CAST = MutableStateFlow(CastUi())

/** The Cast state of [player], or "nothing to cast to" for a player that cannot. */
@Composable
fun rememberCastUi(player: LumoPlayer): CastUi {
    val flow = remember(player) { (player as? CastControl)?.castUi ?: NO_CAST }
    val ui by flow.collectAsStateWithLifecycle()
    return ui
}

/** The Cast button, only when a device is detected and the stream may be sent (S16-01). */
@Composable
fun CastButton(player: LumoPlayer, modifier: Modifier = Modifier) {
    val ui = rememberCastUi(player)
    val button = LocalCastButton.current ?: return
    if (ui.buttonVisible) button(modifier)
}

/**
 * The picture, or the remote control when a Chromecast plays (S16-03).
 *
 * Play/pause and the scrubber stay the screen's own controls — they drive the same
 * player, which forwards them to the receiver. This adds what only a cast has: the
 * device, the artwork, the volume and *Stop casting*.
 *
 * @param onTogglePlay for a screen with no play/pause of its own (the channel
 *   player): the panel draws one while casting. [playing] labels it.
 */
@Composable
fun PlayerPicture(
    player: LumoPlayer,
    modifier: Modifier = Modifier,
    onTogglePlay: (() -> Unit)? = null,
    playing: Boolean = false,
) {
    val ui = rememberCastUi(player)
    val control = player as? CastControl
    if (ui.casting && control != null) {
        CastingPanel(ui = ui, control = control, onTogglePlay = onTogglePlay, playing = playing, modifier = modifier)
    } else {
        LumoVideoSurface(player = player, modifier = modifier)
    }
}

@Composable
private fun CastingPanel(
    ui: CastUi,
    control: CastControl,
    onTogglePlay: (() -> Unit)?,
    playing: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ui.title?.let { title ->
            LumoPoster(posterUrl = ui.artworkUrl, title = title, modifier = Modifier.width(ARTWORK_WIDTH))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = ui.deviceName?.let { stringResource(R.string.core_player_cast_playing_on, it) }
                ?: stringResource(R.string.core_player_cast_playing),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        onTogglePlay?.let { toggle ->
            Button(onClick = toggle) {
                Text(
                    text = stringResource(if (playing) R.string.core_player_cast_pause else R.string.core_player_cast_play),
                )
            }
        }
        ui.volume?.let { volume -> VolumeSlider(volume = volume, onChange = control::setVolume) }
        OutlinedButton(onClick = control::stopCasting) {
            Text(text = stringResource(R.string.core_player_cast_stop), color = Color.White)
        }
    }
}

/** Follows the device, except while a thumb is on it — the scrubber's rule. */
@Composable
private fun VolumeSlider(volume: Float, onChange: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val label = stringResource(R.string.core_player_cast_volume)
    val percent = (((dragging ?: volume) * PERCENT).toInt()).coerceIn(0, PERCENT.toInt())
    val valueText = stringResource(R.string.core_player_cast_volume_value, percent)
    Row(
        modifier = Modifier.fillMaxWidth(VOLUME_WIDTH_SHARE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.White)
        Slider(
            value = dragging ?: volume,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onChange)
                dragging = null
            },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                },
        )
    }
}

/**
 * What a cast has to say over the player (S16-03, S16-05): the refusal, the start
 * from zero on a server without Range, and — after *Stop casting* — the offer to go
 * on here.
 */
@Composable
fun CastMessages(player: LumoPlayer, modifier: Modifier = Modifier) {
    val ui = rememberCastUi(player)
    val control = player as? CastControl ?: return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        ui.notice?.let { notice ->
            Banner(
                text = stringResource(
                    when (notice) {
                        CastNotice.REFUSED -> R.string.core_player_cast_refused
                        CastNotice.STARTED_FROM_BEGINNING -> R.string.core_player_cast_from_beginning
                    },
                ),
                assertive = notice == CastNotice.REFUSED,
            ) {
                TextButton(onClick = control::dismissNotice) {
                    Text(text = stringResource(R.string.core_player_cast_dismiss), color = Color.White)
                }
            }
        }

        ui.resumeOffer?.let { offer ->
            Banner(
                text = offer.positionMs?.takeIf { it > 0L }
                    ?.let { stringResource(R.string.core_player_cast_stopped_at, it.asClock()) }
                    ?: stringResource(R.string.core_player_cast_stopped),
                assertive = false,
            ) {
                Button(onClick = control::resumeHere) {
                    Text(text = stringResource(R.string.core_player_cast_resume_here))
                }
                TextButton(onClick = control::dismissResumeOffer) {
                    Text(text = stringResource(R.string.core_player_cast_later), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, assertive: Boolean, actions: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.md)
            .background(Color.Black.copy(alpha = SCRIM_ALPHA), LumoShapes.large)
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.semantics {
                liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) { actions() }
    }
}

private fun Long.asClock(): String {
    val total = (this / 1_000L).coerceAtLeast(0L)
    val hours = total / 3_600L
    val minutes = (total / 60L) % 60L
    val seconds = total % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private val ARTWORK_WIDTH = 120.dp
private const val VOLUME_WIDTH_SHARE = 0.8f
private const val PERCENT = 100f
private const val SCRIM_ALPHA = 0.8f
