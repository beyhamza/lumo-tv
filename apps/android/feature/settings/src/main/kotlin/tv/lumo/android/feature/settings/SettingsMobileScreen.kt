package tv.lumo.android.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.designsystem.component.LumoConfirmDialog
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.network.generated.model.Device

/**
 * The settings of the phone, in the five sections US-025 validated — minus the
 * one that has nothing to show yet (S8-06).
 *
 * <h2>Account and devices · My sources · Application · Help and information</h2>
 *
 * In that order, and no Playback: its settings arrive with sprint 13, and the
 * rule of the sprint is that what is not delivered is not drawn. That is also
 * why the two `[mock]` rows this screen used to carry — the language *choice*,
 * playback on mobile data — are gone rather than disabled. What is left is read
 * from the server, the package or the build, through [SettingsViewModel].
 *
 * <h2>Devices, as the server tells them</h2>
 *
 * The row marked "This device" is the one `is_current` names; the others print a
 * last activity when the server has one and say "unavailable" when it does not
 * — never "online", which nothing announces. Each of the others can be
 * disconnected, behind a confirmation that names it and whose default is
 * Cancel; a refusal keeps the device and the question, with the reason under it.
 *
 * <h2>Why the address is on the card</h2>
 *
 * "Reopened still connected" is not observable if the only thing on display is
 * the words *signed in*; the address is the cheapest thing that could not have
 * been guessed. The session line and the address come from the view model the
 * television reads too — a second reading of "is there a session" written here
 * would be exactly the duplicated logic AGENTS.md §2 calls a defect.
 */
@Composable
fun SettingsMobileScreen(
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = currentLanguage()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.feature_settings_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Section(title = stringResource(R.string.feature_settings_section_account)) {
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

        Section(title = stringResource(R.string.feature_settings_section_sources)) {
            SettingsRow(
                label = stringResource(R.string.feature_settings_my_sources),
                value = state.sourceCount?.let {
                    pluralStringResource(R.plurals.feature_settings_sources_active, it, it)
                },
                chevron = true,
                // The row had its chevron and no destination until US-017 took
                // "Source" out of the bar: this is now the way to that screen,
                // and where it leads is the application's wire, not this module's.
                onClick = onOpenSources,
            )
        }

        Section(title = stringResource(R.string.feature_settings_section_application)) {
            // Read-only, and no chevron: there is nothing to open. The line
            // under it is the one sentence US-025 allows about what comes later.
            SettingsRow(
                label = stringResource(R.string.feature_settings_language),
                value = language.displayName,
                note = stringResource(R.string.feature_settings_language_note),
            )
        }

        Section(title = stringResource(R.string.feature_settings_section_help)) {
            // Derived from the same variable as the pairing link, so a local
            // stack cannot pair against one host and open guides on another.
            // Absent rather than dead when the build carries no origin.
            SettingsLinks.guidesUrlOf(state.activationUrl, language.code)?.let { guides ->
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

        if (!state.signedIn) {
            Text(
                text = stringResource(sessionLabelOf(state)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** The interface language: its ISO code, for a URL, and its name in itself, for a row. */
private data class InterfaceLanguage(val code: String, val displayName: String)

/**
 * Read from the configuration and nowhere else: `Locale.getDefault()` is not
 * observable, and a composable that reads it does not recompose when the
 * language changes (lint `NonObservableLocale`). An empty locale list is
 * impossible on a real configuration; the empty strings are only the honest
 * fallback for one that says nothing.
 */
@Composable
private fun currentLanguage(): InterfaceLanguage {
    val locales = LocalConfiguration.current.locales
    if (locales.isEmpty) return InterfaceLanguage(code = "", displayName = "")
    val locale = locales[0]
    return InterfaceLanguage(
        code = locale.language,
        displayName = locale.getDisplayLanguage(locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
    )
}

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
