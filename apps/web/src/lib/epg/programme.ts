import type { EpgChannelProgrammes, EpgProgramme } from "@/lib/api/types";

/**
 * The programme sheet's temporal rule, web side (S9-06-04, GD-07/08).
 *
 * <h2>One rule, mirrored from the television, never rewritten in the panel</h2>
 *
 * The Android sheet (S9-06-01, `ProgrammeSheet.kt`) defines three pure
 * functions — `momentOf`, `watchAvailable`, `watchAllowed` — precisely so the
 * panel does not re-implement the rule in line (review D1). This is the same
 * rule, same half-open interval: a programme is **current** from `starts_at`
 * inclusive to `ends_at` exclusive, and only a current programme offers
 * « Regarder en direct ». It is a plain function of two instants, so it is a
 * plain unit test, and so the sheet can re-read the instant at activation
 * without a second copy drifting from the one it draws (GD-08).
 *
 * <h2>Why the instant is a parameter and not `new Date()`</h2>
 *
 * The browser's clock and the server's are two clocks. A qualification session
 * pins the server's with `LUMO_NOW` (`lib/epg/clock.ts`), which a client
 * component can neither import (`server-only`) nor read as an environment
 * variable. The sheet therefore receives the server's instant as a prop and
 * advances it with the browser's own elapsed time (`live-clock.ts`); every
 * decision below takes that instant as an argument, so the two clocks never
 * have to be compared directly.
 */

/** Which of the three moments a programme is in at an instant (GD-07/08). */
export type ProgrammeMoment = "future" | "current" | "past";

/** The two instants a moment is decided from; `EpgProgramme` satisfies it. */
export type TimedProgramme = Pick<EpgProgramme, "starts_at" | "ends_at">;

/** An instant as a `Date` or as milliseconds since the epoch. */
type Instant = Date | number;

function toMillis(instant: Instant): number {
  return instant instanceof Date ? instant.getTime() : instant;
}

/**
 * The moment of `programme` at `now`.
 *
 * Start inclusive, end exclusive: at 21:00 exactly a `20:00–21:00` programme is
 * over, and the `21:00–` one is current. Instants only, never local hours — the
 * midnight and DST cases (QA-06-L02/L03) are the same comparison here.
 *
 * A time that cannot be read is reported as **past**: the action is not offered
 * for something the product cannot place in time. The grid already skips such a
 * programme; this is the same posture for the one that reaches the sheet.
 */
export function momentOf(programme: TimedProgramme, now: Instant): ProgrammeMoment {
  const starts = Date.parse(programme.starts_at);
  const ends = Date.parse(programme.ends_at);
  if (Number.isNaN(starts) || Number.isNaN(ends)) return "past";

  const at = toMillis(now);
  if (at < starts) return "future";
  if (at < ends) return "current";
  return "past";
}

/** The action is offered for a current programme and no other. */
export function watchAvailable(moment: ProgrammeMoment): boolean {
  return moment === "current";
}

/**
 * GD-08: whether the watch action may run when it is pressed.
 *
 * The moment is read **again** at that instant, so a sheet left open across the
 * programme's end refuses the action even though the panel drew it — the
 * instant's being advanced by the client clock does not weaken the check.
 */
export function watchAllowed(programme: TimedProgramme, now: Instant): boolean {
  return watchAvailable(momentOf(programme, now));
}

/**
 * The next instant at which {@link momentOf} changes, or `undefined` when it
 * never does again.
 *
 * The client clock has no business ticking: it wakes exactly when a programme
 * starts (future → current) or ends (current → past) and sleeps otherwise. A
 * past or unreadable programme has no next transition.
 */
export function nextTransitionAt(
  programme: TimedProgramme,
  now: Instant,
): number | undefined {
  const starts = Date.parse(programme.starts_at);
  const ends = Date.parse(programme.ends_at);
  if (Number.isNaN(starts) || Number.isNaN(ends)) return undefined;

  const at = toMillis(now);
  if (at < starts) return starts;
  if (at < ends) return ends;
  return undefined;
}

/** The programme of `?programme=`, and the channel that carries it. */
export type ProgrammeSelection = {
  channel: EpgChannelProgrammes;
  programme: EpgProgramme;
};

/**
 * Resolve a selected programme inside the guide window the page already read.
 *
 * The sheet reads no guide of its own: a selected programme is looked up in the
 * single grouped answer (S9-03), and an identifier that is not in that window —
 * a stale link, another day — opens no sheet rather than a fabricated one.
 */
export function findProgrammeInGrid(
  channels: readonly EpgChannelProgrammes[],
  programmeId: string,
): ProgrammeSelection | undefined {
  for (const channel of channels) {
    const programme = channel.programmes.find((candidate) => candidate.id === programmeId);
    if (programme) return { channel, programme };
  }
  return undefined;
}
