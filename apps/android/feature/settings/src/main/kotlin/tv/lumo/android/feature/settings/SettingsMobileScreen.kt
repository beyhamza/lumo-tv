package tv.lumo.android.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.common.locale.InterfaceLanguage
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.designsystem.component.LumoConfirmDialog
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.network.generated.model.Device

/**
 * The settings of the phone: the five sections of US-025 as a list, then the
 * section that was opened (S13-04, docs/design/0.2.0/settings.md).
 *
 * <h2>List, then section</h2>
 *
 * Account and devices · My sources · Playback · Application · Help and
 * information, in the website's order ([SettingsSection]). A row opens its
 * section over the list; `BACK` closes it before leaving Settings. "My sources"
 * opens US-024's screen rather than a second one ([SettingsSections.targetOf]).
 * The open section is saved, so a recreation — a change of interface language
 * is one — comes back to it.
 *
 * <h2>Playback edits what the player reads</h2>
 *
 * Audio language, subtitles on/off and their language, next episode, and
 * playback on mobile data: one store, `PlaybackPreferencesRepository`, so a
 * subtitle preference set here is what the next stream opens with, and a track
 * picked in the player is what this section shows next.
 *
 * <h2>Application: the platform's per-app language</h2>
 *
 * French or English, each named in itself, through [InterfaceLanguage] — the
 * activity is redrawn in the new language and the choice outlives a sign-out
 * (Q6). It writes nothing to the account.
 *
 * <h2>Devices, as the server tells them</h2>
 *
 * The row marked "This device" is the one `is_current` names; the others print a
 * last activity when the server has one and say "unavailable" when it does not
 * — never "online", which nothing announces. Each of the others can be
 * disconnected, behind a confirmation that names it and whose default is
 * Cancel; a refusal keeps the device and the question, with the reason under it.
 */
@Composable
fun SettingsMobileScreen(
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val open = SettingsSections.fromKey(openKey)
    var chooser by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = open != null) { openKey = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        if (open == null) {
            Text(
                text = stringResource(R.string.feature_settings_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            SectionList(
                state = state,
                onOpen = { section ->
                    when (SettingsSections.targetOf(section, SettingsSurface.Mobile)) {
                        SettingsTarget.OpenSources -> onOpenSources()
                        is SettingsTarget.Panel -> openKey = section.key
                    }
                },
            )
            if (!state.signedIn) {
                Text(
                    text = stringResource(sessionLabelOf(state)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SectionHeader(section = open, onBack = { openKey = null })
            when (open) {
                SettingsSection.Account -> AccountSection(state, viewModel)
                // Never drawn here: the row opens US-024's screen.
                SettingsSection.Sources -> Unit
                SettingsSection.Playback -> PlaybackSection(
                    state = state,
                    viewModel = viewModel,
                    onChooseLanguage = { chooser = it },
                )
                SettingsSection.Application -> ApplicationSection()
                SettingsSection.About -> AboutSection(state)
            }
        }
    }

    when (chooser) {
        CHOOSE_AUDIO -> LanguageChooser(
            title = stringResource(R.string.feature_settings_audio_language),
            current = state.playback.tracks.audioLanguage,
            onChoose = {
                viewModel.setAudioLanguage(it)
                chooser = null
            },
            onDismiss = { chooser = null },
        )
        CHOOSE_SUBTITLES -> LanguageChooser(
            title = stringResource(R.string.feature_settings_subtitle_language),
            current = state.playback.tracks.subtitleLanguage,
            onChoose = {
                viewModel.setSubtitleLanguage(it)
                chooser = null
            },
            onDismiss = { chooser = null },
        )
    }

    when (val asked = state.confirmation) {
        null -> Unit

        SettingsConfirmation.SignOut -> LumoConfirmDialog(
            title = stringResource(R.string.feature_settings_sign_out_title),
            message = stringResource(R.string.feature_settings_sign_out_body),
            confirmLabel = stringResource(R.string.feature_settings_sign_out),
            cancelLabel = stringResource(R.string.feature_settings_cancel),
            onConfirm = viewModel::confirmSignOut,
            onCancel = viewModel::dismissConfirmation,
        )

        is SettingsConfirmation.Revoke -> LumoConfirmDialog(
            title = stringResource(R.string.feature_settings_revoke_title, deviceTitle(asked.device)),
            message = stringResource(R.string.feature_settings_revoke_body),
            confirmLabel = stringResource(R.string.feature_settings_revoke),
            cancelLabel = stringResource(R.string.feature_settings_cancel),
            onConfirm = viewModel::confirmRevoke,
            onCancel = viewModel::dismissConfirmation,
            busy = asked.busy,
            failure = asked.failure?.let { failure ->
                stringResource(
                    if (failure is LumoError.Offline) {
                        R.string.feature_settings_revoke_offline
                    } else {
                        R.string.feature_settings_revoke_failed
                    },
                )
            },
        )
    }
}

private const val CHOOSE_AUDIO = "audio"
private const val CHOOSE_SUBTITLES = "subtitles"

/** The five rows, each with the one fact that tells it apart before it is opened. */
@Composable
private fun SectionList(state: SettingsUiState, onOpen: (SettingsSection) -> Unit) {
    Section(title = null) {
        SettingsSection.entries.forEachIndexed { index, section ->
            if (index > 0) RowDivider()
            SettingsRow(
                label = stringResource(section.titleRes),
                note = when (section) {
                    SettingsSection.Account -> state.email ?: stringResource(sessionLabelOf(state))
                    SettingsSection.Sources -> state.sourceCount?.let {
                        pluralStringResource(R.plurals.feature_settings_sources_active, it, it)
                    }
                    SettingsSection.Playback -> null
                    SettingsSection.Application -> InterfaceLanguage.nativeName(currentInterfaceLanguage())
                    SettingsSection.About -> state.appVersion?.let {
                        stringResource(R.string.feature_settings_version_value, it)
                    }
                },
                chevron = true,
                onClick = { onOpen(section) },
            )
        }
    }
}

/** "‹ Settings" over the section's own title. `BACK` does the same. */
@Composable
private fun SectionHeader(section: SettingsSection, onBack: () -> Unit) {
    val back = stringResource(R.string.feature_settings_back)
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = back },
        ) {
            Text(
                text = stringResource(R.string.feature_settings_back_label),
                color = accentInk(),
            )
        }
        Text(
            text = stringResource(section.titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

// ---- Account and devices ---------------------------------------------------

@Composable
private fun AccountSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    Section(title = null) {
        AccountCard(state)
        RowDivider()
        Devices(state = state, onRevoke = viewModel::askRevoke, onRetry = viewModel::reloadDevices)
        RowDivider()
        SettingsRow(
            label = stringResource(R.string.feature_settings_pair_tv),
            value = state.activationLabel,
            valueColor = accentInk(),
            chevron = true,
            onClick = { openInBrowser(context, state.activationUrl) },
        )
        if (state.signedIn) {
            RowDivider()
            SettingsRow(
                label = stringResource(R.string.feature_settings_sign_out),
                labelColor = MaterialTheme.colorScheme.error,
                onClick = if (state.signingOut) null else viewModel::askSignOut,
            ) {
                if (state.signingOut) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(LumoSpacing.md),
                    )
                }
            }
        }
    }
}

// ---- Playback ----------------------------------------------------------------

/**
 * S13-E05/E06: languages, next episode, and — on a phone — mobile data.
 * Quality is not here: it belongs to the current playback (US-023).
 */
@Composable
private fun PlaybackSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onChooseLanguage: (String) -> Unit,
) {
    val tracks = state.playback.tracks
    val notSet = stringResource(R.string.feature_settings_language_not_set)

    Note(stringResource(R.string.feature_settings_playback_scope))
    Section(title = null) {
        SettingsSections.playbackRows(SettingsSurface.Mobile).forEachIndexed { index, row ->
            if (index > 0) RowDivider()
            when (row) {
                PlaybackRow.AudioLanguage -> SettingsRow(
                    label = stringResource(R.string.feature_settings_audio_language),
                    value = playbackLanguageName(tracks.audioLanguage) ?: notSet,
                    note = stringResource(R.string.feature_settings_audio_language_note),
                    chevron = true,
                    onClick = { onChooseLanguage(CHOOSE_AUDIO) },
                )
                PlaybackRow.Subtitles -> SettingsRow(
                    label = stringResource(R.string.feature_settings_subtitles),
                    onClick = { viewModel.setSubtitlesEnabled(!tracks.subtitlesEnabled) },
                ) {
                    Switch(checked = tracks.subtitlesEnabled, onCheckedChange = viewModel::setSubtitlesEnabled)
                }
                PlaybackRow.SubtitleLanguage -> SettingsRow(
                    label = stringResource(R.string.feature_settings_subtitle_language),
                    value = playbackLanguageName(tracks.subtitleLanguage) ?: notSet,
                    note = stringResource(R.string.feature_settings_subtitle_language_note),
                    chevron = true,
                    onClick = { onChooseLanguage(CHOOSE_SUBTITLES) },
                )
                PlaybackRow.AutoplayNext -> SettingsRow(
                    label = stringResource(R.string.feature_settings_autoplay_next),
                    note = stringResource(R.string.feature_settings_autoplay_next_note),
                    onClick = { viewModel.setAutoplayNext(!state.playback.autoplayNext) },
                ) {
                    Switch(checked = state.playback.autoplayNext, onCheckedChange = viewModel::setAutoplayNext)
                }
                PlaybackRow.MobileData -> SettingsRow(
                    label = stringResource(R.string.feature_settings_mobile_data),
                    note = stringResource(R.string.feature_settings_mobile_data_note),
                    onClick = { viewModel.setMobileDataAllowed(!state.playback.mobileDataAllowed) },
                ) {
                    Switch(
                        checked = state.playback.mobileDataAllowed,
                        onCheckedChange = viewModel::setMobileDataAllowed,
                    )
                }
            }
        }
    }
    if (state.playbackNotSaved) {
        Text(
            text = stringResource(R.string.feature_settings_playback_not_saved),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Note(stringResource(R.string.feature_settings_quality_note))
}

/** A radio list of the offered languages, the stored one ticked. Choosing closes it. */
@Composable
private fun LanguageChooser(
    title: String,
    current: String?,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(SettingsSections.languageChoices(current)) { code ->
                    val selected = SettingsSections.isCurrentLanguage(code, current)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(code) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(text = playbackLanguageName(code) ?: code, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}

// ---- Application ---------------------------------------------------------------

/**
 * S13-E08: French or English, each named in itself. Choosing redraws the
 * application in that language; the current one is ticked and does nothing.
 */
@Composable
private fun ApplicationSection() {
    val context = LocalContext.current
    val current = currentInterfaceLanguage()

    Section(title = stringResource(R.string.feature_settings_language)) {
        InterfaceLanguage.SUPPORTED.forEachIndexed { index, language ->
            if (index > 0) RowDivider()
            val selected = language == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!selected) context.findActivity()?.let { InterfaceLanguage.choose(it, language) }
                        },
                    )
                    .heightIn(min = 56.dp)
                    .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(
                    text = InterfaceLanguage.nativeName(language),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Text(
                        text = stringResource(R.string.feature_settings_language_current),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Note(stringResource(R.string.feature_settings_language_note))
}

// ---- Help and information ----------------------------------------------------

@Composable
private fun AboutSection(state: SettingsUiState) {
    val context = LocalContext.current
    val language = currentInterfaceLanguage()
    Section(title = null) {
        // Derived from the same variable as the pairing link, so a local
        // stack cannot pair against one host and open guides on another.
        // Absent rather than dead when the build carries no origin.
        SettingsLinks.guidesUrlOf(state.activationUrl, language)?.let { guides ->
            SettingsRow(
                label = stringResource(R.string.feature_settings_guides),
                value = SettingsLinks.label(guides),
                valueColor = accentInk(),
                chevron = true,
                onClick = { openInBrowser(context, guides) },
            )
            RowDivider()
        }
        // Privacy and terms would follow, and do not: the website has no
        // such pages yet (docs/design/0.2.0/settings.md), and a link to a
        // page that does not exist is a link somebody presses to find out.
        SettingsRow(
            label = stringResource(R.string.feature_settings_version),
            value = state.appVersion ?: stringResource(R.string.feature_settings_unknown_value),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = LumoSpacing.xs),
    )
}

/**
 * The account: a disc in the brand gradient carrying the address's initial,
 * the display name when the user gave one, and the address.
 *
 * The gradient is the one place the charter allows it besides focus and
 * progress — the mark — and the disc is the mark's own shape, drawn the way
 * `LumoWordmark` draws it. No chevron: the contract lets the profile change
 * only its display name and locale, and editing it is not in this lot
 * (docs/design/0.2.0/settings.md) — a chevron over nothing is a promise.
 */
@Composable
private fun AccountCard(state: SettingsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(LumoColors.Accent, LumoColors.AccentViolet),
                        center = Offset(0.35f, 0.3f),
                        radius = 0.75f,
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.initial,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = LumoColors.OnAccent,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.displayName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.feature_settings_account_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The address when there is one, the session line when there is
                // not, so the line is never empty and never says less than it could.
                text = state.email ?: stringResource(sessionLabelOf(state)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * This device, then the others — or the reason neither can be listed.
 *
 * A list that failed to load offers a retry and is never drawn as "no other
 * device" (docs/design/0.2.0/settings.md). While it loads, one line says so:
 * an account with three devices must not read as one with none for a frame.
 */
@Composable
private fun Devices(
    state: SettingsUiState,
    onRevoke: (Device) -> Unit,
    onRetry: () -> Unit,
) {
    when (val devices = state.devices) {
        DevicesState.Loading -> SettingsRow(
            label = stringResource(R.string.feature_settings_devices_loading),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DevicesState.Unavailable -> SettingsRow(
            label = stringResource(R.string.feature_settings_devices_unavailable),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.feature_settings_devices_retry))
            }
        }

        is DevicesState.Loaded -> {
            state.currentDevice?.let { current ->
                SettingsRow(
                    label = deviceTitle(current),
                    note = stringResource(R.string.feature_settings_this_device),
                )
            }
            state.otherDevices.forEach { device ->
                RowDivider()
                SettingsRow(
                    label = deviceTitle(device),
                    note = lastActivity(device),
                ) {
                    // Quiet and in the error colour, like a deletion: the
                    // prominent thing on this row is its name, not its removal.
                    TextButton(onClick = { onRevoke(device) }) {
                        Text(
                            text = stringResource(R.string.feature_settings_revoke),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** The name the user gave the device, else its model, else its platform. */
@Composable
private fun deviceTitle(device: Device): String =
    device.givenTitle() ?: stringResource(device.platform.labelRes())

/**
 * "Last activity 2 hours ago", or that it is unavailable. A last request seen,
 * never a presence: the contract says so in as many words.
 */
@Composable
private fun lastActivity(device: Device): String {
    val seen = device.lastSeenAt ?: return stringResource(R.string.feature_settings_last_seen_unknown)
    return stringResource(
        R.string.feature_settings_last_seen,
        DateUtils.getRelativeTimeSpanString(
            seen.toInstant().toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString(),
    )
}

/** A titled group of rows on one raised card, as the mock-up stacks them. */
@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LumoSpacing.xs),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LumoShapes.large)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}

/**
 * One row: a label on the left, and on the right a value, a note, a chevron, a
 * control — whichever of those the caller gives it.
 *
 * @param note a second line under the label, in the muted colour: a device's
 *   last activity, the sentence under the language.
 */
@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    note: String? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // ≥ 48 dp: the touch target the charter fixes for the phone.
            .heightIn(min = 56.dp)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = valueColor,
            )
        }

        trailing()

        if (chevron) Chevron()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = LumoSpacing.md),
    )
}

/** A glyph, because the product has no icon set — the same choice as the nav bar. */
@Composable
private fun Chevron() {
    Text(
        text = stringResource(R.string.feature_settings_chevron),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The accent as ink: the Spectre cyan on the dark theme, the primary ink on the
 * phone's light scheme, where the cyan falls under 3:1 (see `LumoMobileTheme`).
 */
@Composable
private fun accentInk(): Color =
    if (isSystemInDarkTheme()) LumoColors.Accent else MaterialTheme.colorScheme.primary

/**
 * Hands a page to whatever browser the phone has.
 *
 * A phone with no browser is a phone that cannot pair a television this way,
 * and the tap does nothing rather than crash: the page's address is on the row
 * for the user to type elsewhere.
 */
private fun openInBrowser(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (noBrowser: ActivityNotFoundException) {
        // See above.
    }
}
