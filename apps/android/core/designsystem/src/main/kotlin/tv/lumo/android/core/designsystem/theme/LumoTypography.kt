package tv.lumo.android.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Two type scales from one set of roles, both transcribed from
 * `docs/design/canvas/lumo-tokens.json`.
 *
 * The roles are shared; the sizes are not, and they must not be. A phone is read
 * at arm's length, a television from three metres (docs/architecture.md §3). The
 * charter fixes both scales rather than leaving the second to be guessed at: its
 * `platforms.tv` block multiplies the type scale by 1.75 and writes the results
 * down — 56 / 35 / 26 / 21 — with one line that is not a suggestion, **`body ≥
 * 24px non négociable à 3 m`**.
 *
 * That line is why the TV body size moved from 20 sp to 26 sp in S2-00. Twenty
 * was under the floor the charter sets, which is the kind of miss nobody notices
 * on a desk and everybody notices on a sofa.
 *
 * <h2>The font family is the platform default, and that is a decision</h2>
 *
 * The charter asks for Sora, with `system-ui, sans-serif` as its own declared
 * fallback. Both applications take the fallback, deliberately:
 *
 * - embedding a family costs every user an APK download — on a TV box, over a
 *   connection nobody chose — for a difference that is not visible at three
 *   metres;
 * - the same face is already what the fallback in the charter names, so this is
 *   using the charter's second choice rather than ignoring it.
 *
 * The site keeps Sora, where a web font costs one cached request and the
 * marketing pages are the product's front door. The two platforms differ here on
 * purpose, and this comment is the record of it (S2-00).
 *
 * <h2>What a TextStyle cannot carry</h2>
 *
 * The charter's `detail` role is uppercase. Compose has no text transform on
 * [TextStyle] — the case change belongs to the call site, which is also where it
 * has to be, since uppercasing is a locale-sensitive operation and FR and EN do
 * not always agree about it.
 */
internal object LumoTypeScale {

    val mobile = LumoTypeSet(
        display = TextStyle(
            fontSize = 32.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
        ),
        title = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        body = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Light),
        label = TextStyle(
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.em,
        ),
    )

    val tv = LumoTypeSet(
        display = TextStyle(
            fontSize = 56.sp,
            lineHeight = 62.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
        ),
        title = TextStyle(fontSize = 35.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
        body = TextStyle(fontSize = 26.sp, lineHeight = 39.sp, fontWeight = FontWeight.Light),
        label = TextStyle(
            fontSize = 21.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.em,
        ),
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
