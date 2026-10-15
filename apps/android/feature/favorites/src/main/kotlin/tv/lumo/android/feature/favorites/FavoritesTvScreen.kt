package tv.lumo.android.feature.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.component.LumoTvConfirmDialog
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * "My library" on a television: favourite channels, their groups, and organising
 * them with the remote (US-022, S11-E01 → S11-E04).
 *
 * <h2>Focus map</h2>
 *
 * - **Groups on the left, channels on the right**, as the design asks for the
 *   television and the web. The column is "All favourites", the groups, then
 *   "New group". `OK` on a group opens it; `RIGHT` enters the grid; `LEFT` from
 *   the grid's first column comes back, and once more reaches the rail.
 * - **Arrival** on the channel `OK` last launched, else the first card, else
 *   "All favourites".
 * - **Hold `OK`** on a card or a group for its options — the gesture Live already
 *   uses on a channel. The options are a dialog: the remote cannot wander onto
 *   the grid behind it, `BACK` closes it, the first option takes the focus.
 * - Inside a group, a card's options are "Move before", "Move after" (only the
 *   moves that exist), Organise and "Remove from this group". In All favourites,
 *   Organise and "Remove from all my favourites", which asks first with **Cancel
 *   focused**.
 * - **Focus return.** When a dialog closes, the focus goes back to the card or
 *   group that opened it; if that card is gone — removed — to the neighbour that
 *   took its place, or to "All favourites" when the view is empty.
 */
@Composable
fun FavoritesTvScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Back on screen, or back from the background: another device may have
    // starred or removed something (S11-05). In place — the grid is keyed by
    // channel, so the focused card keeps the focus if it is still there.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    FavoritesTvContent(
        state = state,
        actions = FavoritesTvActions(
            onPlay = onPlay,
            onOpenLive = onOpenLive,
            onSelectGroup = viewModel::onGroupSelected,
            onCreateGroup = viewModel::onCreateGroupRequested,
            onCreateGroupConfirmed = viewModel::onCreateGroupConfirmed,
            onRename = viewModel::onRenameRequested,
            onRenameConfirmed = viewModel::onRenameConfirmed,
            onDelete = viewModel::onDeleteRequested,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onDialogDismissed = viewModel::onDialogDismissed,
            onMoveGroup = viewModel::onGroupMoved,
            onMoveFavorite = viewModel::onFavoriteMoved,
            onOrganize = viewModel::onOrganizeRequested,
            onToggleMembership = viewModel::onMembershipToggled,
            onOrganizeDismissed = viewModel::onOrganizeDismissed,
            onRemoveFromGroup = viewModel::onRemoveFromGroup,
            onRemoveEverywhere = viewModel::onRemoveEverywhereRequested,
            onRemoveEverywhereConfirmed = viewModel::onRemoveEverywhereConfirmed,
            onRemovalRetried = viewModel::onRemovalRetried,
            onRemovalDismissed = viewModel::onRemovalDismissed,
            onRemovalShown = viewModel::onRemovalShown,
            onNoticeShown = {
                viewModel.onReorderNoticeShown()
                viewModel.onErrorShown()
            },
            onRetry = viewModel::refresh,
        ),
        modifier = modifier,
    )
}

/** Everything the screen can ask for, so a focus test can drive it without Hilt. */
internal class FavoritesTvActions(
    val onPlay: (channelId: String, name: String?) -> Unit = { _, _ -> },
    val onOpenLive: () -> Unit = {},
    val onSelectGroup: (String?) -> Unit = {},
    val onCreateGroup: () -> Unit = {},
    val onCreateGroupConfirmed: (String) -> Unit = {},
    val onRename: (FavoriteGroup) -> Unit = {},
    val onRenameConfirmed: (String) -> Unit = {},
    val onDelete: (FavoriteGroup) -> Unit = {},
    val onDeleteConfirmed: () -> Unit = {},
    val onDialogDismissed: () -> Unit = {},
    val onMoveGroup: (FavoriteGroup, Int) -> Unit = { _, _ -> },
    val onMoveFavorite: (FavoriteChannel, Int) -> Unit = { _, _ -> },
    val onOrganize: (FavoriteChannel) -> Unit = {},
    val onToggleMembership: (groupId: String, checked: Boolean) -> Unit = { _, _ -> },
    val onOrganizeDismissed: () -> Unit = {},
    val onRemoveFromGroup: (FavoriteChannel) -> Unit = {},
    val onRemoveEverywhere: (FavoriteChannel) -> Unit = {},
    val onRemoveEverywhereConfirmed: () -> Unit = {},
    val onRemovalRetried: () -> Unit = {},
    val onRemovalDismissed: () -> Unit = {},
    val onRemovalShown: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** What opened a dialog, to put the focus back (or next to it) when it closes. */
private data class FocusOrigin(
    val kind: Kind,
    val id: String,
    val index: Int,
    /** The action may remove the card: wait for it to go before choosing the target. */
    val mayRemove: Boolean = false,
) {
    enum class Kind { Card, Group }
}

@Composable
internal fun FavoritesTvContent(
    state: FavoritesState,
    actions: FavoritesTvActions,
    modifier: Modifier = Modifier,
) {
    val favorites = state.visible.withReadableNames()
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val groupRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()

    // The dialogs this screen holds itself: the options of a card or of a group.
    var cardMenu by remember { mutableStateOf<FavoriteChannel?>(null) }
    var groupMenu by remember { mutableStateOf<FavoriteGroup?>(null) }
    var origin by remember { mutableStateOf<FocusOrigin?>(null) }
    // The channel `OK` last launched, saved with the back stack entry: back from the
    // player, the remote lands where it left.
    var launchedId by rememberSaveable { mutableStateOf<String?>(null) }

    val dialogOpen = cardMenu != null || groupMenu != null || state.organizing != null ||
        state.removal != null || state.renaming != null || state.deleting != null || state.creatingGroup
    val currentIds by rememberUpdatedState(favorites.map { it.channel.id })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            // Not on the leading edge: the rail is there and has already paid for it.
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        Column(
            modifier = Modifier.padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg)) {
                Text(
                    text = stringResource(R.string.feature_favorites_tv_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = LumoColors.OnDark,
                )
                if (state.busy) {
                    Text(
                        text = stringResource(R.string.feature_favorites_saving),
                        style = MaterialTheme.typography.titleMedium,
                        color = LumoColors.OnDarkMuted,
                    )
                }
            }

            Notice(state = state, onShown = actions.onNoticeShown, onRemovalShown = actions.onRemovalShown)

            when {
                // Distinct from "no favourites": the first Room emission has not
                // arrived. It lasts a frame, and the rail is focusable meanwhile.
                state.loading -> Text(
                    text = stringResource(R.string.feature_favorites_tv_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )

                state.nothingAtAll && state.groups.isEmpty() -> Empty(actions.onOpenLive)

                else -> Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg)) {
                    GroupColumn(
                        state = state,
                        requesters = groupRequesters,
                        onSelect = actions.onSelectGroup,
                        onCreate = actions.onCreateGroup,
                        onRetry = actions.onRetry,
                        onOptions = { index, group ->
                            origin = FocusOrigin(FocusOrigin.Kind.Group, group.id, index)
                            groupMenu = group
                        },
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = state.selectedGroup?.displayName() ?: stringResource(R.string.feature_favorites_all),
                            style = MaterialTheme.typography.titleLarge,
                            color = LumoColors.OnDarkMuted,
                        )
                        Text(
                            text = stringResource(R.string.feature_favorites_tv_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LumoColors.OnDarkMuted,
                        )
                        if (favorites.isEmpty()) {
                            Text(
                                text = stringResource(
                                    if (state.nothingAtAll) {
                                        R.string.feature_favorites_tv_empty_body
                                    } else {
                                        R.string.feature_favorites_group_empty_body
                                    },
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = LumoColors.OnDarkMuted,
                            )
                        } else {
                            Grid(
                                favorites = favorites,
                                gridState = gridState,
                                requesters = cardRequesters,
                                // `LEFT` from the first column returns to the
                                // group on show, not to whichever entry of the
                                // column happens to be level with the card
                                // (BUG-S11-06-07). Read at the moment of the key
                                // press: an entry scrolled out of the column has
                                // no requester, and the default search applies.
                                leftTarget = {
                                    groupRequesters[groupKeyOf(state.selectedGroupId)] ?: FocusRequester.Default
                                },
                                arrivalId = launchedId,
                                onPlay = { channel ->
                                    launchedId = channel.id
                                    actions.onPlay(channel.id, channel.name)
                                },
                                onOptions = { index, favorite ->
                                    origin = FocusOrigin(FocusOrigin.Kind.Card, favorite.channel.id, index)
                                    cardMenu = favorite
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- dialogs ---------------------------------------------------------------

    cardMenu?.let { favorite ->
        CardOptions(
            favorite = favorite,
            state = state,
            onDismiss = { cardMenu = null },
            onMove = { delta ->
                cardMenu = null
                actions.onMoveFavorite(favorite, delta)
            },
            onOrganize = {
                cardMenu = null
                actions.onOrganize(favorite)
            },
            onRemoveFromGroup = {
                cardMenu = null
                origin = origin?.copy(mayRemove = true)
                actions.onRemoveFromGroup(favorite)
            },
            onRemoveEverywhere = {
                cardMenu = null
                origin = origin?.copy(mayRemove = true)
                actions.onRemoveEverywhere(favorite)
            },
        )
    }

    groupMenu?.let { group ->
        GroupOptions(
            group = group,
            state = state,
            onDismiss = { groupMenu = null },
            onRename = {
                groupMenu = null
                actions.onRename(group)
            },
            onMove = { delta ->
                groupMenu = null
                actions.onMoveGroup(group, delta)
            },
            onDelete = {
                groupMenu = null
                origin = origin?.copy(mayRemove = true)
                actions.onDelete(group)
            },
        )
    }

    state.renaming?.let { group ->
        NameDialog(
            title = stringResource(R.string.feature_favorites_rename_title),
            confirmLabel = stringResource(R.string.feature_favorites_rename_confirm),
            initial = group.displayName(),
            onConfirm = actions.onRenameConfirmed,
            onDismiss = actions.onDialogDismissed,
        )
    }

    if (state.creatingGroup) {
        NameDialog(
            title = stringResource(R.string.feature_favorites_new_group_title),
            confirmLabel = stringResource(R.string.feature_favorites_create),
            initial = "",
            onConfirm = actions.onCreateGroupConfirmed,
            onDismiss = actions.onDialogDismissed,
        )
    }

    state.deleting?.let { group ->
        val count = state.countIn(group)
        val defaultName = state.groups.firstOrNull { it.isDefault }?.displayName()
            ?: stringResource(R.string.feature_favorites_default_group)
        LumoTvConfirmDialog(
            title = stringResource(R.string.feature_favorites_delete_title, group.displayName()),
            message = groupDeletionMessage(count, defaultName),
            confirmLabel = stringResource(R.string.feature_favorites_delete_confirm),
            cancelLabel = stringResource(R.string.feature_favorites_cancel),
            onConfirm = actions.onDeleteConfirmed,
            onCancel = actions.onDialogDismissed,
        )
    }

    state.organizing?.let { channel ->
        OrganizeDialog(
            channel = channel,
            state = state,
            onToggle = actions.onToggleMembership,
            onDismiss = actions.onOrganizeDismissed,
        )
    }

    state.removal?.let { removal ->
        RemovalDialog(
            removal = removal,
            state = state,
            onConfirm = actions.onRemoveEverywhereConfirmed,
            onRetry = actions.onRemovalRetried,
            onDismiss = actions.onRemovalDismissed,
        )
    }

    // ---- focus return ------------------------------------------------------------

    LaunchedEffect(dialogOpen, state.busy) {
        if (origin == null || dialogOpen || state.busy) return@LaunchedEffect
        // An option that hands over to a dialog of the view model's (Organise, the
        // removal question) closes this screen's own a frame before the next one
        // is drawn: let it settle, or the focus would return in between. A dialog
        // opening meanwhile restarts this effect.
        delay(DIALOG_SETTLE_MILLIS)
        val from = origin ?: return@LaunchedEffect

        // A removal is written to the cache before the write returns, but Room
        // tells the screen a moment later: wait for the card to go, briefly, so
        // the focus is not put on a card about to disappear.
        if (from.kind == FocusOrigin.Kind.Card && from.mayRemove) {
            withTimeoutOrNull(REMOVAL_WAIT_MILLIS) { snapshotFlow { currentIds }.first { from.id !in it } }
        }
        origin = null
        withFrameNanos { }

        when (from.kind) {
            FocusOrigin.Kind.Card -> {
                val ids = currentIds
                val target = when {
                    from.id in ids -> ids.indexOf(from.id)
                    ids.isEmpty() -> -1
                    else -> from.index.coerceAtMost(ids.lastIndex)
                }
                if (target < 0) {
                    runCatching { groupRequesters[ALL_KEY]?.requestFocus() }
                } else {
                    gridState.scrollToItem(target)
                    withFrameNanos { }
                    runCatching { cardRequesters[ids[target]]?.requestFocus() }
                }
            }
            FocusOrigin.Kind.Group -> {
                val requester = groupRequesters[from.id] ?: groupRequesters[ALL_KEY]
                runCatching { requester?.requestFocus() }
            }
        }
    }
}

/**
 * One line under the title for what the last write left behind. A reorder that
 * stopped halfway is said in so many words; a removal that did complete is said
 * once and then goes.
 */
@Composable
private fun Notice(state: FavoritesState, onShown: () -> Unit, onRemovalShown: () -> Unit) {
    val (text, isError) = when {
        state.offline -> stringResource(R.string.feature_favorites_offline) to false
        state.reorderNotice == ReorderNotice.NotFullyApplied ->
            stringResource(R.string.feature_favorites_reorder_not_fully_applied) to true
        state.reorderNotice == ReorderNotice.NotApplied ->
            stringResource(R.string.feature_favorites_reorder_not_applied) to true
        state.writeError != null -> stringResource(R.string.feature_favorites_write_failed) to true
        // Stays while it is true; its Retry is the last entry of the group column,
        // where the remote reaches it (S11-05).
        state.partial -> stringResource(R.string.feature_favorites_tv_partial) to true
        state.error != null -> stringResource(R.string.feature_favorites_refresh_failed) to true
        state.removalDone != null ->
            stringResource(R.string.feature_favorites_removed_everywhere, state.removalDone.name) to false
        else -> return
    }

    // No button to dismiss it: one more focus target in the way of the grid. It
    // stays long enough to be read at three metres, then goes.
    LaunchedEffect(text) {
        if (state.offline || (state.partial && state.writeError == null && state.reorderNotice == null)) {
            return@LaunchedEffect
        }
        delay(NOTICE_MILLIS)
        if (state.removalDone != null) onRemovalShown() else onShown()
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isError) LumoColors.Error else LumoColors.OnDarkMuted,
        modifier = Modifier
            .widthIn(max = NOTICE_WIDTH)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(NOTICE_TAG),
    )
}

@Composable
private fun GroupColumn(
    state: FavoritesState,
    requesters: MutableMap<String, FocusRequester>,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onOptions: (index: Int, FavoriteGroup) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
        contentPadding = PaddingValues(LumoSpacing.sm),
        modifier = Modifier
            .width(GROUP_COLUMN_WIDTH)
            .fillMaxHeight(),
    ) {
        item(key = ALL_KEY) {
            GroupItem(
                label = stringResource(R.string.feature_favorites_all),
                selected = state.isAllView,
                requesterKey = ALL_KEY,
                requesters = requesters,
                onClick = { onSelect(null) },
                // The column has ends: `UP` from its head does not wander to the rail.
                blockUp = true,
            )
        }
        items(state.groups, key = { it.id }) { group ->
            GroupItem(
                label = group.displayName(),
                selected = group.id == state.selectedGroupId,
                requesterKey = group.id,
                requesters = requesters,
                onClick = { onSelect(group.id) },
                onLongClick = { onOptions(state.groups.indexOf(group), group) },
            )
        }
        item(key = NEW_KEY) {
            GroupItem(
                label = "+ " + stringResource(R.string.feature_favorites_new_group),
                selected = false,
                requesterKey = NEW_KEY,
                requesters = requesters,
                onClick = onCreate,
                blockDown = !state.partial,
            )
        }
        // The partial notice's Retry (S11-05), at the end of the column the remote
        // already walks: a button beside the notice would be above everything the
        // focus can reach from.
        if (state.partial) {
            item(key = RETRY_KEY) {
                GroupItem(
                    label = stringResource(R.string.feature_favorites_retry),
                    selected = false,
                    requesterKey = RETRY_KEY,
                    requesters = requesters,
                    onClick = onRetry,
                    blockDown = true,
                )
            }
        }
    }
}

private const val RETRY_KEY = "retry"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupItem(
    label: String,
    selected: Boolean,
    requesterKey: String,
    requesters: MutableMap<String, FocusRequester>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    blockUp: Boolean = false,
    blockDown: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    DisposableEffect(requesterKey, requester) {
        requesters[requesterKey] = requester
        onDispose { if (requesters[requesterKey] === requester) requesters.remove(requesterKey) }
    }

    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = when {
            focused -> LumoColors.OnAccent
            selected -> LumoColors.Accent
            else -> LumoColors.OnDarkMuted
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(
                when {
                    focused -> LumoColors.Accent
                    selected -> LumoColors.SurfaceRaised
                    else -> LumoColors.Surface
                },
            )
            // `combinedClickable` alone: focusable, `OK` selects, holding `OK` opens
            // the options. A `focusable()` beside it would be a second target.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.feature_favorites_group_actions) },
                onClick = onClick,
            )
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

@Composable
private fun Grid(
    favorites: List<FavoriteChannel>,
    gridState: LazyGridState,
    requesters: MutableMap<String, FocusRequester>,
    leftTarget: () -> FocusRequester,
    arrivalId: String?,
    onPlay: (Channel) -> Unit,
    onOptions: (index: Int, FavoriteChannel) -> Unit,
) {
    // Once per arrival. Keyed on nothing that changes while the screen is up: a
    // favourite removed from the phone must not yank the focus across the grid.
    LaunchedEffect(Unit) {
        val index = favorites.indexOfFirst { it.channel.id == arrivalId }.coerceAtLeast(0)
        gridState.scrollToItem(index)
        withFrameNanos { }
        runCatching { requesters[favorites[index].channel.id]?.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        // Room for the focus scale: a lazy grid clips to its bounds.
        contentPadding = PaddingValues(LumoSpacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        // By channel: in All favourites the membership on show may change while the
        // channel stays, and the focus follows the channel.
        itemsIndexed(favorites, key = { _, favorite -> favorite.channel.id }) { index, favorite ->
            val requester = remember { FocusRequester() }
            DisposableEffect(favorite.channel.id, requester) {
                requesters[favorite.channel.id] = requester
                onDispose { if (requesters[favorite.channel.id] === requester) requesters.remove(favorite.channel.id) }
            }
            ChannelCard(
                channel = favorite.channel,
                focusRequester = requester,
                // Not resolved: nothing to play; `OK` opens its options, where it
                // can be removed (S11-05).
                onPlay = { if (favorite.resolved) onPlay(favorite.channel) else onOptions(index, favorite) },
                onOptions = { onOptions(index, favorite) },
                // The grid has ends too: `UP` from the first row and `DOWN` from the
                // last stay in it rather than jumping to the rail.
                firstRow = index < GRID_COLUMNS,
                lastRow = index / GRID_COLUMNS == favorites.lastIndex / GRID_COLUMNS,
                leftTarget = leftTarget.takeIf { returnsLeftToGroup(index) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    focusRequester: FocusRequester,
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    firstRow: Boolean,
    lastRow: Boolean,
    leftTarget: (() -> FocusRequester)?,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .focusRequester(focusRequester)
            .focusProperties {
                if (firstRow) up = FocusRequester.Cancel
                if (lastRow) down = FocusRequester.Cancel
                leftTarget?.let { left = it() }
            }
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onOptions,
                onLongClickLabel = stringResource(R.string.feature_favorites_channel_actions),
                onClick = onPlay,
            )
            .padding(LumoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs, Alignment.CenterVertically),
    ) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        channel.number?.let { number ->
            Text(
                text = "%03d".format(number),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = if (focused) LumoColors.Accent else LumoColors.OnDarkMuted,
            )
        }
    }
}

// ---- dialogs ---------------------------------------------------------------------

/** A dialog window sized for three metres; the remote stays inside, `BACK` closes. */
@Composable
private fun TvDialog(onDismiss: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LumoColors.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(DIALOG_WIDTH)
                    .clip(LumoTvShapes.large)
                    .background(LumoColors.Surface)
                    .padding(LumoSpacing.xl)
                    .testTag(tag),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            ) { content() }
        }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = LumoColors.OnDark,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Options as a column of buttons, the first focused on arrival. */
@Composable
private fun OptionColumn(options: List<Pair<String, () -> Unit>>) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        options.forEachIndexed { index, (label, onClick) ->
            LumoTvButton(
                text = label,
                onClick = onClick,
                primary = index == 0,
                focusRequester = if (index == 0) first else null,
            )
        }
    }
}

@Composable
private fun CardOptions(
    favorite: FavoriteChannel,
    state: FavoritesState,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit,
    onOrganize: () -> Unit,
    onRemoveFromGroup: () -> Unit,
    onRemoveEverywhere: () -> Unit,
) {
    val options = buildList {
        if (state.isAllView) {
            add(stringResource(R.string.feature_favorites_organize) to onOrganize)
            add(stringResource(R.string.feature_favorites_remove_everywhere) to onRemoveEverywhere)
        } else {
            if (state.canMoveFavorite(favorite, -1)) {
                add(stringResource(R.string.feature_favorites_move_before) to { onMove(-1) })
            }
            if (state.canMoveFavorite(favorite, 1)) {
                add(stringResource(R.string.feature_favorites_move_after) to { onMove(1) })
            }
            add(stringResource(R.string.feature_favorites_organize) to onOrganize)
            add(stringResource(R.string.feature_favorites_remove_from_group) to onRemoveFromGroup)
        }
        add(stringResource(R.string.feature_favorites_cancel) to onDismiss)
    }
    TvDialog(onDismiss = onDismiss, tag = CARD_OPTIONS_TAG) {
        DialogTitle(favorite.channel.name)
        if (!state.canWrite && state.offline) {
            Text(
                text = stringResource(R.string.feature_favorites_offline),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
            )
        }
        OptionColumn(options)
    }
}

@Composable
private fun GroupOptions(
    group: FavoriteGroup,
    state: FavoritesState,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val options = buildList {
        add(stringResource(R.string.feature_favorites_rename) to onRename)
        if (state.canMoveGroup(group, -1)) add(stringResource(R.string.feature_favorites_move_before) to { onMove(-1) })
        if (state.canMoveGroup(group, 1)) add(stringResource(R.string.feature_favorites_move_after) to { onMove(1) })
        // The default group offers no delete: the server refuses it.
        if (!group.isDefault) add(stringResource(R.string.feature_favorites_delete) to onDelete)
        add(stringResource(R.string.feature_favorites_cancel) to onDismiss)
    }
    TvDialog(onDismiss = onDismiss, tag = GROUP_OPTIONS_TAG) {
        DialogTitle(group.displayName())
        if (group.isDefault) {
            Text(
                text = stringResource(R.string.feature_favorites_default_not_deletable),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
            )
        }
        OptionColumn(options)
    }
}

/**
 * Organise with the remote: one row per group, `OK` ticks or unticks it — one
 * call each. The last group cannot be unticked here; the dialog says why.
 */
@Composable
private fun OrganizeDialog(
    channel: Channel,
    state: FavoritesState,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val memberOf = state.membershipsOf(channel.id).mapTo(HashSet()) { it.groupId }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    TvDialog(onDismiss = onDismiss, tag = ORGANIZE_TAG) {
        DialogTitle(stringResource(R.string.feature_favorites_organize_title, channel.name))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
            items(state.groups, key = { it.id }) { group ->
                MembershipRow(
                    label = group.displayName(),
                    checked = group.id in memberOf,
                    onClick = { onToggle(group.id, group.id !in memberOf) },
                    modifier = if (group.id == state.groups.first().id) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        if (state.organizeNotice == OrganizeNotice.LastGroup) {
            Text(
                text = stringResource(R.string.feature_favorites_organize_last_group),
                style = MaterialTheme.typography.bodyLarge,
                color = LumoColors.Error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            LumoTvButton(text = stringResource(R.string.feature_favorites_done), onClick = onDismiss)
            if (state.busy) {
                Text(
                    text = stringResource(R.string.feature_favorites_saving),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
        }
    }
}

@Composable
private fun MembershipRow(label: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A glyph rather than a Material checkbox, as in Live's group sheet.
        Text(
            text = if (checked) "✓" else "·",
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) LumoColors.OnAccent else LumoColors.OnDarkMuted,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) LumoColors.OnAccent else LumoColors.OnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "Remove from all my favourites" (S11-E04): the question with **Cancel focused**,
 * then — when not everything went — the verdict, with the groups still holding the
 * channel and a Retry for those only. Close is focused there too: it changes
 * nothing, and it is never presented as a success (FO-07).
 */
@Composable
private fun RemovalDialog(
    removal: GlobalRemoval,
    state: FavoritesState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = removal.channel.name
    when (val phase = removal.phase) {
        RemovalPhase.Confirming, RemovalPhase.Running -> {
            val count = state.membershipsOf(removal.channel.id).size.coerceAtLeast(1)
            LumoTvConfirmDialog(
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
                onConfirm = onConfirm,
                onCancel = onDismiss,
                busy = phase == RemovalPhase.Running,
            )
        }

        is RemovalPhase.Incomplete -> Outcome(
            title = stringResource(R.string.feature_favorites_removal_incomplete_title),
            body = pluralStringResource(
                R.plurals.feature_favorites_removal_incomplete_body,
                phase.remaining.size,
                phase.remaining.size,
                groupNames(state.groupsOf(phase.remaining)),
            ),
            onRetry = onRetry,
            onClose = onDismiss,
        )

        is RemovalPhase.Unconfirmed -> Outcome(
            title = stringResource(R.string.feature_favorites_removal_unconfirmed_title),
            body = stringResource(R.string.feature_favorites_removal_unconfirmed_body),
            onRetry = onRetry,
            onClose = onDismiss,
        )

        is RemovalPhase.NotStarted -> Outcome(
            title = stringResource(R.string.feature_favorites_removal_not_started_title),
            body = stringResource(
                if (phase.offline) {
                    R.string.feature_favorites_removal_offline_body
                } else {
                    R.string.feature_favorites_removal_not_started_body
                },
            ),
            onRetry = onRetry,
            onClose = onDismiss,
        )
    }
}

@Composable
private fun Outcome(title: String, body: String, onRetry: () -> Unit, onClose: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { close.requestFocus() }
    }
    TvDialog(onDismiss = onClose, tag = OUTCOME_TAG) {
        DialogTitle(title)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
            LumoTvButton(text = stringResource(R.string.feature_favorites_retry), onClick = onRetry)
            LumoTvButton(
                text = stringResource(R.string.feature_favorites_close),
                onClick = onClose,
                primary = true,
                focusRequester = close,
            )
        }
    }
}

/**
 * A name typed with the remote: the field takes the focus, `OK` on it raises the
 * keyboard, `DOWN` reaches the buttons.
 */
@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { field.requestFocus() }
    }

    TvDialog(onDismiss = onDismiss, tag = NAME_TAG) {
        DialogTitle(title)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .lumoTvFocus(focused, shape = LumoTvShapes.small)
                .clip(LumoTvShapes.small)
                .background(LumoColors.SurfaceRaised)
                .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        ) {
            if (name.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_favorites_group_name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LumoColors.OnDark),
                cursorBrush = SolidColor(LumoColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
            LumoTvButton(
                text = confirmLabel,
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                primary = true,
                enabled = name.isNotBlank(),
            )
            LumoTvButton(text = stringResource(R.string.feature_favorites_cancel), onClick = onDismiss)
        }
    }
}

/**
 * Nothing starred in this source. A television stars a channel in Live, so the
 * message names that screen and its button goes there — and gives this state a
 * focus target.
 */
@Composable
private fun Empty(onOpenLive: () -> Unit) {
    val button = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { button.requestFocus() } }

    Column(
        modifier = Modifier
            .padding(top = LumoSpacing.lg)
            .widthIn(max = MESSAGE_WIDTH),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_favorites_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
        )
        Text(
            text = stringResource(R.string.feature_favorites_tv_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
        LumoTvButton(
            text = stringResource(R.string.feature_favorites_tv_open_live),
            onClick = onOpenLive,
            primary = true,
            focusRequester = button,
            modifier = Modifier.padding(top = LumoSpacing.sm, start = LumoSpacing.xs),
        )
    }
}

internal const val ALL_KEY = "favorites-all"

/** The key of the group column's entry for [selectedGroupId]: "All favourites" when null. */
internal fun groupKeyOf(selectedGroupId: String?): String = selectedGroupId ?: ALL_KEY

/**
 * Whether `LEFT` from the card at [index] leaves the grid for the group column:
 * the first column only. From the others, `LEFT` is the card beside it.
 */
internal fun returnsLeftToGroup(index: Int): Boolean = index % GRID_COLUMNS == 0
private const val NEW_KEY = "favorites-new-group"
internal const val CARD_OPTIONS_TAG = "favorites-card-options"
internal const val GROUP_OPTIONS_TAG = "favorites-group-options"
internal const val ORGANIZE_TAG = "favorites-organize"
internal const val OUTCOME_TAG = "favorites-removal-outcome"
internal const val NAME_TAG = "favorites-name"
internal const val NOTICE_TAG = "favorites-notice"

// Three columns beside the group column: a name stays readable at three metres.
private const val GRID_COLUMNS = 3
private val CARD_HEIGHT = 150.dp
private val GROUP_COLUMN_WIDTH = 180.dp
private val MESSAGE_WIDTH = 900.dp
private val NOTICE_WIDTH = 1100.dp
private val DIALOG_WIDTH = 640.dp
private const val NOTICE_MILLIS = 8_000L
private const val REMOVAL_WAIT_MILLIS = 1_500L
private const val DIALOG_SETTLE_MILLIS = 150L
