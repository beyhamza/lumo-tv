/**
 * The five days a guide screen browses: **J−1 → J+3**, around "today" in the
 * zone it is given (US-16, S9-05 — the web twin of Android's `EpgDayWindow`).
 *
 * <h2>Instants, not local hours</h2>
 *
 * A guide grid is a comparison between instants; a local time is only how the
 * device prints one (guide-interactions.md, "heure locale"). So a day is two
 * instants — its first and the next day's first — and never `from + 24 h`: on
 * the spring-forward day a day lasts 23 hours, on the fall-back day 25, and a
 * zone that skips midnight starts at 01:00, not at an instant that is not
 * there. `from` is inclusive, `to` is exclusive, so a day's `to` is the next
 * day's `from` and a programme ending exactly at midnight belongs to one day,
 * not two.
 *
 * <h2>Bounds, and nothing promised behind them</h2>
 *
 * This returns days, not programmes. A day with no listing is still a day: the
 * screen decides what an empty slot says, and nothing here claims a provider
 * published anything for {@link EpgDay.date}. The grid (S9-05-02/03/04) asks
 * the server for what it needs and shows a gap as a gap.
 *
 * <h2>Pure, and whose zone</h2>
 *
 * The zone is a parameter and the clock is a parameter: no `new Date()`, no
 * default zone, no `Intl` resolved from the machine. The `Intl` calls below
 * always name an explicit `timeZone`, so the same `now` and zone give the same
 * five days on a server in UTC and on a laptop in Paris — which is what the
 * midnight and DST cases of GD-12 are tested against.
 *
 * <h2>Why the boundaries are searched and not guessed</h2>
 *
 * "First instant of a calendar day in a zone" cannot be obtained by adding the
 * zone offset to UTC midnight: on a day whose midnight does not exist the two
 * are not the same instant, and on the days a year the offset changes the
 * offset of `from` is not the offset of `to`. The local date of an instant is
 * monotonic, so the first instant carrying a wanted date is found by bisection
 * between two instants known to sit either side of it — no timezone database
 * of our own, and the same answer as `LocalDate.atStartOfDay(zone)`.
 */

/** How many days before "today" the window opens. The product's number. */
export const DAYS_BEFORE = 1;

/** How many days after "today" it closes. J−1 → J+3 is five days. */
export const DAYS_AFTER = 3;

/** {@link DAYS_BEFORE} + 1 + {@link DAYS_AFTER}, so a caller can size a grid. */
export const DAY_COUNT = DAYS_BEFORE + 1 + DAYS_AFTER;

const HOUR_MS = 60 * 60 * 1000;

/**
 * One day of the guide, as a half-open interval of instants.
 */
export type EpgDay = {
  /** The calendar day in the window's zone, `YYYY-MM-DD`. A label, never a comparison. */
  date: string;
  /** Inclusive: the first instant of {@link date} in that zone. */
  from: Date;
  /** Exclusive: the first instant of the next day, never `from + 24 h`. */
  to: Date;
};

/**
 * The {@link DAY_COUNT} days around `now`, from J−1 to J+3 inclusive.
 *
 * "Today" is `now` read in `zone`; the list is always contiguous, ordered from
 * oldest to newest, and each day's `to` is the next day's `from`.
 *
 * @param now the reference instant. Its local date in `zone`, and only that,
 *   decides the window — the hour it was read at changes nothing.
 * @param zone an IANA zone name (`"Europe/Paris"`). Required; there is no
 *   fallback to the machine's zone.
 * @throws RangeError when `zone` is not a known IANA zone.
 */
export function epgDayWindow(now: Date, zone: string): EpgDay[] {
  const today = localDateOf(now.getTime(), zone);
  const days: EpgDay[] = [];
  for (let offset = -DAYS_BEFORE; offset <= DAYS_AFTER; offset += 1) {
    const date = shiftDay(today, offset);
    const next = shiftDay(date, 1);
    days.push({
      date,
      from: new Date(startOfDay(date, zone)),
      to: new Date(startOfDay(next, zone)),
    });
  }
  return days;
}

/** Formatters are stateless and reusable; one per zone keeps bisection cheap. */
const formatters = new Map<string, Intl.DateTimeFormat>();

function formatterFor(zone: string): Intl.DateTimeFormat {
  let formatter = formatters.get(zone);
  if (formatter === undefined) {
    formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: zone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    });
    formatters.set(zone, formatter);
  }
  return formatter;
}

/** The calendar day an instant falls on in `zone`, as `YYYY-MM-DD`. */
function localDateOf(instant: number, zone: string): string {
  const parts = formatterFor(zone).formatToParts(new Date(instant));
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  // `formatToParts` on a valid zone always yields the three; a missing one
  // would mean the zone silently changed shape, which is not a value to guess.
  return `${year}-${month}-${day}`;
}

/** `date` moved by whole calendar days. UTC arithmetic: no DST, no zone. */
function shiftDay(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const shifted = new Date(Date.UTC(year, month - 1, day + days));
  return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}`;
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

/**
 * The first instant whose local date in `zone` is `date`, as epoch
 * milliseconds.
 *
 * Bisection rather than an offset addition, so a skipped midnight and a
 * 23- or 25-hour day both land on the real boundary. The bracket is widened by
 * more than any zone offset ever reaches (±14 h), so it always straddles the
 * first instant of the day.
 */
function startOfDay(date: string, zone: string): number {
  const [year, month, day] = date.split("-").map(Number);
  const utcMidnight = Date.UTC(year, month - 1, day);
  // Invariant kept below: local date at `low` is before `date`, local date at
  // `high` is `date` or later.
  let low = utcMidnight - 15 * HOUR_MS;
  let high = utcMidnight + 15 * HOUR_MS;
  while (high - low > 1) {
    const middle = low + Math.floor((high - low) / 2);
    if (localDateOf(middle, zone) >= date) high = middle;
    else low = middle;
  }
  return high;
}
