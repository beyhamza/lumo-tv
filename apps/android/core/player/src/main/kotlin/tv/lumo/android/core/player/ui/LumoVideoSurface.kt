package tv.lumo.android.core.player.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    PlayerSurface(
        player = media3.exoPlayer,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
        modifier = modifier,
    )
}
