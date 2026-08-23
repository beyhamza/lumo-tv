package tv.lumo.android.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.designsystem.component.LumoTvPlaceholder

/**
 * Placeholder for the television.
 *
 * Same strings as [LiveMobileScreen], different screen: overscan margins,
 * TV type scale, and a focus target the remote can reach.
 */
@Composable
fun LiveTvScreen(modifier: Modifier = Modifier) {
    LumoTvPlaceholder(
        title = stringResource(R.string.feature_live_title),
        body = stringResource(R.string.feature_live_placeholder),
        modifier = modifier,
    )
}
