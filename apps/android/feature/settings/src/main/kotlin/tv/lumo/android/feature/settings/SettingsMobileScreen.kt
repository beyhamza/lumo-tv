package tv.lumo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The account, and the way out of it (US-04).
 *
 * Still mostly a placeholder — playback and language settings belong to later
 * stories — but no longer only a placeholder: it shows which account this device
 * is signed in as, and it can end that session.
 *
 * Both matter to the story rather than to this screen. "Reopened still connected"
 * is not observable if the only thing on display is the words *signed in*; the
 * address is the cheapest thing that could not have been guessed. And without a
 * way out, a device that signs in once can never be used to test signing in
 * again.
 *
 * The session line and the address come from [SettingsViewModel], which the
 * television reads too — a second reading of "is there a session" written here
 * would be exactly the duplicated logic AGENTS.md §2 calls a defect.
 */
@Composable
fun SettingsMobileScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_settings_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.feature_settings_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(sessionLabelOf(state)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        state.email?.let { address ->
            Text(
                text = address,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (state.signedIn) {
            OutlinedButton(
                onClick = viewModel::signOut,
                enabled = !state.signingOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.signingOut) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(LumoSpacing.md),
                    )
                } else {
                    Text(stringResource(R.string.feature_settings_sign_out))
                }
            }
        }
    }
}
