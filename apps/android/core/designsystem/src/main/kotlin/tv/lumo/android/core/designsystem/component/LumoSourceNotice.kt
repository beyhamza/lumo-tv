package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes

/**
 * What a source is doing, drawn **over** the content and never instead of it.
 *
 * <h2>Why it is here</h2>
 *
 * It was private to the home screen, the only one that kept its content on
 * display while a source refreshed. Since lot C4 the channel, film and series
 * grids do too (US-024): four screens in four features that may not import each
 * other, drawing one thing. So the drawing moved down here, and the *meaning* —
 * which state, which sentence — stayed in `core:data` (`SourceNotice`), which
 * this module does not know. Hence plain strings: a design system that imported
 * the contract's enums to colour a card would be the dependency pointing the
 * wrong way.
 *
 * <h2>Compact on purpose</h2>
 *
 * It sits above a grid somebody came to browse. A title, the real step or the
 * reason, one quieter line, and at most one action — the screen where a source is
 * looked after.
 *
 * @param hint the quieter line: that playback waits for the end of a refresh,
 * or that the catalogue on screen may be out of date.
 * @param actionLabel null draws no button. The caller decides; on the phone a
 * failure always has one.
 */
@Composable
fun LumoSourceNotice(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LumoShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(LumoSpacing.md)
            // Polite: a step that moves is read out when the reader is idle, and
            // never interrupts the channel name somebody is listening to.
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The same notice on a television.
 *
 * **Without an action it is text and not a focus stop**: there is nothing to
 * press, and a stop with nothing behind it is a dead end on the way `UP`. With
 * one it has exactly one control, to the right of the text so that the notice
 * costs a row of height and not two (docs/design/tv-focus-map.md, Home).
 *
 * @param compact the form for a **grid's header line**. A television grid has
 * the height of two rows of cards after overscan, and a card-sized notice above
 * it would push the second row off the panel — a row nobody sees is a row nobody
 * focuses. Compact drops the card and the padding, and says title, then message
 * and hint on one line, in the label size the header already uses.
 */
@Composable
fun LumoTvSourceNotice(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    compact: Boolean = false,
) {
    Row(
        modifier = if (compact) {
            modifier
        } else {
            modifier
                .fillMaxWidth()
                .clip(LumoTvShapes.medium)
                .background(LumoColors.Surface)
                .padding(LumoSpacing.lg)
        },
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        ) {
            TvText(
                text = title,
                style = if (compact) {
                    TvMaterialTheme.typography.labelLarge
                } else {
                    TvMaterialTheme.typography.titleLarge
                },
                color = if (isError) LumoColors.Error else LumoColors.OnDark,
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            if (compact) {
                TvText(
                    text = listOfNotNull(message, hint).joinToString(separator = " "),
                    style = TvMaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                TvText(
                    text = message,
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = LumoColors.OnDark,
                )
                hint?.let {
                    TvText(
                        text = it,
                        style = TvMaterialTheme.typography.labelLarge,
                        color = LumoColors.OnDarkMuted,
                    )
                }
            }
        }

        if (actionLabel != null) {
            LumoTvButton(text = actionLabel, onClick = onAction)
        }
    }
}
