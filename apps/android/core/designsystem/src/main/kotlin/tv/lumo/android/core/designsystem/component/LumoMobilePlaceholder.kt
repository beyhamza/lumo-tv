package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTypeScale

/**
 * The phone placeholder each feature renders until it has a real screen.
 *
 * It lives here rather than being copied into eight feature modules. Its TV
 * counterpart is [LumoTvPlaceholder] — deliberately a separate file, because the
 * two draw from different Material libraries (`androidx.compose.material3` here,
 * `androidx.tv.material3` there) and mixing them in one file means every `Text`
 * needs a fully-qualified name.
 *
 * Takes resolved strings: resolution happens in the feature, from that feature's
 * own `strings.xml`, so a missing translation is caught by lint in the module
 * that owns the text (AGENTS.md §4).
 */
@Composable
fun LumoMobilePlaceholder(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    // Colours come from the theme rather than straight from LumoColors: the
    // phone follows the system's light/dark setting, and LumoMobileTheme is
    // where the tokens are mapped onto each scheme. Painting Ink directly here
    // would give a dark screen under a light status bar. The television has no
    // such setting, which is why its components do read the tokens directly.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = LumoTypeScale.mobile.display,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = LumoTypeScale.mobile.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
