package tv.lumo.android.feature.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.component.LumoMockMissingData
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus
import android.text.format.DateUtils

/**
 * `TV5 — Réglages`: a sub-menu down the left — Sources, Compte, Lecture,
 * Langue, À propos — and one panel on the right for whichever entry is open.
 *
 * <h2>What is real and what says `[mock]`</h2>
 *
 * Sources, the account, the devices count, automatic refresh and the version
 * come from the server or the build. Playback preferences (quality, subtitles,
 * audio language) and the language choice are drawn as the canvas draws them
 * and labelled `[mock] données manquantes`: the product has no preference
 * store yet, and a switch that flips nothing is worse than a switch that says
 * so.
 *
 * <h2>Focus</h2>
 *
 * Arrival lands on the first menu entry. `DOWN` walks the menu, `RIGHT` enters
 * the panel where it has something to press, `LEFT` returns to the menu and
 * then to the rail. Opening an entry is a matter of focusing it: a menu that
 * needed `OK` to reveal its panel would cost a press for every look.
 */
@Composable
fun SettingsTvScreen(
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(SettingsSection.Sources) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

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
            SettingsSection.entries.forEachIndexed { index, entry ->
                MenuItem(
                    label = stringResource(entry.titleRes),
                    selected = section == entry,
                    onFocused = { section = entry },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
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
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            when (section) {
                SettingsSection.Sources -> SourcesPanel(state, onOpenSources)
                SettingsSection.Account -> AccountPanel(state, viewModel)
                SettingsSection.Playback -> PlaybackPanel()
                SettingsSection.Language -> LanguagePanel()
                SettingsSection.About -> AboutPanel(state)
            }
        }
    }
}

private enum class SettingsSection(val titleRes: Int) {
    Sources(R.string.feature_settings_tv_menu_sources),
    Account(R.string.feature_settings_tv_menu_account),
    Playback(R.string.feature_settings_tv_menu_playback),
    Language(R.string.feature_settings_tv_menu_language),
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
                DateUtils.getRelativeTimeSpanString(
                    checked.toInstant().toEpochMilli(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
            )
        },
        trailing = { StatusPill(label, color) },
    )
}

// ---- Compte ----------------------------------------------------------------

@Composable
private fun AccountPanel(state: SettingsUiState, viewModel: SettingsViewModel) {
    PanelRow(
        title = state.email ?: stringResource(R.string.feature_settings_session_none),
        subtitle = state.email?.let { stringResource(R.string.feature_settings_tv_account_signed_in, it) },
    )
    PanelRow(
        title = stringResource(R.string.feature_settings_tv_account_devices),
        trailing = {
            if (state.deviceCount == null) {
                LumoMockMissingData(scale = TV_TYPE_SCALE)
            } else {
                Text(
                    text = state.deviceCount.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    color = LumoColors.OnDark,
                )
            }
        },
    )
    if (state.signedIn) {
        LumoTvButton(
            text = stringResource(R.string.feature_settings_tv_account_sign_out),
            onClick = { if (!state.signingOut) viewModel.signOut() },
            primary = true,
            enabled = !state.signingOut,
        )
    }
}

// ---- Lecture / Langue / À propos ------------------------------------------

@Composable
private fun PlaybackPanel() {
    PanelRow(title = stringResource(R.string.feature_settings_tv_playback_quality), trailing = { LumoMockMissingData(scale = TV_TYPE_SCALE) })
    PanelRow(title = stringResource(R.string.feature_settings_tv_playback_subtitles), trailing = { LumoMockMissingData(scale = TV_TYPE_SCALE) })
    PanelRow(title = stringResource(R.string.feature_settings_tv_playback_audio), trailing = { LumoMockMissingData(scale = TV_TYPE_SCALE) })
}

@Composable
private fun LanguagePanel() {
    PanelRow(
        title = stringResource(R.string.feature_settings_tv_menu_language),
        subtitle = stringResource(R.string.feature_settings_tv_language_value),
        trailing = { LumoMockMissingData(scale = TV_TYPE_SCALE) },
    )
}

@Composable
private fun AboutPanel(state: SettingsUiState) {
    PanelRow(
        title = stringResource(R.string.feature_settings_tv_about_version, state.appVersion ?: "—"),
        subtitle = stringResource(R.string.feature_settings_tv_about_site),
    )
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

/** `platforms.tv.typeScale` — what the mock badge grows by on a television. */
private const val TV_TYPE_SCALE = 1.75f

