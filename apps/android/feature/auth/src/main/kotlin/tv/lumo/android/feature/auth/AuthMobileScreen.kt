package tv.lumo.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * Signing in with an email and a password (US-02).
 *
 * <h2>The screen does not know what comes next</h2>
 *
 * There is no success branch and no navigation. Signing in opens a session,
 * `AppStartDecision` is watching it, and the shell rebuilds its graph onto the
 * catalogue — or onto the source form for an account that has none. Registration,
 * Google and the television's device code all end the same way, which is why none
 * of them needs a callback either.
 *
 * <h2>Every refusal is a different sentence</h2>
 *
 * Four, and they are not interchangeable:
 *
 * - wrong credentials get a message that does **not** say whether the email
 *   exists. That is the contract's rule and the reason the server's own answer is
 *   generic: an error that distinguishes the two turns a sign-in form into a way
 *   to test whether somebody has an account here;
 * - too many attempts is a **wait**, with the server's own delay when it sent
 *   one. Saying "something went wrong" here would invite the user to try again
 *   immediately and be refused again — which also extends the delay;
 * - the plan's device ceiling names both ways out, because the user cannot guess
 *   that unlinking another device is a thing they may do;
 * - no network says so, because it is the only case where trying again unchanged
 *   is worth offering.
 */
@Composable
fun AuthMobileScreen(
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The keyboard covers the button on a short screen otherwise, and a
            // submit button nobody can reach is the oldest bug in mobile forms.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_auth_sign_in_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.feature_auth_sign_in_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(R.string.feature_auth_email_label)) },
            singleLine = true,
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        val submit = remember(viewModel) { { viewModel.submit() } }

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.feature_auth_password_label)) },
            singleLine = true,
            enabled = !state.submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            // The keyboard's own action submits, which is how this form is
            // actually used: two fields and a thumb already on the return key.
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        state.failure?.let { failure ->
            Text(
                text = failure.message(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                // Announced when it appears: a message a screen reader has to be
                // hunted for is a message a blind user does not get.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        Button(
            onClick = submit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(LumoSpacing.md),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.feature_auth_sign_in_submit))
            }
        }

        // The other way in, when the build has an OAuth client. It draws
        // nothing when it has not, which is the state of a fresh checkout.
        GoogleSignInButton()

        // The way to the other half. Onboarding is where this choice will
        // eventually be made, but it is still a placeholder, and a screen that
        // cannot be left is worse than a link in the wrong place.
        TextButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_auth_no_account))
        }
    }
}

/** The wording for each refusal, resolved here because this is where the locale is. */
@Composable
private fun SignInFailure.message(): String = when (this) {
    SignInFailure.InvalidCredentials ->
        stringResource(R.string.feature_auth_error_invalid_credentials)

    is SignInFailure.TooManyAttempts -> if (seconds == null) {
        // No `Retry-After`, or one this build could not read. Saying "later"
        // without a number is honest; inventing one is not.
        stringResource(R.string.feature_auth_error_rate_limited)
    } else {
        stringResource(R.string.feature_auth_error_rate_limited_seconds, seconds)
    }

    SignInFailure.DeviceLimitReached ->
        stringResource(R.string.feature_auth_error_device_limit)

    SignInFailure.Offline -> stringResource(R.string.feature_auth_error_offline)

    SignInFailure.Unexpected -> stringResource(R.string.feature_auth_error_unexpected)
}
