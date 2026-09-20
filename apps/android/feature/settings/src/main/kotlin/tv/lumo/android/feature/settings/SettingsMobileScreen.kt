package tv.lumo.android.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The account, and the way out of it (US-04) — laid out as the M6 mock-up
 * draws it (docs/design/canvas, mobile artboard 22).
 *
 * <h2>What is real and what is not</h2>
 *
 * The account card, the source count, the automatic-refresh switch, the device
 * count, the pairing link and the version are read from the server or the
 * package, through [SettingsViewModel]. Two rows — the language and playback on
 * mobile data — have nothing behind them yet: no preference store, and no
 * contract field. They are drawn so the layout is the mock-up's, and each one
 * says `[mock]` rather than looking like a control that forgot to work.
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

        AccountCard(state)

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
            RowDivider()
            SettingsRow(label = stringResource(R.string.feature_settings_auto_sync)) {
                LumoSwitch(
                    checked = state.autoSync == true,
                    // No source to refresh, or a write in flight: the switch
                    // shows the last known value and takes no tap.
                    enabled = state.autoSync != null && !state.autoSyncPending,
                    onCheckedChange = viewModel::setAutoSync,
                )
            }
        }

        Section(title = stringResource(R.string.feature_settings_section_devices)) {
            SettingsRow(
                label = stringResource(R.string.feature_settings_devices_connected),
                value = state.deviceCount?.toString(),
                chevron = true,
            )
            RowDivider()
            SettingsRow(
                label = stringResource(R.string.feature_settings_pair_tv),
                value = state.activationLabel,
                valueColor = accentInk(),
                chevron = true,
                onClick = { openInBrowser(context, state.activationUrl) },
            )
        }

        Section(title = stringResource(R.string.feature_settings_section_application)) {
            SettingsRow(
                label = stringResource(R.string.feature_settings_language),
                value = currentLanguageName(),
                note = stringResource(R.string.feature_settings_mock_missing),
                chevron = true,
            )
            RowDivider()
            SettingsRow(
                label = stringResource(R.string.feature_settings_mobile_data),
                note = stringResource(R.string.feature_settings_mock_missing),
            ) {
                LumoSwitch(checked = false, enabled = false, onCheckedChange = {})
            }
        }

        if (state.signedIn) {
            Section(title = null) {
                SettingsRow(
                    label = stringResource(R.string.feature_settings_sign_out),
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = if (state.signingOut) null else viewModel::signOut,
                ) {
                    if (state.signingOut) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(LumoSpacing.md),
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(sessionLabelOf(state)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(LumoSpacing.md))

        Text(
            text = stringResource(
                R.string.feature_settings_footer,
                state.appVersion ?: stringResource(R.string.feature_settings_unknown_value),
            ),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The account: a disc in the brand gradient carrying the address's initial.
 *
 * The gradient is the one place the charter allows it besides focus and
 * progress — the mark — and the disc is the mark's own shape, drawn the way
 * `LumoWordmark` draws it.
 */
@Composable
private fun AccountCard(state: SettingsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.large)
            .background(MaterialTheme.colorScheme.surface)
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
                text = stringResource(R.string.feature_settings_account_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
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

        Chevron()
    }
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
 * @param note a second line under the value, in the muted colour: what the
 *   `[mock]` rows use to say they are not wired.
 */
@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    note: String? = null,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * The charter's switch: an accent track with a dark thumb when on, a raised
 * surface with a muted thumb when off. Cyan here is a state, not a fill — the
 * mock-up draws it this way and it is the one control where "active" is what the
 * colour means.
 */
@Composable
private fun LumoSwitch(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = accentInk(),
            checkedThumbColor = if (isSystemInDarkTheme()) LumoColors.OnAccent else MaterialTheme.colorScheme.onPrimary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            disabledCheckedTrackColor = accentInk().copy(alpha = 0.5f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/**
 * The accent as ink: the Spectre cyan on the dark theme, the primary ink on the
 * phone's light scheme, where the cyan falls under 3:1 (see `LumoMobileTheme`).
 */
@Composable
private fun accentInk(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) LumoColors.Accent else MaterialTheme.colorScheme.primary

/**
 * The interface language, named in itself: "Français", "English".
 *
 * Read from the configuration and nowhere else: `Locale.getDefault()` is not
 * observable, and a composable that reads it does not recompose when the
 * language changes (lint `NonObservableLocale`). An empty locale list is
 * impossible on a real configuration; the empty string is only the honest
 * fallback for one that says nothing.
 */
@Composable
private fun currentLanguageName(): String {
    val locales = LocalConfiguration.current.locales
    if (locales.isEmpty) return ""
    val locale = locales[0]
    return locale.getDisplayLanguage(locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/**
 * Hands the activation page to whatever browser the phone has.
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
