package tv.lumo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.component.LumoMobilePlaceholder

/**
 * Placeholder for the phone. Replaced when this feature's story is picked up.
 *
 * Text comes from this module's own strings.xml, in FR and EN — no literal ever
 * reaches a Composable (AGENTS.md §4).
 *
 * The session line comes from [SettingsViewModel], not from this screen: the
 * television renders the same value from the same view model, and a second
 * reading of "is there a session" written here would be exactly the duplicated
 * logic AGENTS.md §2 calls a defect.
 */
@Composable
fun SettingsMobileScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LumoMobilePlaceholder(
        title = stringResource(R.string.feature_settings_title),
        body = stringResource(R.string.feature_settings_placeholder),
        modifier = modifier,
        detail = stringResource(sessionLabelOf(state)),
    )
}
