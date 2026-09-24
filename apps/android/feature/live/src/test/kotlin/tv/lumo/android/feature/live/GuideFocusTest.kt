package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import tv.lumo.android.core.data.EpgDay
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * The television guide grid's hour-of-reference rule (US-16, S9-05-03,
 * GD-04/05/06).
 *
 * <h2>The example the design writes out, as a test</h2>
 *
 * Reference 20:25; row A 20:00–20:45, row B 20:15–20:30, row C 20:00–21:00.
 * Going down then back up must return A **without drifting to 20:00** — the
 * exact failure a reference-less grid makes, because the second row's shorter
 * programme would otherwise move the hour it compares from. The design draws
 * this in `docs/design/0.2.0/guide-interactions.md`; here it is executable.
 *
 * <h2>What else this pins</h2>
 *
 * A gap is a target and never removes its row (GD-06); left/right move one cell
 * of the same row and put the reference at its start (GD-05); the edges of the
 * day and of the row list do not wrap (GD-06); and the visible window only moves
 * when the selection leaves it. The remote itself is still verified on a real
 * one (`docs/design/tv-focus-map.md`) — this is the logic underneath it.
 */
class GuideFocusTest {

    private val day = EpgDay(
        date = LocalDate.of(2026, 9, 24),
        from = Instant.parse("2026-09-24T00:00:00Z"),
        to = Instant.parse("2026-09-25T00:00:00Z"),
    )

    private val now = at(20, 25)

    // ---- the reference hour, the example of the design ---------------------

    @Test
    fun `down then up returns the first row without drifting`() {
        val rows = listOf(
            GuideRow("a", listOf(programme("a1", at(20, 0), at(20, 45)))),
            GuideRow("b", listOf(programme("b1", at(20, 15), at(20, 30)))),
            GuideRow("c", listOf(programme("c1", at(20, 0), at(21, 0)))),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))
        assertThat(entered.rowIndex).isEqualTo(0)
        assertThat(entered.reference).isEqualTo(now)
        assertThat(block(rows, entered).programme?.id).isEqualTo("a1")

        val downB = moveVertical(rows, entered, +1, day)
        assertThat(downB.rowIndex).isEqualTo(1)
        assertThat(downB.reference).isEqualTo(now)
        assertThat(block(rows, downB).programme?.id).isEqualTo("b1")

        val downC = moveVertical(rows, downB, +1, day)
        assertThat(downC.rowIndex).isEqualTo(2)
        assertThat(block(rows, downC).programme?.id).isEqualTo("c1")

        val upB = moveVertical(rows, downC, -1, day)
        assertThat(upB.rowIndex).isEqualTo(1)
        assertThat(block(rows, upB).programme?.id).isEqualTo("b1")

        val upA = moveVertical(rows, upB, -1, day)
        assertThat(upA.rowIndex).isEqualTo(0)
        assertThat(upA.reference).isEqualTo(now)
        assertThat(block(rows, upA).programme?.id).isEqualTo("a1")
    }

    @Test
    fun `a shorter programme on the next row is selected at the same hour`() {
        val rows = listOf(
            GuideRow("a", listOf(programme("a1", at(20, 0), at(20, 45)))),
            GuideRow("b", listOf(programme("b1", at(20, 15), at(20, 30)))),
        )

        val selection = moveVertical(rows, checkNotNull(entrySelection(rows, day, now)), +1, day)

        assertThat(selection.reference).isEqualTo(now)
        assertThat(block(rows, selection).programme?.id).isEqualTo("b1")
    }

    // ---- the half-open interval -------------------------------------------

    @Test
    fun `a programme that ends exactly at the hour is over`() {
        val rows = listOf(
            GuideRow(
                "a",
                listOf(
                    programme("a1", at(20, 0), at(20, 30)),
                    programme("a2", at(20, 30), at(21, 0)),
                ),
            ),
        )

        val selection = checkNotNull(entrySelection(rows, day, at(20, 30)))

        assertThat(block(rows, selection).programme?.id).isEqualTo("a2")
    }

    @Test
    fun `a programme that starts at the hour is the one on`() {
        val rows = listOf(
            GuideRow(
                "a",
                listOf(
                    programme("a1", at(20, 0), at(20, 30)),
                    programme("a2", at(20, 30), at(21, 0)),
                ),
            ),
        )

        val selection = checkNotNull(entrySelection(rows, day, at(20, 0)))

        assertThat(block(rows, selection).programme?.id).isEqualTo("a1")
    }

    // ---- gaps --------------------------------------------------------------

    @Test
    fun `a gap is a neutral cell and does not skip the channel`() {
        val rows = listOf(
            GuideRow("a", listOf(programme("a1", at(20, 0), at(20, 45)))),
            GuideRow(
                "b",
                listOf(
                    programme("b1", at(20, 0), at(20, 15)),
                    programme("b2", at(20, 40), at(21, 0)),
                ),
            ),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))
        val down = moveVertical(rows, entered, +1, day)

        // 20:25 falls in B's gap 20:15–20:40: the row is kept and the gap cell
        // is selected, with no programme behind it.
        assertThat(down.rowIndex).isEqualTo(1)
        assertThat(block(rows, down).programme).isNull()
        assertThat(block(rows, down).startsAt).isEqualTo(at(20, 15))
    }

    @Test
    fun `the reference is clamped to the edge of a row thinner than the hour`() {
        val rows = listOf(
            GuideRow("a", listOf(programme("a1", at(20, 0), at(20, 45)))),
            GuideRow("b", listOf(programme("b1", at(18, 0), at(18, 30)))),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))
        val down = moveVertical(rows, entered, +1, day)

        // 20:25 is after everything on row B: the last cell (the gap from 18:30
        // to the end of the day) is the honest landing.
        assertThat(down.rowIndex).isEqualTo(1)
        assertThat(block(rows, down).programme).isNull()
        assertThat(block(rows, down).startsAt).isEqualTo(at(18, 30))
    }

    // ---- vertical edges ----------------------------------------------------

    @Test
    fun `the first and last rows do not wrap`() {
        val rows = listOf(
            GuideRow("a", listOf(programme("a1", at(20, 0), at(20, 45)))),
            GuideRow("b", listOf(programme("b1", at(20, 0), at(20, 45)))),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))
        assertThat(moveVertical(rows, entered, -1, day)).isEqualTo(entered)

        val last = moveVertical(rows, entered, +1, day)
        assertThat(moveVertical(rows, last, +1, day)).isEqualTo(last)
    }

    // ---- horizontal, GD-05 -------------------------------------------------

    @Test
    fun `right sets the reference to the start of the next cell`() {
        val rows = listOf(
            GuideRow(
                "b",
                listOf(
                    programme("b1", at(20, 15), at(20, 30)),
                    programme("b2", at(20, 30), at(21, 0)),
                ),
            ),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))
        val right = moveHorizontal(rows, entered, +1, day)

        assertThat(block(rows, right).programme?.id).isEqualTo("b2")
        assertThat(right.reference).isEqualTo(at(20, 30))

        // And up from there selects the programme covering 20:30, the design's
        // second mermaid step.
        val rowsWithA = rows + GuideRow("a", listOf(programme("a1", at(20, 0), at(20, 45))))
        val up = moveVertical(rowsWithA, right, +1, day)
        assertThat(up.rowIndex).isEqualTo(1)
        assertThat(block(rowsWithA, up).programme?.id).isEqualTo("a1")
        assertThat(up.reference).isEqualTo(at(20, 30))
    }

    @Test
    fun `left and right do not wrap at the day's edges`() {
        val rows = listOf(
            GuideRow(
                "a",
                listOf(
                    programme("a1", at(18, 0), at(19, 0)),
                    programme("a2", at(22, 0), at(23, 0)),
                ),
            ),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))
        // 20:25 is in the gap 19:00–22:00, block index 2 of five.
        val leftMost = moveHorizontal(rows, moveHorizontal(rows, entered, -1, day), -1, day)
        val leftAtEdge = moveHorizontal(rows, leftMost, -1, day)
        assertThat(leftMost.blockIndex).isEqualTo(0)
        assertThat(leftAtEdge.blockIndex).isEqualTo(0)

        val rightMost =
            moveHorizontal(rows, moveHorizontal(rows, entered, +1, day), +1, day)
        val rightAtEdge = moveHorizontal(rows, rightMost, +1, day)
        assertThat(rightMost.blockIndex).isEqualTo(4)
        assertThat(rightAtEdge.blockIndex).isEqualTo(4)
    }

    // ---- the moving window -------------------------------------------------

    @Test
    fun `the window does not move while the reference is visible`() {
        val visible = Duration.ofHours(3)
        val previous = GuideWindow(at(20, 0), at(23, 0))

        val moved = guideWindow(previous, at(21, 30), day, visible)

        assertThat(moved).isEqualTo(previous)
    }

    @Test
    fun `the window moves only when the reference leaves it`() {
        val visible = Duration.ofHours(3)
        val previous = GuideWindow(at(10, 0), at(13, 0))

        val after = guideWindow(previous, at(14, 0), day, visible)

        assertThat(after.from).isEqualTo(at(14, 0))
        assertThat(after.to).isEqualTo(at(17, 0))
    }

    @Test
    fun `the window is clamped inside the day`() {
        val visible = Duration.ofHours(3)
        val previous = GuideWindow(at(0, 0), at(3, 0))

        val after = guideWindow(previous, at(23, 30), day, visible)

        assertThat(after.to).isEqualTo(day.to)
        assertThat(after.from).isEqualTo(day.to.minus(visible))
    }

    @Test
    fun `a day shorter than the window gives the whole day`() {
        val twentyThreeHourDay = EpgDay(
            date = LocalDate.of(2026, 3, 29),
            from = Instant.parse("2026-03-28T23:00:00Z"),
            to = Instant.parse("2026-03-29T22:00:00Z"),
        )

        val window = guideWindow(
            previous = GuideWindow(twentyThreeHourDay.from, twentyThreeHourDay.from.plusSeconds(3600)),
            reference = twentyThreeHourDay.to.minusSeconds(60),
            day = twentyThreeHourDay,
            visible = Duration.ofHours(24),
        )

        assertThat(window.from).isEqualTo(twentyThreeHourDay.from)
        assertThat(window.to).isEqualTo(twentyThreeHourDay.to)
    }

    // ---- blocks ------------------------------------------------------------

    @Test
    fun `the blocks fill the gaps, in order, clipped to the window`() {
        val blocks = dayBlocks(
            programmes = listOf(
                // Ends exactly at the window's start: not of this window.
                programme("before", at(19, 0), at(19, 30)),
                // Overlaps the window's start: drawn from the window's start.
                programme("crossing", at(19, 0), at(20, 0)),
                programme("a2", at(20, 30), at(21, 30)),
            ),
            from = at(19, 30),
            to = at(21, 30),
        )

        assertThat(blocks.map { it.key }).containsExactly(
            "crossing",
            "gap-" + at(20, 0).toEpochMilli(),
            "a2",
        ).inOrder()
        assertThat(blocks.first().startsAt).isEqualTo(at(19, 30))
        assertThat(blocks.first().title).isEqualTo("Programme crossing")
        assertThat(blocks.last().endsAt).isEqualTo(at(21, 30))
        assertThat(blocks.last().programme?.id).isEqualTo("a2")
    }

    @Test
    fun `an empty row is one neutral cell for the whole day`() {
        val blocks = dayBlocks(emptyList(), day.from, day.to)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single().isEmpty).isTrue()
        assertThat(blocks.single().startsAt).isEqualTo(day.from)
        assertThat(blocks.single().endsAt).isEqualTo(day.to)
    }

    @Test
    fun `hour marks step by absolute hours to the day's end`() {
        val marks = hourMarks(day.from, day.to)

        assertThat(marks).hasSize(25)
        assertThat(marks.first()).isEqualTo(day.from)
        assertThat(marks.last()).isEqualTo(day.to)
        assertThat(marks[1]).isEqualTo(day.from.plusSeconds(3600))
    }

    // ---- placeholders (Paging skeletons) -----------------------------------

    @Test
    fun `a vertical move steps over a row Paging has not loaded`() {
        val rows = listOf<GuideRow?>(
            GuideRow("a", listOf(programme("a1", at(20, 0), at(20, 45)))),
            null,
            GuideRow("c", listOf(programme("c1", at(20, 0), at(21, 0)))),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))
        val down = moveVertical(rows, entered, +1, day)

        assertThat(down.rowIndex).isEqualTo(2)
        assertThat(block(rows, down).programme?.id).isEqualTo("c1")
        assertThat(down.reference).isEqualTo(now)
    }

    @Test
    fun `entry lands on the first loaded row`() {
        val rows = listOf<GuideRow?>(
            null,
            GuideRow("b", listOf(programme("b1", at(20, 0), at(20, 45)))),
        )

        val entered = checkNotNull(entrySelection(rows, day, now))

        assertThat(entered.rowIndex).isEqualTo(1)
        assertThat(block(rows, entered).programme?.id).isEqualTo("b1")
    }

    // ---- helpers -----------------------------------------------------------

    private fun block(rows: List<GuideRow?>, selection: GuideSelection): GuideBlock =
        dayBlocks(checkNotNull(rows[selection.rowIndex]).programmes, day.from, day.to)[selection.blockIndex]

    private fun at(hour: Int, minute: Int): Instant =
        LocalDate.of(2026, 9, 24).atTime(hour, minute).toInstant(ZoneOffset.UTC)

    private fun programme(id: String, start: Instant, end: Instant) = EpgProgramme(
        id = id,
        startsAt = start,
        endsAt = end,
        title = "Programme $id",
        description = null,
        category = null,
    )
}
