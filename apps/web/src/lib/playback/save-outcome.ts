/**
 * What a progress save means for the intervals it carried (S12-02).
 *
 * - `ok`: stored; the intervals are the server's now.
 * - `retry`: no answer, or the server failed (`5xx`): keep the intervals and
 *   send them with the next save. Harmless if they did arrive — the server keeps
 *   a union, so a resent interval adds nothing (CW-33).
 * - `drop`: refused for good — no session, a malformed body, a deleted source.
 *   Sending the same thing again would be refused again.
 */
export type SaveOutcome = "ok" | "retry" | "drop";

export function saveOutcomeOf(status: number): SaveOutcome {
  if (status >= 200 && status < 300) return "ok";
  if (status >= 500 || status === 408 || status === 429) return "retry";
  return "drop";
}
