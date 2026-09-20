package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * A catalogue screen with no catalogue on it, saying why and offering one way on.
 *
 * Two situations draw it, in the three catalogues alike (US-024): the **first
 * import** of a source — running, with its real step, or failed, with its reason
 * — and a section whose request **failed with nothing cached**, which must say so
 * rather than pass for an empty one. Both are a title, the fact of the moment,
 * a sentence, and a single action; six screens in three features that may not
 * import each other would otherwise each draw their own.
 *
 * @param detail the line that moves or names the cause: a synchronisation step,
 * an ingestion error. Announced politely when it changes.
 */
@Composable
fun LumoStateMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The same, for a television.
 *
 * The button is what makes this a screen and not a poster: a content area with
 * no focus target leaves the rail as the only place the remote can be, and `RIGHT`
 * from it as a key that does nothing (docs/design/tv-focus-map.md, rule 1). It
 * does not take the focus on arrival — the viewer came from the rail and is
 * still there; `RIGHT` finds it.
 */
@Composable
fun LumoTvStateMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(LumoSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        TvText(
            text = title,
            style = TvMaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
        )
        detail?.let {
            TvText(
                text = it,
                style = TvMaterialTheme.typography.titleLarge,
                color = if (isError) LumoColors.Error else LumoColors.Accent,
            )
        }
        TvText(
            text = body,
            style = TvMaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
        if (actionLabel != null) {
            LumoTvButton(text = actionLabel, onClick = onAction, primary = true)
        }
    }
}
