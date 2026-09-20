package tv.lumo.android.feature.source

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.designsystem.component.LumoMockNotImplemented
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The television's view of the account's source.
 *
 * A television never types a source (docs/architecture.md §5): this screen
 * reads. With a source it shows the card the canvas draws in `TV5 — Réglages`:
 * name, kind, how many channels, when it was last checked, its state. Without
 * one it is `TV6 — aucune source`: the sentence that says where to add it, and
 * the button the canvas puts under it.
 *
 * That button, « Afficher le code d'association », is the one control here and
 * it opens nothing yet: a television that is signed in has no pairing code to
 * show, and what the canvas means by it is a question for the design. It is on
 * screen, it takes the focus (the screen must have one target, AGENTS.md §6),
 * and pressing it says `[mock]` rather than pretending.
 */
@Composable
fun SourceTvScreen(
    modifier: Modifier = Modifier,
    viewModel: SourceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscan(),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            state.step is AddSourceStep.Loading -> Text(
                text = stringResource(R.string.feature_source_submitting),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDarkMuted,
            )

            state.source != null -> WithSource(state.source!!)

            else -> NoSource()
        }
    }
}

@Composable
private fun WithSource(source: Source) {
    Column(
        modifier = Modifier.widthIn(max = 1_100.dp),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.feature_source_title),
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
        )
        Text(
            text = stringResource(R.string.feature_source_tv_managed_elsewhere),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
        SourceCard(source)
    }
}

/**
 * One source, as a card that can take the focus — so that the screen has a
 * target and `OK` does nothing, which is the truthful outcome on a set that
 * cannot edit it.
 */
@Composable
private fun SourceCard(source: Source, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val error = source.status == SourceStatus.ERROR

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .then(
                if (error) Modifier.border(2.dp, LumoColors.Error.copy(alpha = 0.5f), LumoTvShapes.medium) else Modifier,
            )
            .focusable()
            .padding(LumoSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindMark(source.kind)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        ) {
            Text(
                text = source.label,
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
            )
            Text(
                text = summaryOf(source),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = if (error) LumoColors.Error else LumoColors.OnDarkMuted,
            )
            if (error) {
                Text(
                    text = stringResource(R.string.feature_source_tv_error_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
            source.expiresAt?.let { expires ->
                Text(
                    text = stringResource(
                        R.string.feature_source_tv_expires,
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(expires),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
        }

        StatusPill(source.status)
    }
}

/** `m3u` / `xtr` in a gradient square — the canvas's mark for the kind. */
@Composable
private fun KindMark(kind: SourceKind) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(LumoTvShapes.small)
            .background(Brush.linearGradient(listOf(LumoColors.Accent.copy(alpha = 0.25f), LumoColors.AccentViolet.copy(alpha = 0.25f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (kind == SourceKind.XTREAM) "xtr" else "m3u",
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = LumoColors.Accent,
        )
    }
}

@Composable
private fun StatusPill(status: SourceStatus) {
    val (label, color) = when (status) {
        SourceStatus.READY -> stringResource(R.string.feature_source_tv_status_active) to LumoColors.Accent
        SourceStatus.ERROR -> stringResource(R.string.feature_source_tv_status_error) to LumoColors.Error
        SourceStatus.SYNCING -> stringResource(R.string.feature_source_tv_status_syncing) to LumoColors.OnDarkMuted
        else -> stringResource(R.string.feature_source_tv_status_pending) to LumoColors.OnDarkMuted
    }

    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), LumoTvShapes.pill)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(LumoTvShapes.pill)
                .background(color),
        )
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun summaryOf(source: Source): String {
    val kind = if (source.kind == SourceKind.XTREAM) "Xtream" else "M3U"
    val count = source.channelCount
    val checked = source.lastSyncedAt

    return if (count == null || checked == null) {
        stringResource(R.string.feature_source_tv_summary_unsynced, kind)
    } else {
        stringResource(
            R.string.feature_source_tv_summary,
            kind,
            count,
            DateUtils.getRelativeTimeSpanString(
                checked.toInstant().toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
        )
    }
}

@Composable
private fun NoSource() {
    var pressed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(820.dp)
                .clip(LumoTvShapes.large)
                .background(LumoColors.Surface)
                .padding(LumoSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(LumoTvShapes.pill)
                    .background(Brush.radialGradient(listOf(LumoColors.Accent, LumoColors.AccentViolet))),
            )
            Text(
                text = stringResource(DataR.string.core_data_source_tv_none_title),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(DataR.string.core_data_source_tv_none_body),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
                textAlign = TextAlign.Center,
            )
            LumoTvButton(
                text = stringResource(R.string.feature_source_tv_show_code),
                onClick = { pressed = true },
                primary = true,
            )
            if (pressed) LumoMockNotImplemented(scale = TV_TYPE_SCALE)
        }
    }
}

/** `platforms.tv.typeScale` — what the mock badge grows by on a television. */
private const val TV_TYPE_SCALE = 1.75f
