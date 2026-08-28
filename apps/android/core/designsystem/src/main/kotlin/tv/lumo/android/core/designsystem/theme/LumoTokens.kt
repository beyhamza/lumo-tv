package tv.lumo.android.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The design tokens, shared by both applications and mirrored on the web
 * (backlog S0-07, S2-00).
 *
 * <h2>Every value here comes from `docs/design/canvas/lumo-tokens.json`</h2>
 *
 * That file is the Spectre direction and it says of itself that no value may be
 * redefined anywhere else. This object is a transcription of it into Compose
 * types, not a second opinion about it. A colour invented here — however
 * reasonable — is a colour the next revision of the charter will not move, and
 * the phone will drift away from the television and from the site.
 *
 * Where a value is *derived* rather than transcribed, the derivation is written
 * next to it. There are three, and no others.
 *
 * <h2>Tokens, not styled components</h2>
 *
 * `LumoColors.Accent` means the same thing on the phone and on the television;
 * the component built from it does not — a TV card is bigger, carries a focus
 * state and is read from three metres away (docs/architecture.md §3). Sharing the
 * palette and diverging the components is the whole design strategy in one
 * sentence.
 *
 * <h2>Dark first, and light is not the product</h2>
 *
 * The charter scopes its light theme to `web marketing uniquement`. The phone
 * still follows the system setting, so a light scheme exists below — but it is
 * derived from the charter's marketing block, and its accent values are the ones
 * `apps/web` already derived and documented for the same reason: the Spectre
 * cyan and violet are tuned for a dark ground and fall under 3:1 on a light one.
 */
object LumoColors {
    // ---- Neutrals ----------------------------------------------------------

    /** `color.bg` — the application background. */
    val Ink = Color(0xFF0D0C12)

    /**
     * `color.surface-1`, flattened.
     *
     * The charter models elevation as layered transparency: 5 % white over the
     * background. Compose can express that literally, and it is the wrong choice
     * for a Material colour scheme — `surface` is handed to components that draw
     * it over arbitrary parents, so a translucent value composites against
     * whatever happens to be behind, not against `Ink`. The flattened result is
     * the same pixel in the case that matters and predictable in the ones that
     * do not.
     *
     * `5 % of #FFFFFF over #0D0C12` = `#19181E`.
     */
    val Surface = Color(0xFF19181E)

    /** `color.surface-2`, flattened the same way: 9 % white over the background. */
    val SurfaceRaised = Color(0xFF232227)

    /** `color.border`. */
    val Outline = Color(0xFF26232F)

    /**
     * What a modal layer puts over the screen behind it.
     *
     * Translucent rather than flattened, and this is the case where that is right:
     * a scrim exists precisely to composite against whatever is underneath. The
     * charter has no token for it — it describes pages, not television overlays —
     * so the value is [Ink] at the opacity that keeps a grid legible underneath
     * while leaving no doubt about which layer is taking key presses. At three
     * metres that doubt is the whole problem a scrim solves.
     */
    val Scrim = Ink.copy(alpha = 0.82f)

    // ---- Text --------------------------------------------------------------

    /** `color.text-primary` — 17.1:1 on [Ink], AAA. */
    val OnDark = Color(0xFFF2F0F7)

    /** `color.text-secondary` — 7.2:1 on [Ink], AAA. */
    val OnDarkMuted = Color(0xFFA29FB3)

    // ---- Brand -------------------------------------------------------------

    /**
     * `color.accent-cyan`.
     *
     * This is the focus signature of the product, on every surface: a focused
     * element is outlined in cyan and never in a variation of the background.
     */
    val Accent = Color(0xFF6EE7F0)

    /**
     * Derived: [Accent] under the charter's `surface-3` overlay, 13 % toward
     * [Ink].
     *
     * The charter has no pressed accent, and its elevation model is the honest
     * place to get one from — pressed is `surface-3` everywhere else in the
     * system, so the same 13 % is applied to the accent rather than a darkening
     * chosen by eye.
     */
    val AccentPressed = Color(0xFF61CAD3)

    /** `color.on-accent` — the background colour, used as ink on cyan. */
    val OnAccent = Color(0xFF0D0C12)

    // ---- Status ------------------------------------------------------------

    /** `color.danger`. Used for a failed stream and a refused source alike. */
    val Error = Color(0xFFFF7A8A)

    /** Ink on danger, as everywhere else in the charter. */
    val OnError = Color(0xFF0D0C12)

    // ---- Light scheme, phone only ------------------------------------------
    // `color.light-theme` in the charter, whose scope is written into the token
    // file itself. The television never uses any of these.

    val LightSurface = Color(0xFFF7F6FA)
    val LightSurfaceRaised = Color(0xFFFFFFFF)
    val OnLight = Color(0xFF17141F)
    val OnLightMuted = Color(0xFF55516A)

    /**
     * Derived, and taken from `apps/web` rather than re-derived: the charter's
     * light block stops at four values, and the site had to invent a border for
     * the same block first. Same charter, same answer, one value.
     */
    val LightOutline = Color(0xFFE6E3EE)

    /**
     * Derived, and again the site's answer rather than a second one: the charter
     * defines `danger` for the dark theme only, and #FF7A8A on #F7F6FA is 2.5:1 —
     * unreadable. Same hue, darkened until it passes AA for body text.
     */
    val LightError = Color(0xFFC2334A)
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

/**
 * `radius.sm`, `radius.md`, `radius.lg` — 8 / 14 / 20.
 *
 * The charter also says *"TV : radius × 1.5"*, because viewing distance flattens
 * the perception of a curve. That is not expressible here: one object serves both
 * applications, and a second, TV-scaled set is a new name rather than a new
 * value. Left for the TV screens that will need it (S2-13).
 */
object LumoShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
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
 *
 * <h2>One thing the charter and the architecture do not agree on</h2>
 *
 * The charter's elevation principle reads *"Transparence superposée, jamais
 * d'ombre portée"*, and `lumoTvFocus` draws a drop shadow. Read strictly, the two
 * conflict; read as the same idea in two vocabularies, the charter's "elevation"
 * is a lighter surface and satisfies the architecture's third cue without a
 * shadow at all.
 *
 * S2-00 changes values, not structure, so nothing is decided here — but it is
 * worth deciding before the TV grid is built on it (S2-13).
 */
object LumoFocus {
    const val Scale = 1.08f

    /**
     * The charter specifies a 2 px outline. This is the television, where the
     * charter multiplies radius and spacing by 1.5 for viewing distance; the
     * same factor applied to the outline gives 3 dp, which is the value that was
     * already here.
     */
    val BorderWidth = 3.dp

    val Elevation = 16.dp

    /** `motion.duration-fast` — attached to the D-pad press, still readable. */
    const val AnimationMillis = 120
}

/**
 * Overscan margin, as a fraction of each screen dimension.
 *
 * Televisions crop the edges of the picture — how much depends on the set, and
 * the viewer cannot turn it off on many of them. Five per cent on every side is
 * Google's guidance, the figure docs/architecture.md §3 fixes, and the charter's
 * `platforms.tv.safeArea`. Anything placed outside it may simply not exist for
 * some users.
 */
const val LUMO_TV_OVERSCAN_FRACTION = 0.05f
