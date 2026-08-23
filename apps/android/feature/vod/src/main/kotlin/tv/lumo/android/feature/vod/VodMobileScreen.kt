package tv.lumo.android.feature.vod

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
fun VodMobileScreen(modifier: Modifier = Modifier) {
    LumoMobilePlaceholder(
        title = stringResource(R.string.feature_vod_title),
        body = stringResource(R.string.feature_vod_placeholder),
        modifier = modifier,
    )
}
