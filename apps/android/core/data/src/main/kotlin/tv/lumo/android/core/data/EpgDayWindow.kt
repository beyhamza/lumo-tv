package tv.lumo.android.core.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day of the guide, as a half-open interval of instants.
 *
 * @param date the calendar day in the zone the window was built for. Kept for
 * the label a screen prints ("lundi 24"), never for a comparison.
 * @param from inclusive: the first instant of [date] in that zone.
 * @param to exclusive: the first instant of the next day. This is a different
 * instant from `from + 24 h` on the two days a year the offset changes.
 */
data class EpgDay(
    val date: LocalDate,
    val from: Instant,
    val to: Instant,
)

/**
 * The five days a guide screen browses: **J−1 → J+3**, around "today" in the
 * zone it is given (US-16, S9-05).
 *
 * <h2>Instants, not local hours</h2>
 *
 * A guide grid is a comparison between instants; a local time is only how the
 * device prints one (guide-interactions.md, "heure locale"). So a day is two
 * instants — its first and the next day's first — and never `from + 24 h`: on
 * the spring-forward day a day lasts 23 hours, on the fall-back day 25, and
 * `atStartOfDay` gives the first instant that actually exists in the zone (a
 * zone that skips midnight starts at 01:00, not at an instant that is not
 * there).
 *
 * <h2>Bounds, and nothing promised behind them</h2>
 *
 * This returns days, not programmes. A day with no listing is still a day: the
 * screen decides what an empty slot says, and nothing here claims a provider
 * published anything for [EpgDay.date]. S9-05-02/03/04 ask the server per day
 * and show the gap as a gap.
 *
 * <h2>Pure</h2>
 *
 * The zone is a parameter and there is no `TimeZone.getDefault()`, no `Clock`,
 * no `Locale` — the same [now] and zone give the same five days on every
 * device, which is what the DST and midnight cases of GD-12 are tested against.
 */
object EpgDayWindow {

    /** How many days before "today" the window opens. The product's number. */
    const val DAYS_BEFORE: Long = 1

    /** How many days after "today" it closes. J−1 → J+3 is five days. */
    const val DAYS_AFTER: Long = 3

    /** [DAYS_BEFORE] + 1 + [DAYS_AFTER], so a caller can size a grid. */
    const val DAY_COUNT: Int = (DAYS_BEFORE + DAYS_AFTER + 1).toInt()

    /**
     * The [DAY_COUNT] days around [now], from J−1 to J+3 inclusive.
     *
     * "Today" is [now] read in [zone]; the list is always contiguous, ordered
     * from oldest to newest, and each day's [EpgDay.to] is the next day's
     * [EpgDay.from].
     */
    fun around(now: Instant, zone: ZoneId): List<EpgDay> {
        val today = now.atZone(zone).toLocalDate()
        return (-DAYS_BEFORE..DAYS_AFTER).map { offset -> day(today.plusDays(offset), zone) }
    }

    private fun day(date: LocalDate, zone: ZoneId): EpgDay = EpgDay(
        date = date,
        from = date.atStartOfDay(zone).toInstant(),
        to = date.plusDays(1).atStartOfDay(zone).toInstant(),
    )
}
