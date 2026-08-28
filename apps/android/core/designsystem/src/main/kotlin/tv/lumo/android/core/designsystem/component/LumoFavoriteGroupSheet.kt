package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * One group, as the sheet needs it.
 *
 * A UI type rather than the data layer's `FavoriteGroup`, because
 * `settings.gradle.kts` puts shared behaviour **down** into `core/` and this
 * module is the design system: it draws, it does not know what a repository is.
 * The feature that opens the sheet maps its own model into this — including the
 * label, which is the one decision this component must not make (see [label]).
 */
data class LumoFavoriteGroupChoice(
    val id: String,
    /**
     * What to show.
     *
     * The caller resolves it, and it is not a detail: the default group is named
     * by the server, in English, until the user renames it. Deciding whether to
     * render that name or a translated one needs `is_default` **and** whether the
     * name is still the server's — which the feature knows and this component
     * does not.
     */
    val label: String,
    val checked: Boolean,
)

/**
 * The group picker, on a phone (US-12).
 *
 * <h2>Several groups at once, because the model allows it</h2>
 *
 * Checkboxes, not a radio list. One channel can be in "Documentaire" and in
 * "Ciné" at the same time — the server's uniqueness rule is on the pair, not on
 * the channel — and a single-choice list would force somebody to remove a channel
 * from one group in order to file it in another. That is the opposite of what the
 * story asks for.
 *
 * <h2>Creating a group is in here, not in a settings screen</h2>
 *
 * Somebody who wants "Documentaire" wants it while they are looking at a
 * documentary channel. A picker that only picks would send them off to find a
 * screen they have never opened, and they would give up instead.
 *
 * Stateless apart from the text being typed: every toggle is reported upwards
 * immediately, so the caller's optimistic state is the single source of what is
 * checked. A sheet that kept its own copy would show one thing while the list
 * behind it showed another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumoFavoriteGroupSheet(
    groups: List<LumoFavoriteGroupChoice>,
    createLabel: String,
    createPlaceholder: String,
    confirmLabel: String,
    onToggle: (groupId: String, checked: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        ) {
            LazyColumn(
                // Capped so the creation row stays reachable without scrolling to
                // the end of a long list of groups.
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.xxs),
            ) {
                items(groups, key = { it.id }) { group ->
                    GroupRow(group = group, onToggle = onToggle)
                }
            }

            CreateRow(
                createLabel = createLabel,
                placeholder = createPlaceholder,
                confirmLabel = confirmLabel,
                onCreate = onCreate,
            )
        }
    }
}

@Composable
private fun GroupRow(
    group: LumoFavoriteGroupChoice,
    onToggle: (groupId: String, checked: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.small)
            // The whole row, not just the box: a checkbox is a small target for a
            // thumb, and the label is the part people aim at.
            .clickable { onToggle(group.id, !group.checked) }
            .padding(vertical = LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = group.checked,
            // Null: the row owns the click. Two handlers on one row means two
            // toggles when a thumb lands on the box.
            onCheckedChange = null,
        )
        Text(
            text = group.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreateRow(
    createLabel: String,
    placeholder: String,
    confirmLabel: String,
    onCreate: (name: String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    if (!creating) {
        TextButton(onClick = { creating = true }) { Text(createLabel) }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .weight(1f)
                .clip(LumoShapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.sm),
            decorationBox = { inner ->
                if (name.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )

        TextButton(
            // A group with no name is a group nobody can tell apart from another
            // one, and the server refuses it anyway (`minLength: 1`).
            enabled = name.isNotBlank(),
            onClick = {
                onCreate(name.trim())
                name = ""
                creating = false
            },
        ) {
            Text(confirmLabel)
        }
    }
}
