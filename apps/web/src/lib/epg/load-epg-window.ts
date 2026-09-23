import "server-only";

import { api, problemCode } from "@/lib/api/client";
import type { EpgGrid } from "@/lib/api/types";
import { fetchBounded, type BatchAnswer, type BatchRequest } from "./split";

/**
 * The grouped guide read of the web, `server-only` (C1 — S9-03).
 *
 * <h2>One request per screen, never one per card</h2>
 *
 * The rule of the sprint, and the reason `GET /sources/{id}/epg` exists: a
 * rail of twelve channels or a page of fifty asks for its whole batch over one
 * window in one call, and gets one entry per channel back, in its order. What
 * that call becomes when the server refuses it as too large is
 * {@link fetchBounded}'s rule, not this file's.
 *
 * <h2>The token stays here</h2>
 *
 * Like every authenticated call of this site (`AGENTS.md` §3–4): the request
 * leaves from the Next.js server with the access token from the httpOnly
 * cookie, and the browser only ever receives the rendered lines.
 *
 * <h2>Unavailable is a state, not an exception</h2>
 *
 * A guide that could not be read renders **nothing** where programmes would
 * be — no placeholder, no "unavailable" (S7-03: an absent information hides
 * nothing). So this never throws and never rejects: the `attempt()` posture
 * of the catalogue pages, where a call with no answer must not take the parts
 * that answered with it. The code is kept for the one caller that wants to
 * say something — a future grid offering "fewer channels" on
 * `EPG_WINDOW_TOO_LARGE` (S9-05) — and for nobody else.
 *
 * No retry on a network failure, here or below: the page renders without the
 * lines, and the next page load asks again.
 */

/**
 * The window a "now" read covers: enough for the current programme and the
 * one after it, across every card of a screen, in one answer that the bench
 * measured as fitting several times under the ceilings for fifty channels
 * (docs/releases/0.2.0/s9-00-epg-bench.md; sprint 7, S7-03).
 */
export const NOW_WINDOW_MS = 3 * 60 * 60 * 1000;

export type EpgWindow =
  | { state: "ok"; grid: EpgGrid }
  /**
   * Nothing to draw. `code` is the RFC 7807 code when the API refused
   * (`EPG_WINDOW_TOO_LARGE` after every split allowed, `CHANNEL_NOT_FOUND`,
   * …), and absent when there was no answer at all — or nothing to ask.
   */
  | { state: "unavailable"; code?: string };

/**
 * @param channelIds the channels of the screen. Duplicates are dropped; more
 *   than a hundred are chunked. An empty list is `unavailable` without a
 *   request: there is nothing to draw and no metadata to report.
 * @param from inclusive; `to` exclusive. The caller's clock, so that the same
 *   instant decides what is on air.
 */
export async function loadEpgWindow(
  accessToken: string,
  sourceId: string,
  channelIds: readonly string[],
  from: Date,
  to: Date,
): Promise<EpgWindow> {
  if (channelIds.length === 0) return { state: "unavailable" };

  const client = api(accessToken);

  const fetch = async (batch: BatchRequest): Promise<BatchAnswer> => {
    // Throws when there is no answer at all — API down, connection reset —
    // and the planner reads a throw as `failed`. Nothing here retries.
    const answer = await client.GET("/sources/{id}/epg", {
      params: {
        path: { id: sourceId },
        query: {
          // Repeated, as `ids` on the listings: `?channelIds=a&channelIds=b`.
          channelIds: [...batch.channelIds],
          from: batch.from.toISOString(),
          to: batch.to.toISOString(),
        },
      },
    });
    if (answer.data) return { kind: "ok", grid: answer.data };

    // The only code this branches on (`AGENTS.md` §5). Everything else is a
    // refusal the split cannot fix, reported as such.
    const code = problemCode(answer.error);
    return code === "EPG_WINDOW_TOO_LARGE" ? { kind: "too-large" } : { kind: "failed", code };
  };

  const result = await fetchBounded({ channelIds, from, to }, fetch);

  switch (result.kind) {
    case "ok":
      return {
        state: "ok",
        grid: {
          source_id: sourceId,
          // The bounds asked for, not those of any one sub-request: after a
          // split of the window each answer echoes its own half.
          from: from.toISOString(),
          to: to.toISOString(),
          generated_at: result.generatedAt,
          epg: result.epg,
          channels: result.channels,
        },
      };
    case "too-large":
      return { state: "unavailable", code: "EPG_WINDOW_TOO_LARGE" };
    case "failed":
      return { state: "unavailable", code: result.code };
  }
}
