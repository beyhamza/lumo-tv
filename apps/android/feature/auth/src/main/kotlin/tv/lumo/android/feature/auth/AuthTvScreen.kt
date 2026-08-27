package tv.lumo.android.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.tvOverscan

/**
 * Activating this television from a phone (US-05).
 *
 * <h2>Nothing on this screen is typed, and nothing on it is focusable</h2>
 *
 * That is not an oversight of the D-pad rule — it is the flow. There is no
 * control here: the code and the QR are read, the approving happens on a phone,
 * and the set moves on by itself when it does. Adding a button so that something
 * could take focus would be adding a thing to press for no reason, on a screen
 * whose whole argument is that a remote control is the wrong instrument.
 *
 * The rail is hidden while signed out, so `BACK` remains what it always is on a
 * television: the way out of the application.
 *
 * <h2>Everything is very large, and that is a requirement</h2>
 *
 * This is read from three metres, quite possibly by somebody standing up with a
 * phone in one hand. The code is the biggest thing on the panel; the QR is next.
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
            modifier = Modifier.widthIn(max = 900.dp),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.feature_auth_tv_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
            )
            Text(
                text = stringResource(
                    R.string.feature_auth_tv_instruction,
                    step.verificationUri,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
            )

            // The largest thing on the panel, and spaced out: a code read aloud
            // across a room, or copied by thumb, is read one character at a time.
            Text(
                text = step.userCode.chunked(4).joinToString(" "),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.Accent,
                // A new code replaces this one with nobody watching. Announcing
                // it is what tells somebody using a screen reader that the thing
                // they were about to type has just changed.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Text(
                text = stringResource(R.string.feature_auth_tv_waiting),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )
        }

        QrCode(content = step.verificationUriComplete)
    }
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
            .clip(LumoShapes.large)
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
