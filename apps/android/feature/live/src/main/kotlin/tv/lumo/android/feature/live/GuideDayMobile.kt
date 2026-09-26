package tv.lumo.android.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.time.Instant
import java.time.LocalDate
import tv.lumo.android.core.data.EpgDay
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.designsystem.component.LumoStateMessage
import tv.lumo.android.core.designsystem.format.formatTimeOfDay
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The mobile Guide's two levels (US-16, S9-05-04).
 *
 * <h2>Nested, not another destination</h2>
 *
 * On a phone the Guide is a list of what is on, and one channel's day opens on
 * top of it — the design's **S9-E03**: "Liste En ce moment, puis programmes de
 * la journée d'une chaîne" (`docs/design/0.2.0/direct-guide.md`). The two are
 * levels of one route, so Back walks out of a day and back into the list it was
 * opened from, keeping the list where it was (GD-13). This file holds that
 * nesting as a value, so the rule is a unit test rather than a gesture only a
 * real thumb can find.
 *
 * <h2>Instants, never local hours</h2>
 *
 * A day is the [EpgDay] interval, and the programmes are the very blocks the
 * television grid draws ([dayBlocks]) clipped to that interval: a programme that
 * started yesterday shows from midnight, one that runs past midnight stops at
 * the day's end. The labels are printed in the device's zone and never parsed
 * back (GD-12).
 */
internal sealed interface GuideMobileLevel {

    /** The "En ce moment" list. What the Guide opens on. */
    data object Now : GuideMobileLevel

    /** One channel's day, opened from [Now]. */
    data class ChannelDay(val channelId: String) : GuideMobileLevel
}

/** The level a channel's day returns to. Its only exit, on a phone, is the list (GD-13). */
internal fun GuideMobileLevel.back(): GuideMobileLevel = GuideMobileLevel.Now

/** The level [state] is showing, from the channel whose day it holds. */
internal fun guideMobileLevel(state: LiveState): GuideMobileLevel =
    state.dayChannel?.let { GuideMobileLevel.ChannelDay(it.id) } ?: GuideMobileLevel.Now

/**
 * GD-13: true while the mobile Guide is one level deep — a channel's day is
 * open — so Android's Back must climb to "En ce moment" instead of leaving the
 * Direct destination.
 *
 * The screen enables its `BackHandler` on this and nothing else, and reads it
 * from [guideMobileLevel] so the gesture and the level actually drawn come from
 * one rule: a day is open exactly when [LiveState.dayChannel] holds one.
 */
internal fun LiveState.guideChannelDayOpen(): Boolean =
    guideMobileLevel(this) is GuideMobileLevel.ChannelDay

/**
 * One channel's [programmes] clipped to [day] and the gaps between them filled.
 *
 * Thin on purpose: the clipping, the overlap rule and the terminal empty block
 * are [dayBlocks]', shared with the television grid, so the phone and the
 * television can never disagree about what a day contains. What this adds is the
 * name the caller reads: a *channel's* day.
 */
internal fun channelDayBlocks(
    programmes: List<EpgProgramme>,
    day: EpgDay,
): List<GuideBlock> = dayBlocks(programmes, day.from, day.to)

/**
 * The block of [blocks] that contains [now], or `-1` when none does.
 *
 * A day the viewer picked and that is not today has no "now" to land on, and a
 * gap is a real block that may contain [now] — the list still scrolls to it, it
 * simply has no programme. `-1` therefore means "there is nothing to scroll to",
 * never "the guide is empty".
 */
internal fun nowEntryIndex(blocks: List<GuideBlock>, now: Instant): Int =
    blocks.indexOfFirst { it.covers(now) }

/**
 * One channel's day, the second level of the mobile Guide (S9-05-04).
 *
 * <h2>One grouped read per day, never one per programme</h2>
 *
 * Opening the day reads **that channel over that day** — a single id through the
 * same [LiveViewModel.onDayVisible] the television grid uses, so the request is
 * one grouped `/epg` per day on both surfaces and none for a day already held.
 * The day tabs move the read to the day they name; **Maintenant** returns to
 * today and to the slot that is on.
 *
 * <h2>A day without an answer, and a day without a guide</h2>
 *
 * Until the day's read answers, a spinner — an initial load must never announce
 * an empty guide (`guide-interactions.md`). A source with no guide configured
 * draws nothing past the header (S7-03), and an answered day with nothing on
 * draws the one sentence a slot is allowed, invented cause left out.
 */
@Composable
internal fun GuideDayMobile(
    state: LiveState,
    channel: Channel,
    days: List<EpgDay>,
    activeDay: EpgDay,
    today: LocalDate,
    now: Instant,
    onBack: () -> Unit,
    onSelectDay: (EpgDay) -> Unit,
    onNow: () -> Unit,
    onDayVisible: (EpgDay, List<String>) -> Unit,
    onOpenProgramme: (channelId: String, channelName: String?, programme: EpgProgramme) -> Unit,
    onRetry: () -> Unit,
    onSeeChannels: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val guideDay = state.guideDay
    val guideState = guideStateOf(
        configured = guideDay.configured,
        status = guideDay.status,
        answered = guideDay.answered,
        programmes = guideDay.programmes,
    )
    val guideAge = guideAgeOf(guideDay.status)
    // A row with cached programmes is answered at once; an empty one only once
    // the read is terminal, so a load in progress keeps its spinner and never
    // announces an empty guide (GD-10).
    val answered = guideRowAnswered(
        channelId = channel.id,
        status = guideDay.status,
        answered = guideDay.answered,
        programmes = guideDay.programmes,
    )
    val blocks = remember(guideDay.programmes[channel.id], activeDay) {
        channelDayBlocks(guideDay.programmes[channel.id].orEmpty(), activeDay)
    }

    // The day this level is showing, read once for the channel it names: one
    // grouped request per day, dropped by the view model for a source the screen
    // has left (GD-03).
    LaunchedEffect(channel.id, activeDay) {
        onDayVisible(activeDay, listOf(channel.id))
    }

    Column(modifier = modifier.fillMaxSize()) {
        DayHeader(channel = channel, onBack = onBack, onNow = onNow)
        DayTabs(days = days, activeDay = activeDay, today = today, onSelectDay = onSelectDay)

        // No guide on this source: nothing to draw, and the header above keeps
        // the way out reachable (S7-03).
        if (guideDay.configured == false) return@Column

        // A stale guide, or one whose last import did not finish, dates itself
        // (GD-11); fresh and undated guides say nothing.
        guideAge?.let { age ->
            Text(
                text = guideAgeLabel(age),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.xs),
            )
        }

        // A failed read with no data is a screen of its own, never an empty day
        // (GD-10): Réessayer and Voir les chaînes.
        if (guideState == GuideState.InitialError) {
            LumoStateMessage(
                title = stringResource(R.string.feature_live_guide_error_title),
                body = stringResource(R.string.feature_live_guide_error_body),
                isError = true,
                actionLabel = stringResource(R.string.feature_live_guide_retry),
                onAction = onRetry,
                secondaryActionLabel = stringResource(R.string.feature_live_guide_see_channels),
                onSecondaryAction = onSeeChannels,
            )
            return@Column
        }

        // A failed read **with** data keeps the day on screen; only a distinct
        // line and a retry say the update failed (GD-10).
        if (guideState == GuideState.DataError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feature_live_guide_stale_body),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.feature_live_guide_retry),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
        }

        if (!answered) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val nowIndex = nowEntryIndex(blocks, now)
        // Arrival, a new day, a first read that lands after the screen is drawn,
        // and a press on **Maintenant**: all put the list on the slot that is on.
        // Once those have settled, a scroll is the user's — `blocks` and `now`
        // are equal on a plain recomposition, so nothing moves it again.
        LaunchedEffect(activeDay, now, blocks) {
            if (blocks.isNotEmpty()) listState.scrollToItem(nowIndex.coerceAtLeast(0))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = LumoSpacing.lg,
                vertical = LumoSpacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            itemsIndexed(blocks, key = { _, block -> block.key }) { index, block ->
                DayRow(
                    block = block,
                    current = index == nowIndex && !block.isEmpty,
                    onOpen = block.programme?.let { programme ->
                        { onOpenProgramme(channel.id, channel.name, programme) }
                    },
                )
            }
        }
    }
}

/** The channel's name, the way back, and **Maintenant**. */
@Composable
private fun DayHeader(channel: Channel, onBack: () -> Unit, onNow: () -> Unit) {
    val backDescription = stringResource(R.string.feature_live_guide_back)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_live_glyph_back),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = LumoSpacing.sm, vertical = LumoSpacing.xs)
                .semantics { contentDescription = backDescription },
        )
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.feature_live_guide_now),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onNow)
                .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        )
    }
}

/** The five day tabs, J−1 → J+3, on their own scrollable line. */
@Composable
private fun DayTabs(
    days: List<EpgDay>,
    activeDay: EpgDay,
    today: LocalDate,
    onSelectDay: (EpgDay) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = LumoSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { day ->
            val selected = day.date == activeDay.date
            Text(
                text = dayTabLabel(day.date, today),
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
                    .clickable { onSelectDay(day) }
                    .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
            )
        }
    }
}

/**
 * One programme of the day, or the neutral row a gap is.
 *
 * The full range is printed here — a phone row is the width of the screen, not
 * the ~200 dp a television cell is, so the range that #215 had to drop from the
 * grid reads comfortably on the day list. A gap draws the one sentence the
 * product allows and is never marked "current".
 */
@Composable
private fun DayRow(block: GuideBlock, current: Boolean, onOpen: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumoShapes.medium)
            .clickable(enabled = onOpen != null) { onOpen?.invoke() }
            .background(
                if (current) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(LumoSpacing.sm + LumoSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        if (block.isEmpty) {
            Text(
                text = stringResource(R.string.feature_live_guide_empty_slot),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            return@Column
        }

        Text(
            text = stringResource(
                R.string.feature_live_guide_time_range,
                formatTimeOfDay(block.startsAt),
                formatTimeOfDay(block.endsAt),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = block.title.orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A day's tab: "Aujourd'hui" for today, a short date otherwise. */
@Composable
private fun dayTabLabel(date: LocalDate, today: LocalDate): String {
    if (date == today) return stringResource(R.string.feature_live_guide_today)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        java.time.format.DateTimeFormatter.ofPattern("EEE d", locale).format(date)
    }
}
