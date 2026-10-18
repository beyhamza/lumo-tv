package tv.lumo.android.core.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.Media3Player

/**
 * The video itself. The only Composable in the product that touches Media3.
 *
 * Keeping the surface in `core:player` rather than in a feature is what lets
 * [LumoPlayer] stay free of `androidx.media3` types: a screen composes this and
 * never learns what engine is behind it.
 *
 * `SurfaceView`, not `TextureView`: it goes through the hardware overlay, which
 * on a TV box is the difference between smooth 1080p and dropped frames, and it
 * keeps HDMI-attached decoders happy. The trade-off — a SurfaceView cannot be
 * animated or rotated freely — costs nothing for a full-bleed video surface.
 *
 * <h2>Subtitles are drawn here too (S13-01)</h2>
 *
 * `PlayerSurface` draws pictures, not cues, and the classic `SubtitleView` is a
 * View from an artifact this project does not ship. So the text of the current
 * cues is drawn over the picture, at a size proportional to its height: the same
 * share of the screen at three metres and at arm's length. Text cues only —
 * bitmap formats are offered as unavailable in the menu rather than drawn as
 * nothing.
 */
// media3-ui-compose is still marked @UnstableApi in 1.11 — the whole Compose
// surface API is. The opt-in is confined to this one function, which is the
// other half of why the abstraction is worth having: when the API settles or
// changes shape, one file moves.
@OptIn(UnstableApi::class)
@Composable
fun LumoVideoSurface(
    player: LumoPlayer,
    modifier: Modifier = Modifier,
) {
    val media3 = player as? Media3Player
        ?: error("LumoVideoSurface requires the Media3 implementation of LumoPlayer")
    val cues by media3.cues.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = modifier) {
        PlayerSurface(
            player = media3.exoPlayer,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.fillMaxSize(),
        )

        if (cues.isNotEmpty()) {
            val fontSize = with(LocalDensity.current) { (maxHeight * CUE_HEIGHT_SHARE).toSp() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = maxHeight * CUE_BOTTOM_SHARE, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                BasicText(
                    text = cues.joinToString("\n"),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = fontSize,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = CUE_BACKGROUND_ALPHA))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Text height as a share of the picture's: about 48 px on a 1080p television. */
private const val CUE_HEIGHT_SHARE = 0.045f

/** Lifted off the bottom edge, inside the overscan a television crops. */
private const val CUE_BOTTOM_SHARE = 0.08f

private const val CUE_BACKGROUND_ALPHA = 0.6f
