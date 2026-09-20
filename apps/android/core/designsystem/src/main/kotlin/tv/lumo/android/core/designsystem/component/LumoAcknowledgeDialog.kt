package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes

/**
 * A sentence and **one** way on.
 *
 * For the moment something has already happened and the screen underneath can no
 * longer go on — the first use is a player whose source was deleted from another
 * device (US-024). There is nothing to choose, so there is one action; and there
 * is nothing to go back *to*, so the back gesture is that same action rather
 * than a way of dismissing the sentence and staying on a dead screen.
 *
 * Shared because the three players that show it live in three features that may
 * not import each other.
 */
@Composable
fun LumoAcknowledgeDialog(
    message: String,
    actionLabel: String,
    onAcknowledge: () -> Unit,
) {
    AlertDialog(
        // Back and a tap outside both mean "I have read it": the only thing this
        // dialog can do is let the caller move on.
        onDismissRequest = onAcknowledge,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onAcknowledge) { Text(actionLabel) }
        },
    )
}

/**
 * The same, for a remote control.
 *
 * A dialog window, so the focus cannot leak to the player underneath, and the
 * single button **takes the focus on arrival**: a layer with no focused target
 * leaves `BACK` as the only key that works (docs/design/tv-focus-map.md, rule 1).
 * `BACK` acknowledges, like `OK` — a dialog that `BACK` closed onto a stopped
 * player would strand the viewer on a black screen.
 */
@Composable
fun LumoTvAcknowledgeDialog(
    message: String,
    actionLabel: String,
    onAcknowledge: () -> Unit,
) {
    val action = remember { FocusRequester() }

    // Failing to focus is recoverable — `BACK` still acknowledges — and throwing
    // would take the dialog down under the viewer.
    LaunchedEffect(Unit) { runCatching { action.requestFocus() } }

    Dialog(
        onDismissRequest = onAcknowledge,
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
                    text = message,
                    style = TvMaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDark,
                )
                LumoTvButton(
                    text = actionLabel,
                    onClick = onAcknowledge,
                    primary = true,
                    focusRequester = action,
                )
            }
        }
    }
}

/** Wide enough for one sentence at three metres, narrow enough to read as a layer. */
private val DIALOG_WIDTH = 640.dp
