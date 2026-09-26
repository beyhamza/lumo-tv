package tv.lumo.android.feature.live

import java.time.Duration
import java.time.Instant
import tv.lumo.android.core.data.EpgDay
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * The television guide grid's geometry and its D-pad rule, as pure functions
 * (US-16, S9-05-03, GD-04/05/06).
 *
 * <h2>Why this is a file of its own, and why it is pure</h2>
 *
 * The one thing the acceptance names about the grid is a rule no screen can be
 * trusted to hold by eye: **a vertical move keeps an hour of reference**, so a
 * programme of a different length under the same hour is selected, and going
 * down then back up returns where it started instead of drifting. Pinning that
 * to a Compose screen would leave it to a real remote to find a regression —
 * which is exactly the defect `docs/design/tv-focus-map.md` exists to prevent.
 * Everything here is a function of instants and lists: no Compose, no clock, no
 * `TimeZone.getDefault()`, so the example the design writes out (référence
 * 20:25, lignes A/B/C) is a plain unit test.
 *
 * <h2>Instants, never local hours</h2>
 *
 * A grid is a comparison between instants (guide-interactions.md, "heure
 * locale"). A day is `[from, to)`, half-open, and a programme's interval
 * includes its start and excludes its end: at 21:00 exactly the 21:00 programme
 * is the one on, and the 20:00–21:00 one is over. Nothing here formats or parses
 * an hour; the screen prints one with the device's zone, and this code never
 * looks at the wall clock.
 */

/**
 * One channel row of the grid: the channel's id and the day's listing.
 *
 * `programmes` is the whole day for that channel, sorted by start as the server
 * and the cache deliver it. An empty list is a real answer — the guide answered
 * and had nothing — not a hole, and the caller draws the one sentence a slot is
 * allowed (guide-interactions.md).
 */
internal data class GuideRow(
    val channelId: String,
    val programmes: List<EpgProgramme>,
    /**
     * Whether the day's read has answered for this channel. False is a row the
     * grid has no guide for yet: it draws nothing and the remote does not stop
     * on it — an initial load must not announce an empty guide
     * (guide-interactions.md, "Données absentes, chargement").
     */
    val answered: Boolean = true,
)

/**
 * One cell of a row: a programme, or the neutral cell that fills a gap.
 *
 * `programme == null` marks a gap before, between or after the day's
 * programmes. A gap is a **real target**: it may be selected and is drawn as an
 * empty slot, but it has no playback action, and a gap never removes its row
 * from the grid nor skips the channel on a vertical move (GD-06).
 */
internal data class GuideBlock(
    /** Stable identity: the programme's id, or `gap-<startEpochMillis>`. */
    val key: String,
    val title: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val programme: EpgProgramme?,
) {
    val isEmpty: Boolean get() = programme == null

    /** Start inclusive, end exclusive — the rule a programme is on follows. */
    fun covers(instant: Instant): Boolean =
        !instant.isBefore(startsAt) && instant.isBefore(endsAt)
}

/**
 * The remote's place on the grid.
 *
 * @param rowIndex the channel row, an index into the rows the screen drew.
 * @param blockIndex the selected block of that row, an index into
 *   [dayBlocks] of the row's programmes.
 * @param reference the instant a vertical move keeps. [moveHorizontal] moves it
 *   to the start of the block it lands on; [moveVertical] never changes it.
 */
internal data class GuideSelection(
    val rowIndex: Int,
    val blockIndex: Int,
    val reference: Instant,
)

/**
 * The part of the day the screen shows at once.
 *
 * A television cannot draw twenty-four readable hour columns, so the grid is a
 * moving window: [guideWindow] shifts it only when the selection leaves it
 * (guide-interactions.md: "la navigation temporelle déplace la fenêtre
 * uniquement si la cible sort du créneau visible").
 */
internal data class GuideWindow(val from: Instant, val to: Instant)

/**
 * A day's programmes clipped to `[from, to)` with the gaps filled.
 *
 * A programme that started yesterday is drawn from `from`, one that ends after
 * the window stops at `to`; one that ends exactly at `from` or starts exactly at
 * `to` is not of this window. Programmes are taken in start order and a listing
 * that would begin before the previous ended starts where the previous left off,
 * so overlapping data never yields a negative or doubled cell — the same rule
 * the web grid applies (S9-05-02, `dayBlocks`).
 *
 * A window with no programmes is one empty block, so a caller always has a cell
 * to draw rather than an absence to guess about.
 */
internal fun dayBlocks(
    programmes: List<EpgProgramme>,
    from: Instant,
    to: Instant,
): List<GuideBlock> {
    if (!from.isBefore(to)) return emptyList()

    val placed = programmes
        .filter { it.startsAt.isBefore(to) && it.endsAt.isAfter(from) }
        .sortedWith(compareBy({ it.startsAt }, { it.id }))

    val blocks = mutableListOf<GuideBlock>()
    var cursor = from

    for (programme in placed) {
        val begins = maxOf(programme.startsAt, cursor)
        val finishes = minOf(programme.endsAt, to)
        if (begins.isAfter(cursor)) blocks += gapBlock(cursor, begins)
        if (finishes.isAfter(begins)) {
            blocks += GuideBlock(
                key = programme.id,
                title = programme.title,
                startsAt = begins,
                endsAt = finishes,
                programme = programme,
            )
            cursor = finishes
        }
    }

    if (cursor.isBefore(to)) blocks += gapBlock(cursor, to)
    return blocks
}

private fun gapBlock(from: Instant, to: Instant) = GuideBlock(
    key = "gap-${from.toEpochMilli()}",
    title = null,
    startsAt = from,
    endsAt = to,
    programme = null,
)

/**
 * The cell the remote lands on when the Guide opens, or a day is picked.
 *
 * The first channel of the filtered result, and on it the programme that
 * contains [now] — or, in a gap, the neutral cell of that gap. The reference is
 * [now] itself when it falls inside [day], and the day's own start otherwise (a
 * day the viewer picked and that is not today has no "now" to speak of).
 *
 * Null only when there is no row to land on.
 */
internal fun entrySelection(rows: List<GuideRow?>, day: EpgDay, now: Instant): GuideSelection? {
    val index = rows.indexOfFirst { it?.answered == true }
    if (index < 0) return null
    val first = checkNotNull(rows[index])
    val reference = if (now >= day.from && now < day.to) now else day.from
    val blocks = dayBlocks(first.programmes, day.from, day.to)
    return GuideSelection(
        rowIndex = index,
        blockIndex = indexCovering(blocks, reference),
        reference = reference,
    )
}

/**
 * Up or down: the neighbouring channel, **at the same hour of reference**.
 *
 * That is the whole rule (GD-04). The row changes, the reference does not, and
 * the block selected on the new row is the one that covers it — so a twenty-five
 * minute programme and an hour-long one both answer to the same hour, and a
 * gap does not skip the channel: the gap's neutral cell is selected. At the top
 * and bottom rows the selection stays where it is and never wraps.
 */
internal fun moveVertical(
    rows: List<GuideRow?>,
    selection: GuideSelection,
    delta: Int,
    day: EpgDay,
): GuideSelection {
    if (rows.isEmpty()) return selection

    // A row Paging has not loaded is a skeleton, and a skeleton is never a
    // target: the move steps over it to the next row that has a channel
    // (docs/design/tv-focus-map.md). At the end of the list it stays put.
    var target = selection.rowIndex
    val step = if (delta > 0) 1 else -1
    repeat(kotlin.math.abs(delta)) {
        var candidate = target + step
        while (candidate in rows.indices && rows[candidate]?.answered != true) candidate += step
        if (candidate in rows.indices) target = candidate
    }
    if (target == selection.rowIndex) return selection

    val row = checkNotNull(rows[target])
    val blocks = dayBlocks(row.programmes, day.from, day.to)
    return selection.copy(
        rowIndex = target,
        blockIndex = indexCovering(blocks, selection.reference),
        reference = selection.reference,
    )
}

/**
 * Left or right: the adjacent cell of the **same row**, and the reference moves
 * to the start of the cell it lands on (GD-05).
 *
 * The cells are the row's blocks, gaps included, so the remote walks a gap one
 * cell at a time instead of jumping across it. At the first and last cells the
 * selection stays and never wraps around the day.
 */
internal fun moveHorizontal(
    rows: List<GuideRow?>,
    selection: GuideSelection,
    delta: Int,
    day: EpgDay,
): GuideSelection {
    val row = rows.getOrNull(selection.rowIndex) ?: return selection
    val blocks = dayBlocks(row.programmes, day.from, day.to)
    if (blocks.isEmpty()) return selection

    val target = (selection.blockIndex + delta).coerceIn(0, blocks.lastIndex)
    return selection.copy(
        blockIndex = target,
        reference = blocks[target].startsAt,
    )
}

/**
 * The window that shows [reference], moving the previous one only when it must.
 *
 * If [reference] is already inside [previous], the window does not move: a
 * vertical step that stays in the visible hours must not jog the whole grid
 * sideways. Otherwise the window is re-anchored on [reference], clamped so it
 * stays inside [day]. A day shorter than the window (the spring-forward day is
 * 23 hours, but a caller may ask for less) gives the whole day rather than a
 * window that runs past its end.
 */
internal fun guideWindow(
    previous: GuideWindow,
    reference: Instant,
    day: EpgDay,
    visible: Duration,
): GuideWindow {
    val daySpan = Duration.between(day.from, day.to)
    if (daySpan <= Duration.ZERO) return GuideWindow(day.from, day.to)

    val span = if (daySpan < visible) daySpan else visible
    if (reference >= previous.from && reference < previous.to) return previous

    val latestStart = day.to.minus(span)
    val start = maxOf(day.from, minOf(reference, latestStart))
    return GuideWindow(start, start.plus(span))
}

/**
 * The visible window the grid can afford, from the width its hour columns get.
 *
 * The design asks the television grid for **two hours** (`direct-guide.md`
 * S9-E02), and asks it to "se réorganiser pour rester lisible" at small widths.
 * So the window is not a constant: it is what fits while keeping a **half-hour
 * cell** readable — the smallest thing the grid draws, and the one
 * BUG-S9-05-03-01 caught cut to "…".
 *
 * The floor is therefore on the half hour and not the hour. A 200 dp hour (the
 * earlier, wrong floor) let a 30-minute cell fall to ~100 dp on the 1080p panel
 * and clip its title and its times. Two hours are shown once two readable
 * half-hours fit per hour — `4 × [MIN_HALF_HOUR_WIDTH_DP]` of columns — one
 * below, never zero, so a narrow grid still draws a column instead of nothing,
 * and never more than the target, so a wide screen does not drift back to the
 * three-hour window.
 *
 * Pure, like the rest of this file: the composable measures the hour columns
 * once and passes their width, the test passes numbers.
 */
internal fun guideVisibleWindow(hourColumnsWidthDp: Int): Duration {
    val hoursThatFit = hourColumnsWidthDp / (2 * MIN_HALF_HOUR_WIDTH_DP)
    return when {
        hoursThatFit >= GUIDE_TARGET_HOURS -> GUIDE_TARGET_WINDOW
        hoursThatFit < 1 -> GUIDE_MIN_WINDOW
        else -> Duration.ofHours(hoursThatFit.toLong())
    }
}

/**
 * The width a **half-hour** cell needs to stay readable at three metres: room
 * for a title and the two times under it once the cell's own padding is out
 * (BUG-S9-05-03-01).
 */
internal const val MIN_HALF_HOUR_WIDTH_DP: Int = 144

/** The window the design asks for: two hours (direct-guide.md S9-E02). */
internal val GUIDE_TARGET_WINDOW: Duration = Duration.ofHours(2)

/** Never fewer than one hour, so a narrow grid still draws a column. */
internal val GUIDE_MIN_WINDOW: Duration = Duration.ofHours(1)

private const val GUIDE_TARGET_HOURS: Int = 2

/**
 * The block of [blocks] that covers [instant], clamped to the ends.
 *
 * The blocks tile the day, so a reference inside it always finds one. The clamp
 * only serves a reference that fell outside the day — after switching to a day
 * the viewer did not measure — and then the first or last cell is the honest
 * landing rather than an index that does not exist.
 */
private fun indexCovering(blocks: List<GuideBlock>, instant: Instant): Int {
    if (blocks.isEmpty()) return 0
    val index = blocks.indexOfFirst { it.covers(instant) }
    if (index >= 0) return index
    return if (instant.isBefore(blocks.first().startsAt)) 0 else blocks.lastIndex
}

/**
 * The instants an hour column starts at, from [from] stepped by fixed [step],
 * plus [to] as the exclusive end.
 *
 * Stepped by an absolute duration and not rebuilt from calendar hours: a
 * spring-forward day yields 23 intervals and a fall-back day 25, and the
 * duplicated hour a fall-back prints is the honest picture of that instant (the
 * web grid's `hourMarks`, S9-05-02). The last mark is always [to], so a caller
 * can use each mark as a column boundary without a special case.
 */
internal fun hourMarks(
    from: Instant,
    to: Instant,
    step: Duration = Duration.ofHours(1),
): List<Instant> {
    if (!from.isBefore(to) || step <= Duration.ZERO) return emptyList()
    val marks = mutableListOf<Instant>()
    var instant = from
    while (instant < to) {
        marks += instant
        instant = instant.plus(step)
    }
    marks += to
    return marks
}
