import type { Channel, EpgChannelProgrammes, EpgProgramme } from "@/lib/api/types";
import type { EpgDay } from "@/lib/epg/day-window";
import { clockTime } from "@/lib/epg/format";
import type { EpgWindow } from "@/lib/epg/load-epg-window";

/**
 * The web time grid of the guide: channels as rows, hours as columns (US-16,
 * S9-05-02, GD-05/06).
 *
 * <h2>One day at a time, one request for it</h2>
 *
 * The caller reads **one** grouped window — the day it is about to draw — with
 * one call to `loadEpgWindow`, whatever the number of rows below. This
 * component only lays that answer out: it takes the `EpgWindow` its parent
 * already fetched and never asks for anything itself. The sprint's rule is
 * that the number of `/epg` calls must not depend on the number of cards,
 * channels or cells, and a component that fetched inside a row would break it
 * exactly there.
 *
 * <h2>Instants, never local hours</h2>
 *
 * Every position and every comparison is a fraction of the day's two instants
 * (`from` inclusive, `to` exclusive). It is what makes a spring-forward day of
 * 23 hours, a fall-back day of 25, and a programme crossing midnight all land
 * where they belong: a grid measured in "20:00" would put two distinct
 * instants at the same x on the day the clock repeats (GD-12). The hour labels
 * are printed through {@link clockTime}, with the zone the caller passes in —
 * never `new Date()` resolved from the machine.
 *
 * <h2>An empty slot says so, an absent guide says nothing</h2>
 *
 * A row with a gap, or with no programme at all over this day, renders
 * **« Aucun programme disponible sur ce créneau »** — the one sentence the
 * product puts in a slot, and it invents no cause for the emptiness
 * (guide-interactions.md). The whole grid renders **nothing** when the read
 * did not answer, or when the source has no guide configured at all
 * (`epg.configured` false): that absence is explained by S9-06, not turned
 * here into a wall of "nothing available" (S7-03).
 *
 * <h2>Links, because the zone runs without JavaScript</h2>
 *
 * The day tabs, **Maintenant**, the way out to the channel list and the
 * channel names are all `<a href>` the caller builds. The page is a Server
 * Component and the grid holds no state (AGENTS.md §3).
 */

/** One hour, in milliseconds. The one granularity a label column needs. */
export const HOUR_MS = 60 * 60 * 1000;

/**
 * The width of one hour column, in pixels.
 *
 * 128 px is about eight characters of a 30-minute title — enough for a name or
 * an hour, where a fixed `48rem` across 24 hours left ~32 px per hour and cut
 * both to a glyph or two (S9-05-02). The day is as wide as its hours need and
 * the row scrolls; the grid never squeezes the hours back down to fit.
 */
export const HOUR_COLUMN_PX = 128;

/** The channel-name column, matching the `w-40` it is drawn with. */
export const NAME_COLUMN_PX = 160;

/**
 * The inner grid's width: the name column plus every hour of the day.
 *
 * Every hour the day actually has, not a fixed 24: a spring-forward day draws
 * 23 columns and a fall-back day 25, exactly as {@link hourMarks} steps them,
 * so a column keeps its width on those days. An hour is never narrower than
 * {@link HOUR_COLUMN_PX}; the horizontal scroll absorbs the rest.
 */
export function gridWidth(from: Date, to: Date): number {
  const hours = Math.max(1, Math.ceil((to.getTime() - from.getTime()) / HOUR_MS));
  return NAME_COLUMN_PX + hours * HOUR_COLUMN_PX;
}

/** The minimum a block needs; `EpgProgramme` satisfies it. */
type Timed = Pick<EpgProgramme, "id" | "title" | "starts_at" | "ends_at">;

/**
 * One rectangle of a row, as a fraction of the day.
 *
 * `empty` marks a gap between two programmes (or the whole of a row), which is
 * drawn as the empty-slot sentence rather than as a programme.
 */
export type GridBlock = {
  /** Stable React key; `gap-<start>` for an empty slot. */
  key: string;
  title?: string;
  startsAt: number;
  endsAt: number;
  /** `from`-relative start, 0–1. */
  left: number;
  /** Extent, strictly positive, 0–1. */
  width: number;
  empty: boolean;
};

/**
 * The instants where an hour column starts, plus the day's exclusive end.
 *
 * Stepped by fixed {@link HOUR_MS} from `from` and not rebuilt from calendar
 * hours, so a 23-hour day yields 23 intervals and a 25-hour day 25 — the
 * duplicates a fall-back hour prints are the honest picture of that instant.
 * The last element is always `to`, so a caller can use each mark as a column
 * boundary without a special case.
 */
export function hourMarks(from: Date, to: Date): Date[] {
  const start = from.getTime();
  const end = to.getTime();
  const marks: Date[] = [];
  for (let instant = start; instant < end; instant += HOUR_MS) {
    marks.push(new Date(instant));
  }
  marks.push(new Date(end));
  return marks;
}

/**
 * A day's programmes, clipped to `[from, to)` and with the gaps filled.
 *
 * A programme is placed by its instants: one that started yesterday is drawn
 * from `from`, one that ends after the window stops at `to`, one that ends
 * exactly at `from` or starts exactly at `to` is not of this day. Programmes
 * are taken in start order; one that would begin before the previous ended
 * starts where the previous left off, so overlapping listings never produce a
 * negative rectangle. An unreadable time is a programme that cannot be placed
 * and is skipped, not a reason to place nothing.
 *
 * A day with no programmes is one empty block: the whole row is a créneau
 * without data, and the caller prints the one sentence it is allowed to.
 */
export function dayBlocks(
  programmes: readonly Timed[],
  from: Date,
  to: Date,
): GridBlock[] {
  const start = from.getTime();
  const end = to.getTime();
  const span = end - start;
  if (span <= 0) return [];

  const placed = programmes
    .map((programme) => ({
      ...programme,
      starts: Date.parse(programme.starts_at),
      ends: Date.parse(programme.ends_at),
    }))
    .filter((programme) => !Number.isNaN(programme.starts) && !Number.isNaN(programme.ends))
    .filter((programme) => programme.starts < end && programme.ends > start)
    .sort((a, b) => a.starts - b.starts || a.id.localeCompare(b.id));

  const blocks: GridBlock[] = [];
  let cursor = start;

  for (const programme of placed) {
    const begins = Math.max(programme.starts, cursor);
    const finishes = Math.min(programme.ends, end);
    if (begins > cursor) blocks.push(emptyBlock(cursor, begins, start, span));
    if (finishes > begins) {
      blocks.push({
        key: programme.id,
        title: programme.title,
        startsAt: begins,
        endsAt: finishes,
        left: (begins - start) / span,
        width: (finishes - begins) / span,
        empty: false,
      });
      cursor = finishes;
    }
  }

  if (cursor < end) blocks.push(emptyBlock(cursor, end, start, span));
  return blocks;
}

function emptyBlock(
  startsAt: number,
  endsAt: number,
  dayStart: number,
  span: number,
): GridBlock {
  return {
    key: `gap-${startsAt}`,
    startsAt,
    endsAt,
    left: (startsAt - dayStart) / span,
    width: (endsAt - startsAt) / span,
    empty: true,
  };
}

/**
 * A day's tab label, `lun. 24 sept.`, in the reader's language.
 *
 * The date is a calendar label, not an instant: it is formatted in UTC so that
 * a server in one zone and a reader in another print the same day (the rule
 * `format.ts` applies to times, with the zone made explicit rather than
 * defaulted).
 */
export function dayLabel(date: string, locale: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat(locale, {
    weekday: "short",
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

/** The sentences and names the grid draws, all resolved by the caller. */
export type EpgGridLabels = {
  /** Accessible name of the whole grid. */
  grid: string;
  /** Accessible name of the day tabs. */
  days: string;
  /** Replaces a day's date when it is today. */
  today: string;
  /** The control that returns to today. */
  nowButton: string;
  /** The one sentence an empty slot may say. */
  emptySlot: string;
  /** The way out to the channel list. */
  seeChannels: string;
};

export function EpgGrid({
  window: guide,
  channels,
  days,
  activeDate,
  todayDate,
  timeZone,
  locale,
  labels,
  dayHref,
  nowHref,
  channelsHref,
  playHref,
}: {
  /** The caller's single grouped read for the displayed day. */
  window: EpgWindow;
  channels: readonly Channel[];
  days: readonly EpgDay[];
  /** The day being drawn, `YYYY-MM-DD`; caller-resolved to a day of `days`. */
  activeDate: string;
  todayDate: string;
  timeZone: string;
  locale: string;
  labels: EpgGridLabels;
  dayHref: (date: string) => string;
  nowHref: string;
  channelsHref: string;
  playHref: (channelId: string) => string;
}) {
  // A read that did not answer, or a source with no guide at all, draws no
  // grid and no sentence (S7-03). S9-06 owns the "no guide" explanation.
  if (guide.state !== "ok" || !guide.grid.epg.configured) return null;

  const active = days.find((day) => day.date === activeDate) ?? days[0];
  if (!active) return null;

  const marks = hourMarks(active.from, active.to);
  const byChannel = new Map<string, EpgChannelProgrammes>(
    guide.grid.channels.map((row) => [row.channel_id, row]),
  );

  return (
    <section aria-label={labels.grid}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <nav aria-label={labels.days}>
          <ul className="flex flex-wrap gap-2 text-sm">
            {days.map((day) => (
              <li key={day.date}>
                <a
                  href={dayHref(day.date)}
                  aria-current={day.date === activeDate ? "page" : undefined}
                  className={
                    day.date === activeDate
                      ? "bg-secondary text-secondary-foreground block rounded-lg px-3 py-1.5 font-medium"
                      : "text-muted-foreground hover:text-foreground block rounded-lg px-3 py-1.5"
                  }
                >
                  {day.date === todayDate ? labels.today : dayLabel(day.date, locale)}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        {/* Both ways out of the grid, reachable without scrolling a row: the
            current slot, and the channel list for somebody whose guide is not
            enough (GD-06). */}
        <div className="flex items-center gap-2">
          <a
            href={nowHref}
            aria-current={activeDate === todayDate ? "page" : undefined}
            className="border-border hover:bg-secondary/60 rounded-lg border px-3 py-1.5 text-sm font-medium"
          >
            {labels.nowButton}
          </a>
          <a
            href={channelsHref}
            className="text-muted-foreground hover:text-foreground rounded-lg px-3 py-1.5 text-sm font-medium"
          >
            {labels.seeChannels}
          </a>
        </div>
      </div>

      <div className="mt-4 overflow-x-auto">
        {/* As wide as its hours need, never narrower: at 128 px an hour a
            30-minute block keeps enough room for a title and its two times
            (S9-05-02). Narrower viewports scroll this, they do not compress it. */}
        <div
          role="table"
          aria-label={labels.grid}
          style={{ minWidth: gridWidth(active.from, active.to) }}
        >
          <div className="flex" role="row">
            <div className="shrink-0" role="presentation" style={{ width: NAME_COLUMN_PX }} />
            <div className="border-border relative h-6 flex-1 border-b" role="presentation">
              {marks.slice(0, -1).map((mark) => (
                <span
                  key={mark.getTime()}
                  className="text-muted-foreground absolute top-0 text-[11px] tabular-nums"
                  style={{ left: `${fractionOf(mark.getTime(), active)}%` }}
                >
                  {clockTime(mark, locale, timeZone)}
                </span>
              ))}
            </div>
          </div>

          {channels.map((channel) => (
            <div key={channel.id} className="border-border flex border-b" role="row">
              <div
                className="shrink-0 py-2 pr-3"
                role="rowheader"
                style={{ width: NAME_COLUMN_PX }}
              >
                {/* A link, like a channel row of the Chaînes view: choosing a
                    channel plays it. The programme sheet is S9-06. */}
                <a
                  href={playHref(channel.id)}
                  className="block truncate text-sm font-medium underline-offset-4 hover:underline"
                >
                  {channel.name}
                </a>
              </div>

              <div className="relative h-14 flex-1" role="cell">
                {dayBlocks(byChannel.get(channel.id)?.programmes ?? [], active.from, active.to).map(
                  (block) =>
                    block.empty ? (
                      <div
                        key={block.key}
                        className="border-border/60 absolute inset-y-0.5 overflow-hidden border-l"
                        style={{ left: `${block.left * 100}%`, width: `${block.width * 100}%` }}
                      >
                        <span className="text-muted-foreground block truncate px-1.5 py-1 text-[11px]">
                          {labels.emptySlot}
                        </span>
                      </div>
                    ) : (
                      <div
                        key={block.key}
                        title={block.title}
                        className="border-border bg-secondary/40 absolute inset-y-0.5 overflow-hidden rounded border px-1.5 py-1"
                        style={{ left: `${block.left * 100}%`, width: `${block.width * 100}%` }}
                      >
                        <span className="block truncate text-xs font-medium">{block.title}</span>
                        <span className="text-muted-foreground block truncate text-[11px] tabular-nums">
                          {clockTime(new Date(block.startsAt), locale, timeZone)} –{" "}
                          {clockTime(new Date(block.endsAt), locale, timeZone)}
                        </span>
                      </div>
                    ),
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/** An instant's position in a day, 0–100, by its two bounds and nothing else. */
function fractionOf(instant: number, day: EpgDay): number {
  const span = day.to.getTime() - day.from.getTime();
  if (span <= 0) return 0;
  return ((instant - day.from.getTime()) / span) * 100;
}
