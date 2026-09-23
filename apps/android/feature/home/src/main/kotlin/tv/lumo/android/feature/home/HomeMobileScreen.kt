package tv.lumo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import tv.lumo.android.core.data.R as DataR
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.data.wording
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.component.LumoSourceNotice
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.feature.home.navigation.HomeActions

/**
 * The home screen, on a phone (US-017, design S8-E01 and S8-E02).
 *
 * <h2>Three rails, in one order, and only the ones with something in them</h2>
 *
 * Continue, Favourites, Live — the layout validated on 19 September 2026. Which
 * rails exist and what is in them is [HomeState.sections]; this file draws that
 * list and decides nothing. A rail with nothing in it has no title and takes no
 * space, and an account with nothing in any of them gets an invitation and three
 * doors instead of three empty rows.
 *
 * <h2>Pressing a card plays it</h2>
 *
 * Every card on this screen starts something: a "Continue" card resumes at the
 * stored position, a channel starts the live player. That is the point of a home
 * screen — it is the list of things somebody does not want to look for again. The
 * film or series behind a "Continue" card is one more press away, on the
 * **Details** button under it, because a card with a single gesture cannot also
 * open a synopsis.
 *
 * <h2>What is deliberately not here</h2>
 *
 * "Remove from Continue" (sprint 12, and it needs a contract the API does not have
 * yet) and the way into the TV guide (S9-04). Neither is drawn disabled: a
 * control that does nothing is one somebody presses to find out.
 *
 * <h2>What is on, under the Live cards (US-16, S9-03)</h2>
 *
 * One line under a Live card's name: the programme on air, from one request for
 * the whole rail, and nothing at all when the guide has nothing for the channel
 * — no placeholder, no "unavailable" (S7-03). The favourites rail does not carry
 * it: the cadrage of S9-03 names the Direct cards.
 */
@Composable
fun HomeMobileScreen(
    actions: HomeActions,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Every time this screen comes into view — launch, return from a player,
    // return from another tab. See `HomeViewModel.onShown`.
    LaunchedEffect(Unit) { viewModel.onShown() }

    // The step of an import is only worth showing if it moves. Polled from the
    // composition so that it stops when the screen does.
    val syncing = state.notice is SourceNotice.Refreshing
    LaunchedEffect(syncing) {
        while (syncing) {
            delay(SYNC_POLL_MILLIS)
            viewModel.refreshSource()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state.step) {
            HomeStep.Loading -> Centered { CircularProgressIndicator() }

            HomeStep.NoSource -> Message(
                title = stringResource(R.string.feature_home_no_source_title),
                body = stringResource(R.string.feature_home_no_source_body),
            ) {
                Button(onClick = actions.onAddSource) {
                    Text(stringResource(R.string.feature_home_add_source))
                }
            }

            // No button: the shell's switcher has already opened the list, and it
            // is the only thing that can answer.
            HomeStep.NeedsChoice -> Message(
                title = stringResource(R.string.feature_home_needs_choice_title),
                body = stringResource(R.string.feature_home_needs_choice_body),
            )

            // The two ways on US-024 names for an outage: ask again, or stop
            // depending on this answer and choose a source in "My sources".
            HomeStep.Unavailable -> Message(
                title = stringResource(R.string.feature_home_unavailable_title),
                body = stringResource(R.string.feature_home_unavailable_body),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
                    Button(onClick = viewModel::refreshSource) {
                        Text(stringResource(R.string.feature_home_retry))
                    }
                    OutlinedButton(onClick = actions.onOpenSources) {
                        Text(stringResource(DataR.string.core_data_notice_change_source))
                    }
                }
            }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
    ) {
        state.notice?.let { notice ->
            item(key = "notice") {
                Notice(
                    notice = notice,
                    onOpenSources = actions.onOpenSources,
                    onRetry = onRetry,
                )
            }
        }

        when {
            state.waiting -> item(key = "waiting") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LumoSpacing.xxl),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.blank -> item(key = "blank") { Blank(actions) }

            else -> items(sections, key = { it.railKey }) { section ->
                when (section) {
                    is HomeSection.Continue -> ContinueRail(section, actions)

                    is HomeSection.Favorites -> ChannelRail(
                        title = stringResource(R.string.feature_home_favorites),
                        action = stringResource(R.string.feature_home_see_all_favorites),
                        onAction = actions.onOpenLibrary,
                        channels = section.channels.map { it.channel },
                        onPlay = actions.onPlayChannel,
                    )

                    is HomeSection.Live -> ChannelRail(
                        title = stringResource(R.string.feature_home_live),
                        action = stringResource(R.string.feature_home_all_channels),
                        onAction = actions.onOpenLive,
                        channels = section.channels,
                        onPlay = actions.onPlayChannel,
                        onAir = state.onAir,
                    )
                }
            }
        }
    }
}

private val HomeSection.railKey: String
    get() = when (this) {
        is HomeSection.Continue -> "continue"
        is HomeSection.Favorites -> "favorites"
        is HomeSection.Live -> "live"
    }

// ---- the source, above the rails -------------------------------------------

/**
 * What the source is doing, over the rails and never instead of them.
 *
 * The drawing is `LumoSourceNotice` and the sentences are `core:data`'s, shared
 * with the three catalogue grids since they keep their content during a refresh
 * too (US-024, lot C4): the **real step** of an import, because "refreshing…"
 * with nothing after it is a spinner in words; a failure in the words of its
 * ingestion code, and whether what is on screen may be out of date. One action,
 * the screen where a source is looked after — and, for an outage, a second one
 * that asks the server again (US-024, "Indisponibilité et hors ligne").
 */
@Composable
private fun Notice(notice: SourceNotice, onOpenSources: () -> Unit, onRetry: () -> Unit) {
    val wording = notice.wording()

    LumoSourceNotice(
        title = stringResource(wording.title),
        message = stringResource(wording.message),
        hint = wording.hint?.let { stringResource(it) },
        isError = wording.failed,
        actionLabel = stringResource(wording.action),
        onAction = onOpenSources,
        retryLabel = wording.retry?.let { stringResource(it) },
        onRetry = onRetry,
        modifier = Modifier.padding(horizontal = LumoSpacing.md),
    )
}

// ---- Continue ---------------------------------------------------------------

@Composable
private fun ContinueRail(section: HomeSection.Continue, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        RailHeader(title = stringResource(R.string.feature_home_continue))

        LazyRow(
            contentPadding = PaddingValues(horizontal = LumoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            items(section.items, key = { it.key }) { item -> ContinueCard(item, actions) }
        }
    }
}

/**
 * One thing to carry on with.
 *
 * Two targets, and they are kept apart on purpose: the poster and its title are
 * one — pressing them **resumes** — and "Details" sits underneath as a button of
 * its own. A long press would have hidden the second action from everybody who
 * never tries one, and an overflow menu holding a single entry is a menu somebody
 * opens to find a button.
 */
@Composable
private fun ContinueCard(item: ContinueItem, actions: HomeActions) {
    val view = continueCardOf(item)

    Column(
        modifier = Modifier.width(CONTINUE_CARD_WIDTH),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Column(
            modifier = Modifier.clickable { view.resume(actions) },
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A series says which episode pressing it will open. "Continue"
            // without saying what is being continued is a button somebody presses
            // to find out.
            view.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val detailsOf = stringResource(R.string.feature_home_details_of, view.title)
        TextButton(
            onClick = { view.open(actions) },
            contentPadding = PaddingValues(horizontal = LumoSpacing.xs),
            // Twelve buttons reading "Details" are twelve identical announcements
            // to a screen reader; each one names what it opens.
            modifier = Modifier.semantics { contentDescription = detailsOf },
        ) {
            Text(stringResource(R.string.feature_home_details))
        }
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

// ---- Favourites and Live ----------------------------------------------------

/**
 * A rail of channels: the favourites, or the recently watched.
 *
 * One composable for both because they are the same thing to the thumb — a channel
 * that plays when pressed — and differ only in where their trailing action leads.
 * That action is in the header, at the end of the title's line, so it is reachable
 * without scrolling a rail of twelve to its far end.
 */
@Composable
private fun ChannelRail(
    title: String,
    action: String,
    onAction: () -> Unit,
    channels: List<Channel>,
    onPlay: (channelId: String, name: String?) -> Unit,
    onAir: Map<String, EpgProgramme> = emptyMap(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm)) {
        RailHeader(title = title) {
            TextButton(onClick = onAction) { Text(action) }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = LumoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelCard(channel = channel, onAir = onAir[channel.id], onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, onAir: EpgProgramme?, onPlay: (String, String?) -> Unit) {
    Column(
        modifier = Modifier
            .width(CHANNEL_CARD_WIDTH)
            .clip(LumoShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onPlay(channel.id, channel.name) }
            .padding(LumoSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Logo(channel)
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            // Two lines of a fixed height, so a rail of short and long names keeps
            // one baseline instead of cards of three different heights.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Absent, not blank, when the guide has nothing (S7-03).
        onAir?.let { programme ->
            Text(
                text = programme.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    if (channel.logoUrl == null) {
        Initial(channel)
        return
    }

    SubcomposeAsyncImage(
        model = channel.logoUrl,
        contentDescription = null,
        modifier = Modifier
            .size(LOGO_SIZE)
            .clip(LumoShapes.small),
        loading = { Initial(channel) },
        error = { Initial(channel) },
    )
}

@Composable
private fun Initial(channel: Channel) {
    Box(
        modifier = Modifier
            .size(LOGO_SIZE)
            .clip(LumoShapes.small)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel.name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- shared pieces ----------------------------------------------------------

@Composable
private fun RailHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        trailing()
    }
}

/**
 * A source, and nothing watched, starred or played yet (S8-E02).
 *
 * The three catalogues are offered whatever the source holds, for the reason the
 * bar gives: an empty catalogue is a reply — it opens onto a grid that says so —
 * and hiding a door is what made somebody conclude the films did not exist.
 */
@Composable
private fun Blank(actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.feature_home_blank_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.feature_home_blank_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = actions.onOpenLive, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_home_explore_live))
        }
        OutlinedButton(onClick = actions.onOpenFilms, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_home_explore_films))
        }
        OutlinedButton(onClick = actions.onOpenSeriesCatalogue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_home_explore_series))
        }
    }
}

@Composable
private fun Message(title: String, body: String, action: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LumoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action()
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * Narrower than the catalogue's two-column poster, wider than a thumbnail: a rail
 * shows two cards and the edge of a third on a 360 dp phone, and that cut-off
 * third card is what says the row scrolls.
 */
private val CONTINUE_CARD_WIDTH = 132.dp
private val CHANNEL_CARD_WIDTH = 104.dp
private val LOGO_SIZE = 56.dp
private val POSITION_BAR_HEIGHT = 4.dp

/**
 * How often the step of an import is asked again while it is on display.
 *
 * A little slower than the add-source screen's two seconds: there it is the thing
 * somebody is waiting on, here it is a notice above what they came for.
 */
internal const val SYNC_POLL_MILLIS = 3_000L
