package tv.lumo.android.feature.source

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import tv.lumo.android.core.data.retryAfterMinutes
import tv.lumo.android.core.designsystem.effect.PollWhile
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.labelRes
import tv.lumo.android.core.data.messageRes
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SyncStep

/**
 * The `source` destination on the phone: **"My sources"**, and the flow that adds
 * or corrects one (US-024).
 *
 * <h2>One destination, two faces</h2>
 *
 * The list is what the destination shows; *Add a source* and *Correct…* open the
 * flow over it, and Back — or the flow's own way out — returns to the list. A
 * state of this screen rather than a second route: both applications, the
 * switcher and Settings already point at `source`, and the flow has nothing a
 * deep link could name.
 *
 * An account with **no source** lands directly in the flow, as it always has: a
 * list with nothing in it and a button under it would be one more press between
 * a new user and the only thing they can do (US-06).
 *
 * <h2>Leaving during an import is allowed, and is the point</h2>
 *
 * US-024: while a source imports, somebody can leave this screen and browse
 * another source. So Back closes the flow at any step; the import goes on on the
 * server and the list shows its real step.
 *
 * @param onDiscoverCatalogue *Discover my catalogue*, once the source being
 * browsed is ready: the application decides where that is — Home.
 */
@Composable
fun SourceMobileScreen(
    onDiscoverCatalogue: () -> Unit,
    modifier: Modifier = Modifier,
    listViewModel: MySourcesViewModel = hiltViewModel(),
    flowViewModel: SourceViewModel = hiltViewModel(),
) {
    val list by listViewModel.state.collectAsStateWithLifecycle()
    val flow by flowViewModel.state.collectAsStateWithLifecycle()

    // A synchronisation on display has to move. From the composition, so that it
    // stops with the screen.
    PollWhile(
        active = list.anyRefreshing && flow.step == AddSourceStep.Idle,
        everyMillis = SOURCE_LIST_POLL_MILLIS,
        onTick = listViewModel::reload,
    )

    // No source at all: the flow is the screen. Keyed on the phase, so that
    // deleting the last source lands here too (US-018).
    val empty = list.phase == MySourcesPhase.Empty
    LaunchedEffect(empty) {
        if (empty && flow.step == AddSourceStep.Idle) flowViewModel.startAdding(knownCount = 0)
    }

    val flowOpen = flow.step != AddSourceStep.Idle

    // Back leaves the flow for the list — but not when there is no list to go
    // back to, where Back is the application's.
    BackHandler(enabled = flowOpen && !empty) {
        flowViewModel.close()
        listViewModel.reload()
    }

    when {
        flowOpen -> AddSourceFlow(
            state = flow,
            viewModel = flowViewModel,
            onDone = {
                flowViewModel.close()
                listViewModel.reload()
            },
            onDiscoverCatalogue = {
                flowViewModel.close()
                onDiscoverCatalogue()
            },
            canReturnToList = !empty,
            modifier = modifier,
        )

        list.phase == MySourcesPhase.Listed -> MySourcesList(
            state = list,
            actions = MySourcesActions(
                onAdd = { flowViewModel.startAdding(knownCount = list.sources.size) },
                onUse = listViewModel::use,
                onRefresh = listViewModel::refresh,
                onFix = flowViewModel::startFixing,
                onAutoSync = listViewModel::setAutoSync,
                onAskRename = listViewModel::askRename,
                onRenameChange = listViewModel::onRenameChange,
                onConfirmRename = listViewModel::confirmRename,
                onAskDelete = listViewModel::askDelete,
                onConfirmDelete = listViewModel::confirmDelete,
                onDismissDialog = listViewModel::dismissDialog,
            ),
            modifier = modifier,
        )

        list.phase == MySourcesPhase.Unavailable -> SourcesUnavailable(
            onRetry = listViewModel::reload,
            modifier = modifier,
        )

        else -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

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
private fun AddSourceFlow(
    state: AddSourceState,
    viewModel: SourceViewModel,
    onDone: () -> Unit,
    onDiscoverCatalogue: () -> Unit,
    canReturnToList: Boolean,
    modifier: Modifier = Modifier,
) {
    val correcting = state.step is AddSourceStep.Fixing

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
            text = stringResource(
                when {
                    correcting -> R.string.feature_source_fix_title
                    state.step is AddSourceStep.Watching -> R.string.feature_source_watch_title
                    else -> R.string.feature_source_add_title
                },
            ),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // The source being watched or corrected, by name: with several sources
        // "Importing" alone does not say which.
        state.source?.takeIf { state.step !is AddSourceStep.Filling }?.let { source ->
            Text(
                text = source.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (state.step !is AddSourceStep.Watching && !correcting) {
            Text(
                text = stringResource(R.string.feature_source_add_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val step = state.step) {
            AddSourceStep.Idle, AddSourceStep.Loading -> CircularProgressIndicator()

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

            is AddSourceStep.Watching -> Watching(
                state = state,
                viewModel = viewModel,
                onDiscoverCatalogue = onDiscoverCatalogue,
            )
        }

        // The way back to the list, at every step — an import in progress
        // included (US-024). Absent when there is no list to go back to.
        if (canReturnToList) {
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_source_back_to_list))
            }
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
private fun Watching(
    state: AddSourceState,
    viewModel: SourceViewModel,
    onDiscoverCatalogue: () -> Unit,
) {
    when (val view = state.view) {
        null -> CircularProgressIndicator()

        is SourceView.Importing -> Importing(view)

        is SourceView.Ready -> {
            Ready(view)

            // After adding (US-024). The account's first source is already the
            // one this device browses: the catalogue is one press away. An
            // additional one did not take the selection, so the offer is to take
            // it — and once taken, the first offer follows.
            if (state.watchedIsActive) {
                Button(onClick = onDiscoverCatalogue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feature_source_discover))
                }
            } else {
                Button(onClick = viewModel::useWatched, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feature_source_list_use))
                }
            }
        }

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
        // Said, because nothing on a waiting screen suggests it: the import does
        // not need this screen, and another source can be browsed meanwhile.
        Text(
            text = stringResource(R.string.feature_source_syncing_leave),
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
        // Only when the server gave both: an unknown number is never drawn as a
        // zero (US-024), and "0 channels" under "Source ready" is `SOURCE_EMPTY`'s
        // sentence, not this one's.
        val channels = view.channels
        val categories = view.categories
        if (channels != null && categories != null) {
            Text(
                text = stringResource(R.string.feature_source_counts, channels, categories),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
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
    AddSourceFailure.Unreachable -> stringResource(DataR.string.core_data_ingestion_unreachable)
    AddSourceFailure.CredentialsRefused ->
        stringResource(DataR.string.core_data_ingestion_auth_failed)
    AddSourceFailure.NotAPlaylist -> stringResource(DataR.string.core_data_ingestion_invalid_format)
    AddSourceFailure.Empty -> stringResource(DataR.string.core_data_ingestion_empty)
    AddSourceFailure.TooLarge -> stringResource(DataR.string.core_data_ingestion_too_large)
    AddSourceFailure.TooManyStreams -> stringResource(DataR.string.core_data_ingestion_max_connections)
    AddSourceFailure.SubscriptionExpired -> stringResource(DataR.string.core_data_ingestion_expired)
    AddSourceFailure.NoRoomLeft -> stringResource(R.string.feature_source_error_limit)
    AddSourceFailure.Invalid -> stringResource(R.string.feature_source_error_validation)
    // The server's own delay, in whole minutes rounded up, or no number at all:
    // a duration is never invented (US-024). Five minutes said as "300 seconds"
    // is a number nobody reads.
    is AddSourceFailure.TooManyAttempts -> waitMessage(retryAfterMinutes(seconds))
    AddSourceFailure.Offline -> stringResource(R.string.feature_source_error_offline)
    AddSourceFailure.Unexpected -> stringResource(DataR.string.core_data_ingestion_unexpected)
}
