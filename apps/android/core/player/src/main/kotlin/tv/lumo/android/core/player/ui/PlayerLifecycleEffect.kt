package tv.lumo.android.core.player.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * The two moments every player screen reacts to, in one place for all six.
 *
 * `ON_STOP`, not `ON_PAUSE`: a dialog or the notification shade over the
 * picture pauses the activity without hiding the video, and pausing then would
 * be wrong. `ON_STOP` is HOME, the TV's input switch, the screen going off.
 * Neither app recreates its activity on rotation (`configChanges`), so turning
 * a phone does not count as leaving.
 *
 * The `ON_START` of opening the screen reaches the callback too; the callers
 * already treat it as a no-op, since nothing was paused by leaving yet.
 */
@Composable
fun PlayerLifecycleEffect(onForeground: () -> Unit, onBackground: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onBackground() }
}
