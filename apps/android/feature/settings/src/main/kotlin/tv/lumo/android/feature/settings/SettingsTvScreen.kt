package tv.lumo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.component.LumoTvPlaceholder

/**
 * The same account state as [SettingsMobileScreen], on a television.
 *
 * The card itself is the control, and its own text says what the centre key does.
 * That is not a shortcut: a screen with one action wants one focus target, and a
 * button drawn inside a focusable card would give the D-pad two places to land on
 * a screen that has one thing to do (US-10, AGENTS.md §6).
 *
 * The address is what makes "reopened still connected" observable at three
 * metres — the words *signed in* would look identical on a set that had silently
 * forgotten the session.
 *
 * Signing out from a television is deliberately kept: it is how the qualification
 * plan gets a set back to its activation screen without a factory reset, and how
 * someone hands a box on.
 */
@Composable
fun SettingsTvScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LumoTvPlaceholder(
        title = stringResource(R.string.feature_settings_title),
        body = if (state.signedIn) {
            stringResource(R.string.feature_settings_sign_out_tv)
        } else {
            stringResource(R.string.feature_settings_placeholder)
        },
        modifier = modifier,
        // The address when there is one, the session line when there is not, so
        // the line is never empty and never says less than it could.
        detail = state.email ?: stringResource(sessionLabelOf(state)),
        // Nothing to press when there is no session to end — but the card stays
        // focusable, because a TV screen with no focus target leaves BACK as the
        // only key that does anything.
        onClick = { if (state.signedIn && !state.signingOut) viewModel.signOut() },
    )
}
