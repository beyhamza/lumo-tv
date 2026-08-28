package tv.lumo.android.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                                sourceLabel = state.sourceLabel(favorite),
                                onPlay = onPlay,
                            )
                        }
                    }
                }
            }
        }
    }
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
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        modifier = Modifier.padding(bottom = LumoSpacing.sm),
    ) {
        items(groups, key = { it.id }) { group ->
            GroupChip(
                label = group.displayName(),
                selected = group.id == selectedId,
                onClick = { onSelect(group.id) },
            )
        }
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
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
            .clickable(onClick = onClick)
            .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.xs),
    )
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteChannel,
    sourceLabel: String?,
    onPlay: (String, String?) -> Unit,
) {
    val channel = favorite.channel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay(channel.id, channel.name) }
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
            // Which subscription this came from, and only when there is more than
            // one to tell apart. Two channels of the same name from two providers
            // are otherwise the same row, and a group that mixes sources is the
            // point of the feature; under a single-source account the same line
            // would be noise on every row.
            sourceLabel?.let { label ->
                Text(
                    text = stringResource(R.string.feature_favorites_from_source, label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
