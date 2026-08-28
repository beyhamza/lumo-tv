package tv.lumo.android.feature.source

import androidx.annotation.StringRes
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
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SyncStep

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
    viewModel: SourceViewModel = hiltViewModel(),
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
                correcting = false,
            )

            is AddSourceStep.Fixing -> Form(
                kind = step.kind,
                state = state,
                viewModel = viewModel,
                correcting = true,
            )

            is AddSourceStep.Watching -> Watching(state = state, viewModel = viewModel)
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

/**
 * @param correcting true when this is the second attempt at a source that failed
 * to import. The name is not asked for again — it is already set and was not the
 * problem — and the password field is empty, because the API returns it to
 * nobody, including its owner (US-06).
 */
@Composable
private fun Form(
    kind: SourceKind,
    state: AddSourceState,
    viewModel: SourceViewModel,
    correcting: Boolean,
) {
    val submit = { viewModel.submit() }

    if (!correcting) {
        Field(
            value = state.label,
            onChange = viewModel::onLabelChange,
            label = stringResource(R.string.feature_source_label_label),
            hint = stringResource(R.string.feature_source_label_hint),
            enabled = !state.submitting,
        )
    }

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

    if (!correcting) {
        TextButton(onClick = viewModel::onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_source_change_kind))
        }
    }
}

/**
 * What the source is doing, and what to do about it (S2-09).
 *
 * Three shapes, and the third has four exits. The rule the whole screen turns on:
 * **a message with the wrong button cannot be acted on.** Offering "retry" to
 * somebody whose password was refused makes them press it until they give up, and
 * offering nothing to somebody whose server was merely down makes them re-type a
 * correct address.
 */
@Composable
private fun Watching(state: AddSourceState, viewModel: SourceViewModel) {
    when (val view = state.view) {
        null -> CircularProgressIndicator()

        is SourceView.Importing -> Importing(view)

        is SourceView.Ready -> Ready(view)

        is SourceView.Failed -> Failed(view, viewModel)
    }

    // A poll that stopped answering. The last known state stays above it, because
    // it is the only true thing this screen knows.
    state.failure?.let { failure ->
        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Importing, and how far.
 *
 * The named step is the point. Onboarding waits here — a large playlist takes up
 * to a minute — and a minute of an indeterminate spinner is where somebody
 * concludes the application is broken and closes it. A phase says two things a
 * spinner cannot: that it is moving, and how far it got if it stops.
 */
@Composable
private fun Importing(view: SourceView.Importing) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Text(
            text = stringResource(R.string.feature_source_syncing_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(view.step.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
 * Ready, and counted.
 *
 * US-06 and US-07 both ask for the number of channels found, and the reason is
 * `SOURCE_EMPTY`: a success screen that says only "ready" is indistinguishable
 * from one that imported nothing.
 */
@Composable
private fun Ready(view: SourceView.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Text(
            text = stringResource(R.string.feature_source_ready_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(
                R.string.feature_source_counts,
                view.channels ?: 0,
                view.categories ?: 0,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // Xtream only, and only when the panel gives them. Absent is not zero,
        // so an absent value is a line that is not drawn rather than a "0".
        view.expiresAt?.let {
            Text(
                text = stringResource(R.string.feature_source_expires_on, it),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        view.maxConnections?.let {
            Text(
                text = stringResource(R.string.feature_source_max_connections, it),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Failed(view: SourceView.Failed, viewModel: SourceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Text(
            text = stringResource(R.string.feature_source_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(view.reason.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )

        // The explanation only the format failure needs: somebody who typed the
        // wrong kind of address cannot correct it without knowing what the right
        // kind looks like.
        if (view.reason == IngestionErrorCode.SOURCE_INVALID_FORMAT) {
            Text(
                text = stringResource(R.string.feature_source_error_invalid_format_help),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (view.exit) {
            SourceExit.FixCredentials -> Button(
                onClick = viewModel::fixInput,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_source_fix_credentials)) }

            SourceExit.FixAddress -> Button(
                onClick = viewModel::fixInput,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_source_fix_address)) }

            SourceExit.Retry -> Button(
                onClick = viewModel::retry,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_source_retry)) }

            // Nothing on this screen can fix an expired subscription, a playlist
            // past the size cap, or a playlist with no channel in it. A button
            // that cannot help is worse than no button: it costs a try and a wait
            // to learn what the sentence above already said.
            SourceExit.None -> Unit
        }
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


/**
 * The four phases, as the server actually distinguishes them.
 *
 * Null is a source the server has accepted but not started, which is a real state
 * and gets its own line rather than an empty one.
 */
@StringRes
private fun SyncStep?.labelRes(): Int = when (this) {
    SyncStep.CONNECTING -> R.string.feature_source_step_connecting
    SyncStep.AUTHENTICATED -> R.string.feature_source_step_authenticated
    SyncStep.PARSING_CHANNELS -> R.string.feature_source_step_parsing
    SyncStep.PARSING_VOD -> R.string.feature_source_step_parsing_vod
    SyncStep.FETCHING_EPG -> R.string.feature_source_step_epg
    // Includes a phase newer than this build: the honest answer is that it
    // started, which is true of every phase there could be.
    else -> R.string.feature_source_step_pending
}

/**
 * One sentence per ingestion code, and never a shared one.
 *
 * The contract forbids a generic message on this surface, and the reason is
 * visible in the four sentences: they send the reader to four different places.
 */
@StringRes
private fun IngestionErrorCode?.messageRes(): Int = when (this) {
    IngestionErrorCode.SOURCE_AUTH_FAILED -> R.string.feature_source_error_auth_failed
    IngestionErrorCode.SOURCE_UNREACHABLE -> R.string.feature_source_error_unreachable
    IngestionErrorCode.SOURCE_INVALID_FORMAT -> R.string.feature_source_error_invalid_format
    IngestionErrorCode.SOURCE_EMPTY -> R.string.feature_source_error_empty
    IngestionErrorCode.SOURCE_TOO_LARGE -> R.string.feature_source_error_too_large
    IngestionErrorCode.SOURCE_MAX_CONNECTIONS -> R.string.feature_source_error_max_connections
    IngestionErrorCode.SOURCE_EXPIRED -> R.string.feature_source_error_expired
    else -> R.string.feature_source_error_unexpected
}
