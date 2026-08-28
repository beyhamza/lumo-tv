package tv.lumo.android.feature.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.SubcomposeAsyncImage
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.designsystem.component.LumoFavoriteGroupChoice
import tv.lumo.android.core.designsystem.component.LumoFavoriteGroupSheet
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The channel list (US-08).
 *
 * <h2>Fifteen thousand channels is an ordinary source</h2>
 *
 * Which is why the list is a `LazyColumn` fed by Paging 3 reading windows
 * straight out of SQLite, and never a `List<Channel>`. Materialising a catalogue
 * that size allocates on every emission and drops frames on a phone; the story
 * asks for it to stay smooth past five hundred, and the way to be sure of that is
 * never to hold more than a screenful.
 *
 * <h2>The offline indicator is small on purpose</h2>
 *
 * US-08 asks for a *discreet* one, and it is right to: the list works offline, so
 * the state is normal rather than a failure. A banner would say "something is
 * wrong" about a screen that is doing exactly what it was built to do. What would
 * be wrong is saying nothing — a catalogue silently a week old is the failure a
 * user cannot diagnose.
 *
 * <h2>Logos are the user's own, or nothing</h2>
 *
 * `tvg-logo` from their playlist. Lumo ships no bundled artwork and no fallback
 * image (AGENTS.md §1), so a channel with no logo gets its initial — never a
 * picture of ours standing in for one of theirs.
 */
@Composable
fun LiveMobileScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state.step) {
            LiveStep.Loading -> Centered { CircularProgressIndicator() }

            LiveStep.NoSource -> Message(
                title = stringResource(R.string.feature_live_no_source_title),
                body = stringResource(R.string.feature_live_no_source_body),
            )

            LiveStep.NotReadyYet -> Message(
                title = stringResource(R.string.feature_live_not_ready_title),
                body = stringResource(R.string.feature_live_not_ready_body),
            )

            LiveStep.Browsing -> {
                Header(state = state, onRefresh = viewModel::refresh)

                Categories(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = viewModel::onCategorySelected,
                )

                // A favourite write needs the network, and nothing is queued for
                // later: US-12 asks for an offline change to be refused out loud
                // rather than silently lost. Said here, next to the list the
                // change was meant for, and dismissed by tapping it.
                state.favoriteError?.let {
                    Text(
                        text = stringResource(R.string.feature_live_favorite_failed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = viewModel::onFavoriteErrorShown)
                            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.xs),
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        count = channels.itemCount,
                        key = channels.itemKey { it.id },
                    ) { index ->
                        // Null is a placeholder Paging has not loaded yet. It is
                        // drawn as a row of the right height so the scrollbar
                        // keeps its size on a fifteen-thousand-channel list.
                        val channel = channels[index]
                        ChannelRow(
                            channel = channel,
                            favorited = channel != null && state.isFavorited(channel.id),
                            onPlay = onPlay,
                            onFavorite = viewModel::onFavoriteClicked,
                            onFavoriteLongPress = viewModel::onFavoriteLongPressed,
                        )
                    }
                }
            }
        }
    }

    state.sheetChannel?.let { channel ->
        val inGroups = state.groupsOf(channel.id)
        LumoFavoriteGroupSheet(
            groups = state.groups.map { group ->
                LumoFavoriteGroupChoice(
                    id = group.id,
                    label = group.displayName(),
                    checked = group.id in inGroups,
                )
            },
            createLabel = stringResource(R.string.feature_live_favorite_new_group),
            createPlaceholder = stringResource(R.string.feature_live_favorite_group_name),
            confirmLabel = stringResource(R.string.feature_live_favorite_group_create),
            onToggle = { groupId, checked ->
                viewModel.onGroupToggled(channel.id, groupId, checked)
            },
            onCreate = { name -> viewModel.onGroupCreated(channel.id, name) },
            onDismiss = viewModel::onGroupSheetDismissed,
        )
    }
}

/**
 * What to call a group on screen.
 *
 * The server has to name the group it creates on the first add, and names it
 * `Favorites`, in English — a user-visible string in one language. `is_default`
 * is what lets a client translate it, and the second half of the condition is
 * what stops the translation overriding the user: once they have renamed the
 * group, their name wins, flag or no flag.
 */
@Composable
private fun FavoriteGroup.displayName(): String =
    if (isDefault && name == SERVER_DEFAULT_GROUP_NAME) {
        stringResource(R.string.feature_live_favorite_default_group)
    } else {
        name
    }

/** The name the server gives the default group, verbatim. */
private const val SERVER_DEFAULT_GROUP_NAME = "Favorites"

@Composable
private fun Header(state: LiveState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_live_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Discreet, and only when it is true: the muted label rather than a
            // banner, because serving the cache is what this screen does well.
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_live_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.refreshing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(start = LumoSpacing.sm)
                        .size(LumoSpacing.md),
                )
            } else {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.feature_live_refresh))
                }
            }
        }
    }

    if (state.refreshFailed) {
        Text(
            text = stringResource(R.string.feature_live_refresh_failed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = LumoSpacing.md),
        )
    }
}

/**
 * The categories, with how many channels each holds.
 *
 * A horizontal row rather than a side list: a phone is narrow, the names are
 * long, and the first thing someone does here is scan them. "All" comes first and
 * is what the screen opens on — a catalogue that opens on somebody's first
 * category is a catalogue that hides the rest.
 */
@Composable
private fun Categories(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        modifier = Modifier.padding(bottom = LumoSpacing.sm),
    ) {
        item {
            CategoryChip(
                label = stringResource(R.string.feature_live_all_categories),
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                // The count comes from the server and is null when it did not
                // count. An absent count is a chip without a number, not a zero.
                label = category.channelCount
                    ?.let { stringResource(R.string.feature_live_category_count, category.name, it) }
                    ?: category.name,
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(LumoShapes.small)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/**
 * One channel.
 *
 * `number` is the provider's and **not** `position`: position is a display index
 * reassigned at every ingestion, while this is the number the user knows by heart.
 * Null for the many playlists that carry none, and an absent number is a blank
 * column rather than a zero.
 *
 * `quality` is echoed exactly as the source wrote it. A badge reading `fhd` in
 * lower case is a source that wrote `fhd`; normalising it here would be this layer
 * deciding what the provider meant.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: Channel?,
    favorited: Boolean,
    onPlay: (channelId: String, name: String?) -> Unit,
    onFavorite: (Channel) -> Unit,
    onFavoriteLongPress: (Channel) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Null is a row Paging has not loaded yet: it draws, and it does
            // nothing when tapped, rather than opening a player for no channel.
            .clickable(enabled = channel != null) {
                channel?.let { onPlay(it.id, it.name) }
            }
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel?.number?.toString().orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LumoSpacing.xl),
        )

        Logo(channel)

        Text(
            text = channel?.name.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        channel?.quality?.let { quality ->
            Text(
                text = quality,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, LumoShapes.small)
                    .padding(horizontal = LumoSpacing.xs, vertical = LumoSpacing.xxs),
            )
        }

        if (channel != null) {
            Heart(
                favorited = favorited,
                onClick = { onFavorite(channel) },
                onLongClick = { onFavoriteLongPress(channel) },
            )
        }
    }
}

/**
 * The favourite control (US-12).
 *
 * <h2>A glyph, because the product has no icon set</h2>
 *
 * The same reason `LumoMobileNavBar` is a row of labels rather than a Material
 * bar: a placeholder icon is a design decision made by accident. A filled and an
 * outlined heart are two characters that carry the state honestly until there is
 * a real icon to replace them with.
 *
 * <h2>Its own target, and its own gestures</h2>
 *
 * The row opens the player; this opens nothing. Long-pressing it opens the group
 * picker — the row's own long press is free, and a person aiming at a heart to
 * choose a group should not have to find a different part of the row to do it.
 *
 * `contentDescription` says what a tap *does*, not what is drawn: TalkBack
 * reading "heart" tells somebody nothing about whether the channel is starred.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Heart(favorited: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val description = if (favorited) {
        stringResource(R.string.feature_live_favorite_remove)
    } else {
        stringResource(R.string.feature_live_favorite_add)
    }

    Text(
        text = if (favorited) "♥" else "♡",
        style = MaterialTheme.typography.titleMedium,
        color = if (favorited) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(LumoShapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = description,
            )
            // A 48dp target: the glyph is small and the row it sits in is a
            // scrolling list, which is where a near-miss becomes a channel
            // launching instead.
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .wrapContentSize()
            .semantics { contentDescription = description },
    )
}

/**
 * The logo the user's own playlist advertises, or their channel's initial.
 *
 * **Lumo ships no fallback artwork** (AGENTS.md §1). A channel with no logo gets a
 * letter, never a bundled image of ours standing in for one of theirs.
 *
 * <h2>A logo that does not load looks like a channel with no logo</h2>
 *
 * And that is deliberate, because it will happen often. Most panels advertise
 * their logos over `http`, and this application does not permit cleartext — so on
 * a real source many of these will fail. An empty grey square for each would read
 * as a broken list; the initial reads as a channel whose provider gave no picture,
 * which is the same thing from the user's side and true from ours.
 *
 * The cleartext question itself is bigger than a logo — the streams are `http`
 * too — and is written up against `S2-11`, which is where it decides whether
 * anything plays at all.
 */
@Composable
private fun Logo(channel: Channel?) {
    val size = 32.dp
    val shape = LumoShapes.small

    if (channel?.logoUrl == null) {
        Initial(channel, size, shape)
        return
    }

    SubcomposeAsyncImage(
        model = channel.logoUrl,
        // Decorative: the name is right next to it, and a screen reader reading
        // "logo of X" before "X" is noise.
        contentDescription = null,
        loading = { Initial(channel, size, shape) },
        error = { Initial(channel, size, shape) },
        modifier = Modifier
            .size(size)
            .clip(shape),
    )
}

@Composable
private fun Initial(channel: Channel?, size: Dp, shape: Shape) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel?.name?.take(1)?.uppercase().orEmpty(),
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
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
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
