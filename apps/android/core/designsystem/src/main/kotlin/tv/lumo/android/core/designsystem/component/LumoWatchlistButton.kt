package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text as TvText
import tv.lumo.android.core.designsystem.R
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.theme.LumoTypeScale
import tv.lumo.android.core.designsystem.tv.lumoTvFocus

/**
 * What a film or series sheet's "My list" button shows (US-022, S11-E06).
 *
 * A UI model of its own rather than `core:data`'s state: this module draws and
 * does not know the data layer. The sheet maps one onto the other.
 */
data class LumoWatchlistButtonState(
    /** True in the list, false out of it, null while it is not known. */
    val inList: Boolean?,
    /** Whether a press does anything. */
    val enabled: Boolean,
    /** The sentence under the button, if any. */
    val note: LumoWatchlistNote? = null,
    /** Whether to offer "Retry" beside the note. */
    val retryable: Boolean = false,
)

/** The four things the button may have to explain. */
enum class LumoWatchlistNote {
    /** "Change not confirmed": sent, and nobody knows whether it was accepted. */
    Unconfirmed,

    /** Offline: the list can be read, not changed. */
    Offline,

    /** The server refused the change. */
    Rejected,

    /** The membership could not be read. */
    CheckFailed,
}

/**
 * "Add to my list" / "In my list" on a phone sheet. Pressing "In my list" removes
 * the item; playback progress is a different thing and is not touched.
 */
@Composable
fun LumoWatchlistButton(
    state: LumoWatchlistButtonState,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = watchlistLabel(state.inList)
    val inListDescription = stringResource(R.string.lumo_watchlist_in_state)
    val outDescription = stringResource(R.string.lumo_watchlist_out_state)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
        OutlinedButton(
            onClick = onToggle,
            enabled = state.enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    state.inList?.let { stateDescription = if (it) inListDescription else outDescription }
                },
        ) {
            Text(label)
        }

        state.note?.let { note ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            ) {
                Text(
                    text = noteText(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (note == LumoWatchlistNote.Offline) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (state.retryable) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.lumo_watchlist_retry)) }
                }
            }
        }
    }
}

/**
 * The same button on a television sheet.
 *
 * **Focusable even when it cannot act**: offline, or while the membership is being
 * checked, the remote can still land on it and the sentence under it says why
 * nothing happens. A control the D-pad skips is a control nobody can ask about.
 * "Retry" sits to its right, one `RIGHT` away, only when there is something to retry.
 */
@Composable
fun LumoTvWatchlistButton(
    state: LumoWatchlistButtonState,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val inListDescription = stringResource(R.string.lumo_watchlist_in_state)
    val outDescription = stringResource(R.string.lumo_watchlist_out_state)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .lumoTvFocus(focused)
                    .clip(LumoTvShapes.medium)
                    .background(
                        when {
                            focused && state.enabled -> LumoColors.Accent
                            focused -> LumoColors.SurfaceRaised
                            else -> LumoColors.Surface
                        },
                    )
                    // `clickable` alone, always enabled so the remote can land here;
                    // the press is ignored when the state says it does nothing.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { if (state.enabled) onToggle() }
                    .semantics {
                        state.inList?.let { stateDescription = if (it) inListDescription else outDescription }
                    }
                    .padding(horizontal = LumoSpacing.xl, vertical = LumoSpacing.md),
            ) {
                TvText(
                    text = watchlistLabel(state.inList),
                    style = LumoTypeScale.tv.label,
                    color = when {
                        !state.enabled -> LumoColors.OnDarkMuted
                        focused -> LumoColors.OnAccent
                        else -> LumoColors.OnDark
                    },
                )
            }

            if (state.retryable) {
                LumoTvButton(text = stringResource(R.string.lumo_watchlist_retry), onClick = onRetry)
            }
        }

        state.note?.let { note ->
            TvText(
                text = noteText(note),
                style = LumoTypeScale.tv.label,
                color = if (note == LumoWatchlistNote.Offline) LumoColors.OnDarkMuted else LumoColors.Error,
                modifier = Modifier
                    .widthIn(max = NOTE_WIDTH)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun watchlistLabel(inList: Boolean?): String = stringResource(
    when (inList) {
        true -> R.string.lumo_watchlist_in
        false -> R.string.lumo_watchlist_add
        null -> R.string.lumo_watchlist_checking
    },
)

@Composable
private fun noteText(note: LumoWatchlistNote): String = stringResource(
    when (note) {
        LumoWatchlistNote.Unconfirmed -> R.string.lumo_watchlist_unconfirmed
        LumoWatchlistNote.Offline -> R.string.lumo_watchlist_offline
        LumoWatchlistNote.Rejected -> R.string.lumo_watchlist_rejected
        LumoWatchlistNote.CheckFailed -> R.string.lumo_watchlist_check_failed
    },
)

private val NOTE_WIDTH = 640.dp
