package tv.lumo.android.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.wording
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.component.LumoTvSourceNotice
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges
import tv.lumo.android.feature.home.navigation.HomeActions

/**
 * The home screen, on a television (US-017, design S8-E01 and S8-E02).
 *
 * The focus map for this screen — arrival focus and every direction from every
 * zone — belongs in `docs/design/tv-focus-map.md`, and the rules it follows are
 * the ones written there.
 *
 * <h2>Rails, here and nowhere else on the television</h2>
 *
 * `S2-13` and `S4-08` kept rails **off the catalogue grids**: a grid already has a
 * strip of chips, and a rail above it would have been a second mechanism on a
 * screen that has one. That ruling stands and those grids are untouched. The home
 * screen is not a grid with something added — it *is* three rails, and that layout
 * was validated as such on 19 September 2026. The product owner confirmed the same
 * day that it prevails **for this screen only**.
 *
 * <h2>One gesture per card, and it plays</h2>
 *
 * `OK` on a "Continue" card resumes it; `OK` on a channel starts it. There is no
 * second target on a card: two per card would double the horizontal journey
 * through a rail, which is the argument the channel grid already makes against a
 * heart button. The film or the series behind a "Continue" card is on a **long
 * press of `OK`** — the gesture that grid uses for its own secondary action — so
 * it costs no focus stop and traps nothing, and the rail's heading says it exists.
 *
 * <h2>Where the focus goes, and when it is left alone</h2>
 *
 * [homeFocusTarget] decides, and documents why. This file's part is mechanical:
 * one [FocusRequester], attached to whichever card is the target, and a rail that
 * scrolls that card into being before asking for the focus — a card `LazyRow` has
 * not composed cannot take it.
 */
@Composable
fun HomeTvScreen(
    actions: HomeActions,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onShown() }

    val syncing = state.notice is SourceNotice.Refreshing
    LaunchedEffect(syncing) {
        while (syncing) {
            delay(SYNC_POLL_MILLIS)
            viewModel.refreshSource()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            // Not on the leading edge: the rail is there and has already paid for
            // that margin.
            .tvOverscanEdges(top = true, end = true, bottom = true),
    ) {
        when (state.step) {
            // No target, and none is needed: this lasts as long as a DataStore
            // read, and the rail beside it is focusable throughout.
            HomeStep.Loading -> Centered {
                Text(
                    text = stringResource(R.string.feature_home_loading),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            // A television never types a source (docs/architecture.md §5): it
            // says where one is added, in the words the source screen uses. The
            // one thing it *can* do is look again, so that is its button — a
            // screen with no target leaves BACK as the only key that works.
            HomeStep.NoSource -> Message(
                title = stringResource(DataR.string.core_data_source_tv_none_title),
                body = stringResource(DataR.string.core_data_source_tv_none_body),
                action = stringResource(R.string.feature_home_check_again),
                onAction = viewModel::refreshSource,
            )

            // The shell's chooser is already open over this screen, and it is a
            // dialog: the focus is inside it and cannot reach here. A button
            // behind it would be a control nobody can press.
            HomeStep.NeedsChoice -> Message(
                title = stringResource(R.string.feature_home_needs_choice_title),
                body = stringResource(R.string.feature_home_needs_choice_body),
            )

            // The two ways on US-024 names for an outage, "try again" taking the
            // focus and "change source" one `RIGHT` away.
            HomeStep.Unavailable -> Message(
                title = stringResource(R.string.feature_home_unavailable_title),
                body = stringResource(R.string.feature_home_unavailable_body),
                action = stringResource(R.string.feature_home_retry),
                onAction = viewModel::refreshSource,
                secondaryAction = stringResource(DataR.string.core_data_notice_change_source),
                onSecondaryAction = actions.onOpenSources,
            )

            HomeStep.Browsing -> Browsing(
                state = state,
                actions = actions,
                onRetry = viewModel::refreshSource,
            )
        }
    }
}

@Composable
private fun Browsing(state: HomeState, actions: HomeActions, onRetry: () -> Unit) {
    val sections = remember(state) { state.sections }
    val keys = remember(sections) { focusKeysOf(sections) }

    // Survives the trip to a player and back: the composition is discarded while
    // the player is on screen, the back stack entry's saved state is not.
    var returnKey by rememberSaveable { mutableStateOf<String?>(null) }
    var placedKey by remember { mutableStateOf<String?>(null) }
    var focusedKey by remember { mutableStateOf<String?>(null) }

    val focus = remember { FocusRequester() }
    // What a rail is about to ask the focus for. A plain holder and not state:
    // it is written and read inside one focus change, between two compositions.
    val pending = remember { arrayOfNulls<String>(1) }

    val target = homeFocusTarget(keys, returnKey, placedKey, focusedKey)

    val cards = HomeCardFocus(
        target = target,
        requester = focus,
        onPlacing = { key -> pending[0] = key },
        onFocusChanged = { key, focused ->
            if (focused) {
                focusedKey = key
                if (key == pending[0]) placedKey = key
                // The viewer has walked away from the card they came back to:
                // it has done its job, and must not pull the focus again.
                if (key != returnKey) returnKey = null
            } else if (focusedKey == key) {
                // Left without another card taking over: the focus is in the
                // rail, or on a button. `homeFocusTarget` reads that as "moved".
                focusedKey = null
            }
        },
        onLaunch = { key -> returnKey = key },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // A column that scrolls rather than a lazy one: three rails at most,
            // and every one of them composed means `UP` and `DOWN` always find a
            // card to land on — a rail that is not composed is not in the focus
            // search. Focusing a card scrolls it into view by itself.
            .verticalScroll(rememberScrollState())
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.feature_home_title),
            style = MaterialTheme.typography.displayMedium,
            color = LumoColors.OnDark,
        )

        state.notice?.let { notice ->
            Notice(notice = notice, onOpenSources = actions.onOpenSources, onRetry = onRetry)
        }

        when {
            state.waiting -> Text(
                text = stringResource(R.string.feature_home_loading),
                style = MaterialTheme.typography.titleLarge,
                color = LumoColors.OnDarkMuted,
            )

            state.blank -> Blank(actions)

            else -> sections.forEach { section ->
                when (section) {
                    is HomeSection.Continue -> ContinueRail(section, actions, cards)

                    is HomeSection.Favorites -> ChannelRail(
                        title = stringResource(R.string.feature_home_favorites),
                        action = stringResource(R.string.feature_home_see_all_favorites),
                        onAction = actions.onOpenLibrary,
                        channels = section.channels.map { it.channel },
                        keyOf = ::favoriteKey,
                        onPlay = actions.onPlayChannel,
                        cards = cards,
                    )

                    is HomeSection.Live -> ChannelRail(
                        title = stringResource(R.string.feature_home_live),
                        action = stringResource(R.string.feature_home_all_channels),
                        onAction = actions.onOpenLive,
                        channels = section.channels,
                        keyOf = ::recentKey,
                        onPlay = actions.onPlayChannel,
                        cards = cards,
                    )
                }
            }
        }
    }
}

/**
 * What every card needs to take part in the focus rules, passed as one value.
 *
 * @param target the card the screen wants focused, or null to leave the remote be.
 * @param onPlacing called just before the focus is asked for, so that the focus
 * change that follows can be told apart from one the viewer made.
 * @param onLaunch called when a card opens a player or a detail screen, so that
 * `BACK` from there returns to it.
 */
private class HomeCardFocus(
    val target: String?,
    val requester: FocusRequester,
    val onPlacing: (String) -> Unit,
    val onFocusChanged: (key: String, focused: Boolean) -> Unit,
    val onLaunch: (String) -> Unit,
)

/** One requester, carried by whichever card is the target and by no other. */
private fun Modifier.focusTargetOf(cards: HomeCardFocus, key: String): Modifier =
    if (key == cards.target) focusRequester(cards.requester) else this

/**
 * Brings the target card into being, then gives it the focus.
 *
 * `scrollToItem` first: on the way back from a player the card may be the ninth of
 * its rail, and `LazyRow` only composes what is near the viewport. Failing to
 * focus is recoverable — the D-pad still works — and throwing would take the
 * screen down, hence `runCatching`.
 */
@Composable
private fun PlaceFocus(
    keys: List<String>,
    cards: HomeCardFocus,
    scrollTo: suspend (index: Int) -> Unit,
) {
    LaunchedEffect(cards.target, keys) {
        val target = cards.target ?: return@LaunchedEffect
        val index = keys.indexOf(target)
        if (index < 0) return@LaunchedEffect

        scrollTo(index)
        cards.onPlacing(target)
        runCatching { cards.requester.requestFocus() }
    }
}

// ---- the source, above the rails -------------------------------------------

/**
 * What the source is doing, over the rails and never instead of them.
 *
 * An import in progress is **text and not a focus stop**: there is nothing to
 * press, and a stop with nothing behind it is a dead end on the way `UP`. A failed
 * one has exactly one control, "My sources" — a television cannot correct a source,
 * but that screen says what is wrong with it and where to fix it. An outage has
 * two, "try again" then "change source", on the notice's one line. Drawn by
 * `LumoTvSourceNotice`, worded by `core:data`, like the three grids' (US-024).
 */
@Composable
private fun Notice(notice: SourceNotice, onOpenSources: () -> Unit, onRetry: () -> Unit) {
    val wording = notice.wording()

    LumoTvSourceNotice(
        title = stringResource(wording.title),
        message = stringResource(wording.message),
        hint = wording.hint?.let { stringResource(it) },
        isError = wording.failed,
        actionLabel = stringResource(wording.action).takeIf { wording.actionable },
        onAction = onOpenSources,
        retryLabel = wording.retry?.let { stringResource(it) },
        onRetry = onRetry,
    )
}

// ---- Continue ---------------------------------------------------------------

@Composable
private fun ContinueRail(
    section: HomeSection.Continue,
    actions: HomeActions,
    cards: HomeCardFocus,
) {
    val listState = rememberLazyListState()
    PlaceFocus(keys = section.items.map { it.key }, cards = cards, scrollTo = listState::scrollToItem)

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            RailTitle(stringResource(R.string.feature_home_continue))
            // The long press is the only gesture on this screen nothing draws, so
            // it is written down where it applies.
            Text(
                text = stringResource(R.string.feature_home_tv_details_hint),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            // Room for the focus scale: a lazy row clips to its bounds, and a card
            // that grows by 8 % would otherwise lose its outline on the way.
            contentPadding = RAIL_PADDING,
        ) {
            items(section.items, key = { it.key }) { item ->
                ContinueCard(item = item, actions = actions, cards = cards)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(item: ContinueItem, actions: HomeActions, cards: HomeCardFocus) {
    val view = continueCardOf(item)
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .focusTargetOf(cards, view.key)
            .width(POSTER_WIDTH)
            .onFocusChanged {
                focused = it.isFocused
                cards.onFocusChanged(view.key, it.isFocused)
            }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Ink)
            // `combinedClickable` alone: it makes the card focusable and binds the
            // centre key, short and long. A `focusable()` beside it would put two
            // targets on one card (AGENTS.md §6).
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClickLabel = stringResource(R.string.feature_home_details),
                onLongClick = {
                    cards.onLaunch(view.key)
                    view.open(actions)
                },
            ) {
                cards.onLaunch(view.key)
                view.resume(actions)
            }
            .padding(LumoSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        LumoPoster(
            posterUrl = view.posterUrl,
            title = view.title,
            modifier = Modifier.fillMaxWidth(),
            overlay = { PositionBar(view.fraction) },
        )
        Text(
            text = view.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (focused) LumoColors.OnDark else LumoColors.OnDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Always a second line, empty for a film: a rail mixing films and series
        // would otherwise have cards of two heights, and `DOWN` from the short
        // ones would travel further than from the tall ones.
        Text(
            text = view.subtitle.orEmpty(),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = LumoColors.OnDarkMuted,
            maxLines = 1,
        )
    }
}

/** How far in, across the foot of the poster. Nothing when the length is unknown. */
@Composable
private fun BoxScope.PositionBar(fraction: Float?) {
    if (fraction == null) return

    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(POSITION_BAR_HEIGHT)
            .background(LumoColors.SurfaceRaised),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(LumoColors.Accent),
        )
    }
}

// ---- Favourites and Live ----------------------------------------------------

/**
 * A rail of channels, closed by the action that leads to the whole list.
 *
 * **The action is the last tile of the rail, not a button beside the title.** A
 * button on the title's line would be a focus row of its own between two rails:
 * every trip `UP` or `DOWN` the page would stop on it. At the end of the rail it
 * costs nothing to somebody who is not looking for it — and the nav rail's own
 * entries lead to the same two places in fewer presses, so nobody *has* to travel
 * twelve cards to reach it.
 */
@Composable
private fun ChannelRail(
    title: String,
    action: String,
    onAction: () -> Unit,
    channels: List<Channel>,
    keyOf: (channelId: String) -> String,
    onPlay: (channelId: String, name: String?) -> Unit,
    cards: HomeCardFocus,
) {
    val listState = rememberLazyListState()
    PlaceFocus(keys = channels.map { keyOf(it.id) }, cards = cards, scrollTo = listState::scrollToItem)

    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        RailTitle(title)

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            contentPadding = RAIL_PADDING,
        ) {
            items(channels, key = { keyOf(it.id) }) { channel ->
                ChannelCard(
                    channel = channel,
                    key = keyOf(channel.id),
                    onPlay = onPlay,
                    cards = cards,
                )
            }
            item(key = "action") { ActionTile(label = action, onClick = onAction) }
        }
    }
}

/**
 * One channel. `OK` starts it — selecting a favourite launches the live player at
 * once (US-020) — and there is no long press: filing a favourite is the channel
 * grid's gesture, and group management is not on this screen.
 */
@Composable
private fun ChannelCard(
    channel: Channel,
    key: String,
    onPlay: (channelId: String, name: String?) -> Unit,
    cards: HomeCardFocus,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .focusTargetOf(cards, key)
            .width(CHANNEL_CARD_WIDTH)
            .height(CHANNEL_CARD_HEIGHT)
            .onFocusChanged {
                focused = it.isFocused
                cards.onFocusChanged(key, it.isFocused)
            }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Surface)
            .clickable(interactionSource = interactionSource, indication = null) {
                cards.onLaunch(key)
                onPlay(channel.id, channel.name)
            }
            .padding(LumoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs, Alignment.CenterVertically),
    ) {
        // The channel grid's card, deliberately: a name in two lines and a number
        // in a monospaced face. A logo at this size is a smudge at three metres.
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

/** "See all", "All channels": the tile that closes a rail. Same height as its cards. */
@Composable
private fun ActionTile(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(CHANNEL_CARD_WIDTH)
            .height(CHANNEL_CARD_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoTvShapes.medium)
            .background(if (focused) LumoColors.SurfaceRaised else LumoColors.Ink)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(LumoSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // Selection-coloured ink, never a fill: it is a way onward, and it
            // should not read as one more channel.
            color = if (focused) LumoColors.OnDark else LumoColors.Accent,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- shared pieces ----------------------------------------------------------

@Composable
private fun RailTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = LumoColors.OnDark,
    )
}

/**
 * A source, and nothing watched, starred or played yet (S8-E02).
 *
 * Three doors in a row, and the focus on the first: "Live" is what a television is
 * for. They duplicate three entries of the nav rail on purpose — somebody reading
 * "nothing here yet" should be one press from somewhere, not sent looking for a
 * menu.
 */
@Composable
private fun Blank(actions: HomeActions) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(
        modifier = Modifier.widthIn(max = MESSAGE_WIDTH),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_home_blank_title),
            style = MaterialTheme.typography.titleLarge,
            color = LumoColors.OnDark,
        )
        Text(
            text = stringResource(R.string.feature_home_blank_body),
            style = MaterialTheme.typography.bodyLarge,
            color = LumoColors.OnDarkMuted,
        )
        Row(
            modifier = Modifier.padding(top = LumoSpacing.sm, start = LumoSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            LumoTvButton(
                text = stringResource(R.string.feature_home_explore_live),
                onClick = actions.onOpenLive,
                primary = true,
                focusRequester = first,
            )
            LumoTvButton(
                text = stringResource(R.string.feature_home_explore_films),
                onClick = actions.onOpenFilms,
            )
            LumoTvButton(
                text = stringResource(R.string.feature_home_explore_series),
                onClick = actions.onOpenSeriesCatalogue,
            )
        }
    }
}

/**
 * A full-screen message, with its one action when it has one.
 *
 * The action takes the focus on arrival: it is the only thing on the screen that
 * answers a key. A second one, when there is one, sits to its right — `RIGHT`
 * reaches it, and it never takes the focus first.
 */
@Composable
private fun Message(
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
    secondaryAction: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    val button = remember { FocusRequester() }
    LaunchedEffect(action) { if (action != null) runCatching { button.requestFocus() } }

    Column(
        modifier = Modifier
            .padding(LumoSpacing.xxl)
            .widthIn(max = MESSAGE_WIDTH),
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
        if (action != null) {
            Row(
                modifier = Modifier.padding(top = LumoSpacing.sm, start = LumoSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
            ) {
                LumoTvButton(
                    text = action,
                    onClick = onAction,
                    primary = true,
                    focusRequester = button,
                )
                if (secondaryAction != null) {
                    LumoTvButton(text = secondaryAction, onClick = onSecondaryAction)
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * Poster width, chosen for three metres and for the page.
 *
 * Narrower than the film grid's 200 dp, because that grid is the whole screen and
 * this rail is a third of one: at 2:3 this is a 240 dp poster, which leaves the
 * heading of the next rail on screen under it — and what says "there is more
 * below" on a television is seeing the top of it.
 */
private val POSTER_WIDTH = 160.dp

/** The channel grid's card, a little narrower: a rail shows five and the edge of a sixth. */
private val CHANNEL_CARD_WIDTH = 232.dp
private val CHANNEL_CARD_HEIGHT = 112.dp
private val POSITION_BAR_HEIGHT = 6.dp
private val MESSAGE_WIDTH = 900.dp

private val RAIL_PADDING = PaddingValues(horizontal = LumoSpacing.sm, vertical = LumoSpacing.md)
