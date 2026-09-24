package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * The five days J−1→J+3 as instants (US-16, S9-05, GD-12).
 *
 * [EpgDayWindow.around] is pure — a zone and an instant in, bounds out — so the
 * cases that go wrong on a real evening can be written down without a clock or
 * a device: the day a program crosses midnight, the two days a year whose
 * length is not 24 hours, and the same instant read in two zones.
 *
 * The tests compare instants. Where a local date is asserted it is the label a
 * screen prints, never the thing the function reasoned on.
 */
class EpgDayWindowTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `five days, centred on the local date of now`() {
        val window = EpgDayWindow.around(local(paris, "2026-09-24T20:00"), paris)

        assertThat(window.map { it.date }).containsExactly(
            LocalDate.parse("2026-09-23"),
            LocalDate.parse("2026-09-24"),
            LocalDate.parse("2026-09-25"),
            LocalDate.parse("2026-09-26"),
            LocalDate.parse("2026-09-27"),
        ).inOrder()
        assertThat(window).hasSize(EpgDayWindow.DAY_COUNT)
        assertThat(window[EpgDayWindow.DAYS_BEFORE.toInt()].date)
            .isEqualTo(LocalDate.parse("2026-09-24"))
    }

    @Test
    fun `each day is exactly the calendar day in the zone, with no gap and no overlap`() {
        val window = EpgDayWindow.around(local(paris, "2026-09-24T20:00"), paris)

        window.forEach { day ->
            assertThat(day.from).isEqualTo(day.date.atStartOfDay(paris).toInstant())
            assertThat(day.to).isEqualTo(day.date.plusDays(1).atStartOfDay(paris).toInstant())
        }
        // A day's end is the next one's start.
        window.zipWithNext().forEach { (day, next) -> assertThat(day.to).isEqualTo(next.from) }
    }

    @Test
    fun `23h and 00h30 fall in different days`() {
        val window = EpgDayWindow.around(local(paris, "2026-09-24T23:00"), paris)

        val beforeMidnight = window.single { it.contains(local(paris, "2026-09-24T23:00")) }
        val afterMidnight = window.single { it.contains(local(paris, "2026-09-25T00:30")) }

        assertThat(beforeMidnight.date).isEqualTo(LocalDate.parse("2026-09-24"))
        assertThat(afterMidnight.date).isEqualTo(LocalDate.parse("2026-09-25"))
        assertThat(beforeMidnight).isNotEqualTo(afterMidnight)
    }

    @Test
    fun `spring forward keeps five days, and the shortened day lasts 23 hours`() {
        // Europe/Paris, 2026-03-29: 02:00 CET jumps to 03:00 CEST.
        val window = EpgDayWindow.around(local(paris, "2026-03-29T12:00"), paris)

        assertThat(window.map { it.date }).containsExactly(
            LocalDate.parse("2026-03-28"),
            LocalDate.parse("2026-03-29"),
            LocalDate.parse("2026-03-30"),
            LocalDate.parse("2026-03-31"),
            LocalDate.parse("2026-04-01"),
        ).inOrder()

        val shortened = window.single { it.date == LocalDate.parse("2026-03-29") }
        assertThat(Duration.between(shortened.from, shortened.to)).isEqualTo(Duration.ofHours(23))
        // Its neighbours are ordinary days, and the bounds stay contiguous.
        assertThat(Duration.between(window.first().from, window.first().to))
            .isEqualTo(Duration.ofHours(24))
        window.zipWithNext().forEach { (day, next) -> assertThat(day.to).isEqualTo(next.from) }
    }

    @Test
    fun `fall back keeps five days, and the lengthened day lasts 25 hours`() {
        // Europe/Paris, 2026-10-25: 03:00 CEST falls back to 02:00 CET.
        val window = EpgDayWindow.around(local(paris, "2026-10-25T12:00"), paris)

        assertThat(window).hasSize(EpgDayWindow.DAY_COUNT)
        val lengthened = window.single { it.date == LocalDate.parse("2026-10-25") }
        assertThat(Duration.between(lengthened.from, lengthened.to)).isEqualTo(Duration.ofHours(25))
    }

    @Test
    fun `the zone parameter drives the window, not the machine's zone`() {
        // One instant: 23:30 UTC is already the 25th in Paris (UTC+2) and still
        // the 24th in New York (UTC-4). Both the first date and the first
        // instant differ, so nothing defaulted to the machine's zone.
        val now = Instant.parse("2026-09-24T23:30:00Z")

        val inParis = EpgDayWindow.around(now, paris)
        val inNewYork = EpgDayWindow.around(now, newYork)

        assertThat(inParis.first().date).isEqualTo(LocalDate.parse("2026-09-24"))
        assertThat(inNewYork.first().date).isEqualTo(LocalDate.parse("2026-09-23"))
        assertThat(inParis.first().from).isNotEqualTo(inNewYork.first().from)
    }

    private fun local(zone: ZoneId, text: String): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun EpgDay.contains(instant: Instant): Boolean =
        !instant.isBefore(from) && instant.isBefore(to)
}
