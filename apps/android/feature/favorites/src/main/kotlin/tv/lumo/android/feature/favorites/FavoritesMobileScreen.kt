package tv.lumo.android.feature.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The channels the user filed themselves (US-12).
 *
 * <h2>A tab per group, and the list in the user's own order</h2>
 *
 * Sorted by `position`, never by name: `position` is what S4-05 lets somebody
 * change, and sorting alphabetically here would make that whole task invisible.
 *
 * <h2>Each row says which source it came from</h2>
 *
 * Discreetly, and it is not decoration. A group belongs to the account and can
 * hold channels from two subscriptions — that is the point of the feature — and
 * two channels with the same name from two providers are otherwise identical
 * rows.
 *
 * <h2>Two empty states, because they are two different situations</h2>
 *
 * An account with nothing starred needs the *gesture* named: go to the channels
 * and press the heart. A group that happens to be empty in an account full of
 * favourites needs no such lesson — it needs to say that this shelf is empty. One
 * message for both would be wrong for one of them.
 *
 * <h2>A list, not Paging</h2>
 *
 * Unlike the catalogue. Fifteen thousand channels is an ordinary source; a group
 * of favourites is something somebody built by hand, counted in tens. Paging forty
 * rows costs more than it saves.
 */
@Composable
fun FavoritesMobileScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Header(refreshing = state.refreshing, onRefresh = viewModel::refresh)

        state.error?.let {
            Text(
                text = stringResource(R.string.feature_favorites_refresh_failed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::onErrorShown)
                    .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
            )
        }

        when {
            state.loading -> Centered { CircularProgressIndicator() }

            state.nothingAtAll -> Message(
                title = stringResource(R.string.feature_favorites_empty_title),
                body = stringResource(R.string.feature_favorites_empty_body),
            )

            else -> {
                GroupTabs(
                    groups = state.groups,
                    selectedId = state.selectedGroupId,
                    onSelect = viewModel::onGroupSelected,
                    canMove = state::canMoveGroup,
                    onRename = viewModel::onRenameRequested,
                    onDelete = viewModel::onDeleteRequested,
                    onMove = viewModel::onGroupMoved,
                )

                val visible = state.visible
                if (visible.isEmpty()) {
                    Message(
                        title = stringResource(R.string.feature_favorites_group_empty_title),
                        body = stringResource(R.string.feature_favorites_group_empty_body),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.favoriteId }) { favorite ->
                            FavoriteRow(
                                favorite = favorite,
                                canMove = state::canMoveFavorite,
                                hasOtherGroups = state.moveTargets(favorite).isNotEmpty(),
                                onPlay = onPlay,
                                onMove = viewModel::onFavoriteMoved,
                                onMoveToGroup = viewModel::onMoveRequested,
                                onRemove = viewModel::onFavoriteRemoved,
                            )
                        }
                    }
                }
            }
        }
    }

    Dialogs(state = state, viewModel = viewModel)
}

/**
 * Renaming, deleting and moving, in three dialogs.
 *
 * Gathered here rather than scattered through the list so that a row can be
 * recycled by `LazyColumn` without taking its open dialog with it.
 */
@Composable
private fun Dialogs(state: FavoritesState, viewModel: FavoritesViewModel) {
    state.renaming?.let { group ->
        RenameDialog(
            initial = group.displayName(),
            onConfirm = viewModel::onRenameConfirmed,
            onDismiss = viewModel::onDialogDismissed,
        )
    }

    state.deleting?.let { group ->
        val count = state.countIn(group)
        val defaultName = stringResource(R.string.feature_favorites_default_group)
        AlertDialog(
            onDismissRequest = viewModel::onDialogDismissed,
            title = { Text(stringResource(R.string.feature_favorites_delete_title, group.displayName())) },
            text = {
                // The count, and where they are going. "Are you sure?" would say
                // nothing this person does not already know; this lets them
                // predict the state they will be in.
                Text(
                    pluralStringResource(
                        R.plurals.feature_favorites_delete_body,
                        count,
                        count,
                        state.groups.firstOrNull { it.isDefault }?.displayName() ?: defaultName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirmed) {
                    Text(stringResource(R.string.feature_favorites_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDialogDismissed) {
                    Text(stringResource(R.string.feature_favorites_cancel))
                }
            },
        )
    }

    state.moving?.let { favorite ->
        AlertDialog(
            onDismissRequest = viewModel::onDialogDismissed,
            title = { Text(stringResource(R.string.feature_favorites_move_title)) },
            text = {
                Column {
                    state.moveTargets(favorite).forEach { group ->
                        Text(
                            text = group.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onMoveConfirmed(group.id) }
                                .padding(vertical = LumoSpacing.sm),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDialogDismissed) {
                    Text(stringResource(R.string.feature_favorites_cancel))
                }
            },
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_favorites_rename_title)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.feature_favorites_group_name)) },
            )
        },
        confirmButton = {
            TextButton(
                // The server refuses an empty name (`minLength: 1`), and a group
                // nobody can tell from another is not a rename anyone wanted.
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) {
                Text(stringResource(R.string.feature_favorites_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_favorites_cancel))
            }
        },
    )
}

@Composable
private fun Header(refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_favorites_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (refreshing) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(LumoSpacing.md))
        } else {
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.feature_favorites_refresh))
            }
        }
    }
}

/**
 * One tab per group, the default group first.
 *
 * The same horizontal strip as the catalogue's categories, and deliberately so: a
 * group filters a list of channels exactly as a category does, and giving the two
 * different shapes would be a difference with nothing behind it.
 */
@Composable
private fun GroupTabs(
    groups: List<FavoriteGroup>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    canMove: (FavoriteGroup, Int) -> Boolean,
    onRename: (FavoriteGroup) -> Unit,
    onDelete: (FavoriteGroup) -> Unit,
    onMove: (FavoriteGroup, Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        modifier = Modifier.padding(bottom = LumoSpacing.sm),
    ) {
        items(groups, key = { it.id }) { group ->
            GroupChip(
                group = group,
                selected = group.id == selectedId,
                onClick = { onSelect(group.id) },
                canMove = canMove,
                onRename = onRename,
                onDelete = onDelete,
                onMove = onMove,
            )
        }
    }
}

/**
 * A group tab, and everything one can do to it.
 *
 * The menu is on a long press rather than behind a visible button: a row of chips
 * is already narrow on a phone, and an overflow dot on each would halve the space
 * the names have. A tap still does the frequent thing, which is switching tab.
 *
 * **The default group offers no delete.** The server refuses it — it is where the
 * others empty into — and an entry that is only ever answered by an error is an
 * entry that teaches somebody the application is broken.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(
    group: FavoriteGroup,
    selected: Boolean,
    onClick: () -> Unit,
    canMove: (FavoriteGroup, Int) -> Boolean,
    onRename: (FavoriteGroup) -> Unit,
    onDelete: (FavoriteGroup) -> Unit,
    onMove: (FavoriteGroup, Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Text(
            text = group.displayName(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(LumoShapes.small)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = stringResource(R.string.feature_favorites_group_actions),
                )
                .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.xs),
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(R.string.feature_favorites_rename) {
                menuOpen = false
                onRename(group)
            }
            // Only the moves that exist. An entry that does nothing at the end of
            // the list is an entry somebody presses once to find out.
            if (canMove(group, -1)) {
                MenuItem(R.string.feature_favorites_move_left) {
                    menuOpen = false
                    onMove(group, -1)
                }
            }
            if (canMove(group, 1)) {
                MenuItem(R.string.feature_favorites_move_right) {
                    menuOpen = false
                    onMove(group, 1)
                }
            }
            if (!group.isDefault) {
                MenuItem(R.string.feature_favorites_delete) {
                    menuOpen = false
                    onDelete(group)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

/**
 * One favourite, and everything one can do to it.
 *
 * A tap plays it — that is what somebody came here for. The organising is behind a
 * long press, for the reason the group chips have theirs: buttons on every row of
 * a list somebody reads would leave the channel names half the width.
 *
 * **"Move up" and "move down" rather than only drag-and-drop.** The order matters
 * here — it is the user's own, and the list is sorted by it — and a reorder that
 * exists only as a drag gesture is a reorder nobody using TalkBack can perform.
 * The drag is the comfort; this is the mechanism.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    favorite: FavoriteChannel,
    canMove: (FavoriteChannel, Int) -> Boolean,
    hasOtherGroups: Boolean,
    onPlay: (String, String?) -> Unit,
    onMove: (FavoriteChannel, Int) -> Unit,
    onMoveToGroup: (FavoriteChannel) -> Unit,
    onRemove: (FavoriteChannel) -> Unit,
) {
    val channel = favorite.channel
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (canMove(favorite, -1)) {
                MenuItem(R.string.feature_favorites_move_up) {
                    menuOpen = false
                    onMove(favorite, -1)
                }
            }
            if (canMove(favorite, 1)) {
                MenuItem(R.string.feature_favorites_move_down) {
                    menuOpen = false
                    onMove(favorite, 1)
                }
            }
            if (hasOtherGroups) {
                MenuItem(R.string.feature_favorites_move_to_group) {
                    menuOpen = false
                    onMoveToGroup(favorite)
                }
            }
            MenuItem(R.string.feature_favorites_remove) {
                menuOpen = false
                onRemove(favorite)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onPlay(channel.id, channel.name) },
                onLongClick = { menuOpen = true },
                onLongClickLabel = stringResource(R.string.feature_favorites_channel_actions),
            )
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(channel)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // No line naming the subscription any more (US-018). Every row here
            // now comes from the source being browsed, and the shell names that
            // source next to its switcher — the same sentence under each row is
            // the noise this line was always careful not to be.
        }

        channel.quality?.let { quality ->
            Text(
                text = quality,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The logo the user's own playlist advertises, or the channel's initial.
 *
 * **Lumo ships no fallback artwork** (AGENTS.md §1). A logo that fails to load
 * looks like a channel with no logo, which is deliberate and will be common: most
 * panels advertise their logos over `http`, and this application does not permit
 * cleartext.
 */
@Composable
private fun Logo(channel: Channel) {
    val size = LumoSpacing.xl
    val shape = LumoShapes.small

    if (channel.logoUrl == null) {
        Initial(channel, size, shape)
        return
    }

    SubcomposeAsyncImage(
        model = channel.logoUrl,
        contentDescription = null,
        modifier = Modifier.size(size).clip(shape),
        loading = { Initial(channel, size, shape) },
        error = { Initial(channel, size, shape) },
    )
}

@Composable
private fun Initial(channel: Channel, size: Dp, shape: Shape) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel.name.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * What to call a group on screen.
 *
 * The server names the group it creates on the first add, and names it
 * `Favorites`, in English. `is_default` is what lets a client translate it; the
 * second half of the condition is what stops the translation overriding the user
 * once they have renamed the group.
 */
@Composable
private fun FavoriteGroup.displayName(): String =
    if (isDefault && name == SERVER_DEFAULT_GROUP_NAME) {
        stringResource(R.string.feature_favorites_default_group)
    } else {
        name
    }

/** The name the server gives the default group, verbatim. */
private const val SERVER_DEFAULT_GROUP_NAME = "Favorites"
