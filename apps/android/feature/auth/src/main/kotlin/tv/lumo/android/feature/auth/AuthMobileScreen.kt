package tv.lumo.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.component.LumoWordmark
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * Signing in with an email and a password (US-02), laid out as the M1 mock-up
 * draws it (docs/design/canvas, mobile artboard 05): the mark, a title, captioned
 * fields on the first surface level, a pill button, and the way to the other
 * half pinned to the bottom.
 *
 * <h2>The screen does not know what comes next</h2>
 *
 * There is no success branch and no navigation. Signing in opens a session,
 * `AppStartDecision` is watching it, and the shell rebuilds its graph onto the
 * catalogue — or onto the source form for an account that has none. Registration
 * and the television's device code end the same way, which is why neither needs
 * a callback either.
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
 *
 * <h2>Two fields where the mock-up draws one</h2>
 *
 * The mock-up's "Continuer" leads to a second step for the password. The
 * contract signs in with both at once and the flow is not being changed here;
 * the password field simply sits under the address, in the same clothes.
 */
@Composable
fun AuthMobileScreen(
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val submit = remember(viewModel) { { viewModel.submit() } }

    MobileAuthScaffold(
        modifier = modifier,
        footer = {
            // The way to the other half. Onboarding is where this choice will
            // eventually be made, but it is still a placeholder, and a screen
            // that cannot be left is worse than a link in the wrong place.
            TextButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.feature_auth_no_account),
                    color = mobileLinkColor(),
                )
            }
        },
    ) {
        MobileAuthHeading(
            title = stringResource(R.string.feature_auth_sign_in_title),
            subtitle = stringResource(R.string.feature_auth_sign_in_subtitle),
        )

        MobileFormField(
            caption = stringResource(R.string.feature_auth_email_caption),
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = stringResource(R.string.feature_auth_email_placeholder),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        MobileFormField(
            caption = stringResource(R.string.feature_auth_password_caption),
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            enabled = !state.submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            // The keyboard's own action submits, which is how this form is
            // actually used: two fields and a thumb already on the return key.
            keyboardActions = KeyboardActions(onDone = { submit() }),
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

        PillButton(
            label = stringResource(R.string.feature_auth_sign_in_submit),
            onClick = submit,
            enabled = state.canSubmit,
            busy = state.submitting,
        )

        // "Continue with Google" used to follow. Removed with the 0.2.0 scope
        // (US-025, decision of 17 September 2026): the free version ships
        // without a third-party sign-in, and a button that leads nowhere is not
        // drawn disabled. The server's OAuth endpoint and the repository call
        // behind it stay (docs/backlog/dette.md §1); nothing on this side calls
        // them.
    }
}

/**
 * The frame both authentication screens share: a scrolling body that keeps
 * clear of the keyboard, and a footer pinned under it.
 *
 * Pinned rather than scrolled with the rest because the mock-up draws it as a
 * bar, and because it is the one control somebody who landed on the wrong
 * screen is looking for — it should be where the thumb already is.
 */
@Composable
internal fun MobileAuthScaffold(
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The keyboard covers the button on a short screen otherwise, and a
            // submit button nobody can reach is the oldest bug in mobile forms.
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            content()
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(modifier = Modifier.padding(LumoSpacing.sm)) { footer() }
    }
}

/** The mark, then the title, then one line under it — as every M1 artboard opens. */
@Composable
internal fun MobileAuthHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        LumoWordmark(
            height = 20.dp,
            textColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = LumoSpacing.md, bottom = LumoSpacing.xs),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A captioned field: the label in capitals above, the input on the first
 * surface level with the charter's radius, cyan only when focused.
 *
 * The caption is a label of its own rather than the text field's floating one,
 * because the mock-up keeps the two apart and because a caption that stays put
 * is one a screen reader can read before the field rather than inside it.
 */
@Composable
internal fun MobileFormField(
    caption: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val placeholderSlot: (@Composable () -> Unit)? =
            if (placeholder == null) null else { { Text(placeholder) } }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholderSlot,
            singleLine = true,
            enabled = enabled,
            isError = isError,
            supportingText = supportingText,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = LumoShapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = LumoColors.Accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = LumoColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The primary action as the mock-up draws it: a 52 dp pill in light ink.
 *
 * Light on dark, not cyan — the same reading of the charter the theme makes:
 * cyan is the focus signature, and a filled cyan button puts the colour that
 * means "the remote is here" on something that is merely present.
 */
@Composable
internal fun PillButton(label: String, onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(PILL_HEIGHT),
    ) {
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(LumoSpacing.md),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

private val PILL_HEIGHT = 52.dp

/**
 * The colour of a link: the accent on the dark theme the product is designed
 * for, the primary ink on the phone's light scheme — where the Spectre cyan
 * falls under 3:1 and cannot carry text (see `LumoMobileTheme`).
 */
@Composable
internal fun mobileLinkColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) LumoColors.Accent else MaterialTheme.colorScheme.primary

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
