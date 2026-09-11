package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import tv.lumo.android.core.designsystem.theme.LumoColors

/**
 * The brand mark: the word `lum` and, where the `o` would sit, a disc filled
 * with the Spectre gradient.
 *
 * Drawn rather than shipped as a bitmap, for the reason the charter gives for
 * everything else: it scales to whatever a panel or a phone gives it, and it is
 * built from the two accent tokens instead of carrying its own copy of them.
 * The letters are set in the system face, as all text on Android is (see
 * `design-system.md`, typography); the geometry is the `Directions` canvas at
 * 1 : 1 — the disc is 0.56 × the cap height and sits a hair below the baseline.
 *
 * @param height height of the letters. The disc and the gap derive from it.
 * @param textColor the ink; defaults to the primary text colour of the dark
 *   theme, which is where the mark lives on every product surface.
 */
@Composable
fun LumoWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    textColor: Color = LumoColors.OnDark,
) {
    val disc = height * 0.56f
    // Compose gradients take pixels, not fractions: the highlight sits a third
    // of the way in from the top-left, as the canvas draws the orb.
    val discPx = with(LocalDensity.current) { disc.toPx() }
    Row(
        modifier = modifier.semantics { contentDescription = "Lumo" },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "lum",
            style = TextStyle(
                fontSize = height.value.sp,
                fontWeight = FontWeight.SemiBold,
                // `-0.02em`, which Compose has no unit for.
                letterSpacing = (height.value * -0.02f).sp,
                lineHeight = height.value.sp,
                color = textColor,
            ),
        )
        Box(
            modifier = Modifier
                .offset(x = height * 0.06f, y = height * 0.02f)
                .size(disc)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(LumoColors.Accent, LumoColors.AccentViolet),
                        center = Offset(discPx * 0.35f, discPx * 0.3f),
                        radius = discPx * 0.75f,
                    ),
                    shape = CircleShape,
                ),
        )
    }
}
