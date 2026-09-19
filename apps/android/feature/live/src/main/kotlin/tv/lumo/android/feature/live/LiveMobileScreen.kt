package tv.lumo.android.feature.live

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * The channel list (US-08), laid out as the M4 mock-up draws it
 * (docs/design/canvas, mobile artboard 15): a heading with a round search
 * button, a strip of pill-shaped category chips, and one card per channel —
 * logo on the left, name over "category · quality", chevron on the right.
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
 *
 * <h2>What the mock-up does not show, and is kept anyway</h2>
 *
 * The favourite heart (US-12) is smaller and muted, but still its own 48 dp
 * target: the mock-up wins on looks, not on taking a gesture away. A long press
 * on the whole card opens the group picker too. The refresh button sits beside
 * the search button because the list has one and the mock-up simply did not draw
 * it; and the search button says `[mock]` when tapped, because the screen behind
 * it is still a placeholder (see `MobileDestinations`).
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

            LiveStep.NeedsChoice -> Message(
                title = stringResource(R.string.feature_live_needs_choice_title),
                body = stringResource(R.string.feature_live_needs_choice_body),
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
                            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.xs),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = LumoSpacing.lg,
                        vertical = LumoSpacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                ) {
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
                            categoryName = channel?.categoryId?.let { id ->
                                state.categories.firstOrNull { it.id == id }?.name
                            },
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
 * The heading, with the two round buttons on its right.
 *
 * The search button leads nowhere yet and says so on tap: a notice under the
 * heading, dismissed by tapping it, rather than a snackbar that needs a scaffold
 * this screen does not own.
 */
@Composable
private fun Header(state: LiveState, onRefresh: () -> Unit) {
    var searchNotice by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_live_channels_heading),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.refreshing) {
                Box(modifier = Modifier.size(ROUND_BUTTON), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(LumoSpacing.md),
                    )
                }
            } else {
                RoundButton(
                    glyph = stringResource(R.string.feature_live_glyph_refresh),
                    description = stringResource(R.string.feature_live_refresh),
                    onClick = onRefresh,
                )
            }

            RoundButton(
                glyph = stringResource(R.string.feature_live_glyph_search),
                description = stringResource(R.string.feature_live_search),
                onClick = { searchNotice = true },
            )
        }
    }

    // Discreet, and only when it is true: the muted label rather than a
    // banner, because serving the cache is what this screen does well.
    if (state.origin == DataOrigin.Cache) {
        Notice(
            text = stringResource(R.string.feature_live_offline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (state.refreshFailed) {
        Notice(
            text = stringResource(R.string.feature_live_refresh_failed),
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (searchNotice) {
        Notice(
            text = stringResource(R.string.feature_live_search_mock),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { searchNotice = false },
        )
    }
}

@Composable
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.xs),
    )
}

/** A 48 dp disc on the first surface level, carrying one glyph. */
@Composable
private fun RoundButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(ROUND_BUTTON)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        contentPadding = PaddingValues(horizontal = LumoSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        modifier = Modifier.padding(bottom = LumoSpacing.xs),
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

/**
 * A pill: light ink on the selected one, the first surface level on the rest.
 * Never cyan — that colour means "the remote is here", not "this is open".
 */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = LumoSpacing.md)
            .wrapContentSize(Alignment.Center),
    )
}

/**
 * One channel, as a card.
 *
 * The second line is "category · quality", either half dropped when unknown.
 * `quality` is echoed exactly as the source wrote it: a badge reading `fhd` in
 * lower case is a source that wrote `fhd`, and normalising it here would be this
 * layer deciding what the provider meant. The category name is looked up from the
 * list the chips already hold — nothing new is asked of the view model.
 *
 * The provider's channel number is no longer a column of its own: the mock-up
 * has none, and the number is still the key a remote control types on the
 * television, where it is drawn.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: Channel?,
    categoryName: String?,
    favorited: Boolean,
    onPlay: (channelId: String, name: String?) -> Unit,
    onFavorite: (Channel) -> Unit,
    onFavoriteLongPress: (Channel) -> Unit,
) {
    val subtitle = subtitleOf(categoryName, channel?.quality)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            // Null is a row Paging has not loaded yet: it draws, and it does
            // nothing when tapped, rather than opening a player for no channel.
            .combinedClickable(
                enabled = channel != null,
                onClick = { channel?.let { onPlay(it.id, it.name) } },
                onLongClick = { channel?.let(onFavoriteLongPress) },
            )
            .padding(LumoSpacing.sm + LumoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm + LumoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(channel)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (channel != null) {
            Heart(
                favorited = favorited,
                onClick = { onFavorite(channel) },
                onLongClick = { onFavoriteLongPress(channel) },
            )
        }

        Text(
            text = stringResource(R.string.feature_live_glyph_chevron),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Category · HD", "Category", "HD", or nothing — never a stray separator. */
@Composable
private fun subtitleOf(categoryName: String?, quality: String?): String? = when {
    categoryName != null && quality != null ->
        stringResource(R.string.feature_live_subtitle_join, categoryName, quality)
    else -> categoryName ?: quality
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
 * <h2>Discreet, and still its own target</h2>
 *
 * The mock-up draws no heart on the card, so this one is small and muted — but a
 * gesture that exists is not taken away for a picture. The row opens the player;
 * this opens nothing. Long-pressing it, or the row, opens the group picker.
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
        style = MaterialTheme.typography.bodyLarge,
        color = if (favorited) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = description,
            )
            // A 48dp target: the glyph is small and the row it sits in is a
            // scrolling list, which is where a near-miss becomes a channel
            // launching instead.
            .sizeIn(minWidth = 40.dp, minHeight = 48.dp)
            .wrapContentSize()
            .semantics { contentDescription = description },
    )
}

/**
 * The logo the user's own playlist advertises, or their channel's initial, on a
 * 76 × 48 plate as the mock-up sizes it.
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
 */
@Composable
private fun Logo(channel: Channel?) {
    val shape = LumoShapes.small

    if (channel?.logoUrl == null) {
        Initial(channel, shape)
        return
    }

    SubcomposeAsyncImage(
        model = channel.logoUrl,
        // Decorative: the name is right next to it, and a screen reader reading
        // "logo of X" before "X" is noise.
        contentDescription = null,
        // Fit, not crop: a logo is a mark, and cropping one is cutting somebody's
        // brand in half. The plate colour fills what the logo does not.
        contentScale = ContentScale.Fit,
        loading = { Initial(channel, shape) },
        error = { Initial(channel, shape) },
        modifier = Modifier
            .width(LOGO_WIDTH)
            .height(LOGO_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(LumoSpacing.xs),
    )
}

@Composable
private fun Initial(channel: Channel?, shape: Shape) {
    Box(
        modifier = Modifier
            .width(LOGO_WIDTH)
            .height(LOGO_HEIGHT)
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

private val ROUND_BUTTON = 48.dp
private val LOGO_WIDTH = 76.dp
private val LOGO_HEIGHT = 48.dp
