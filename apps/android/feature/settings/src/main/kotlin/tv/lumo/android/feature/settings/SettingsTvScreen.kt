package tv.lumo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.component.LumoTvPlaceholder

/**
 * Placeholder for the television.
 *
 * Same strings and the same [SettingsViewModel] as [SettingsMobileScreen],
 * different screen: overscan margins, TV type scale, and a focus target the
 * remote can reach.
 */
@Composable
fun SettingsTvScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LumoTvPlaceholder(
        title = stringResource(R.string.feature_settings_title),
        body = stringResource(R.string.feature_settings_placeholder),
        modifier = modifier,
        detail = stringResource(sessionLabelOf(state)),
    )
}
