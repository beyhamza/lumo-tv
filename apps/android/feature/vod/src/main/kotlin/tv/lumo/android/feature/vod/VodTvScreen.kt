package tv.lumo.android.feature.vod

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.designsystem.component.LumoTvPlaceholder

/**
 * Placeholder for the television.
 *
 * The phone screen exists now ([VodMobileScreen]); this one is still S5-09, and
 * the reasons are in the sprint: a poster grid at three metres is not a poster
 * grid on a phone with bigger cards, and the D-pad map is a document before it is
 * a layout.
 */
@Composable
fun VodTvScreen(modifier: Modifier = Modifier) {
    LumoTvPlaceholder(
        title = stringResource(R.string.feature_vod_title),
        body = stringResource(R.string.feature_vod_placeholder),
        modifier = modifier,
    )
}
