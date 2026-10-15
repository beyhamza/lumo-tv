/**
 * Time actually watched, measured in wall-clock intervals (S12-02, `adr/0012`
 * ruling 1).
 *
 * <h2>What counts</h2>
 *
 * An interval opens when the media element is genuinely playing — not paused,
 * not seeking, not waiting for data, not ended, and on a visible page — and
 * closes the moment any of those stops being true. A seek therefore produces no
 * interval at all (CW-04), and neither does a pause or a stall (CW-05). The
 * server keeps the union of what every device sends, so this side only has to
 * be honest about its own playback: overlap with another device, or the same
 * interval sent twice, is the server's business and costs nothing (CW-29, CW-33).
 *
 * <h2>Why a class with an injected clock</h2>
 *
 * The rules are a small state machine driven by media events, and the only way
 * to pin "play, buffer, seek, play" sequences in a test is to drive it with a
 * clock the test owns. Nothing here touches the DOM.
 *
 * <h2>Flushing</h2>
 *
 * Every save {@link take}s what has accumulated: the open interval is cut at
 * the save's instant and re-opened right there, so a long uninterrupted
 * playback is reported in thirty-second pieces that the server merges back
 * together. At most {@link MAX_SEGMENTS_PER_SAVE} per request, by contract;
 * anything beyond waits for the next save rather than being dropped. A save that
 * did not reach the server {@link giveBack}s its intervals — resending is
 * harmless by construction, losing them is not.
 */

/** The contract's `maxItems` on `SaveProgressRequest.watched_segments`. */
export const MAX_SEGMENTS_PER_SAVE = 20;

/** One interval as the contract carries it. */
export type WatchedSegment = { started_at: string; ended_at: string };

type Interval = { start: number; end: number };

export class WatchMeter {
  private openedAt: number | null = null;
  private pending: Interval[] = [];
  private firstStart: number | null = null;

  /** The media is playing for real, as of `now` (epoch milliseconds). Idempotent. */
  start(now: number): void {
    if (this.openedAt !== null) return;
    this.openedAt = now;
    if (this.firstStart === null) this.firstStart = now;
  }

  /** The media stopped playing for real — pause, seek, stall, end, hidden page. Idempotent. */
  stop(now: number): void {
    if (this.openedAt === null) return;
    this.close(now);
    this.openedAt = null;
  }

  /** Whether an interval is open. */
  get playing(): boolean {
    return this.openedAt !== null;
  }

  /**
   * When playback really started in this page session: the first genuine
   * playing state, never the sheet opening nor a refused autoplay (CW-18, CW-31).
   * Null until then.
   */
  get sessionStartedAt(): string | null {
    return this.firstStart === null ? null : iso(this.firstStart);
  }

  /**
   * The intervals to send with a save at `now`, oldest first, at most `max`.
   * The open interval, if any, is cut at `now` and keeps running.
   */
  take(now: number, max: number = MAX_SEGMENTS_PER_SAVE): WatchedSegment[] {
    if (this.openedAt !== null) {
      this.close(now);
      this.openedAt = Math.max(now, this.openedAt);
    }
    return this.pending.splice(0, Math.max(0, max)).map(toSegment);
  }

  /** Puts back intervals whose save did not reach the server. */
  giveBack(segments: readonly WatchedSegment[]): void {
    const restored = segments
      .map((segment) => ({ start: Date.parse(segment.started_at), end: Date.parse(segment.ended_at) }))
      .filter((interval) => Number.isFinite(interval.start) && interval.end > interval.start);
    this.pending = [...restored, ...this.pending];
  }

  /** How many closed intervals are waiting for a save. */
  get waiting(): number {
    return this.pending.length;
  }

  private close(now: number): void {
    const start = this.openedAt!;
    // A clock that went backwards, or a zero-length blip between two events,
    // is not playback.
    if (now > start) this.pending.push({ start, end: now });
  }
}

/**
 * Whether a media element is playing for real, from what it reports.
 *
 * `readyState >= 3` (HAVE_FUTURE_DATA) is what separates playing from waiting:
 * an element that is "not paused" but starved of data shows a frozen frame.
 */
export function isActuallyPlaying(media: {
  paused: boolean;
  seeking: boolean;
  ended: boolean;
  readyState: number;
  visible: boolean;
}): boolean {
  return (
    !media.paused && !media.seeking && !media.ended && media.readyState >= 3 && media.visible
  );
}

/**
 * What the server receives from a browser, re-checked on the server side of the
 * Server Action: anything posted to it is untrusted input. Malformed intervals
 * are dropped rather than failing the save — the position is worth keeping
 * whatever else is wrong — and the list is capped at the contract's maximum.
 */
export function sanitizeWatchExtras(input: {
  watchedSegments?: unknown;
  observedAt?: unknown;
  sessionStartedAt?: unknown;
}): {
  watched_segments?: WatchedSegment[];
  observed_at?: string;
  session_started_at?: string;
} {
  const out: {
    watched_segments?: WatchedSegment[];
    observed_at?: string;
    session_started_at?: string;
  } = {};

  if (Array.isArray(input.watchedSegments)) {
    const segments = input.watchedSegments
      .flatMap((candidate): WatchedSegment[] => {
        if (!candidate || typeof candidate !== "object") return [];
        const { started_at, ended_at } = candidate as Record<string, unknown>;
        const start = instant(started_at);
        const end = instant(ended_at);
        if (start === null || end === null || end < start) return [];
        return [{ started_at: iso(start), ended_at: iso(end) }];
      })
      .slice(0, MAX_SEGMENTS_PER_SAVE);
    if (segments.length > 0) out.watched_segments = segments;
  }

  const observed = instant(input.observedAt);
  if (observed !== null) out.observed_at = iso(observed);
  const session = instant(input.sessionStartedAt);
  if (session !== null) out.session_started_at = iso(session);

  return out;
}

function instant(value: unknown): number | null {
  if (typeof value !== "string" || value.length > 40) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function iso(ms: number): string {
  return new Date(ms).toISOString();
}

function toSegment(interval: Interval): WatchedSegment {
  return { started_at: iso(interval.start), ended_at: iso(interval.end) };
}
