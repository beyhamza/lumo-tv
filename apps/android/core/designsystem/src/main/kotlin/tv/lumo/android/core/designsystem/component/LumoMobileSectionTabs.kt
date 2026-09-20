package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tv.lumo.android.core.common.navigation.LumoDestination
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTypeScale

/**
 * The sections of one bottom-bar entry, across the top of the phone's content.
 *
 * It exists for *Explore*, which gathers Live, Films and Series under one entry
 * of the bar (US-017): the bar says where somebody is in the application, this
 * says where they are inside that entry.
 *
 * <h2>Drawn differently from the bar, on purpose</h2>
 *
 * The bar's selected entry is a filled pill. If these were pills too, a phone
 * would show the same control at both ends of the screen, and "which of the two
 * rows am I in" would be a question the design had created. So a section is a
 * label with a rule under the open one — the convention a tab strip already has
 * everywhere else on the device.
 *
 * Built from tokens rather than from Material's `TabRow`, for the reason the bar
 * gives: the product has no icon set, and the row's defaults — indicator
 * animation, ripple, divider — are decisions nobody here has made yet.
 *
 * `selectable` with `Role.Tab`, not `clickable`: a screen reader then announces
 * "tab, selected, one of three", which a row of clickable labels cannot say.
 */
@Composable
fun LumoMobileSectionTabs(
    sections: List<LumoDestination>,
    selectedRoute: String?,
    onSelect: (LumoDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        sections.forEach { section ->
            val selected = section.route == selectedRoute

            Column(
                modifier = Modifier
                    // Equal shares: three sections should not shift sideways
                    // when the language changes the length of one label.
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(section) },
                    )
                    // With the label and the rule this clears the 48 dp a thumb
                    // needs, without a fixed height that would clip a large font.
                    .padding(top = LumoSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm + LumoSpacing.xs),
            ) {
                Text(
                    text = stringResource(section.titleRes),
                    style = LumoTypeScale.mobile.label,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(INDICATOR_HEIGHT)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                        ),
                )
            }
        }
    }
}

private val INDICATOR_HEIGHT = 2.dp
