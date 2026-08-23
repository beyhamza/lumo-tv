package tv.lumo.android.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The design tokens, shared by both applications and mirrored on the web
 * (backlog S0-07).
 *
 * Tokens, not styled components: `LumoColors.Accent` means the same thing on the
 * phone and on the television, while the component built from it does not — a TV
 * card is bigger, carries a focus state and is read from three metres away
 * (docs/architecture.md §3). Sharing the palette and diverging the components is
 * the whole design strategy in one sentence.
 *
 * The palette is dark-first. A living-room player is used in the dark, and a
 * bright surface on a large panel at night is the fastest way to make an app
 * feel wrong.
 */
object LumoColors {
    // Neutrals.
    val Ink = Color(0xFF07090F)
    val Surface = Color(0xFF11141C)
    val SurfaceRaised = Color(0xFF1A1F2A)
    val Outline = Color(0xFF2C3341)

    // Text.
    val OnDark = Color(0xFFF2F5FA)
    val OnDarkMuted = Color(0xFFA3AEC2)

    // Brand. A cold blue reads as "signal" on a TV panel and stays legible on an
    // OLED phone at low brightness.
    val Accent = Color(0xFF4CB8FF)
    val AccentPressed = Color(0xFF2E9BE6)
    val OnAccent = Color(0xFF04121C)

    // Status. `Error` is used for a failed stream and a refused source alike.
    val Error = Color(0xFFFF6B6B)
    val OnError = Color(0xFF1C0505)
    val Success = Color(0xFF5AD69B)

    // Light scheme, phone only. The television never uses these.
    val LightSurface = Color(0xFFFBFCFE)
    val LightSurfaceRaised = Color(0xFFFFFFFF)
    val OnLight = Color(0xFF0B0E14)
    val OnLightMuted = Color(0xFF57617A)
    val LightOutline = Color(0xFFD5DBE6)
}

/** A 4 dp rhythm. Every margin in the product is a multiple of it. */
object LumoSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object LumoShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(20.dp)
}

/**
 * The TV focus signature.
 *
 * docs/architecture.md §3 requires a focused element to be obvious through
 * **scale, border and elevation together**. One channel of feedback is not
 * enough on a television: colour alone disappears on a washed-out panel or for a
 * colour-blind viewer, scale alone is invisible in a dense grid, and a shadow
 * alone vanishes against a bright poster. Three cues survive all three cases.
 */
object LumoFocus {
    const val Scale = 1.08f
    val BorderWidth = 3.dp
    val Elevation = 16.dp

    /** Fast enough to feel attached to the D-pad press, slow enough to read. */
    const val AnimationMillis = 120
}

/**
 * Overscan margin, as a fraction of each screen dimension.
 *
 * Televisions crop the edges of the picture — how much depends on the set, and
 * the viewer cannot turn it off on many of them. Five per cent on every side is
 * Google's guidance and the figure docs/architecture.md §3 fixes. Anything
 * placed outside it may simply not exist for some users.
 */
const val LUMO_TV_OVERSCAN_FRACTION = 0.05f
