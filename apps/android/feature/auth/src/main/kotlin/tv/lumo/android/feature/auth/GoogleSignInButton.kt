package tv.lumo.android.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * "Continue with Google", for the sign-in and the sign-up screen alike (US-03).
 *
 * <h2>It draws nothing when no OAuth client is configured</h2>
 *
 * Not a disabled button and not an error: nothing. That is the state of a fresh
 * checkout — no client ID in `.env`, and none may be committed (AGENTS.md §5) — and
 * a button that is certain to fail teaches a user that this application is broken.
 * The email form is complete on its own, which is what makes hiding this honest
 * rather than hiding a problem.
 *
 * <h2>One button, one flow, both screens</h2>
 *
 * The screens are two because a form is two, but Google is not: on a first sign-in
 * the account is created, on a later one it is linked or reused, and the server
 * decides which. Asking the user to pick the right screen first would be asking
 * them a question only the server can answer (US-03).
 */
@Composable
fun GoogleSignInButton(
    modifier: Modifier = Modifier,
    viewModel: GoogleSignInViewModel = hiltViewModel(),
) {
    if (!viewModel.available) return

    val state by viewModel.state.collectAsStateWithLifecycle()
    // The picker opens a window over this one, so it needs the activity this
    // screen is in — not the application context.
    val activity = LocalActivity.current ?: return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        HorizontalDivider()

        OutlinedButton(
            onClick = { viewModel.signIn(activity) },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(LumoSpacing.md),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(stringResource(R.string.feature_auth_google_continue))
            }
        }

        state.failure?.let { failure ->
            Text(
                text = failure.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

/** The wording for each refusal, resolved here because this is where the locale is. */
@Composable
private fun GoogleSignInFailure.message(): String = when (this) {
    GoogleSignInFailure.NoAccount -> stringResource(R.string.feature_auth_google_no_account)

    // Says what is true — this way in is not working — and does not suggest
    // trying again, because nothing will have changed by the next press.
    GoogleSignInFailure.Unavailable -> stringResource(R.string.feature_auth_google_unavailable)

    // Not the user's doing: a token that failed the server's verification, most
    // often a build configured with the wrong OAuth client. Blaming the account
    // would send somebody to Google to fix something that is not there.
    GoogleSignInFailure.Rejected -> stringResource(R.string.feature_auth_google_rejected)

    is GoogleSignInFailure.TooManyAttempts -> if (seconds == null) {
        stringResource(R.string.feature_auth_error_rate_limited)
    } else {
        stringResource(R.string.feature_auth_error_rate_limited_seconds, seconds)
    }

    // The same ceiling and the same sentence as an email sign-in: the plan's
    // device limit does not care how the session was opened.
    GoogleSignInFailure.DeviceLimitReached ->
        stringResource(R.string.feature_auth_error_device_limit)

    GoogleSignInFailure.Offline -> stringResource(R.string.feature_auth_error_offline)

    GoogleSignInFailure.Unexpected -> stringResource(R.string.feature_auth_error_unexpected)
}
