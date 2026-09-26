package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import tv.lumo.android.core.data.EpgDay
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * The mobile Guide's channel-day level (US-16, S9-05-04).
 *
 * <h2>What this level owns</h2>
 *
 * The grouped request itself, the drop of an answer for a source that has left,
 * and the keeping of the day's programmes are the view model's (`onDayVisible`,
 * `guideDay`) and are shared with the television grid. What this level owns is
 * the shape of a **channel's day** — [channelDayBlocks], which is the television
 * grid's `dayBlocks` under another name — and the two rules GD-13 names: Back
 * returns to the "En ce moment" list, and the list it returns to is where it was
 * ([GuideMobileLevel.back]). The scrolling position itself is Compose's, and is
 * kept because the list's state lives above the level switch.
 *
 * <h2>Instants, never local hours</h2>
 *
 * A programme that crosses midnight is clipped to the day and not dropped, and a
 * day is the half-open `[from, to)` interval, so the midnight boundary is one
 * test and not an evening of thumbing on a phone.
 */
class GuideDayMobileTest {

    private val day = EpgDay(
        date = LocalDate.parse("2026-09-24"),
        from = Instant.parse("2026-09-24T00:00:00Z"),
        to = Instant.parse("2026-09-25T00:00:00Z"),
    )

    // ---- what a channel's day is -------------------------------------------

    @Test
    fun `a programme started the day before is clipped to the day`() {
        val blocks = channelDayBlocks(
            programmes = listOf(
                programme("p1", "2026-09-23T23:00:00Z", "2026-09-24T01:00:00Z"),
            ),
            day = day,
        )

        assertThat(blocks.first().startsAt).isEqualTo(day.from)
        assertThat(blocks.first().endsAt).isEqualTo(Instant.parse("2026-09-24T01:00:00Z"))
        assertThat(blocks.first().title).isEqualTo("Programme p1")
    }

    @Test
    fun `a programme running past midnight stops at the day's end`() {
        val blocks = channelDayBlocks(
            programmes = listOf(
                programme("p1", "2026-09-24T23:30:00Z", "2026-09-25T00:30:00Z"),
            ),
            day = day,
        )

        assertThat(blocks.last().endsAt).isEqualTo(day.to)
        assertThat(blocks.last().title).isEqualTo("Programme p1")
    }

    @Test
    fun `a gap between two programmes is a block with no programme`() {
        val blocks = channelDayBlocks(
            programmes = listOf(
                programme("p1", "2026-09-24T20:00:00Z", "2026-09-24T20:30:00Z"),
                programme("p2", "2026-09-24T21:00:00Z", "2026-09-24T22:00:00Z"),
            ),
            day = day,
        )

        val gap = blocks.first { it.covers(Instant.parse("2026-09-24T20:45:00Z")) }
        assertThat(gap.isEmpty).isTrue()
        assertThat(gap.title).isNull()
    }

    @Test
    fun `a channel with nothing on answers one empty block`() {
        val blocks = channelDayBlocks(programmes = emptyList(), day = day)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single().isEmpty).isTrue()
        assertThat(blocks.single().startsAt).isEqualTo(day.from)
        assertThat(blocks.single().endsAt).isEqualTo(day.to)
    }

    // ---- what is on right now ----------------------------------------------

    @Test
    fun `the slot that is on is the one containing now`() {
        val blocks = channelDayBlocks(
            programmes = listOf(
                programme("p1", "2026-09-24T20:00:00Z", "2026-09-24T21:00:00Z"),
            ),
            day = day,
        )

        val index = nowEntryIndex(blocks, Instant.parse("2026-09-24T20:15:00Z"))
        assertThat(blocks[index].title).isEqualTo("Programme p1")
        assertThat(blocks[index].isEmpty).isFalse()
    }

    @Test
    fun `a gap can be the slot that is on`() {
        val blocks = channelDayBlocks(
            programmes = listOf(
                programme("p1", "2026-09-24T19:00:00Z", "2026-09-24T20:00:00Z"),
                programme("p2", "2026-09-24T21:00:00Z", "2026-09-24T22:00:00Z"),
            ),
            day = day,
        )

        val index = nowEntryIndex(blocks, Instant.parse("2026-09-24T20:30:00Z"))
        assertThat(blocks[index].isEmpty).isTrue()
    }

    @Test
    fun `now outside the day selects nothing`() {
        val blocks = channelDayBlocks(
            programmes = listOf(
                programme("p1", "2026-09-24T20:00:00Z", "2026-09-24T21:00:00Z"),
            ),
            day = day,
        )

        // The day is half-open: its own end belongs to the next day.
        assertThat(nowEntryIndex(blocks, day.to)).isEqualTo(-1)
        assertThat(nowEntryIndex(blocks, Instant.parse("2026-09-25T00:00:00Z"))).isEqualTo(-1)
    }

    // ---- the nesting GD-13 names -------------------------------------------

    @Test
    fun `back from a channel's day returns to the on-now list`() {
        assertThat(GuideMobileLevel.ChannelDay("c1").back()).isEqualTo(GuideMobileLevel.Now)
    }

    @Test
    fun `the level follows the channel whose day is open`() {
        assertThat(guideMobileLevel(LiveState())).isEqualTo(GuideMobileLevel.Now)
        assertThat(guideMobileLevel(LiveState(dayChannel = channel("c1"))))
            .isEqualTo(GuideMobileLevel.ChannelDay("c1"))
    }

    private fun channel(id: String) = Channel(
        id = id,
        sourceId = "s1",
        categoryId = null,
        name = "Channel $id",
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun programme(id: String, start: String, end: String) = EpgProgramme(
        id = id,
        startsAt = Instant.parse(start),
        endsAt = Instant.parse(end),
        title = "Programme $id",
        description = null,
        category = null,
    )
}
