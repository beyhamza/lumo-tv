package tv.lumo.android.core.designsystem.format

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.Instant

/**
 * An instant as a time of day, in the device's zone and the user's own clock
 * format — `21:30`, or `9:30 PM` where the system is set that way.
 *
 * The one place a programme time is turned into text (US-16, S9-03: "les
 * heures s'affichent dans le fuseau de l'appareil"). Everything upstream
 * compares instants; a screen that formatted with its own `SimpleDateFormat`
 * would be the screen that shows `21:30` on a television whose settings say
 * `9:30 PM`, or that drifts an hour on the night the clocks change.
 */
@Composable
fun formatTimeOfDay(instant: Instant): String {
    val context = LocalContext.current
    return remember(instant, context) {
        DateUtils.formatDateTime(context, instant.toEpochMilli(), DateUtils.FORMAT_SHOW_TIME)
    }
}
