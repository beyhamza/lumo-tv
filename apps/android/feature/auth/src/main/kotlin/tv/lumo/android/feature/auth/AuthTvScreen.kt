package tv.lumo.android.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import tv.lumo.android.core.designsystem.component.LumoWordmark
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.tvOverscan

/**
 * Activating this television from a phone (US-05), laid out as `TV1 —
 * Activation` in the canvas: the mark, a headline, the address, the code in
 * eight cells, how long it stays valid, and the QR on the right.
 *
 * <h2>Nothing on this screen is typed, and nothing on it is focusable</h2>
 *
 * The canvas draws an « Actualiser le code » button; it is deliberately not
 * built. There is no control here: the code and the QR are read, the approving
 * happens on a phone, and the set moves on by itself when it does — including
 * when the code expires, since a new one is requested without anybody pressing
 * anything. A button that only did what already happens would be a thing to
 * press for no reason, on a screen whose whole argument is that a remote control
 * is the wrong instrument (docs/design/tv-focus-map.md).
 *
 * The rail is hidden while signed out, so `BACK` remains what it always is on a
 * television: the way out of the application.
 *
 * <h2>Everything is very large, and that is a requirement</h2>
 *
 * This is read from three metres, quite possibly by somebody standing up with a
 * phone in one hand. The code is the biggest thing on the panel, one character
 * per cell in a monospaced face so that `0` and `O` cannot be confused; the QR
 * is next.
 *
 * <h2>The QR carries the whole address</h2>
 *
 * `verification_uri_complete` — the one with `?code=…` already in it — so the
 * nominal path involves no typing at all. The short address and the code are
 * there for the phone that will not scan, which is the fallback rather than the
 * plan.
 */
@Composable
fun AuthTvScreen(
    modifier: Modifier = Modifier,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscan(),
        contentAlignment = Alignment.Center,
    ) {
        when (val step = state.step) {
            ActivationStep.Loading -> Text(
                text = stringResource(R.string.feature_auth_tv_preparing),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDarkMuted,
            )

            is ActivationStep.Waiting -> Waiting(step)

            ActivationStep.Denied -> Message(
                title = stringResource(R.string.feature_auth_tv_denied_title),
                body = stringResource(R.string.feature_auth_tv_denied_body),
            )

            ActivationStep.Unavailable -> Message(
                title = stringResource(R.string.feature_auth_tv_unavailable_title),
                body = stringResource(R.string.feature_auth_tv_unavailable_body),
            )
        }
    }
}

@Composable
private fun Waiting(step: ActivationStep.Waiting) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            // Weighted, so the QR is measured first and the text takes what is
            // left: without it the column claims the whole width of a 1080p
            // panel and pushes the QR off the edge, under a sentence that says
            // to scan it.
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 1_000.dp),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            LumoWordmark(height = 44.dp)

            // Title size, not display: the canvas sets the headline at the
            // title step, and the display step wraps it onto three lines in
            // the 496 dp the QR leaves on a 1080p panel.
            Text(
                text = stringResource(R.string.feature_auth_tv_headline),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
            )
            Text(
                text = stringResource(
                    R.string.feature_auth_tv_instruction_short,
                    step.verificationUri,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
            )

            CodeCells(step.userCode)

            Expiry(step.expiresAtMillis)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            QrCode(content = step.verificationUriComplete)
            Text(
                text = stringResource(R.string.feature_auth_tv_qr_caption),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )
        }
    }
}

/**
 * One character per cell, a gap where the canvas draws its dash.
 *
 * Announced as a whole to a screen reader: a new code replaces this one with
 * nobody watching, and the announcement is what tells somebody the thing they
 * were about to type has just changed.
 */
@Composable
private fun CodeCells(userCode: String) {
    val spoken = userCode.chunked(4).joinToString(" ")

    Row(
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = spoken
        },
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        userCode.forEachIndexed { index, char ->
            if (index == userCode.length / 2) {
                Box(
                    modifier = Modifier
                        .width(LumoSpacing.md)
                        .height(3.dp)
                        .background(LumoColors.Outline),
                )
            }
            // Eight cells, a dash and their gaps add up to 480 dp: what the
            // column has once the QR and the overscan have taken theirs.
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 76.dp)
                    .clip(LumoTvShapes.medium)
                    .background(LumoColors.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
                    color = LumoColors.OnDark,
                )
            }
        }
    }
}

/**
 * « Expire dans 4:32 — se valide tout seul. » — the second half is the whole
 * reassurance: nothing here needs the viewer when the clock runs out.
 */
@Composable
private fun Expiry(expiresAtMillis: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(expiresAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val remaining = ((expiresAtMillis - now) / 1_000L).coerceAtLeast(0L)
    val clock = "%d:%02d".format(remaining / 60, remaining % 60)

    Text(
        text = stringResource(R.string.feature_auth_tv_expires, clock),
        style = MaterialTheme.typography.labelLarge,
        color = LumoColors.OnDarkMuted,
    )
}

/**
 * The QR, drawn rather than rasterised.
 *
 * ZXing produces a matrix of bits and this paints it on a Canvas: no bitmap, no
 * image loader, and it stays crisp at whatever size a panel gives it. Quiet zone
 * one module wide, as the specification requires — a QR flush against a dark
 * background is a QR that does not scan.
 *
 * White on white-ish, deliberately: scanners expect dark-on-light, and inverting
 * a QR to match a dark theme is a design choice that costs people the ability to
 * use it.
 */
@Composable
private fun QrCode(content: String, size: Dp = 320.dp) {
    val matrix = remember(content) { encodeQr(content) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(LumoTvShapes.large)
            .background(Color.White)
            .padding(LumoSpacing.md),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val modules = matrix.width
            val cell = this.size.width / modules

            for (x in 0 until modules) {
                for (y in 0 until matrix.height) {
                    if (!matrix.get(x, y)) continue
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        // A hair over one cell: adjacent modules must touch, and
                        // exact arithmetic leaves hairlines that some scanners
                        // read as gaps.
                        size = Size(cell + 1f, cell + 1f),
                    )
                }
            }
        }
    }
}

private fun encodeQr(content: String): BitMatrix = QRCodeWriter().encode(
    content,
    BarcodeFormat.QR_CODE,
    QR_MODULES,
    QR_MODULES,
    mapOf(
        // A television is read at an angle, sometimes off a photograph of the
        // screen. Q recovers from a quarter of the code being unreadable.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
        EncodeHintType.MARGIN to 1,
    ),
)

@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier.widthIn(max = 1_200.dp),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        LumoWordmark(height = 44.dp)
        Spacer(modifier = Modifier.height(LumoSpacing.sm))
        Text(text = title, style = MaterialTheme.typography.displayMedium, color = LumoColors.OnDark)
        Text(text = body, style = MaterialTheme.typography.bodyLarge, color = LumoColors.OnDarkMuted)
    }
}

/**
 * The requested matrix size in modules.
 *
 * ZXing returns at least what the content needs, so this is a floor rather than a
 * size: the Canvas scales whatever comes back to the space it is given.
 */
private const val QR_MODULES = 256
