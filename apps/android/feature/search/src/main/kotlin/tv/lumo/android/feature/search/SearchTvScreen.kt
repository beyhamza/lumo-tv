package tv.lumo.android.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.designsystem.component.LumoTvPlaceholder

/**
 * Placeholder for the television.
 *
 * Same strings as [SearchMobileScreen], different screen: overscan margins,
 * TV type scale, and a focus target the remote can reach.
 */
@Composable
fun SearchTvScreen(modifier: Modifier = Modifier) {
    LumoTvPlaceholder(
        title = stringResource(R.string.feature_search_title),
        body = stringResource(R.string.feature_search_placeholder),
        modifier = modifier,
    )
}
