/**
 * Has this source been deleted? — asked every minute while something plays
 * (US-024 "Suppression", contract lot C4 — P6 and D5).
 *
 * <h2>Three answers, and the third one is the point</h2>
 *
 * - `exists` — the API returned the source;
 * - `gone` — the API said **`404 SOURCE_NOT_FOUND`**, and nothing else counts;
 * - `unknown` — everything else: no network, a timeout, a `5xx`, an expired
 *   session, a rate limit, a code nobody has seen yet.
 *
 * `unknown` never stops playback. The stream comes from the user's own provider,
 * not from this API, so a film plays perfectly well through an outage of ours —
 * and an outage **proves no deletion** (US-018, US-024). The failure this
 * prevents is the expensive kind: a server restart at 21:00 that interrupts every
 * viewer with "this source has been removed from your account".
 *
 * <h2>Two hops, two functions, the same rule</h2>
 *
 * The browser may not hold the access token (`apps/web/AGENTS.md` §4), so the
 * question crosses a same-origin route handler:
 *
 * ```
 * player ──fetch──▶ /api/sources/{id}/exists ──token──▶ GET /sources/{id}
 *        ◀─{ exists }──                      ◀─ 200 / 404 / anything ──
 * ```
 *
 * {@link existenceFromApi} reads the API's answer in the handler;
 * {@link existenceFromRoute} reads the handler's answer in the player. Both are
 * pure, and both default to `unknown`: deleting is the only verdict that has to
 * be earned.
 */
export type SourceExistence = "exists" | "gone" | "unknown";

/** What the route handler saw when it asked the API. */
export type ApiOutcome =
  /** No HTTP response at all: connection refused, DNS, timeout, abort. */
  | { kind: "no-response" }
  | { kind: "response"; status: number; code?: string };

export function existenceFromApi(outcome: ApiOutcome): SourceExistence {
  if (outcome.kind === "no-response") return "unknown";

  if (outcome.status >= 200 && outcome.status < 300) return "exists";

  // The status **and** the code. The code is what a client branches on (RFC
  // 7807, `apps/web/AGENTS.md` §5); the status is asked for as well because
  // this is the one verdict that stops somebody's film, and a `404` carrying
  // the generic `NOT_FOUND` is an unrouted path — a deployment problem, not a
  // deletion (`lib/api/fetched.ts`).
  if (outcome.status === 404 && outcome.code === "SOURCE_NOT_FOUND") return "gone";

  return "unknown";
}

/**
 * Reads the route handler's answer, in the player.
 *
 * `gone` needs a successful response whose body is exactly `{ exists: false }`.
 * A `401` from an expired session, a `502`, an HTML error page from a proxy in
 * between, a body that did not parse: all `unknown`.
 */
export function existenceFromRoute(ok: boolean, body: unknown): SourceExistence {
  if (!ok || body === null || typeof body !== "object") return "unknown";

  const exists = (body as { exists?: unknown }).exists;
  if (exists === true) return "exists";
  if (exists === false) return "gone";
  return "unknown";
}

/** How often a player asks (C4, D5). */
export const EXISTENCE_CHECK_EVERY_MS = 60_000;

/**
 * How long either hop may take before it counts as `unknown`.
 *
 * Well under the interval, so two checks never overlap, and generous for a
 * request that reads one row.
 */
export const EXISTENCE_CHECK_TIMEOUT_MS = 10_000;
