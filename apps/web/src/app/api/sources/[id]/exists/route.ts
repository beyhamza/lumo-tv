import { NextResponse } from "next/server";
import { api, problemCode } from "@/lib/api/client";
import { getSession } from "@/lib/session/session";
import { isUuid } from "@/lib/sources/active-source";
import {
  EXISTENCE_CHECK_TIMEOUT_MS,
  existenceFromApi,
  type ApiOutcome,
} from "@/lib/sources/existence";

/**
 * "Does this source still exist?" — for a player, once a minute (US-024
 * "Suppression", contract lot C4 — D5).
 *
 * <h2>Why a route handler</h2>
 *
 * While something plays, the page talks to nobody: the stream comes from the
 * user's own provider. A source deleted from the phone would go unnoticed until
 * the next navigation, so the players ask. They cannot ask the API themselves —
 * the access token lives in an httpOnly cookie and never reaches client
 * JavaScript (`apps/web/AGENTS.md` §4) — so they ask here, same origin, and this
 * handler asks `GET /sources/{id}` with the token, exactly as the
 * `api/playback/*` handlers do for a stream URL.
 *
 * <h2>It answers one bit, and nothing about the source</h2>
 *
 * `{ exists: true }` or `{ exists: false }`. Not the label, not the status, not
 * the host: the page already rendered what it needed, and a polling endpoint
 * that echoed a `Source` would be a second, unreviewed way for one to reach
 * client JavaScript.
 *
 * `false` is returned **only** for `404 SOURCE_NOT_FOUND` — the rule is
 * `existenceFromApi`, pure and tested. Everything else (no network, a timeout, a
 * `5xx`, a refusal) is a `502 { code }`, which the player reads as "unknown" and
 * which never stops playback: an outage proves no deletion.
 *
 * <h2>The checks, in order</h2>
 *
 * 1. a session, or `401` — same as the playback handlers;
 * 2. the id is a UUID before it goes anywhere. openapi-fetch encodes path
 *    parameters, so this is not what keeps the request on `/sources/…`; it is
 *    that a value which cannot name a source has no business costing the API a
 *    request, sixty times an hour;
 * 3. a timeout, so a hung API yields "unknown" well before the next check;
 * 4. `no-store` on every answer: this is a per-user fact that changes.
 *
 * Whose source it is, is the API's decision and stays there: a source of another
 * account answers `404 SOURCE_NOT_FOUND` by contract, which tells the caller
 * nothing they could not already see — their own pages never show it.
 *
 * <h2>The session is not refreshed here</h2>
 *
 * `proxy.ts` refreshes tokens and deliberately does not run on `/api`. An access
 * token that expires mid-film therefore turns these checks into `401`s — that is
 * to say into "unknown", the safe side. The film and episode players keep the
 * session fresh anyway: their progress saves are Server Actions, which do go
 * through the proxy.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const session = await getSession();

  if (!session) {
    return problem(401, "UNAUTHENTICATED");
  }
  if (!isUuid(id)) {
    return problem(400, "VALIDATION_FAILED");
  }

  let outcome: ApiOutcome;
  try {
    const result = await api(session.accessToken).GET("/sources/{id}", {
      params: { path: { id } },
      signal: AbortSignal.timeout(EXISTENCE_CHECK_TIMEOUT_MS),
    });
    outcome = {
      kind: "response",
      status: result.response.status,
      code: problemCode(result.error),
    };
  } catch {
    // Refused, unresolvable, aborted by the timeout: no answer is not an answer.
    outcome = { kind: "no-response" };
  }

  const existence = existenceFromApi(outcome);
  if (existence === "unknown") {
    return problem(502, "NETWORK");
  }

  return NextResponse.json(
    { exists: existence === "exists" },
    {
      status: 200,
      headers: {
        "Cache-Control": "no-store, no-cache, must-revalidate, private",
        "X-Robots-Tag": "noindex, nofollow",
      },
    },
  );
}

function problem(status: number, code: string) {
  return NextResponse.json(
    { code },
    { status, headers: { "Cache-Control": "no-store" } },
  );
}
