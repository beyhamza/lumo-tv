package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

private const val SCRIM_ALPHA = 0.8f

/** One button of a [LumoPlayerNotice]. The first one is drawn as the main action. */
data class LumoNoticeAction(val label: String, val onClick: () -> Unit)

/**
 * A sentence over a stopped picture on the phone, with what can be done about it.
 *
 * For the states where the player is not failing but holding — "Wi-Fi required",
 * "Wi-Fi available, resume?" (S13-05). Drawn over black, white ink, the way the
 * players draw their failures.
 */
@Composable
fun LumoPlayerNotice(
    title: String,
    message: String?,
    actions: List<LumoNoticeAction>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LumoSpacing.md)
            // The last frame can stay on the surface behind it: a scrim keeps the
            // sentence readable over any picture.
            .background(Color.Black.copy(alpha = SCRIM_ALPHA), LumoShapes.large)
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
            actions.forEachIndexed { index, action ->
                if (index == 0) {
                    Button(onClick = action.onClick) { Text(action.label) }
                } else {
                    TextButton(onClick = action.onClick) { Text(action.label, color = Color.White) }
                }
            }
        }
    }
}
