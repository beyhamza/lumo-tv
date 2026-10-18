package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * One block of the player's options menu: Audio, Subtitles or Quality (S13-01).
 *
 * @property key what the caller is told a choice belongs to.
 * @property note one sentence under the rows — "no other track available",
 *   "your subtitle language is unavailable" — or null.
 */
data class LumoOptionSection(
    val key: String,
    val title: String,
    val choices: List<LumoAudioTrackChoice>,
    val note: String? = null,
)

/**
 * The player's options on a phone: audio, subtitles, quality, as radio groups.
 *
 * **It stays open on a choice**, unlike [LumoAudioTrackSheet]: somebody changing
 * the audio is often about to change the subtitles too, and the tick that moves
 * is the player's answer rather than the tap's echo — the rows are redrawn from
 * what the player actually selected (PS rules: confirm only once the player has
 * answered).
 *
 * @param banner a sentence above everything, for what concerns the whole menu:
 *   "applied for this playback, preference not saved".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumoPlayerOptionsSheet(
    title: String,
    sections: List<LumoOptionSection>,
    onSelect: (sectionKey: String, choiceId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    banner: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LumoSpacing.md)
                .padding(bottom = LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xxs),
        ) {
            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    banner?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }

            sections.forEach { section ->
                item(key = "header:${section.key}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = LumoSpacing.sm)
                            .semantics { heading() },
                    )
                }
                items(section.choices, key = { "${section.key}:${it.id}" }) { choice ->
                    TrackRow(track = choice, onSelect = { onSelect(section.key, choice.id) })
                }
                section.note?.let { note ->
                    item(key = "note:${section.key}") {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = LumoSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}
