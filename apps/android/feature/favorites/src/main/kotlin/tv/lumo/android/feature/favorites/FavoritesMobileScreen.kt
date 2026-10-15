package tv.lumo.android.feature.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.designsystem.component.LumoConfirmDialog
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * Favourite channels, the first section of the phone's Library (US-12, US-022).
 *
 * <h2>"All favourites" first, then the groups</h2>
 *
 * The strip opens on **All favourites**: each channel once, in the library's
 * order (US-020's first occurrence). A tap on a group shows that group, in the
 * user's own order. The two views offer different removals, and the words say
 * which: from a group, "Remove from this group", at once; from All, "Remove from
 * all my favourites", after a confirmation (S11-E04).
 *
 * <h2>Reordering: a drag, and the same thing without one</h2>
 *
 * Inside a group, the handle on each row drags it; the row's menu offers "Move up"
 * and "Move down", because a reorder that exists only as a gesture is one nobody
 * using TalkBack can perform. Either way the view model receives the **visible**
 * order and turns it into full-group moves (S11-00, G1).
 *
 * <h2>Menus behind a long press, and a visible door to them</h2>
 *
 * A tap plays. The row's options are on a long press and on the `⋮` at its end —
 * the second because a long press is a gesture nobody discovers by looking.
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
        Header(refreshing = state.refreshing || state.busy, onRefresh = viewModel::refresh)

        Notices(state = state, viewModel = viewModel)

        when {
            state.loading -> Centered { CircularProgressIndicator() }

            state.nothingAtAll && state.groups.isEmpty() -> Message(
                title = stringResource(R.string.feature_favorites_empty_title),
                body = stringResource(R.string.feature_favorites_empty_body),
            )

            else -> {
                GroupTabs(state = state, viewModel = viewModel)

                val visible = state.visible
                when {
                    visible.isNotEmpty() -> FavoriteList(state = state, viewModel = viewModel, onPlay = onPlay)
                    state.nothingAtAll -> Message(
                        title = stringResource(R.string.feature_favorites_empty_title),
                        body = stringResource(R.string.feature_favorites_empty_body),
                    )
                    else -> Message(
                        title = stringResource(R.string.feature_favorites_group_empty_title),
                        body = stringResource(R.string.feature_favorites_group_empty_body),
                    )
                }
            }
        }
    }

    Dialogs(state = state, viewModel = viewModel)
}

@Composable
private fun Notices(state: FavoritesState, viewModel: FavoritesViewModel) {
    val notice: Pair<String, Boolean>? = when {
        state.offline -> stringResource(R.string.feature_favorites_offline) to false
        state.reorderNotice == ReorderNotice.NotFullyApplied ->
            stringResource(R.string.feature_favorites_reorder_not_fully_applied) to true
        state.reorderNotice == ReorderNotice.NotApplied ->
            stringResource(R.string.feature_favorites_reorder_not_applied) to true
        state.writeError != null -> stringResource(R.string.feature_favorites_write_failed) to true
        state.error != null -> stringResource(R.string.feature_favorites_refresh_failed) to true
        state.removalDone != null ->
            stringResource(R.string.feature_favorites_removed_everywhere, state.removalDone.name) to false
        else -> null
    }
    notice ?: return

    // A completed removal is said once, then goes; failures stay until tapped.
    LaunchedEffect(state.removalDone) {
        if (state.removalDone != null) {
            delay(REMOVAL_NOTICE_MILLIS)
            viewModel.onRemovalShown()
        }
    }

    Text(
        text = notice.first,
        style = MaterialTheme.typography.labelLarge,
        color = if (notice.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    state.offline -> viewModel.refresh()
                    state.reorderNotice != null -> viewModel.onReorderNoticeShown()
                    state.removalDone != null -> viewModel.onRemovalShown()
                    else -> viewModel.onErrorShown()
                }
            }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
    )
}

/**
 * Renaming, creating, deleting, organising and the global removal.
 *
 * Gathered here rather than in the rows, so that a row recycled by `LazyColumn`
 * does not take its open dialog with it.
 */
@Composable
private fun Dialogs(state: FavoritesState, viewModel: FavoritesViewModel) {
    state.renaming?.let { group ->
        NameDialog(
            title = stringResource(R.string.feature_favorites_rename_title),
            confirmLabel = stringResource(R.string.feature_favorites_rename_confirm),
            initial = group.displayName(),
            onConfirm = viewModel::onRenameConfirmed,
            onDismiss = viewModel::onDialogDismissed,
        )
    }

    if (state.creatingGroup) {
        NameDialog(
            title = stringResource(R.string.feature_favorites_new_group_title),
            confirmLabel = stringResource(R.string.feature_favorites_create),
            initial = "",
            onConfirm = viewModel::onCreateGroupConfirmed,
            onDismiss = viewModel::onDialogDismissed,
        )
    }

    state.deleting?.let { group ->
        val count = state.countIn(group)
        val defaultName = state.groups.firstOrNull { it.isDefault }?.displayName()
            ?: stringResource(R.string.feature_favorites_default_group)
        // The count, and where they are going: "are you sure?" would say nothing
        // this person does not already know.
        LumoConfirmDialog(
            title = stringResource(R.string.feature_favorites_delete_title, group.displayName()),
            message = pluralStringResource(R.plurals.feature_favorites_delete_body, count, count, defaultName),
            confirmLabel = stringResource(R.string.feature_favorites_delete_confirm),
            cancelLabel = stringResource(R.string.feature_favorites_cancel),
            onConfirm = viewModel::onDeleteConfirmed,
            onCancel = viewModel::onDialogDismissed,
        )
    }

    state.organizing?.let { channel -> OrganizeDialog(channel = channel, state = state, viewModel = viewModel) }

    state.removal?.let { removal -> RemovalDialog(removal = removal, state = state, viewModel = viewModel) }
}

/**
 * Organise (S11-E02): the channel's groups, ticked, each tick one call. The last
 * one cannot be unticked here — that is the global removal's job, with its own
 * question.
 */
@Composable
private fun OrganizeDialog(channel: Channel, state: FavoritesState, viewModel: FavoritesViewModel) {
    val memberOf = state.membershipsOf(channel.id).mapTo(HashSet()) { it.groupId }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = viewModel::onOrganizeDismissed,
        title = { Text(stringResource(R.string.feature_favorites_organize_title, channel.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
                state.groups.forEach { group ->
                    val checked = group.id in memberOf
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LumoShapes.small)
                            .clickable(enabled = state.canWrite) { viewModel.onMembershipToggled(group.id, !checked) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = state.canWrite)
                        Text(
                            text = group.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.feature_favorites_new_group)) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newName.isNotBlank() && state.canWrite,
                        onClick = {
                            viewModel.onOrganizeGroupCreated(newName)
                            newName = ""
                        },
                    ) { Text(stringResource(R.string.feature_favorites_create)) }
                }
                if (state.organizeNotice == OrganizeNotice.LastGroup) {
                    Text(
                        text = stringResource(R.string.feature_favorites_organize_last_group),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (state.busy) {
                    Text(
                        text = stringResource(R.string.feature_favorites_saving),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onOrganizeDismissed) {
                Text(stringResource(R.string.feature_favorites_done))
            }
        },
    )
}

/**
 * "Remove from all my favourites": the question, then the verdict (S11-E04, G2).
 *
 * Cancel is the prominent answer to the question. After a partial removal the
 * dialog names the groups that still hold the channel and offers to retry those
 * only; closing it is a close, never a success (FO-07).
 */
@Composable
private fun RemovalDialog(removal: GlobalRemoval, state: FavoritesState, viewModel: FavoritesViewModel) {
    val name = removal.channel.name
    when (val phase = removal.phase) {
        RemovalPhase.Confirming, RemovalPhase.Running -> {
            val count = state.membershipsOf(removal.channel.id).size.coerceAtLeast(1)
            LumoConfirmDialog(
                title = stringResource(R.string.feature_favorites_remove_everywhere_title, name),
                message = pluralStringResource(R.plurals.feature_favorites_remove_everywhere_body, count, count),
                confirmLabel = stringResource(
                    if (phase == RemovalPhase.Running) {
                        R.string.feature_favorites_removing
                    } else {
                        R.string.feature_favorites_remove_everywhere_confirm
                    },
                ),
                cancelLabel = stringResource(R.string.feature_favorites_cancel),
                onConfirm = viewModel::onRemoveEverywhereConfirmed,
                onCancel = viewModel::onRemovalDismissed,
                busy = phase == RemovalPhase.Running,
            )
        }

        is RemovalPhase.Incomplete -> OutcomeDialog(
            title = stringResource(R.string.feature_favorites_removal_incomplete_title),
            body = pluralStringResource(
                R.plurals.feature_favorites_removal_incomplete_body,
                phase.remaining.size,
                phase.remaining.size,
                groupNames(state.groupsOf(phase.remaining)),
            ),
            viewModel = viewModel,
        )

        is RemovalPhase.Unconfirmed -> OutcomeDialog(
            title = stringResource(R.string.feature_favorites_removal_unconfirmed_title),
            body = stringResource(R.string.feature_favorites_removal_unconfirmed_body),
            viewModel = viewModel,
        )

        is RemovalPhase.NotStarted -> OutcomeDialog(
            title = stringResource(R.string.feature_favorites_removal_not_started_title),
            body = stringResource(
                if (phase.offline) {
                    R.string.feature_favorites_removal_offline_body
                } else {
                    R.string.feature_favorites_removal_not_started_body
                },
            ),
            viewModel = viewModel,
        )
    }
}

@Composable
private fun OutcomeDialog(title: String, body: String, viewModel: FavoritesViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::onRemovalDismissed,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::onRemovalRetried) { Text(stringResource(R.string.feature_favorites_retry)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onRemovalDismissed) { Text(stringResource(R.string.feature_favorites_close)) }
        },
    )
}

@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.feature_favorites_group_name)) },
            )
        },
        confirmButton = {
            // The server refuses an empty name (`minLength: 1`).
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_favorites_cancel)) }
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
 * "All favourites", then one chip per group, then "New group".
 *
 * The same horizontal strip as the catalogue's categories: a group filters a list
 * of channels exactly as a category does.
 */
@Composable
private fun GroupTabs(state: FavoritesState, viewModel: FavoritesViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        modifier = Modifier.padding(bottom = LumoSpacing.sm),
    ) {
        item(key = "all") {
            Chip(
                label = stringResource(R.string.feature_favorites_all),
                selected = state.isAllView,
                onClick = { viewModel.onGroupSelected(null) },
            )
        }
        items(state.groups, key = { it.id }) { group ->
            GroupChip(group = group, state = state, viewModel = viewModel)
        }
        item(key = "new") {
            Chip(
                label = "+ " + stringResource(R.string.feature_favorites_new_group),
                selected = false,
                onClick = viewModel::onCreateGroupRequested,
            )
        }
    }
}

/**
 * A group tab and everything one can do to it, behind a long press. **The default
 * group offers no delete**: the server refuses it — it is where the others empty
 * into — and an entry only ever answered by an error teaches that the app is broken.
 */
@Composable
private fun GroupChip(group: FavoriteGroup, state: FavoritesState, viewModel: FavoritesViewModel) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Chip(
            label = group.displayName(),
            selected = group.id == state.selectedGroupId,
            onClick = { viewModel.onGroupSelected(group.id) },
            onLongClick = { menuOpen = true },
            longClickLabel = stringResource(R.string.feature_favorites_group_actions),
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(R.string.feature_favorites_rename, enabled = state.canWrite) {
                menuOpen = false
                viewModel.onRenameRequested(group)
            }
            if (state.canMoveGroup(group, -1)) {
                MenuItem(R.string.feature_favorites_move_left, enabled = state.canWrite) {
                    menuOpen = false
                    viewModel.onGroupMoved(group, -1)
                }
            }
            if (state.canMoveGroup(group, 1)) {
                MenuItem(R.string.feature_favorites_move_right, enabled = state.canWrite) {
                    menuOpen = false
                    viewModel.onGroupMoved(group, 1)
                }
            }
            if (!group.isDefault) {
                MenuItem(R.string.feature_favorites_delete, enabled = state.canWrite) {
                    menuOpen = false
                    viewModel.onDeleteRequested(group)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(LumoShapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = longClickLabel)
            .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.xs),
    )
}

@Composable
private fun MenuItem(labelRes: Int, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick, enabled = enabled)
}

/**
 * The open view's channels. In a group, each row carries a drag handle; the order
 * shown while dragging is local, and it is dropped as soon as the server's answer
 * (or its refusal) comes back — the screen never keeps a guess.
 */
@Composable
private fun FavoriteList(
    state: FavoritesState,
    viewModel: FavoritesViewModel,
    onPlay: (String, String?) -> Unit,
) {
    val visible = state.visible
    val listState = rememberLazyListState()
    var dragOrder by remember { mutableStateOf<List<FavoriteChannel>?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val currentVisible by rememberUpdatedState(visible)

    // The server answered (or refused): what is shown is its list again.
    LaunchedEffect(visible, state.busy) {
        if (draggedId == null && !state.busy) dragOrder = null
    }

    val shown = dragOrder ?: visible
    val draggable = !state.isAllView && state.canWrite && visible.size > 1

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(shown, key = { it.favoriteId }) { favorite ->
            val dragged = favorite.favoriteId == draggedId
            FavoriteRow(
                favorite = favorite,
                state = state,
                viewModel = viewModel,
                onPlay = onPlay,
                handle = if (draggable) {
                    { DragHandle(channelName = favorite.channel.name, modifier = Modifier.pointerInput(favorite.favoriteId) {
                        detectDragGestures(
                            onDragStart = {
                                draggedId = favorite.favoriteId
                                dragOrder = currentVisible
                                dragOffset.floatValue = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset.floatValue += amount.y
                                dragOrder = stepDrag(dragOrder ?: return@detectDragGestures, favorite.favoriteId, dragOffset, listState)
                            },
                            onDragEnd = {
                                val order = dragOrder
                                draggedId = null
                                dragOffset.floatValue = 0f
                                if (order != null) viewModel.onFavoritesReordered(order.map { it.favoriteId })
                            },
                            onDragCancel = {
                                draggedId = null
                                dragOrder = null
                                dragOffset.floatValue = 0f
                            },
                        )
                    }) }
                } else {
                    null
                },
                modifier = if (dragged) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragOffset.floatValue }
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * Swaps the dragged row with its neighbour once it has travelled past half of it.
 * The offset is rebased by the neighbour's height, so the row stays under the finger.
 */
private fun stepDrag(
    order: List<FavoriteChannel>,
    draggedId: String,
    offset: androidx.compose.runtime.MutableFloatState,
    listState: LazyListState,
): List<FavoriteChannel> {
    val index = order.indexOfFirst { it.favoriteId == draggedId }
    if (index < 0) return order
    val items = listState.layoutInfo.visibleItemsInfo
    val delta = offset.floatValue
    return when {
        delta > 0 && index < order.lastIndex -> {
            val next = items.firstOrNull { it.key == order[index + 1].favoriteId } ?: return order
            if (delta < next.size / 2f) return order
            offset.floatValue -= next.size
            order.toMutableList().apply { add(index + 1, removeAt(index)) }
        }
        delta < 0 && index > 0 -> {
            val previous = items.firstOrNull { it.key == order[index - 1].favoriteId } ?: return order
            if (-delta < previous.size / 2f) return order
            offset.floatValue += previous.size
            order.toMutableList().apply { add(index - 1, removeAt(index)) }
        }
        else -> order
    }
}

/**
 * One favourite. A tap plays; the long press and the `⋮` open its options, which
 * depend on the view: in All, Organise and "Remove from all my favourites"; in a
 * group, Move up / Move down, Organise and "Remove from this group".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    favorite: FavoriteChannel,
    state: FavoritesState,
    viewModel: FavoritesViewModel,
    onPlay: (String, String?) -> Unit,
    handle: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val channel = favorite.channel
    var menuOpen by remember { mutableStateOf(false) }
    val optionsLabel = stringResource(R.string.feature_favorites_channel_actions)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onPlay(channel.id, channel.name) },
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = optionsLabel,
                )
                .padding(start = LumoSpacing.md, end = LumoSpacing.xs, top = LumoSpacing.sm, bottom = LumoSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(channel)

            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            channel.quality?.let { quality ->
                Text(
                    text = quality,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Glyph(text = "⋮", description = optionsLabel, onClick = { menuOpen = true })

            handle?.invoke()
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (state.isAllView) {
                MenuItem(R.string.feature_favorites_organize, enabled = state.canWrite) {
                    menuOpen = false
                    viewModel.onOrganizeRequested(favorite)
                }
                MenuItem(R.string.feature_favorites_remove_everywhere, enabled = state.canWrite) {
                    menuOpen = false
                    viewModel.onRemoveEverywhereRequested(favorite)
                }
            } else {
                // Only the moves that exist: an entry that does nothing at the end
                // of the list is an entry somebody presses once to find out.
                if (state.canMoveFavorite(favorite, -1)) {
                    MenuItem(R.string.feature_favorites_move_up, enabled = state.canWrite) {
                        menuOpen = false
                        viewModel.onFavoriteMoved(favorite, -1)
                    }
                }
                if (state.canMoveFavorite(favorite, 1)) {
                    MenuItem(R.string.feature_favorites_move_down, enabled = state.canWrite) {
                        menuOpen = false
                        viewModel.onFavoriteMoved(favorite, 1)
                    }
                }
                MenuItem(R.string.feature_favorites_organize, enabled = state.canWrite) {
                    menuOpen = false
                    viewModel.onOrganizeRequested(favorite)
                }
                MenuItem(R.string.feature_favorites_remove_from_group, enabled = state.canWrite) {
                    menuOpen = false
                    viewModel.onRemoveFromGroup(favorite)
                }
            }
        }
    }
}

/** The grip a row is dragged by. Its gestures come from the list, which owns the order. */
@Composable
private fun DragHandle(channelName: String, modifier: Modifier = Modifier) {
    val dragLabel = stringResource(R.string.feature_favorites_drag_handle, channelName)
    Text(
        text = "≡",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .semantics { contentDescription = dragLabel }
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
    )
}

@Composable
private fun Glyph(text: String, description: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(LumoShapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.xs),
    )
}

/**
 * The logo the user's own playlist advertises, or the channel's initial. **Lumo
 * ships no fallback artwork** (AGENTS.md §1).
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

private const val REMOVAL_NOTICE_MILLIS = 6_000L
