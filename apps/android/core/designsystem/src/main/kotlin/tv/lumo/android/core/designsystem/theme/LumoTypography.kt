package tv.lumo.android.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two type scales from one set of roles.
 *
 * The roles are shared; the sizes are not, and they must not be. A phone is read
 * at arm's length, a television from three metres (docs/architecture.md §3), so
 * TV text is roughly 1.5× its mobile equivalent and never drops below 18 sp —
 * below that, body text on a living-room set is guesswork.
 *
 * The font family is the platform default on purpose: shipping a custom family
 * costs an APK download on a TV box for a difference nobody sees at three
 * metres.
 */
internal object LumoTypeScale {

    val mobile = LumoTypeSet(
        display = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )

    val tv = LumoTypeSet(
        display = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold),
        title = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        body = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
        label = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    )
}

internal data class LumoTypeSet(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
) {
    fun withDefaultFamily(): LumoTypeSet = copy(
        display = display.copy(fontFamily = FontFamily.Default),
        title = title.copy(fontFamily = FontFamily.Default),
        body = body.copy(fontFamily = FontFamily.Default),
        label = label.copy(fontFamily = FontFamily.Default),
    )
}
