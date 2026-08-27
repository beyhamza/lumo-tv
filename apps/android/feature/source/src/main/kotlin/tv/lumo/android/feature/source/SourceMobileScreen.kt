package tv.lumo.android.feature.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.network.generated.model.SourceKind

/**
 * Registering a source (US-06, US-07) — the screen the whole product turns on.
 *
 * <h2>It has to work for someone who does not know what an M3U is</h2>
 *
 * That is the requirement S2-08 states, and it is why the first step is two
 * described choices rather than a dropdown, and why every field carries a line of
 * plain language underneath. A form of six correctly-labelled inputs is a form
 * only a person who already knows can fill in — and the people who already know
 * are not the ones this product has to reach.
 *
 * The subtitle says what Lumo is, once, where it matters most: no content is
 * provided, you bring your own (AGENTS.md §1). It is the sentence that explains
 * why this screen exists at all.
 *
 * <h2>Nothing here rejects what the server would accept</h2>
 *
 * Especially the Xtream address: with or without a scheme, with or without a
 * port, with or without a trailing slash. The contract normalises it and asks
 * clients to tolerate rather than reject (US-06), so the hint says so and no
 * validation contradicts it.
 *
 * <h2>The password is written once and never shown again</h2>
 *
 * It is cleared from the state the moment the source is registered, the API does
 * not return it to anybody — not even to its owner — and there is no edit screen
 * that could redisplay it. The hint under the field says all three, because a
 * password field that explains itself is a password field people fill in.
 */
@Composable
fun SourceMobileScreen(
    modifier: Modifier = Modifier,
    viewModel: AddSourceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            text = stringResource(R.string.feature_source_add_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.feature_source_add_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val step = state.step) {
            AddSourceStep.Loading -> CircularProgressIndicator()

            is AddSourceStep.NoRoom -> NoRoom(max = step.max)

            AddSourceStep.ChoosingKind -> KindChoice(onChoose = viewModel::onKindChosen)

            is AddSourceStep.Filling -> Form(
                kind = step.kind,
                state = state,
                viewModel = viewModel,
            )

            is AddSourceStep.Registered -> Registered()
        }
    }
}

/**
 * The plan is full.
 *
 * The number is the server's — read from `Entitlement.max_sources` — and both
 * ways out are named, because a user cannot guess that deleting a source is a
 * thing they may do.
 */
@Composable
private fun NoRoom(max: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Text(
            text = stringResource(R.string.feature_source_limit_title, max),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.feature_source_limit_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The two kinds, each with the sentence that tells them apart.
 *
 * Cards rather than a dropdown: this is the one decision on the screen, the two
 * options mean nothing to a newcomer by name alone, and a dropdown hides the
 * explanation behind a tap.
 */
@Composable
private fun KindChoice(onChoose: (SourceKind) -> Unit) {
    Text(
        text = stringResource(R.string.feature_source_kind_legend),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    KindCard(
        title = stringResource(R.string.feature_source_kind_m3u),
        body = stringResource(R.string.feature_source_kind_m3u_hint),
        onClick = { onChoose(SourceKind.M3U_URL) },
    )
    KindCard(
        title = stringResource(R.string.feature_source_kind_xtream),
        body = stringResource(R.string.feature_source_kind_xtream_hint),
        onClick = { onChoose(SourceKind.XTREAM) },
    )
}

@Composable
private fun KindCard(title: String, body: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline, LumoShapes.medium)
            .clickable(onClick = onClick)
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Form(
    kind: SourceKind,
    state: AddSourceState,
    viewModel: AddSourceViewModel,
) {
    val submit = { viewModel.submit() }

    Field(
        value = state.label,
        onChange = viewModel::onLabelChange,
        label = stringResource(R.string.feature_source_label_label),
        hint = stringResource(R.string.feature_source_label_hint),
        enabled = !state.submitting,
    )

    if (kind == SourceKind.XTREAM) {
        Field(
            value = state.host,
            onChange = viewModel::onHostChange,
            label = stringResource(R.string.feature_source_host_label),
            // Says out loud that the address is normalised, so nobody hunts for
            // the "right" form of it.
            hint = stringResource(R.string.feature_source_host_hint),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Uri,
        )
        Field(
            value = state.username,
            onChange = viewModel::onUsernameChange,
            label = stringResource(R.string.feature_source_username_label),
            hint = null,
            enabled = !state.submitting,
        )
        Field(
            value = state.password,
            onChange = viewModel::onPasswordChange,
            label = stringResource(R.string.feature_source_password_label),
            hint = stringResource(R.string.feature_source_password_hint),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Password,
            masked = true,
        )
    } else {
        Field(
            value = state.playlistUrl,
            onChange = viewModel::onPlaylistUrlChange,
            label = stringResource(R.string.feature_source_playlist_label),
            hint = stringResource(R.string.feature_source_playlist_hint),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Uri,
        )
    }

    Field(
        value = state.epgUrl,
        onChange = viewModel::onEpgUrlChange,
        label = stringResource(R.string.feature_source_epg_label),
        hint = stringResource(R.string.feature_source_epg_hint),
        enabled = !state.submitting,
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Done,
        onDone = submit,
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
            // "Checking…", not "Saving…": the server contacts the user's own
            // provider before answering, and that is what the wait is.
            Text(stringResource(R.string.feature_source_submitting))
        } else {
            Text(stringResource(R.string.feature_source_submit))
        }
    }

    TextButton(onClick = viewModel::onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.feature_source_change_kind))
    }
}

/**
 * Accepted, and being read.
 *
 * The server has already reached the provider and been let in — that is what the
 * `202` means. What the catalogue turns out to contain is the next screen's
 * business (`S2-09`), which is why this says what is happening and does not
 * pretend to know how it ends.
 */
@Composable
private fun Registered() {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Text(
            text = stringResource(R.string.feature_source_syncing_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.feature_source_syncing_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CircularProgressIndicator(modifier = Modifier.size(LumoSpacing.lg))
    }
}

/**
 * One field and the sentence under it.
 *
 * Every field has a hint except the username, which is the only one whose label
 * says everything there is to say. Hints that repeat their labels teach people to
 * stop reading them.
 */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String?,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    masked: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        supportingText = hint?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddSourceFailure.message(): String = when (this) {
    AddSourceFailure.Unreachable -> stringResource(R.string.feature_source_error_unreachable)
    AddSourceFailure.CredentialsRefused ->
        stringResource(R.string.feature_source_error_auth_failed)
    AddSourceFailure.NotAPlaylist -> stringResource(R.string.feature_source_error_invalid_format)
    AddSourceFailure.Empty -> stringResource(R.string.feature_source_error_empty)
    AddSourceFailure.TooLarge -> stringResource(R.string.feature_source_error_too_large)
    AddSourceFailure.TooManyStreams -> stringResource(R.string.feature_source_error_max_connections)
    AddSourceFailure.SubscriptionExpired -> stringResource(R.string.feature_source_error_expired)
    AddSourceFailure.NoRoomLeft -> stringResource(R.string.feature_source_error_limit)
    AddSourceFailure.Invalid -> stringResource(R.string.feature_source_error_validation)
    is AddSourceFailure.TooManyAttempts -> if (seconds == null) {
        stringResource(R.string.feature_source_error_rate_limited)
    } else {
        stringResource(R.string.feature_source_error_rate_limited_seconds, seconds)
    }
    AddSourceFailure.Offline -> stringResource(R.string.feature_source_error_offline)
    AddSourceFailure.Unexpected -> stringResource(R.string.feature_source_error_unexpected)
}

