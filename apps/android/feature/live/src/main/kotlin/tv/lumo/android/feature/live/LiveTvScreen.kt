package tv.lumo.android.feature.live

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.SubcomposeAsyncImage
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.designsystem.component.LumoFavoriteGroupChoice
import tv.lumo.android.core.designsystem.component.LumoMockMissingData
import tv.lumo.android.core.designsystem.component.LumoTvFavoriteGroupSheet
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * `TV3 — Grille de chaînes`: a preview panel on the left that follows the
 * focus, a four-column grid on the right, the category chips above it and one
 * line of key hints at the bottom.
 *
 * <h2>What the panel can and cannot say</h2>
 *
 * The canvas gives it a programme in progress, its time slot and a progress
 * bar. The product has a channel and a category; the programme guide is in the
 * contract (`GET /channels/{id}/epg`) but the server's `epg` package is empty in
 * v1 (`apps/api/AGENTS.md` §2). So the panel draws the slot as the canvas does
 * and labels the programme `[mock] données manquantes` — the honest version of
 * the design, and the first thing to replace when the guide arrives.
 *
 * <h2>Focus</h2>
 *
 * Arrival lands on the first channel, a return from the player on the channel
 * that was being watched (US-10). `LEFT` from the first column reaches the rail;
 * `UP` from the first row reaches the chips. The preview follows whichever card
 * has the focus, and never takes it: it is a mirror, not a control.
 */
@Composable
fun LiveTvScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    modifier: Modifier = Modifier,
    returnedChannelId: String? = null,
    onReturnHandled: () -> Unit = {},
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        when (state.step) {
            LiveStep.Loading -> Centered {
                Text(
                    text = stringResource(R.string.feature_live_tv_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

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

            LiveStep.Browsing -> Browsing(
                state = state,
                channels = channels,
                onSelectCategory = viewModel::onCategorySelected,
                onSelectGroup = viewModel::onGroupSelected,
                onSelectRecent = viewModel::onRecentSelected,
                onFavorite = viewModel::onFavoriteLongPressed,
                onPlay = onPlay,
                returnedChannelId = returnedChannelId,
                onReturnHandled = onReturnHandled,
            )
        }

        state.sheetChannel?.let { channel ->
            val inGroups = state.groupsOf(channel.id)
            LumoTvFavoriteGroupSheet(
                title = channel.name,
                groups = state.groups.map { group ->
                    LumoFavoriteGroupChoice(
                        id = group.id,
                        label = group.displayName(),
                        checked = group.id in inGroups,
                    )
                },
                onToggle = { groupId, checked ->
                    viewModel.onGroupToggled(channel.id, groupId, checked)
                },
                onDismiss = viewModel::onGroupSheetDismissed,
            )
        }
    }
}

@Composable
private fun Browsing(
    state: LiveState,
    channels: LazyPagingItems<Channel>,
    onSelectCategory: (String?) -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectRecent: () -> Unit,
    onFavorite: (Channel) -> Unit,
    onPlay: (channelId: String, name: String?) -> Unit,
    returnedChannelId: String?,
    onReturnHandled: () -> Unit,
) {
    val focusTarget = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var focusIndex by remember { mutableIntStateOf(0) }
    // The channel the panel describes: whichever card holds the focus, and the
    // first one before any card has had it.
    var previewed by remember { mutableStateOf<Channel?>(null) }

    LaunchedEffect(returnedChannelId, channels.itemCount) {
        val target = returnedChannelId ?: return@LaunchedEffect
        val index = channels.itemSnapshotList.items.indexOfFirst { it.id == target }

        if (index < 0) return@LaunchedEffect

        focusIndex = index
        gridState.scrollToItem(index)
        onReturnHandled()
    }

    LaunchedEffect(focusIndex, channels.itemCount) {
        if (channels.itemCount == 0) return@LaunchedEffect
        runCatching { focusTarget.requestFocus() }
    }

    LaunchedEffect(channels.itemCount, state.filter) {
        if (previewed == null || channels.itemSnapshotList.items.none { it.id == previewed?.id }) {
            previewed = channels.itemSnapshotList.items.firstOrNull()
        }
    }

    Column(
        modifier = Modifier.padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_live_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
            )
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_live_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xl),
        ) {
            Preview(
                channel = previewed,
                categoryName = previewed?.categoryId?.let { id ->
                    state.categories.firstOrNull { it.id == id }?.name
                },
                // Three parts in ten, as the canvas divides its width — a fixed
                // width would be right on one panel and wrong on every other.
                modifier = Modifier.weight(PREVIEW_SHARE),
            )

            Column(
                modifier = Modifier.weight(1f - PREVIEW_SHARE),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            ) {
                Filters(
                    groups = state.groupsWithChannels,
                    categories = state.categories,
                    filter = state.filter,
                    hasRecent = state.recent.isNotEmpty(),
                    onSelectCategory = onSelectCategory,
                    onSelectGroup = onSelectGroup,
                    onSelectRecent = onSelectRecent,
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                    state = gridState,
                    contentPadding = PaddingValues(LumoSpacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = channels.itemCount,
                        key = channels.itemKey { it.id },
                    ) { index ->
                        val channel = channels[index]
                        ChannelCard(
                            channel = channel,
                            favorited = channel != null && state.isFavorited(channel.id),
                            onPlay = onPlay,
                            onFavorite = onFavorite,
                            onFocused = { previewed = it },
                            // One requester, moved to whichever card is the target:
                            // the first on arrival, the one just watched on the way
                            // back.
                            modifier = if (index == focusIndex) {
                                Modifier.focusRequester(focusTarget)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.feature_live_tv_grid_hints),
            style = MaterialTheme.typography.labelLarge,
            color = LumoColors.OnDarkMuted,
        )
    }
}

/**
 * The left panel of the canvas: a picture of the channel, its name, what is on,
 * when, and how far along.
 *
 * The picture is the logo, since a television has no still of a live stream;
 * the programme and the slot are the guide the server does not serve yet, said
 * as such rather than invented.
 */
@Composable
private fun Preview(channel: Channel?, categoryName: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(LumoTvShapes.medium)
                .background(LumoColors.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (channel?.logoUrl != null) {
                SubcomposeAsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    loading = { PreviewCaption(channel) },
                    error = { PreviewCaption(channel) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(LumoSpacing.xl),
                )
            } else {
                PreviewCaption(channel)
            }
        }

        Text(
            text = channel?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // « En ce moment : Journal du soir » — the programme is the guide, and
        // the guide is not served yet. The badge stands for the programme and
        // for its slot at once: one badge, not two, in a panel this narrow.
        Text(
            text = stringResource(R.string.feature_live_tv_now, ""),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDark,
        )
        LumoMockMissingData(scale = TV_BADGE_SCALE)

        // « Généralistes · HD » — the category and the quality are real.
        Text(
            text = listOfNotNull(categoryName, channel?.quality).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = LumoColors.OnDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // The progress of a programme nobody knows the length of: the track is
        // drawn, the fill stays at zero.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(LumoTvShapes.pill)
                .background(LumoColors.SurfaceRaised),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = 0f)
                    .height(6.dp)
                    .background(Brush.horizontalGradient(listOf(LumoColors.Accent, LumoColors.AccentViolet))),
            )
        }
    }
}

@Composable
private fun PreviewCaption(channel: Channel?) {
    Text(
        text = stringResource(R.string.feature_live_tv_preview, channel?.name.orEmpty()),
        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
        color = LumoColors.OnDarkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(LumoSpacing.md),
    )
}

@Composable
private fun Filters(
    groups: List<FavoriteGroup>,
    categories: List<Category>,
    filter: CatalogueFilter,
    hasRecent: Boolean,
    onSelectCategory: (String?) -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectRecent: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        contentPadding = PaddingValues(LumoSpacing.xs),
    ) {
        item {
            CategoryChip(
                label = stringResource(R.string.feature_live_all_categories),
                selected = filter is CatalogueFilter.All,
                onClick = { onSelectCategory(null) },
            )
        }
        if (hasRecent) {
            item {
                CategoryChip(
                    label = stringResource(R.string.feature_live_recent),
                    selected = filter is CatalogueFilter.Recent,
                    onClick = onSelectRecent,
                )
            }
        }
        items(groups, key = { "group-" + it.id }) { group ->
            CategoryChip(
                label = group.displayName(),
                selected = (filter as? CatalogueFilter.Group)?.id == group.id,
                onClick = { onSelectGroup(group.id) },
            )
        }
        if (groups.isNotEmpty()) {
            item { Spacer(modifier = Modifier.size(LumoSpacing.lg)) }
        }
        items(categories, key = { "category-" + it.id }) { category ->
            CategoryChip(
                label = category.channelCount
                    ?.let { stringResource(R.string.feature_live_category_count, category.name, it) }
                    ?: category.name,
                selected = (filter as? CatalogueFilter.Category)?.id == category.id,
                onClick = { onSelectCategory(category.id) },
            )
        }
    }
}

/**
 * A chip is selected or focused, and the two are drawn apart: selection is the
 * light pill of the canvas (`Toutes`), focus is the shared outline. Neither is
 * a cyan fill.
 */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            selected -> LumoColors.OnAccent
            focused -> LumoColors.OnDark
            else -> LumoColors.OnDarkMuted
        },
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.pill)
            .clip(LumoTvShapes.pill)
            .background(
                when {
                    selected -> LumoColors.OnDark
                    focused -> LumoColors.SurfaceRaised
                    else -> LumoColors.Surface
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/**
 * One cell of the grid, as the canvas draws it: the name, and the number under
 * it in a monospaced face. The logo lives in the panel — at four columns a
 * card is read by its name, and a logo that small is a smudge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: Channel?,
    favorited: Boolean,
    onPlay: (channelId: String, name: String?) -> Unit,
    onFavorite: (Channel) -> Unit,
    onFocused: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused && channel != null) onFocused(channel)
            }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .combinedClickable(
                enabled = channel != null,
                interactionSource = interactionSource,
                indication = null,
                onLongClick = { channel?.let(onFavorite) },
                onLongClickLabel = stringResource(R.string.feature_live_tv_favorite_hint),
            ) { channel?.let { onPlay(it.id, it.name) } }
            .padding(LumoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs, Alignment.CenterVertically),
    ) {
        // Smaller than body, and two lines: the canvas names its channels in
        // nine characters, real playlists in twenty-five.
        Text(
            text = channel?.name.orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            channel?.number?.let { number ->
                Text(
                    text = "%03d".format(number),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = if (focused) LumoColors.Accent else LumoColors.OnDarkMuted,
                )
            }
            if (favorited) {
                Text(
                    text = "♥",
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.Accent,
                )
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier.padding(LumoSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// Four columns, as the canvas: three metres away a name is still readable at
// this width on a 1080p panel, and a fifth column would not be.
private const val GRID_COLUMNS = 4
private val CARD_HEIGHT = 128.dp

/** The preview panel's share of the content width, as on the canvas. */
private const val PREVIEW_SHARE = 0.30f

/**
 * The badge at the label step, not the TV scale: the preview column is 183 dp
 * on a 1080p panel, and the nineteen monospaced characters of the badge only
 * fit it unscaled. It is read up close by whoever is checking the gap, never
 * from the sofa.
 */
private const val TV_BADGE_SCALE = 1.0f
