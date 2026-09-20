/**
 * The wait the server imposes between two manual refreshes (US-024, contract lot
 * C4 — P5), from the `Retry-After` header to a sentence on the source page.
 *
 * <h2>The one rule: never invent a duration</h2>
 *
 * `POST /sources/{id}/sync` answers `429 SOURCE_SYNC_RATE_LIMITED` with
 * `Retry-After` in **seconds**. The interval behind it is server configuration
 * (`lumo.rate-limit.manual-sync-interval`), so this client holds no copy of "five
 * minutes": every function here answers `null` rather than guess, and `null` is
 * rendered as the message without a number.
 *
 * <h2>Why a query parameter, and why it holds an instant rather than a duration</h2>
 *
 * A Server Action posted by a plain `<form>` — this zone works without
 * JavaScript — can hand nothing back to the page except through its redirect. So
 * the wait travels in the URL of the source page.
 *
 * It travels as the **instant the wait ends** (`retryAt`, epoch seconds), not as
 * the number of seconds: a duration in a URL is wrong as soon as the page is
 * reloaded, and still says "about 5 min" from a bookmark opened the next day. An
 * instant expires by itself — {@link remainingWaitSeconds} answers `null` once it
 * has passed — which is what makes the parameter short-lived without a cookie or
 * any state on this side.
 *
 * <h2>It comes back from the browser, so it is validated like anything else</h2>
 *
 * Digits only, and no further away than {@link MAX_WAIT_SECONDS}. A forged value
 * can therefore do exactly one thing: make this page tell its own visitor to wait
 * up to a day. It gates nothing — the server decides whether a refresh is
 * accepted, on every request.
 *
 * Pure and free of `server-only`, for the reason `switch-target.ts` gives: a
 * `"use server"` file can only export async functions, and logic that reads
 * caller-supplied input ships with its tests.
 */

/**
 * The longest wait this client will repeat. The contract sets no maximum; a day
 * is far beyond any refresh limit and still short enough that a header gone
 * wrong (`Retry-After: 999999999`) is shown as "wait", not as a number of years.
 */
export const MAX_WAIT_SECONDS = 24 * 60 * 60;

/**
 * Reads `Retry-After` as the contract defines it: a positive integer of seconds.
 *
 * HTTP also allows a date there. The contract does not, the API never sends one,
 * and parsing it would mean trusting two clocks to agree — so a date is
 * "unparsable", like anything else that is not plain digits.
 *
 * @returns seconds, or `null` when the header is absent, malformed, zero, or
 *   beyond {@link MAX_WAIT_SECONDS}.
 */
export function parseRetryAfter(header: string | null | undefined): number | null {
  if (header == null) return null;

  const trimmed = header.trim();
  // `\d` only: no sign, no decimal point, no exponent. `Number("1e3")` is 1000
  // and `parseInt("12abc")` is 12 — neither is what the server said.
  if (!/^\d{1,9}$/.test(trimmed)) return null;

  const seconds = Number(trimmed);
  if (seconds < 1 || seconds > MAX_WAIT_SECONDS) return null;
  return seconds;
}

/**
 * The value of the `retryAt` query parameter: when the wait ends, in epoch
 * seconds.
 *
 * The current instant is rounded **down** to the second, on purpose. Rounded up,
 * `Retry-After: 300` comes back as 301 seconds left, and {@link displayedWait}
 * — which rounds up to the minute, as it must — would announce "about 6 min" for
 * a five-minute limit. The fraction of a second this gives away is far inside
 * the "about".
 */
export function retryAtParam(waitSeconds: number, nowMs: number): string {
  return String(Math.floor(nowMs / 1000) + waitSeconds);
}

/**
 * How long is left, from a `retryAt` that came back in the URL.
 *
 * @param value whatever `searchParams` handed over — a string, an array, nothing.
 * @returns whole seconds still to wait, or `null` when there is nothing valid to
 *   say: no parameter, not digits, already past, or further away than any wait
 *   this client would have written.
 */
export function remainingWaitSeconds(value: unknown, nowMs: number): number | null {
  if (typeof value !== "string" || !/^\d{1,12}$/.test(value)) return null;

  const remaining = Number(value) - Math.floor(nowMs / 1000);
  if (remaining < 1 || remaining > MAX_WAIT_SECONDS) return null;
  return remaining;
}

/** A wait, in the unit a person would say it in. */
export type DisplayedWait =
  /** Under a minute. Said as such: "about 0 min" is not a sentence. */
  | { unit: "under-a-minute" }
  | { unit: "minutes"; count: number }
  | { unit: "hours"; count: number };

/**
 * Rounds a wait for display ("again in about 3 min").
 *
 * **Always up.** `Retry-After: 170` rounded to the nearest minute is "3 min",
 * which happens to be right, but `130` would be "2 min" — and somebody who waits
 * two minutes and is refused again has been lied to by ten seconds. Rounded up,
 * the sentence may overstate by under a minute and never understates.
 *
 * Minutes up to an hour, hours beyond: "in about 95 min" is arithmetic left to
 * the reader.
 */
export function displayedWait(seconds: number): DisplayedWait {
  if (seconds < 60) return { unit: "under-a-minute" };
  if (seconds <= 60 * 60) return { unit: "minutes", count: Math.ceil(seconds / 60) };
  return { unit: "hours", count: Math.ceil(seconds / 3600) };
}

/**
 * What the source page says after a refresh was refused for being too soon.
 *
 * - `null` — say nothing: the action did not report a limit, or the wait it
 *   reported is over, or `retryAt` is not something this client wrote;
 * - `{ wait: null }` — the server imposed a wait and gave no usable
 *   `Retry-After`: the message, **without a number**;
 * - `{ wait }` — the message and how long is left.
 *
 * The distinction between "no `retryAt`" and "a `retryAt` that is over or
 * malformed" matters: the first is a server that named no delay and is still
 * worth a sentence; the second is a stale or edited URL, and "you refreshed too
 * recently" on a link opened the next morning would be false.
 */
export function syncLimitNotice(
  sync: unknown,
  retryAt: unknown,
  nowMs: number,
): { wait: DisplayedWait | null } | null {
  if (sync !== "limited") return null;
  if (retryAt === undefined) return { wait: null };

  const remaining = remainingWaitSeconds(retryAt, nowMs);
  return remaining === null ? null : { wait: displayedWait(remaining) };
}
