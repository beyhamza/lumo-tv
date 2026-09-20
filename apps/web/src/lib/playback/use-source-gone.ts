"use client";

import { useEffect, useState } from "react";
import {
  EXISTENCE_CHECK_EVERY_MS,
  EXISTENCE_CHECK_TIMEOUT_MS,
  existenceFromRoute,
} from "@/lib/sources/existence";

/**
 * True once the server has **confirmed** that the source being played was
 * deleted (US-024 "Suppression", contract lot C4 — D5).
 *
 * <h2>Every sixty seconds, while a player is open</h2>
 *
 * Playback is the one moment this application does not talk to its API by
 * itself — the stream comes from the user's own provider — so a source removed
 * from the phone would otherwise play on until the next navigation.
 *
 * <h2>Only `gone` changes anything</h2>
 *
 * The verdict is `existenceFromRoute`, pure and tested. `unknown` — offline, a
 * timeout, a `5xx`, an expired session — leaves playback alone: an outage proves
 * no deletion, and hiding somebody's film because *our* API restarted would be
 * the exact bug this rule exists to avoid. After a reconnection the next tick
 * asks again, and a deletion confirmed then is acted on then.
 *
 * <h2>A hidden tab does not poll</h2>
 *
 * Browsers throttle its timers anyway, and nobody is watching. The interval is
 * dropped while `document.hidden` and **one check runs the moment the tab is
 * visible again** — which is also the "back to the foreground" check C4 asks for,
 * so somebody who deleted the source on their phone and comes back to this tab
 * is told at once rather than up to a minute later.
 *
 * <h2>It stops for good once the answer is `gone`</h2>
 *
 * A deleted source does not come back under the same id. The state only ever
 * goes from false to true, and the effect that polls is torn down when it does.
 *
 * @param sourceId the source the content on screen belongs to.
 * @param watching false until playback has been asked for — a film page that is
 *   merely open is a page, and pages find out by being navigated.
 */
export function useSourceGone(sourceId: string, watching: boolean): boolean {
  const [gone, setGone] = useState(false);

  useEffect(() => {
    if (!watching || gone || !sourceId) return;

    let disposed = false;
    let timer: ReturnType<typeof setInterval> | null = null;

    async function check() {
      let ok = false;
      let body: unknown = null;
      try {
        const response = await fetch(`/api/sources/${encodeURIComponent(sourceId)}/exists`, {
          cache: "no-store",
          signal: AbortSignal.timeout(EXISTENCE_CHECK_TIMEOUT_MS),
        });
        ok = response.ok;
        body = await response.json();
      } catch {
        // Offline, aborted, or a body that is not JSON. `ok` may already be true
        // in the last case, and the null body still reads as "unknown".
        body = null;
      }
      if (!disposed && existenceFromRoute(ok, body) === "gone") setGone(true);
    }

    function startPolling() {
      if (timer === null) timer = setInterval(() => void check(), EXISTENCE_CHECK_EVERY_MS);
    }

    function stopPolling() {
      if (timer !== null) clearInterval(timer);
      timer = null;
    }

    function onVisibilityChange() {
      if (document.hidden) {
        stopPolling();
      } else {
        void check();
        startPolling();
      }
    }

    // No check on mount: the page around this player was rendered from that
    // source's catalogue a moment ago, which a deleted source cannot serve.
    if (!document.hidden) startPolling();
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      disposed = true;
      stopPolling();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [sourceId, watching, gone]);

  return gone;
}
