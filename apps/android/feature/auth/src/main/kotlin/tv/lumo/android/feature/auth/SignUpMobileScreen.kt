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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import tv.lumo.android.network.generated.model.Locale

/**
 * Creating an account (US-01).
 *
 * <h2>The rule is shown before it is broken</h2>
 *
 * US-01 does not merely ask for a minimum length — it asks for the unmet rule to
 * be visible **before** submission and for the button to stay disabled until it
 * is met. That is what makes this different from an ordinary form: the usual
 * shape is to let someone type, submit, and be told. So the rule sits under the
 * field from the first keystroke, and turns red rather than appearing.
 *
 * It is also only a courtesy. The server enforces the same minimum and answers
 * `PASSWORD_TOO_WEAK`, which this screen renders too — a disabled button is not
 * a validation, and nothing stops a different client from posting anyway.
 *
 * <h2>What the already-registered case says, and what it does not</h2>
 *
 * It invites signing in or resetting a password without asserting that the
 * address has an account. The contract protects the same secret from the other
 * side, by answering in constant time; saying it plainly in the message would
 * give back what the timing was hidden to protect.
 */
@Composable
fun SignUpMobileScreen(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val submit = { viewModel.submit(locale) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_auth_sign_up_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.feature_auth_sign_up_subtitle),
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

        val tooShort = state.passwordTooShort == true

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.feature_auth_password_label)) },
            singleLine = true,
            enabled = !state.submitting,
            isError = tooShort,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            // Under the field from the start, red once it is not met. Appearing
            // only on failure would be the ordinary form this story rejects.
            supportingText = {
                Text(stringResource(R.string.feature_auth_password_rule))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text(stringResource(R.string.feature_auth_display_name_label)) },
            singleLine = true,
            enabled = !state.submitting,
            supportingText = {
                Text(stringResource(R.string.feature_auth_display_name_hint))
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        state.failure?.let { failure ->
            Text(
                text = failure.message(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
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
                Text(stringResource(R.string.feature_auth_sign_up_submit))
            }
        }

        TextButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_auth_have_account))
        }
    }
}

/**
 * The language the interface is being read in, as the contract spells it.
 *
 * Sent with the registration so the account is written down in the language the
 * person is actually using: the contract falls back to `Accept-Language` and then
 * to English, and this client sends no such header — so a French user would be
 * recorded as English and receive English email.
 *
 * Anything that is not French is English, because those are the two the product
 * ships (AGENTS.md §4). A German phone gets the English it is already seeing.
 */
@Composable
private fun currentLocale(): Locale {
    val tag = LocalConfiguration.current.locales[0]?.language
    return if (tag == "fr") Locale.FR else Locale.EN
}

@Composable
private fun SignUpFailure.message(): String = when (this) {
    SignUpFailure.EmailMayExist ->
        stringResource(R.string.feature_auth_error_email_may_exist)

    SignUpFailure.PasswordTooWeak ->
        stringResource(R.string.feature_auth_error_password_too_weak)

    SignUpFailure.Invalid -> stringResource(R.string.feature_auth_error_invalid)

    is SignUpFailure.TooManyAttempts -> if (seconds == null) {
        stringResource(R.string.feature_auth_error_rate_limited)
    } else {
        stringResource(R.string.feature_auth_error_rate_limited_seconds, seconds)
    }

    SignUpFailure.Offline -> stringResource(R.string.feature_auth_error_offline)

    SignUpFailure.Unexpected -> stringResource(R.string.feature_auth_error_unexpected)
}
