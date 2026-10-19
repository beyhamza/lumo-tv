package tv.lumo.android.core.player.ui

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import tv.lumo.android.core.common.playback.PictureInPictureSupport
import tv.lumo.android.core.player.FloatingWindowAction
import tv.lumo.android.core.player.FloatingWindowExit
import tv.lumo.android.core.player.FloatingWindowRules
import tv.lumo.android.core.player.R

/**
 * A player screen's floating window, as the screen sees it (S15-01, ADR 0013).
 *
 * @property available the device can show one — the player draws its
 *   "Floating window" button only then. False on a television, below Android 8,
 *   and on a phone whose system has no window.
 * @property active the screen is in the window now: the controls are hidden, the
 *   picture is all there is room for.
 */
@Stable
class FloatingWindow internal constructor(
    val available: Boolean,
    private val open: () -> Unit,
) {
    var active: Boolean by mutableStateOf(false)
        internal set

    /** The player's "Floating window" button. A refusal by the system is said, never swallowed. */
    fun enter() = open()
}

/**
 * Gives a player screen its floating window, for as long as the screen is composed.
 *
 * Android mobile only: the television's screens never call it (ADR 0013), and the
 * TV application's manifest does not declare the support anyway.
 *
 * <h2>What it does with the Activity</h2>
 *
 * - Keeps the Activity's window parameters current: the shape, the buttons (one
 *   play/pause toggle, plus *Direct* for a channel) and, from Android 12, whether
 *   leaving opens the window by itself ([FloatingWindowRules.enterOnLeave]).
 * - Before Android 12, opens it from the "user is leaving" hint — HOME, the
 *   recent apps — under the same rule.
 * - Receives the window's buttons, as broadcasts private to this application.
 * - Reads how the window ended ([FloatingWindowRules.exitOf]): expanded is the
 *   player again, with its controls; closed calls [onClosed], which stops the
 *   playback and saves where it was.
 *
 * While the window is open the Activity is **paused, not stopped**: the
 * `ON_STOP` that pauses playback (S10B-03, [PlayerLifecycleEffect]) does not
 * come, and the view model's progress saves and watched-time count carry on
 * unchanged (S15-04). If the window cannot open — setting off, refused in
 * Android's settings, nothing playing — leaving stops the Activity as before
 * and S10B-03 applies.
 *
 * On leaving the composition the parameters are reset, so HOME from the
 * catalogue never shrinks the catalogue into a window.
 *
 * @param playing the stream is playing (or loading on its way): the toggle shows
 *   *Pause*, and leaving may open the window.
 * @param live a channel: *Direct* is offered.
 * @param enterOnLeave the S15-02 setting.
 */
@Composable
fun rememberFloatingWindow(
    playing: Boolean,
    live: Boolean,
    enterOnLeave: Boolean,
    onTogglePlay: () -> Unit,
    onLive: () -> Unit,
    onClosed: () -> Unit,
): FloatingWindow {
    val activity = LocalActivity.current as? ComponentActivity
    val supported = remember(activity) {
        activity != null && PictureInPictureSupport.deviceSupports(activity)
    }

    val toggle by rememberUpdatedState(onTogglePlay)
    val goLive by rememberUpdatedState(onLive)
    val close by rememberUpdatedState(onClosed)
    val leaveAllowed by rememberUpdatedState(enterOnLeave)
    val isPlaying by rememberUpdatedState(playing)
    val isLive by rememberUpdatedState(live)

    val window = remember(activity, supported) {
        FloatingWindow(available = supported) {
            if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@FloatingWindow
            val opened = PictureInPictureSupport.allowedByUser(activity) &&
                activity.tryEnter(activity.floatingParams(isLive, isPlaying, autoEnter = false))
            if (!opened) {
                Toast.makeText(activity, R.string.core_player_floating_window_refused, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (activity == null || !supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return window

    // The parameters follow the playback: the toggle's icon, and whether leaving
    // now would open a window. Asked of the system on every change rather than
    // cached, because the per-app switch can be turned off while this plays.
    SideEffect {
        val leave = FloatingWindowRules.enterOnLeave(
            settingOn = enterOnLeave,
            available = PictureInPictureSupport.allowedByUser(activity),
            playing = playing,
        )
        activity.setPictureInPictureParams(activity.floatingParams(live, playing, autoEnter = leave))
    }

    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra(EXTRA_ACTION)) {
                    FloatingWindowAction.PLAY.name, FloatingWindowAction.PAUSE.name -> toggle()
                    FloatingWindowAction.LIVE.name -> goLive()
                }
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_FLOATING_WINDOW),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val modeChanges = Consumer<PictureInPictureModeChangedInfo> { info ->
            window.active = info.isInPictureInPictureMode
            if (!info.isInPictureInPictureMode) {
                val started = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (FloatingWindowRules.exitOf(started) == FloatingWindowExit.CLOSED) close()
            }
        }
        activity.addOnPictureInPictureModeChangedListener(modeChanges)
        window.active = activity.isInPictureInPictureMode

        // From Android 12 the auto-enter flag does this, with the system's own
        // animation; before it, the leave hint is the moment.
        val leaving = Runnable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@Runnable
            val leave = FloatingWindowRules.enterOnLeave(
                settingOn = leaveAllowed,
                available = PictureInPictureSupport.allowedByUser(activity),
                playing = isPlaying,
            )
            if (leave) activity.tryEnter(activity.floatingParams(isLive, isPlaying, autoEnter = false))
        }
        activity.addOnUserLeaveHintListener(leaving)

        onDispose {
            activity.removeOnUserLeaveHintListener(leaving)
            activity.removeOnPictureInPictureModeChangedListener(modeChanges)
            activity.unregisterReceiver(receiver)
            activity.setPictureInPictureParams(idleParams())
        }
    }

    return window
}

@RequiresApi(Build.VERSION_CODES.O)
private fun ComponentActivity.tryEnter(params: PictureInPictureParams): Boolean = try {
    enterPictureInPictureMode(params)
} catch (_: IllegalStateException) {
    // Thrown when the system has no window after all (a freeform or locked-down build).
    false
}

@RequiresApi(Build.VERSION_CODES.O)
private fun ComponentActivity.floatingParams(live: Boolean, playing: Boolean, autoEnter: Boolean): PictureInPictureParams {
    val (width, height) = FloatingWindowRules.aspectRatio(0, 0)
    return PictureInPictureParams.Builder()
        .setAspectRatio(Rational(width, height))
        .setActions(FloatingWindowRules.actions(live, playing).map { remoteAction(it) })
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(autoEnter)
                // Video, not a document: resizing it should not cross-fade.
                setSeamlessResizeEnabled(false)
            }
        }
        .build()
}

/** What a screen without a player leaves behind: no buttons, and leaving opens nothing. */
@RequiresApi(Build.VERSION_CODES.O)
private fun idleParams(): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setActions(emptyList())
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(false) }
        .build()

@RequiresApi(Build.VERSION_CODES.O)
private fun Context.remoteAction(action: FloatingWindowAction): RemoteAction {
    val (@DrawableRes icon, @StringRes label) = when (action) {
        FloatingWindowAction.PLAY -> R.drawable.core_player_ic_pip_play to R.string.core_player_floating_window_play
        FloatingWindowAction.PAUSE -> R.drawable.core_player_ic_pip_pause to R.string.core_player_floating_window_pause
        FloatingWindowAction.LIVE -> R.drawable.core_player_ic_pip_live to R.string.core_player_floating_window_live
    }
    val intent = Intent(ACTION_FLOATING_WINDOW)
        .setPackage(packageName)
        .putExtra(EXTRA_ACTION, action.name)
    val pending = PendingIntent.getBroadcast(
        this,
        action.ordinal,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val title = getString(label)
    return RemoteAction(Icon.createWithResource(this, icon), title, title, pending)
}

private const val ACTION_FLOATING_WINDOW = "tv.lumo.android.core.player.FLOATING_WINDOW"
private const val EXTRA_ACTION = "action"
