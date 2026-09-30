#!/usr/bin/env node
/**
 * The next 23:50 Europe/Paris, printed as an RFC 3339 UTC instant.
 *
 * GD-12 needs a session whose clock sits ten minutes before midnight in Paris:
 * the guide's `A1` then straddles midnight, and only a controlled clock can show
 * the 30th cutting it at 00:00 and the 1st resuming it there.
 *
 * The instant must be in the future, and it must be *computed* rather than
 * written down. The API's XMLTV importer keeps only
 * `[now − 1 day, now + 3 days]` (`XmltvStreamParser`), so a date committed to
 * the workflow would expire within days and quietly import nothing. This prints
 * the next occurrence, whatever day and whatever side of the DST switch that is:
 *
 *   node e2e/support/next-paris-2350.mjs   # 2026-09-30T21:50:00Z (23:50 CEST)
 *
 * Read by `.github/workflows/web.yml` for the opt-in anchored pass, and handed
 * to both `BENCH_EPG_ANCHOR` and `LUMO_NOW` through `e2e/support/stack.ts`.
 * Prints nothing else, so the workflow can capture stdout directly.
 */

const TIME_ZONE = "Europe/Paris";
const TARGET_HOUR = 23;
const TARGET_MINUTE = 50;

/** The wall-clock fields Paris shows for an instant. */
function parisParts(date) {
  const formatted = new Intl.DateTimeFormat("en-US", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(date);

  const fields = Object.fromEntries(formatted.map((part) => [part.type, part.value]));
  return {
    year: Number(fields.year),
    month: Number(fields.month),
    day: Number(fields.day),
    // `hour12: false` can render midnight as "24" in some runtimes.
    hour: Number(fields.hour) % 24,
    minute: Number(fields.minute),
  };
}

/**
 * The offset Paris is on at an instant, in minutes.
 *
 * Reads Paris' wall clock, treats it as if it were UTC, and takes the difference
 * from the real reading. At 23:50 there is never a DST transition in progress —
 * those happen at 02:00/03:00 — so the offset this returns is the one in force
 * for the target itself.
 */
function offsetMinutes(date) {
  const parts = parisParts(date);
  const asUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute);
  return Math.round((asUtc - date.getTime()) / 60_000);
}

/** A Paris wall-clock time to the real UTC instant. */
function instant(parts) {
  const guess = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, 0);
  return new Date(guess - offsetMinutes(new Date(guess)) * 60_000);
}

const now = new Date();
const today = parisParts(now);
let at = instant({ ...today, hour: TARGET_HOUR, minute: TARGET_MINUTE });

if (at.getTime() <= now.getTime()) {
  // Tomorrow, calendar-wise. `Date.UTC` normalises the rollover across month and
  // year bounds, so December and New Year need no special case.
  const tomorrow = new Date(Date.UTC(today.year, today.month - 1, today.day + 1, 12, 0, 0));
  at = instant({ ...parisParts(tomorrow), hour: TARGET_HOUR, minute: TARGET_MINUTE });
}

// No milliseconds: the bench parses the value with `date -u -D
// "%Y-%m-%dT%H:%M:%SZ"`, where `.000` matches nothing and the anchor silently
// falls back to the container's real time.
process.stdout.write(`${at.toISOString().replace(/\.\d{3}Z$/, "Z")}\n`);
