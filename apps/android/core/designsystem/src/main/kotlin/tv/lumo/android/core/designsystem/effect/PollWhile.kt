package tv.lumo.android.core.designsystem.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

/**
 * Calls [onTick] every [everyMillis] for as long as [active] is true **and this
 * is in the composition**.
 *
 * <h2>Why a composable polls</h2>
 *
 * The step of a synchronisation is only worth showing if it moves, so a screen
 * that displays one has to ask again. Asking from a view model would go on
 * asking behind a player or under another tab — a view model outlives the screen
 * it serves for as long as its back stack entry is kept — and that is a radio
 * kept awake to update something nobody is looking at. Tied to the composition,
 * the polling stops when the screen does.
 *
 * The first tick comes after one interval, not at once: whoever turned [active]
 * on has just read the value.
 */
@Composable
fun PollWhile(active: Boolean, everyMillis: Long, onTick: () -> Unit) {
    val tick by rememberUpdatedState(onTick)

    LaunchedEffect(active, everyMillis) {
        while (active) {
            delay(everyMillis)
            tick()
        }
    }
}

/**
 * How often a source's state is asked again while a refresh is on display above
 * a catalogue.
 *
 * A little slower than the add-source screen's two seconds: there it is the thing
 * somebody is waiting on, here it is a notice above what they came for.
 */
const val SOURCE_NOTICE_POLL_MILLIS: Long = 3_000L
