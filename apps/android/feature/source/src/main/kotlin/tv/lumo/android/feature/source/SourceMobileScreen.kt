package tv.lumo.android.feature.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.designsystem.component.LumoMobilePlaceholder

/**
 * Placeholder for the phone. Replaced when this feature's story is picked up.
 *
 * Text comes from this module's own strings.xml, in FR and EN — no literal ever
 * reaches a Composable (AGENTS.md §4).
 */
@Composable
fun SourceMobileScreen(modifier: Modifier = Modifier) {
    LumoMobilePlaceholder(
        title = stringResource(R.string.feature_source_title),
        body = stringResource(R.string.feature_source_placeholder),
        modifier = modifier,
    )
}
