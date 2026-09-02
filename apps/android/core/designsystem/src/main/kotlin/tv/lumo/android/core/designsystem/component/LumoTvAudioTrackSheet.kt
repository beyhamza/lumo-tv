package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus

/**
 * Choosing the language on a television.
 *
 * <h2>Why it is not the phone's sheet with bigger text</h2>
 *
 * The same reason [LumoTvFavoriteGroupSheet] is not: a modal bottom sheet is a
 * thumb gesture. A remote has focus, so the panel is centred, the first row takes
 * focus when it opens, and `BACK` closes it — none of which a sheet designed for
 * a swipe provides.
 *
 * <h2>Opening focus, and where it goes</h2>
 *
 * On the **selected** track rather than the first, which on a television is the
 * difference between "here is what you are listening to, choose another" and a
 * list somebody has to read from the top to find their place in. It is also the
 * one row where pressing centre immediately does nothing surprising.
 *
 * <h2>Unplayable tracks stay on screen</h2>
 *
 * Dimmed and unfocusable — a remote skips straight past them, so nobody presses
 * centre on a dead row, and the track is still visible. Most televisions decode
 * Dolby Digital and most telephones do not, so this is the surface where the
 * list is usually complete; the one where it is not needs to say so rather than
 * quietly shorten.
 */
@Composable
fun LumoTvAudioTrackSheet(
    title: String,
    tracks: List<LumoAudioTrackChoice>,
    onSelect: (trackId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opening = remember { FocusRequester() }
    // The selected one, or the first that can be chosen. Focusing a dimmed row
    // would be focusing something the remote cannot act on.
    val focusId = remember(tracks) {
        (tracks.firstOrNull { it.selected && it.enabled }
            ?: tracks.firstOrNull { it.enabled })?.id
    }

    LaunchedEffect(focusId) {
        // Nothing composes on the frame this runs if the list is empty, and
        // failing to focus is recoverable — the remote still works — while
        // throwing would take the screen down under the viewer.
        if (focusId != null) runCatching { opening.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Scrim)
            .onKeyEvent { event ->
                val isBack = event.key == Key.Back || event.key == Key.Escape
                if (isBack && event.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(SHEET_WIDTH)
                .clip(LumoShapes.large)
                .background(LumoColors.Surface)
                .padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onSelect = {
                            onSelect(track.id)
                            onDismiss()
                        },
                        modifier = if (track.id == focusId) {
                            Modifier.focusRequester(opening)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: LumoAudioTrackChoice,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val label = when {
        focused -> LumoColors.OnAccent
        track.enabled -> LumoColors.OnDark
        else -> LumoColors.OnDarkMuted
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoShapes.small)
            .clip(LumoShapes.small)
            .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
            // `enabled = false` also removes the row from the focus order, which
            // is what makes a remote skip it rather than stop on a row whose
            // centre key does nothing.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = track.enabled,
                onClick = onSelect,
            )
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A glyph rather than a Material control, as elsewhere on this surface:
        // the product has no icon set, and the phone's radio button draws at a
        // size nobody reads at three metres.
        Text(
            text = if (track.selected) "●" else "○",
            style = MaterialTheme.typography.titleLarge,
            color = label,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.label,
                style = MaterialTheme.typography.titleMedium,
                color = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (focused) LumoColors.OnAccent else LumoColors.OnDarkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Wide enough for a language and a codec on one line at three metres, narrow
 * enough that the picture stays visible around it — which is what says the sheet
 * is a layer over something still playing, not a new screen.
 */
private val SHEET_WIDTH = 560.dp
