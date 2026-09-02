package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * One audio track, as a picker needs it.
 *
 * A UI type rather than the player's `AudioTrack`, for the reason
 * [LumoFavoriteGroupChoice] is one: `settings.gradle.kts` puts shared behaviour
 * down into `core/`, and this module draws — it does not know what a codec is.
 * The feature resolves the words, because resolving them needs string resources
 * and a locale, and neither belongs here.
 */
data class LumoAudioTrackChoice(
    val id: String,
    /** "Français", "VOSTFR", "Piste 2" — whatever the caller could work out. */
    val label: String,
    /**
     * The second line: "Dolby Digital 5.1", or the reason it cannot be chosen.
     *
     * Null draws no second line at all rather than an empty one, because a track
     * about which nothing is known should look like a plain choice, not like a
     * choice with something missing.
     */
    val detail: String?,
    val selected: Boolean,
    /**
     * Whether this device can play it.
     *
     * **A false track is still drawn**, greyed and unselectable. Hiding it would
     * be the more comfortable choice and it is the wrong one: somebody looking
     * for the French track needs to learn that it is there and that their
     * telephone has no decoder for it. A track that is simply absent is the
     * mystery this feature exists to end.
     */
    val enabled: Boolean,
)

/**
 * Choosing the language on a phone.
 *
 * <h2>Why a picker exists at all</h2>
 *
 * IPTV files routinely carry two or three audio tracks — the original, the dub,
 * sometimes an audio description — and until now the player took whichever the
 * muxer had put first. Somebody who wanted the other one had no way to ask.
 *
 * <h2>Single choice, unlike the favourite groups sheet</h2>
 *
 * Radio semantics, not checkboxes, and the difference is not stylistic: a
 * channel can belong to two groups at once, and a stream plays exactly one audio
 * track. Announcing it as a radio group is what makes a screen reader say "2 of
 * 3" instead of reading three independent toggles.
 *
 * <h2>It closes on choosing</h2>
 *
 * There is nothing to confirm — the sound changes as the row is touched, which
 * is the whole answer to "is this the right track". A confirm button would ask
 * somebody to commit to a language they have not heard yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumoAudioTrackSheet(
    title: String,
    tracks: List<LumoAudioTrackChoice>,
    onSelect: (trackId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LumoSpacing.md)
                .padding(bottom = LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            LazyColumn(
                // Capped rather than free: a file with a dozen dubs would
                // otherwise push the sheet to the top of the screen and hide the
                // picture somebody is choosing a language for.
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.xxs),
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onSelect = {
                            onSelect(track.id)
                            onDismiss()
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
) {
    // Greyed rather than absent, and greyed rather than merely inert: a row that
    // looks selectable and does nothing is worse than one that says it cannot be.
    val tint = if (track.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.small)
            .selectable(
                selected = track.selected,
                enabled = track.enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A glyph rather than a RadioButton: the mark has to sit beside a
        // two-line row, and Material's control aligns to a single line.
        Text(
            text = if (track.selected) "●" else "○",
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
