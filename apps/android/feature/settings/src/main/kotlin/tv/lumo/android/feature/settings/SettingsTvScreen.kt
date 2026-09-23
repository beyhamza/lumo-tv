package tv.lumo.android.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.component.LumoTvConfirmDialog
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.network.generated.model.Device
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * `TV5 — Réglages`: a sub-menu down the left — Sources, Account and devices,
 * Application, About — and one panel on the right for whichever entry is open
 * (US-025, S8-06).
 *
 * <h2>Four entries, and nothing that is not delivered</h2>
 *
 * Playback is absent until its settings exist (sprint 13), and Language is no
 * longer an entry of its own: it is one read-only line of Application, since
 * that is all that is true about it. The `[mock]` badges this screen used to
 * draw are gone with the rows that carried them.
 *
 * <h2>Devices are read here and decided elsewhere</h2>
 *
 * The account panel names this television, lists the other installations with
 * their last activity, and says where one is disconnected: on the phone or on
 * lumo.tv, with the same account (docs/design/0.2.0/settings.md, S13-E10). A
 * remote is the wrong instrument for a decision that names a device, and a
 * list that cannot be acted on is text — no stop on any of its rows.
 *
 * <h2>Focus</h2>
 *
 * The map of this screen is in `docs/design/tv-focus-map.md`. Arrival lands on
 * the first menu entry. `DOWN` walks the menu, and opening an entry is a matter
 * of focusing it: a menu that needed `OK` to reveal its panel would cost a press
 * for every look. `RIGHT` enters the panel where it has something to press —
 * "My sources", "Try again", "Sign out" — and `LEFT` returns to the menu, then
 * to the rail. `BACK` from inside a panel returns to its menu entry; from the
 * menu it is the shell's, and goes to Home. Signing out asks first, in a dialog
 * that holds the focus with Cancel as its default.
 */
@Composable
fun SettingsTvScreen(
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(SettingsSection.Sources) }
    val menu = remember { SettingsSection.entries.associateWith { FocusRequester() } }

    LaunchedEffect(Unit) { runCatching { menu.getValue(SettingsSection.Sources).requestFocus() } }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscan(),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xxl),
    ) {
        Column(
            // A third of the width for the menu, as the canvas: a fixed width is
            // right on one panel and wrong on every other.
            modifier = Modifier
                .weight(MENU_SHARE)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.feature_settings_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
                modifier = Modifier.padding(bottom = LumoSpacing.md),
            )
            SettingsSection.entries.forEach { entry ->
                MenuItem(
                    label = stringResource(entry.titleRes),
                    selected = section == entry,
                    onFocused = { section = entry },
                    modifier = Modifier.focusRequester(menu.getValue(entry)),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.feature_settings_tv_about_version, state.appVersion ?: "—"),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = LumoColors.OnDarkMuted,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f - MENU_SHARE)
                .fillMaxHeight()
                // `BACK` inside a panel goes back to the menu entry that opened
                // it, not to Home: the viewer went right, and the key that means
                // "out" should undo that step before undoing the screen. Seen
                // here first, before Compose treats it as a focus search that
                // finds nothing and lets it fall through to the shell.
                .onPreviewKeyEvent { event ->
                    val isBack = event.key == Key.Back || event.key == Key.Escape
                    if (!isBack) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) {
                        runCatching { menu.getValue(section).requestFocus() }
                    }
                    true
                }
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            when (section) {
                SettingsSection.Sources -> SourcesPanel(state, onOpenSources)
                SettingsSection.Account -> AccountPanel(
                    state = state,
                    onRetryDevices = viewModel::reloadDevices,
                    onSignOut = viewModel::askSignOut,
                )
                SettingsSection.Application -> ApplicationPanel()
                SettingsSection.About -> AboutPanel(state)
            }
        }
    }

    // A dialog window: the focus cannot leak to the panel underneath, Cancel
    // takes it on arrival, and `BACK` cancels. The revoke question never opens
    // here — nothing on this surface asks it.
    if (state.confirmation == SettingsConfirmation.SignOut) {
        LumoTvConfirmDialog(
            title = stringResource(R.string.feature_settings_tv_sign_out_title),
            message = stringResource(R.string.feature_settings_tv_sign_out_body),
            confirmLabel = stringResource(R.string.feature_settings_tv_account_sign_out),
            cancelLabel = stringResource(R.string.feature_settings_cancel),
            onConfirm = viewModel::confirmSignOut,
            onCancel = viewModel::dismissConfirmation,
            busy = state.signingOut,
        )
    }
}

private enum class SettingsSection(val titleRes: Int) {
    Sources(R.string.feature_settings_tv_menu_sources),
    Account(R.string.feature_settings_tv_menu_account),
    Application(R.string.feature_settings_tv_menu_application),
    About(R.string.feature_settings_tv_menu_about),
}

@Composable
private fun MenuItem(
    label: String,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = when {
            selected -> LumoColors.Accent
            focused -> LumoColors.OnDark
            else -> LumoColors.OnDarkMuted
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused || selected) LumoColors.SurfaceRaised else LumoColors.Surface)
            .focusable()
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm + LumoSpacing.xs),
    )
}

// ---- Sources ---------------------------------------------------------------

@Composable
private fun SourcesPanel(
    state: SettingsUiState,
    onOpenSources: () -> Unit,
) {
    Text(
        text = stringResource(R.string.feature_settings_tv_sources_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = LumoColors.OnDarkMuted,
    )

    when {
        state.sourceCount == null -> Unit
        state.sources.isEmpty() -> PanelRow(title = stringResource(R.string.feature_settings_tv_sources_none))
        else -> state.sources.forEach { source -> SourceRow(source) }
    }

    // "Source" left the rail with US-017 — six entries was already one `DOWN` too
    // many — so the screen needs another way in. This is one of the two; the other
    // is "My sources" in the source switcher at the foot of the rail.
    PanelRow(
        title = stringResource(R.string.feature_settings_tv_my_sources),
        trailing = {
            Text(
                text = stringResource(R.string.feature_settings_tv_press_ok),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )
        },
        onClick = onOpenSources,
    )

    // "Refresh the lists now" and the automatic-refresh switch used to follow.
    // Refreshing is per source, in "My sources", where a refusal can be said
    // beside the source it concerns; automatic refresh is set from the phone or
    // lumo.tv, which that screen says (US-024).
}

/**
 * One source as the canvas draws it: name, `M3U · 1 248 chaînes · vérifiée il
 * y a 2 h`, and a state pill. A source in error says so in `danger`, and where
 * to fix it — never here, a television types nothing.
 */
@Composable
private fun SourceRow(source: Source) {
    val error = source.status == SourceStatus.ERROR
    val (label, color) = when (source.status) {
        SourceStatus.READY -> stringResource(R.string.feature_settings_tv_status_active) to LumoColors.Accent
        SourceStatus.ERROR -> stringResource(R.string.feature_settings_tv_status_error) to LumoColors.Error
        SourceStatus.SYNCING -> stringResource(R.string.feature_settings_tv_status_syncing) to LumoColors.OnDarkMuted
        else -> stringResource(R.string.feature_settings_tv_status_pending) to LumoColors.OnDarkMuted
    }
    val kind = if (source.kind == SourceKind.XTREAM) "Xtream" else "M3U"
    val count = source.channelCount
    val checked = source.lastSyncedAt

    PanelRow(
        title = source.label,
        subtitle = when {
            error -> stringResource(R.string.feature_settings_tv_source_error)
            count == null || checked == null -> stringResource(R.string.feature_settings_tv_source_unsynced, kind)
            else -> stringResource(
                R.string.feature_settings_tv_source_summary,
                kind,
                count,
                relativeTime(checked.toInstant().toEpochMilli()),
            )
        },
        trailing = { StatusPill(label, color) },
    )
}

// ---- Account and devices ---------------------------------------------------

/**
 * The account, this television, the other devices — read-only — and the way out.
 *
 * Two stops at most: "Try again" when the list could not be read, and "Sign
 * out". Every device row is text, on purpose: a stop with nothing behind it is
 * a dead end on the way `DOWN`, and the guidance line says where the decision
 * about a device is made.
 */
@Composable
private fun AccountPanel(
    state: SettingsUiState,
    onRetryDevices: () -> Unit,
    onSignOut: () -> Unit,
) {
    PanelRow(
        title = state.email ?: stringResource(R.string.feature_settings_session_none),
        subtitle = state.email?.let { stringResource(R.string.feature_settings_tv_account_signed_in, it) },
    )

    when (val devices = state.devices) {
        DevicesState.Loading -> PanelRow(title = stringResource(R.string.feature_settings_devices_loading))

        DevicesState.Unavailable -> PanelRow(
            title = stringResource(R.string.feature_settings_devices_unavailable),
            trailing = {
                LumoTvButton(
                    text = stringResource(R.string.feature_settings_devices_retry),
                    onClick = onRetryDevices,
                )
            },
        )

        is DevicesState.Loaded -> {
            state.currentDevice?.let { current ->
                PanelRow(
                    title = deviceTitle(current),
                    subtitle = stringResource(R.string.feature_settings_tv_this_device),
                )
            }
            Text(
                text = stringResource(R.string.feature_settings_tv_other_devices),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
                modifier = Modifier.padding(top = LumoSpacing.sm),
            )
            if (state.otherDevices.isEmpty()) {
                PanelRow(title = stringResource(R.string.feature_settings_tv_no_other_device))
            }
            state.otherDevices.forEach { device ->
                PanelRow(title = deviceTitle(device), subtitle = lastActivity(device))
            }
            Text(
                text = stringResource(R.string.feature_settings_tv_devices_guidance),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
            )
        }
    }

    if (state.signedIn) {
        LumoTvButton(
            text = stringResource(R.string.feature_settings_tv_account_sign_out),
            onClick = { if (!state.signingOut) onSignOut() },
            primary = true,
            enabled = !state.signingOut,
        )
    }
}

/** The name the user gave the device, else its model, else its platform. */
@Composable
private fun deviceTitle(device: Device): String =
    device.givenTitle() ?: stringResource(device.platform.labelRes())

/** "Last activity 2 hours ago", or that it is unavailable. A last request seen, never a presence. */
@Composable
private fun lastActivity(device: Device): String {
    val seen = device.lastSeenAt ?: return stringResource(R.string.feature_settings_last_seen_unknown)
    return stringResource(
        R.string.feature_settings_last_seen,
        relativeTime(seen.toInstant().toEpochMilli()),
    )
}

private fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

// ---- Application / About ---------------------------------------------------

/**
 * The interface language, read-only, and the one sentence about what comes
 * later. No stop: there is nothing to press, and `RIGHT` from the menu stays on
 * the menu — which is what a panel of facts should do.
 */
@Composable
private fun ApplicationPanel() {
    PanelRow(
        title = stringResource(R.string.feature_settings_language),
        subtitle = stringResource(R.string.feature_settings_tv_language_value),
        trailing = {
            Text(
                text = currentLanguageName(),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
            )
        },
    )
    Text(
        text = stringResource(R.string.feature_settings_language_note),
        style = MaterialTheme.typography.bodyLarge,
        color = LumoColors.OnDarkMuted,
    )
}

/**
 * The version, and where the guides are. A television opens no browser, so the
 * address is printed for somebody to type on a phone — derived from the same
 * variable as the pairing page, so it names the same site. Privacy and terms
 * would follow, and do not: the website has no such pages yet.
 */
@Composable
private fun AboutPanel(state: SettingsUiState) {
    PanelRow(
        title = stringResource(R.string.feature_settings_tv_about_version, state.appVersion ?: "—"),
        subtitle = stringResource(R.string.feature_settings_tv_about_site),
    )
    SettingsLinks.guidesUrlOf(state.activationUrl, currentLanguageCode())?.let { guides ->
        PanelRow(
            title = stringResource(R.string.feature_settings_guides),
            subtitle = stringResource(R.string.feature_settings_tv_about_guides, SettingsLinks.label(guides)),
        )
    }
}

/**
 * The interface language, named in itself: "Français", "English". Read from the
 * configuration and nowhere else, for the reason the phone's screen gives.
 */
@Composable
private fun currentLanguageName(): String {
    val locales = LocalConfiguration.current.locales
    if (locales.isEmpty) return ""
    val locale = locales[0]
    return locale.getDisplayLanguage(locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

@Composable
private fun currentLanguageCode(): String {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty) "" else locales[0].language
}

// ---- Building blocks -------------------------------------------------------

/**
 * One row of a panel, as the canvas draws them: a title, an optional line
 * under it, something at the end. Focusable only when it does something —
 * a row that takes the focus and swallows `OK` is a row that lies.
 */
@Composable
private fun PanelRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = LumoColors.OnDark)
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = LumoColors.OnDarkMuted)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun StatusPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), LumoTvShapes.pill)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(LumoTvShapes.pill).background(color))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** The sub-menu's share of the content width, as on the canvas. */
private const val MENU_SHARE = 0.34f
