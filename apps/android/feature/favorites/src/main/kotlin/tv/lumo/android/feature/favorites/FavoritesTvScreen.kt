package tv.lumo.android.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * "My library", on a television (US-017).
 *
 * The focus map for this screen belongs in `docs/design/tv-focus-map.md`; the
 * rules it follows are the ones written there.
 *
 * <h2>A modest screen, on purpose</h2>
 *
 * The rail gained a "My library" entry, and a rail entry needs a screen behind it
 * — an unbuilt one is a promise, and that is what keeps search out of the rail.
 * This is that screen and no more: **the favourites of the active source, each
 * channel once, and `OK` plays.** No group tabs, no renaming, no reordering: group
 * management on a television is not in this task, and drawing its controls
 * disabled would be drawing things somebody presses to find out.
 *
 * <h2>Each channel once, and in the library's order</h2>
 *
 * Without tabs the groups are shown together, so a channel filed in two of them
 * would appear twice. [FavoritesState.aggregated] goes through the same
 * `core:data` function as the home screen's rail: walking the groups in their
 * order, then the channels in theirs, the first occurrence deciding the place
 * (US-020). What "See all" opens is therefore the home rail, continued — not a
 * second opinion about the order.
 *
 * <h2>The channel grid's card and the channel grid's rules</h2>
 *
 * Four columns, a name in two lines, `LEFT` from the first column to the rail.
 * Somebody who has learnt the channel screen has learnt this one. What is missing
 * is the long press: there it files a favourite into groups, which is the
 * management this screen does not have.
 */
@Composable
fun FavoritesTvScreen(
    onPlay: (channelId: String, name: String?) -> Unit,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites = remember(state) { state.aggregated }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            // Not on the leading edge: the rail is there and has already paid for
            // that margin.
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        Column(
            modifier = Modifier.padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.feature_favorites_tv_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
            )

            when {
                // Distinct from "no favourites": the first Room emission has not
                // arrived. It lasts a frame, and the rail is focusable meanwhile.
                state.loading -> Text(
                    text = stringResource(R.string.feature_favorites_tv_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )

                favorites.isEmpty() -> Empty(onOpenLive)

                else -> {
                    // What the library holds today. The heading is here so that
                    // the day it holds a second shelf, this one already has a name.
                    Text(
                        text = stringResource(R.string.feature_favorites_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = LumoColors.OnDarkMuted,
                    )
                    Grid(favorites = favorites, onPlay = onPlay)
                }
            }
        }
    }
}

@Composable
private fun Grid(
    favorites: List<FavoriteChannel>,
    onPlay: (channelId: String, name: String?) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val focus = remember { FocusRequester() }

    // The channel `OK` was last pressed on. Saved with the back stack entry, so it
    // survives the player: BACK returns to the channel that was launched, not to
    // the head of the grid (US-10's rule, as on the channel screen).
    var launchedId by rememberSaveable { mutableStateOf<String?>(null) }

    val targetIndex = favorites.indexOfFirst { it.channel.id == launchedId }.coerceAtLeast(0)
    val targetId = favorites[targetIndex].channel.id

    // Once per arrival. Keyed on nothing that changes while the screen is up: a
    // favourite removed from the phone must not yank the focus across the grid.
    LaunchedEffect(Unit) {
        // A card the grid has not composed cannot take the focus.
        gridState.scrollToItem(targetIndex)
        // Failing to focus is recoverable — the D-pad still works — and throwing
        // would take the screen down.
        runCatching { focus.requestFocus() }
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
        items(favorites, key = { it.channel.id }) { favorite ->
            ChannelCard(
                channel = favorite.channel,
                onPlay = { id, name ->
                    launchedId = id
                    onPlay(id, name)
                },
                modifier = if (favorite.channel.id == targetId) {
                    Modifier.focusRequester(focus)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    onPlay: (channelId: String, name: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            // `clickable` alone: it makes the card focusable and binds the centre
            // key. A `focusable()` beside it would be a second target (AGENTS.md §6).
            .clickable(interactionSource = interactionSource, indication = null) {
                onPlay(channel.id, channel.name)
            }
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

/**
 * Nothing starred in this source.
 *
 * A television cannot star a channel from here, so the message names the screen
 * where it is done and the gesture that does it — and its button goes there. The
 * button is also what gives this state a focus target: a screen without one leaves
 * `BACK` as the only key that does anything.
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

// Four columns, as the channel grid: three metres away a name is still readable
// at this width on a 1080p panel, and a fifth column would not be.
private const val GRID_COLUMNS = 4
private val CARD_HEIGHT = 128.dp
private val MESSAGE_WIDTH = 900.dp
