package tv.lumo.android.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * The channels, on a television (US-08).
 *
 * The focus map for this screen — what has focus on arrival and where every
 * direction leads from every zone — is `docs/design/tv-focus-map.md`. It is a
 * deliverable of `S2-13` in its own right, and the reason is the commonest defect
 * in television applications: a control no sequence of key presses reaches.
 *
 * <h2>Horizontal, because a television is</h2>
 *
 * Categories run across the top and channels fill a horizontal grid below — rows
 * that scroll sideways, not a column that scrolls down. A vertical list on a
 * 16:9 panel wastes two thirds of the width and turns every journey into a long
 * run of `DOWN` presses.
 *
 * A grid rather than one rail per category, and that is a real choice: rails look
 * more like a television and cap what each one holds, and a capped rail is a rail
 * whose eight-hundredth channel cannot be reached at all. The category strip picks
 * the shelf; the grid pages through **all** of it (US-08 asks for fluid past five
 * hundred, and Paging reads windows out of SQLite either way).
 *
 * <h2>Focus is the cursor, and it is never only a colour</h2>
 *
 * `Modifier.lumoTvFocus` puts scale, border and elevation together, because any
 * one of them alone fails on some real setup: colour washes out on a badly
 * calibrated panel or for a colour-blind viewer, scale is easy to miss in a dense
 * grid, elevation disappears over bright artwork.
 *
 * Selection is drawn differently from focus. Focus is where the remote is;
 * selection is which category is open. Collapsing them makes the screen look as
 * though it has navigated when the viewer is only looking around.
 */
@Composable
fun LiveTvScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            // Not on the leading edge: the rail is there and has already paid for
            // that margin. Doubling it would be wasted width on the one axis a
            // television has least of after the rail takes its share.
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

            LiveStep.NotReadyYet -> Message(
                title = stringResource(R.string.feature_live_not_ready_title),
                body = stringResource(R.string.feature_live_not_ready_body),
            )

            LiveStep.Browsing -> Browsing(
                state = state,
                channels = channels,
                onSelectCategory = viewModel::onCategorySelected,
                onPlay = onPlay,
            )
        }
    }
}

@Composable
private fun Browsing(
    state: LiveState,
    channels: LazyPagingItems<Channel>,
    onSelectCategory: (String?) -> Unit,
    onPlay: (channelId: String, name: String?) -> Unit,
) {
    // Arrival focus. The grid rather than the category strip: somebody who turns
    // the television on wants a channel, and the shelf they are already on is the
    // right one. Reaching the categories is one `UP` away; reaching a channel from
    // the categories would have been one `DOWN` plus a decision nobody asked for.
    val firstChannel = remember { FocusRequester() }

    Column(
        modifier = Modifier.padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_live_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
            )
            // The offline indicator, discreet here too — and it has to be legible
            // at three metres, which is why it is the label scale rather than a
            // caption nobody would read (US-08).
            if (state.origin == DataOrigin.Cache) {
                Text(
                    text = stringResource(R.string.feature_live_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
        }

        Categories(
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            onSelect = onSelectCategory,
        )

        LazyHorizontalGrid(
            // Two rows: three would put the bottom one under the overscan margin
            // on a 1080p panel once the cards are large enough to read at three
            // metres, and a row nobody can see is a row nobody can focus.
            rows = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            contentPadding = PaddingValues(LumoSpacing.sm),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = channels.itemCount,
                key = channels.itemKey { it.id },
            ) { index ->
                ChannelCard(
                    channel = channels[index],
                    onPlay = onPlay,
                    modifier = if (index == 0) {
                        Modifier.focusRequester(firstChannel)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * The category strip, above the grid.
 *
 * `UP` from the grid lands here, `DOWN` goes back. Every category is reachable
 * with `LEFT`/`RIGHT`, and "All" is first because it is what the screen opens on:
 * a catalogue that starts inside somebody's first category is a catalogue that
 * hides the rest.
 */
@Composable
private fun Categories(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        contentPadding = PaddingValues(LumoSpacing.xs),
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
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            focused -> LumoColors.OnAccent
            selected -> LumoColors.Accent
            else -> LumoColors.OnDarkMuted
        },
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoShapes.small)
            .clip(LumoShapes.small)
            .background(
                when {
                    focused -> LumoColors.Accent
                    selected -> LumoColors.SurfaceRaised
                    else -> LumoColors.Surface
                },
            )
            // `clickable` makes it focusable and binds the centre key at once.
            // Adding `focusable()` as well would put two focus targets on one chip.
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/**
 * One channel, as a card the remote can land on.
 *
 * A null card is a window Paging has not loaded yet. It is drawn at full size so
 * the grid keeps its shape, and it is **not** focusable: a card the D-pad can stop
 * on and that has no channel behind it is a dead end that appears and disappears
 * as the user scrolls.
 */
@Composable
private fun ChannelCard(
    channel: Channel?,
    onPlay: (channelId: String, name: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .clickable(
                enabled = channel != null,
                interactionSource = interactionSource,
                indication = null,
            ) { channel?.let { onPlay(it.id, it.name) } }
            .padding(LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(channel)

        Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs)) {
            Text(
                text = channel?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The provider's own number, and never `position`: this is the one a
            // viewer knows by heart and would type on a remote if they could.
            channel?.number?.let { number ->
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }
        }
    }
}

/**
 * The logo the user's own playlist advertises, or the channel's initial.
 *
 * Lumo ships no artwork (AGENTS.md §1), and a logo that fails to load — many are
 * served over `http`, which ADR 0008 permits but a provider can still refuse —
 * falls back to the same letter rather than to an empty square.
 */
@Composable
private fun Logo(channel: Channel?) {
    val size = 64.dp

    if (channel?.logoUrl == null) {
        Initial(channel, size)
        return
    }

    SubcomposeAsyncImage(
        model = channel.logoUrl,
        contentDescription = null,
        loading = { Initial(channel, size) },
        error = { Initial(channel, size) },
        modifier = Modifier.size(size).clip(LumoShapes.small),
    )
}

@Composable
private fun Initial(channel: Channel?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(LumoShapes.small)
            .background(LumoColors.SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel?.name?.take(1)?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDarkMuted,
        )
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

/**
 * Card size, chosen for three metres rather than for density.
 *
 * Wide enough for two lines of a channel name at the TV body scale, tall enough
 * that two rows fill the panel without either falling into the overscan margin.
 */
private val CARD_WIDTH = 360.dp
private val CARD_HEIGHT = 120.dp
