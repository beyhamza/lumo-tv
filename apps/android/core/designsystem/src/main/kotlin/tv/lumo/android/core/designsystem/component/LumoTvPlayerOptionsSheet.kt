package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes

/**
 * The player's options on a television: audio, subtitles, quality (S13-01).
 *
 * [LumoTvAudioTrackSheet] grown into three blocks, with the same rules:
 * centred panel, `BACK` closes it, dimmed rows skipped by the remote.
 *
 * **Opening focus** on the effective choice of the first block that has one
 * (PS rules: "focus sur le choix effectif"), else the first row a remote can act
 * on. "Off" and "Automatic" are always actionable, so there always is one.
 * **Focus is requested once**, when the panel opens: the rows are redrawn when
 * the player answers a choice, and an answer arriving must not move the focus
 * away from where the viewer has put it.
 *
 * It stays open on a choice, like the phone's: see [LumoPlayerOptionsSheet].
 */
@Composable
fun LumoTvPlayerOptionsSheet(
    title: String,
    sections: List<LumoOptionSection>,
    onSelect: (sectionKey: String, choiceId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    banner: String? = null,
) {
    val opening = remember { FocusRequester() }
    val focusKey = remember {
        sections.firstNotNullOfOrNull { section ->
            section.choices.firstOrNull { it.selected && it.enabled }?.let { "${section.key}:${it.id}" }
        } ?: sections.firstNotNullOfOrNull { section ->
            section.choices.firstOrNull { it.enabled }?.let { "${section.key}:${it.id}" }
        }
    }

    LaunchedEffect(Unit) {
        if (focusKey != null) runCatching { opening.requestFocus() }
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
        LazyColumn(
            modifier = Modifier
                .width(SHEET_WIDTH)
                .heightIn(max = SHEET_MAX_HEIGHT)
                .clip(LumoTvShapes.large)
                .background(LumoColors.Surface),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        ) {
            item(key = "title") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDark,
                )
            }
            banner?.let {
                item(key = "banner") {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = LumoColors.OnDarkMuted)
                }
            }

            sections.forEach { section ->
                item(key = "header:${section.key}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = LumoColors.OnDarkMuted,
                        modifier = Modifier
                            .padding(top = LumoSpacing.sm)
                            .semantics { heading() },
                    )
                }
                if (section.status != null) {
                    // Under the block's title, never taking the focus: the remote
                    // stays on the row that was pressed (asynchronous states do not
                    // steal focus). Retry is reachable with UP from the rows.
                    item(key = "status:${section.key}") {
                        Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
                            Text(
                                text = section.status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (section.statusIsError) LumoColors.Error else LumoColors.OnDarkMuted,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            if (section.actionLabel != null && section.onAction != null) {
                                LumoTvButton(text = section.actionLabel, onClick = section.onAction)
                            }
                        }
                    }
                }
                items(section.choices, key = { "${section.key}:${it.id}" }) { choice ->
                    val key = "${section.key}:${choice.id}"
                    TvTrackRow(
                        track = choice,
                        onSelect = { onSelect(section.key, choice.id) },
                        modifier = if (key == focusKey) Modifier.focusRequester(opening) else Modifier,
                    )
                }
                section.note?.let { note ->
                    item(key = "note:${section.key}") {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LumoColors.OnDarkMuted,
                        )
                    }
                }
            }
        }
    }
}

private val SHEET_WIDTH = 640.dp

/** Inside the 5 % overscan of a 540 dp-high television, with room to spare. */
private val SHEET_MAX_HEIGHT = 460.dp
