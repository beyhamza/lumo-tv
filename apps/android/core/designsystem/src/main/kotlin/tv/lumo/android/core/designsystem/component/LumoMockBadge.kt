package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import tv.lumo.android.core.designsystem.R
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * What the mock-ups draw and the product cannot yet feed, said out loud.
 *
 * Two flavours, one look: a dashed violet outline, monospaced, and a text that
 * starts with `[mock]` so it is found by a search and never mistaken for a
 * value. It is deliberately not styled like an error — nothing is broken — and
 * not like a hint — it will go away.
 *
 * The badge is shared by both applications: the whole point is that the same
 * words appear wherever the same gap exists.
 *
 * @param scale the television reads at 1.75 ×; the phone at 1.
 */
@Composable
fun LumoMockMissingData(modifier: Modifier = Modifier, scale: Float = 1f) =
    LumoMockBadge(stringResource(R.string.lumo_mock_missing_data), modifier, scale)

@Composable
fun LumoMockNotImplemented(modifier: Modifier = Modifier, scale: Float = 1f) =
    LumoMockBadge(stringResource(R.string.lumo_mock_not_implemented), modifier, scale)

@Composable
fun LumoMockBadge(text: String, modifier: Modifier = Modifier, scale: Float = 1f) {
    Text(
        text = text,
        // One line, always: a badge that wraps stops reading as a badge.
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = (12 * scale).sp,
            lineHeight = (17 * scale).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.04.sp,
            color = LumoColors.AccentViolet,
        ),
        modifier = modifier
            .border(1.dp, LumoColors.AccentViolet.copy(alpha = 0.6f), LumoShapes.small)
            .padding(horizontal = LumoSpacing.sm * scale, vertical = LumoSpacing.xxs * scale),
    )
}
