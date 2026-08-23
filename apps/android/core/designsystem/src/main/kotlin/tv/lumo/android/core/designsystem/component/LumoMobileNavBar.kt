package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTypeScale

/**
 * The phone's bottom navigation.
 *
 * Its counterpart is [LumoTvNavRail], and the pair is the clearest example of
 * the divergence docs/architecture.md §3 asks for: a thumb reaches the bottom of
 * a phone, and a D-pad travels left from anywhere on a television. Neither
 * layout works on the other device.
 *
 * Built from tokens rather than from `NavigationBar` for one specific reason:
 * Material 3's bar requires an icon per item, and the product has no icon set
 * yet. A row of labelled targets is honest scaffolding; a bar with placeholder
 * icons would be a decision made by accident.
 */
@Composable
fun LumoMobileNavBar(
    destinations: List<LumoDestination>,
    selectedRoute: String?,
    onSelect: (LumoDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { destination ->
            val selected = destination.route == selectedRoute
            Text(
                text = stringResource(destination.titleRes),
                style = LumoTypeScale.mobile.label,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(LumoShapes.small)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable { onSelect(destination) }
                    .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
            )
        }
    }
}
