package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes

/**
 * A question with two answers, and **Cancel is the one the screen expects**.
 *
 * For an action that costs something to undo — signing out of a device,
 * disconnecting another one (US-025, S8-06). Material puts the confirm button
 * last, where the thumb lands; that slot goes to Cancel on purpose, as the filled
 * button, and the deed is the quiet text button in the error colour. Somebody who
 * presses the prominent thing without reading keeps what they had. Back and a tap
 * outside cancel too.
 *
 * Shared because the settings and the source screens of two applications ask
 * such questions, and a confirmation whose default differs between them is one
 * somebody will get wrong on the second.
 *
 * @param failure a sentence under the question once the deed was refused, in the
 * error colour and announced. The dialog stays open with both buttons live: the
 * design keeps the thing on a failure and offers a retry.
 * @param busy while the deed is on the wire; both buttons wait.
 */
@Composable
fun LumoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean = false,
    failure: String? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                Text(message)
                failure?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onCancel, enabled = !busy) { Text(cancelLabel) }
        },
        dismissButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/**
 * The same question for a remote control.
 *
 * A dialog window, so the focus cannot leak to the screen underneath, and
 * **Cancel takes the focus on arrival** (docs/design/0.2.0/settings.md): `OK`
 * pressed one time too many closes the question and changes nothing. `LEFT`
 * reaches the deed, `BACK` cancels — a layer with no focused target would leave
 * `BACK` as the only key that works (docs/design/tv-focus-map.md, rule 1).
 */
@Composable
fun LumoTvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean = false,
    failure: String? = null,
) {
    val cancel = remember { FocusRequester() }

    // Failing to focus is recoverable — `BACK` still cancels — and throwing
    // would take the dialog down under the viewer.
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }

    Dialog(
        onDismissRequest = { if (!busy) onCancel() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            // A phone's dialog width otherwise. This one draws its own scrim and
            // its own card, sized for three metres.
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LumoColors.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(DIALOG_WIDTH)
                    .clip(LumoTvShapes.large)
                    .background(LumoColors.Surface)
                    .padding(LumoSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
            ) {
                TvText(
                    text = title,
                    style = TvMaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDark,
                )
                TvText(
                    text = message,
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = LumoColors.OnDarkMuted,
                )
                failure?.let {
                    TvText(
                        text = it,
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = LumoColors.Error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
                    // The deed first and Cancel last, so that the focus arrives
                    // on the right and `LEFT` — the direction a viewer reaches
                    // for — is the one that costs a decision.
                    LumoTvButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        enabled = !busy,
                    )
                    LumoTvButton(
                        text = cancelLabel,
                        onClick = onCancel,
                        primary = true,
                        enabled = !busy,
                        focusRequester = cancel,
                    )
                }
            }
        }
    }
}

/** Wide enough for a question at three metres, narrow enough to read as a layer. */
private val DIALOG_WIDTH = 640.dp
