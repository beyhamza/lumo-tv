package tv.lumo.android.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.lumo.android.core.data.EpgDay
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.designsystem.component.LumoTvSourceNotice
import tv.lumo.android.core.designsystem.component.LumoTvStateMessage
import tv.lumo.android.core.designsystem.format.formatTimeOfDay
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus

/**
 * The day the television's grid is showing, and what its reads have answered.
 *
 * The grid reads **one day at a time** — the day it draws — and this is that
 * day's answer, kept per channel so a row can say "not answered yet" and draw
 * nothing (S7-03): an initial load must not announce an empty guide. A source
 * that has no guide configured ([configured] false) draws no grid at all.
 *
 * [programmes] is the whole day for the channel, as the repository delivers it
 * ordered by start; [answered] is the set of channel ids the day's read has
 * come back for, empty programmes included — a real answer is not a hole.
 */
data class GuideDay(
    val day: EpgDay? = null,
    val programmes: Map<String, List<EpgProgramme>> = emptyMap(),
    val answered: Set<String> = emptySet(),
    val configured: Boolean? = null,
    /**
     * What the day's read last did, and how old the guide is (S9-06-03).
     * [GuideState] is derived from it, not stored beside it (see
     * `GuideStates.kt`).
     */
    val status: GuideStatus = GuideStatus(),
)

/**
 * The television guide grid: channels as rows, hours as columns, and a D-pad
 * rule that keeps an hour of reference across rows (US-16, S9-05-03).
 *
 * <h2>One grouped read per displayed day, never one per cell</h2>
 *
 * The screen reports the **page of channels on display** for the day it is
 * showing — the same [epgPageIds] the "En ce moment" list and the channel grid
 * use — and the view model asks the guide once for the pages it does not hold.
 * The number of `/epg` calls does not depend on the number of channels, rows or
 * cells (S9-03/S9-04, and the network proof is S9-07). This component only lays
 * out what the state already holds; it fetches nothing itself.
 *
 * <h2>Instants, never local hours</h2>
 *
 * Every position is a fraction of the day's two instants (`from` inclusive,
 * `to` exclusive), so a 23-hour day, a 25-hour one and a programme crossing
 * midnight all land where they belong; the hour labels are printed through
 * `formatTimeOfDay`, in the device's zone, and never parsed back (GD-12). The
 * reference-hour rule itself lives in `GuideFocus.kt`, pure and unit-tested.
 *
 * <h2>An absent guide says nothing, a gap says one sentence</h2>
 *
 * Until the day's read answers, a row draws nothing: an initial load must not
 * announce an empty guide. Once it has answered, a row with no programme over
 * the visible hours, and every gap inside it, draw the one sentence the product
 * allows — **« Aucun programme disponible sur ce créneau »** — and invent no
 * cause. A source with no guide configured (`configured == false`) draws no grid
 * at all (S7-03), which S9-06 will explain.
 *
 * <h2>Escapes stay reachable</h2>
 *
 * The grid consumes the four directions so the remote walks the day instead of
 * leaving the timeline; at the day's first cell `LEFT` is let through to the
 * category column, and `UP` from the first row is let through to the tabs and
 * the search field, so no control is a dead end (GD-06).
 */
@Composable
internal fun GuideGridTv(
    state: LiveState,
    channels: LazyPagingItems<Channel>,
    days: List<EpgDay>,
    activeDay: EpgDay,
    today: LocalDate,
    now: Instant,
    onNow: () -> Unit,
    onSelectDay: (EpgDay) -> Unit,
    onOpenProgramme: (channelId: String, channelName: String?, programme: EpgProgramme) -> Unit,
    onDayVisible: (EpgDay, List<String>) -> Unit,
    onAnchorChanged: (GuideAnchor?) -> Unit,
    onSeeChannels: () -> Unit,
    onRetryGuide: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val guideDay = state.guideDay

    // The rows the grid navigates: one per Paging item, with the programmes the
    // day's read has delivered. A placeholder or an unanswered row is null-ish
    // for the algorithm, which steps over it (a skeleton is never a target).
    val rows: List<GuideRow?> = remember(channels.itemSnapshotList, guideDay) {
        (0 until channels.itemCount).map { index ->
            channels.itemSnapshotList.getOrNull(index)?.let { channel ->
                GuideRow(
                    channelId = channel.id,
                    programmes = guideDay.programmes[channel.id].orEmpty(),
                    answered = guideRowAnswered(
                        channelId = channel.id,
                        status = guideDay.status,
                        answered = guideDay.answered,
                        programmes = guideDay.programmes,
                    ),
                )
            }
        }
    }

    val initialReference = if (now >= activeDay.from && now < activeDay.to) now else activeDay.from
    // The cell the view model kept from the last visit, so a return from the
    // player lands where the viewer left instead of at the head of the grid
    // (GD-09). Null on a first entry, or when the remembered day is not this one.
    val remembered = state.guideAnchor
    var selection by remember(activeDay) {
        mutableStateOf(resolveReturnSelection(remembered, rows, activeDay, now))
    }
    val focusRequester = remember { FocusRequester() }

    // The page on display, for the day being shown: one grouped read per page,
    // none for a page already held (S9-03's rule, through [epgPageIds]).
    LaunchedEffect(listState, channels, activeDay) {
        snapshotFlow {
            epgPageIds(
                visible = listState.layoutInfo.visibleItemsInfo.map { it.index },
                itemCount = channels.itemCount,
                idAt = { index -> channels.itemSnapshotList.getOrNull(index)?.id },
            )
        }
            .distinctUntilChanged()
            .collect { ids -> onDayVisible(activeDay, ids) }
    }

    // Arrival, and a page arriving. A selection is placed only when there is
    // none — first composition, a new day, or a page that emptied the row under
    // it — because a page loading in must not yank the viewer back to the head
    // of the grid. The `now` read is the one current at that composition; the
    // effect below handles the deliberate re-entry.
    LaunchedEffect(activeDay, rows) {
        val current = selection
        if (current == null || current.rowIndex !in rows.indices) {
            // A return re-resolves the remembered cell against the refreshed
            // guide; a first entry and a new day still place Maintenant.
            selection = resolveReturnSelection(remembered, rows, activeDay, now)
        }
    }

    // **Maintenant** is the one move that re-enters on purpose: it re-reads the
    // clock and places the selection on the programme that contains it, on the
    // first channel of the filtered result. `now` changes on that press and
    // nowhere else, so keying on it alone never fires on a page append.
    LaunchedEffect(now) {
        selection = entrySelection(rows, activeDay, now)
    }

    // The anchor follows the placement: the first landing and every deliberate
    // move are what a later return restores. The channel id lives on the row the
    // cell sits on, not in the cell, and a null selection (no rows) leaves the
    // previous anchor untouched rather than forgetting it.
    LaunchedEffect(selection, rows) {
        val current = selection ?: return@LaunchedEffect
        val channelId = rows.getOrNull(current.rowIndex)?.channelId ?: return@LaunchedEffect
        onAnchorChanged(current.anchorOn(channelId))
    }

    // The selected cell is the only focusable node, so every move has to put the
    // focus back on it: the cell that had it stops being focusable when the
    // selection moves, and Compose clears the focus rather than guessing where
    // it should go. Scroll to the row first — a row the list has not composed
    // yet has no node to receive the request — then request after the frame that
    // composed it. Without this a D-pad press would land once and then fall on
    // the floor.
    LaunchedEffect(selection) {
        val row = selection?.rowIndex ?: return@LaunchedEffect
        listState.scrollToItem(row)
        withFrameNanos { }
        focusRequester.requestFocusAfterRecompose()
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val current = selection ?: return false
        return when (event.key) {
            Key.DirectionUp -> {
                val next = moveVertical(rows, current, -1, activeDay)
                if (next == current) {
                    false // Let the focus reach the tabs and the search field.
                } else {
                    selection = next
                    true
                }
            }

            Key.DirectionDown -> {
                val next = moveVertical(rows, current, +1, activeDay)
                if (next == current) false else {
                    selection = next
                    true
                }
            }

            Key.DirectionLeft -> {
                val next = moveHorizontal(rows, current, -1, activeDay)
                if (next == current) {
                    false // The day's first cell: let LEFT reach the categories.
                } else {
                    selection = next
                    true
                }
            }

            Key.DirectionRight -> {
                selection = moveHorizontal(rows, current, +1, activeDay)
                true
            }

            else -> false
        }
    }

    val guideState = guideStateOf(
        configured = guideDay.configured,
        status = guideDay.status,
        answered = guideDay.answered,
        programmes = guideDay.programmes,
    )
    val guideAge = guideAgeOf(guideDay.status)

    Column(modifier = modifier.fillMaxSize()) {
        GuideGridHeader(
            days = days,
            activeDay = activeDay,
            today = today,
            onSelectDay = onSelectDay,
            onNow = onNow,
            onSeeChannels = onSeeChannels,
        )

        // No guide configured: nothing to draw (S7-03). The header above stays,
        // so the exits and the day tabs remain reachable.
        if (guideDay.configured == false) return@Column

        // The guide's age, when it is old or its last import did not finish
        // (GD-11). A fresh or undated guide says nothing — the ordinary case is
        // not a warning.
        guideAge?.let { age ->
            Text(
                text = guideAgeLabel(age),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = LumoSpacing.xs),
            )
        }

        // A failed read with **no** data is a screen of its own, never an empty
        // guide (GD-10): the neutral sentence belongs to a guide that answered.
        // Réessayer is the primary action, Voir les chaînes the way out.
        if (guideState == GuideState.InitialError) {
            LumoTvStateMessage(
                title = stringResource(R.string.feature_live_guide_error_title),
                body = stringResource(R.string.feature_live_guide_error_body),
                isError = true,
                actionLabel = stringResource(R.string.feature_live_guide_retry),
                onAction = onRetryGuide,
                secondaryActionLabel = stringResource(R.string.feature_live_guide_see_channels),
                onSecondaryAction = onSeeChannels,
            )
            return@Column
        }

        // A failed read **with** data keeps the grid and the focus sitting on it;
        // only a distinct line says the update failed (GD-10). The retry is an
        // extra focus stop, never a focus owner: the selection keeps the focus.
        if (guideState == GuideState.DataError) {
            LumoTvSourceNotice(
                title = stringResource(R.string.feature_live_guide_stale_title),
                message = stringResource(R.string.feature_live_guide_stale_body),
                isError = true,
                retryLabel = stringResource(R.string.feature_live_guide_retry),
                onRetry = onRetryGuide,
                compact = true,
            )
        }

        Box(modifier = Modifier.fillMaxSize().onPreviewKeyEvent(::handleKey)) {
            // The visible window is what fits when a **half-hour** cell stays
            // readable, and the width that matters is the one the hour columns
            // actually get. It is measured on their own Row — not assumed to be
            // the panel minus the name column — so a change to either can never
            // silently widen the window past what is drawn (BUG-S9-05-03-01).
            var hourColumnsWidthDp by remember { mutableStateOf(0) }
            val visible = remember(hourColumnsWidthDp) {
                guideVisibleWindow(hourColumnsWidthDp)
            }
            var window by remember(activeDay, visible) {
                mutableStateOf(
                    guideWindow(
                        previous = GuideWindow(activeDay.from, activeDay.from),
                        reference = initialReference,
                        day = activeDay,
                        visible = visible,
                    ),
                )
            }

            // The window follows the reference only when it must.
            LaunchedEffect(selection?.reference, activeDay, visible) {
                val reference = selection?.reference ?: return@LaunchedEffect
                val next = guideWindow(window, reference, activeDay, visible)
                if (next != window) window = next
            }

            Column(modifier = Modifier.fillMaxSize()) {
                HourHeaderRow(
                    day = activeDay,
                    window = window,
                    onHourColumnsWidth = { hourColumnsWidthDp = it },
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = LumoSpacing.xs),
                ) {
                    items(
                        count = channels.itemCount,
                        key = channels.itemKey { it.id },
                    ) { index ->
                        val channel = channels[index]
                        GuideChannelRow(
                            channel = channel,
                            row = rows.getOrNull(index),
                            day = activeDay,
                            window = window,
                            selected = selection?.takeIf { it.rowIndex == index },
                            focusRequester = focusRequester,
                            onOpenProgramme = onOpenProgramme,
                        )
                    }
                }
            }
        }
    }
}

/** The one line GD-11 allows about the guide's age, or its incomplete import. */
@Composable
internal fun guideAgeLabel(age: GuideAge): String = when (age) {
    is GuideAge.LastImport -> stringResource(
        R.string.feature_live_guide_age,
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(age.at),
    )

    GuideAge.Incomplete -> stringResource(R.string.feature_live_guide_age_incomplete)
}

/**
 * Day tabs, **Maintenant**, and the way out to the channel list (GD-06).
 *
 * Two lines, since BUG-S9-05-03-02: five day tabs and the two exits sharing one
 * weighted row left the tabs the width of a single pill, so only one of the five
 * was ever drawn. The tabs now get the whole width, and the exits sit under
 * them.
 *
 * Both exits are in the header rather than at the end of a row: a television
 * should not have to scroll a day to reach "Voir les chaînes", and the tabs are
 * where somebody looks for another day.
 */
@Composable
private fun GuideGridHeader(
    days: List<EpgDay>,
    activeDay: EpgDay,
    today: LocalDate,
    onSelectDay: (EpgDay) -> Unit,
    onNow: () -> Unit,
    onSeeChannels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LumoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
    ) {
        // The tabs on their own line. Scrollable so a day window with more tabs
        // than a narrow panel can show never makes the last ones unreachable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEach { day ->
                GuideChip(
                    label = dayTabLabel(day.date, today),
                    selected = day.date == activeDay.date,
                    onClick = { onSelectDay(day) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuideChip(
                label = stringResource(R.string.feature_live_guide_now),
                selected = false,
                primary = true,
                onClick = onNow,
            )
            GuideChip(
                label = stringResource(R.string.feature_live_guide_see_channels),
                selected = false,
                onClick = onSeeChannels,
            )
        }
    }
}

/** A day tab or a header action: the same pill, like the view toggle. */
@Composable
private fun GuideChip(
    label: String,
    selected: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            // Selected background is OnDark, so the ink has to come from the
            // dark side: OnDark on OnDark is white on white and the tab reads as
            // a blank pill (BUG-S9-05-03-02).
            selected -> LumoColors.Surface
            primary -> LumoColors.OnDark
            else -> LumoColors.OnDarkMuted
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused, shape = LumoTvShapes.pill)
            .clip(LumoTvShapes.pill)
            .background(
                when {
                    selected -> LumoColors.OnDark
                    focused || primary -> LumoColors.SurfaceRaised
                    else -> LumoColors.Surface
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
    )
}

/** The hour labels, over the same window and the same fractional columns. */
@Composable
private fun HourHeaderRow(
    day: EpgDay,
    window: GuideWindow,
    onHourColumnsWidth: (Int) -> Unit,
) {
    val marks = remember(day) { hourMarks(day.from, day.to) }
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GRID_HEADER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(GRID_NAME_WIDTH))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged { size ->
                    onHourColumnsWidth(with(density) { size.width.toDp() }.value.toInt())
                },
        ) {
            marks.zipWithNext().forEach { (from, to) ->
                val start = maxOf(from, window.from)
                val end = minOf(to, window.to)
                val weight = window.fraction(end, start)
                if (weight > 0f) {
                    Box(
                        modifier = Modifier.weight(weight).fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (!from.isBefore(window.from)) {
                            Text(
                                text = formatTimeOfDay(from),
                                style = MaterialTheme.typography.labelLarge,
                                color = LumoColors.OnDarkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One channel row: its name, then its day clipped to the visible window.
 *
 * A placeholder or an unanswered row draws no cell — **a skeleton is never a
 * target** — and keeps the row's height so the grid does not jump as pages load.
 */
@Composable
private fun GuideChannelRow(
    channel: Channel?,
    row: GuideRow?,
    day: EpgDay,
    window: GuideWindow,
    selected: GuideSelection?,
    focusRequester: FocusRequester,
    onOpenProgramme: (channelId: String, channelName: String?, programme: EpgProgramme) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GRID_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel?.name.orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = LumoColors.OnDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(GRID_NAME_WIDTH)
                .padding(end = LumoSpacing.sm, top = LumoSpacing.xs, bottom = LumoSpacing.xs),
        )

        Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val answered = channel != null && row?.answered == true
            val blocks = if (answered) dayBlocks(row.programmes, day.from, day.to) else emptyList()

            blocks.forEachIndexed { index, block ->
                val start = maxOf(block.startsAt, window.from)
                val end = minOf(block.endsAt, window.to)
                val weight = window.fraction(end, start)
                if (weight > 0f) {
                    GuideCell(
                        block = block,
                        weight = weight,
                        selected = selected?.blockIndex == index,
                        focusRequester = focusRequester,
                        onOpen = { programme -> channel?.let { onOpenProgramme(it.id, it.name, programme) } },
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }

            // An unanswered row keeps its space and draws nothing at all.
            if (!answered) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * One cell: a programme, or a gap that may be selected but not played.
 *
 * Only the selected cell is focusable, so the native focus search can never
 * land on a cell the reference-hour rule did not choose; the caller's key
 * handler drives the selection and this node only carries the focus signature.
 */
@Composable
private fun RowScope.GuideCell(
    block: GuideBlock,
    weight: Float,
    selected: Boolean,
    focusRequester: FocusRequester,
    onOpen: (EpgProgramme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cells = Modifier
        .weight(weight)
        .then(modifier)

    Box(
        modifier = cells
            .padding(horizontal = 1.dp)
            .lumoTvFocus(focused = selected, shape = LumoTvShapes.small)
            .clip(LumoTvShapes.small)
            .background(if (selected) LumoColors.SurfaceRaised else LumoColors.Surface)
            .then(
                if (!selected) {
                    Modifier
                } else if (block.programme != null) {
                    Modifier
                        .focusRequester(focusRequester)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            onOpen(block.programme)
                        }
                } else {
                    Modifier.focusRequester(focusRequester).focusable()
                },
            )
            .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (block.isEmpty) {
            Text(
                text = stringResource(R.string.feature_live_guide_empty_slot),
                style = MaterialTheme.typography.labelLarge,
                color = LumoColors.OnDarkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LumoSpacing.xxs)) {
                Text(
                    text = block.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = LumoColors.OnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val timeLabel = gridCellTimeLabel(
                    startLabel = formatTimeOfDay(block.startsAt),
                    endLabel = formatTimeOfDay(block.endsAt),
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The time line of a guide cell, from the two labels a programme has: the
 * **start alone**, never the range.
 *
 * The cell's end is already carried by the next cell, and the full range belongs
 * to the S9-06 detail sheet (`docs/design/0.2.0/direct-guide.md`). Rendering the
 * range here is what made a 12-hour clock's `12:00 PM – 12:30 PM` overflow the
 * ~200 dp content box and lose its tail (#215); the start alone does not depend
 * on the 12 h / 24 h setting at all. Pure and free of Compose so the shape is a
 * plain unit test. `endLabel` is taken on purpose: the decision to drop it is
 * pinned by a test rather than by a missing parameter.
 */
internal fun gridCellTimeLabel(startLabel: String, endLabel: String): String = startLabel

/** A day's tab: "Aujourd'hui" for today, a short date otherwise. */
@Composable
private fun dayTabLabel(date: LocalDate, today: LocalDate): String {
    if (date == today) return stringResource(R.string.feature_live_guide_today)
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        DateTimeFormatter.ofPattern("EEE d", locale).format(date)
    }
}

/**
 * The visible window's share of `[start, end)`, `0f` for an interval that is not
 * inside it.
 *
 * The one place the grid turns instants into widths: both the hour header and
 * the programme cells call it, so the columns line up without either knowing the
 * other's heights.
 */
private fun GuideWindow.fraction(end: Instant, start: Instant): Float {
    val span = java.time.Duration.between(from, to).toMillis().toFloat()
    if (span <= 0f) return 0f
    val inside = java.time.Duration.between(
        maxOf(start, from),
        minOf(end, to),
    ).toMillis().toFloat()
    return (inside / span).coerceIn(0f, 1f)
}

// ---- constants -------------------------------------------------------------

/** The channel-name column: wide enough for a name, narrow enough to read at 1080p. */
private val GRID_NAME_WIDTH = GRID_NAME_WIDTH_DP.dp

/** The hour-label line. */
private val GRID_HEADER_HEIGHT = 28.dp

/** One channel row: two lines of title and one line of programme. */
private val GRID_ROW_HEIGHT = 84.dp

/**
 * `focusRequester.requestFocus()` after the frame that moved the selection.
 *
 * The selected cell is the only focusable node; when the selection changes, the
 * old node loses it and the new one is composed on the next frame. Requesting in
 * the same frame would target a node that does not exist yet.
 */
private fun FocusRequester.requestFocusAfterRecompose() {
    runCatching { requestFocus() }
}

