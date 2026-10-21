package tv.lumo.android.core.cast

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * The Cast button (S16-01): the SDK's own `MediaRouteButton`, which opens the
 * device picker and, once connected, the "stop casting" dialog.
 *
 * Its accessible name and state ("Cast button. Disconnected", "Connected") come
 * from the mediarouter library, translated in French. When to draw it is decided
 * in core:player (`CastRules.showButton`); this is only the drawing.
 */
@Composable
fun MediaRouteCastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).also { CastButtonFactory.setUpMediaRouteButton(context.applicationContext, it) }
        },
        modifier = modifier.size(BUTTON_SIZE),
    )
}

/** What the phone application hands to `LocalCastButton`. */
val CastButtonContent: @Composable (Modifier) -> Unit = { modifier -> MediaRouteCastButton(modifier) }

private val BUTTON_SIZE = 48.dp
