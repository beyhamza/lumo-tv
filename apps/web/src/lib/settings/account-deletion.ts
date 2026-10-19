/**
 * What an answer to `POST /me/delete` means for this browser (S16B-02).
 *
 * Pure, so the mapping is tested rather than read off a Server Action:
 *
 * - `204` — deleted: end the session and say so on the sign-in page;
 * - `401` — the session is over (or the account already gone): end it, with
 *   nothing to announce;
 * - `403 INVALID_CREDENTIALS` — wrong password, nothing deleted;
 * - `429` — too many attempts, nothing deleted;
 * - anything else, an unknown code or no answer at all — failed, nothing
 *   deleted, try again. Never "deleted" on a guess.
 */
export type DeletionOutcome = "deleted" | "signed-out" | "wrong-password" | "rate-limited" | "failed";

/** The `delete=` values the confirmation renders a message for. */
export const DELETION_FAILURES = ["wrong-password", "rate-limited", "failed"] as const;

export type DeletionFailure = (typeof DELETION_FAILURES)[number];

export function deletionOutcome(answer: { status: number; code?: string }): DeletionOutcome {
  if (answer.status === 204) return "deleted";
  if (answer.status === 401) return "signed-out";
  if (answer.status === 403) return "wrong-password";
  if (answer.status === 429 || answer.code === "RATE_LIMITED") return "rate-limited";
  return "failed";
}

/** Narrows the `delete=` query parameter, which is the browser's to write. */
export function deletionFailure(value: unknown): DeletionFailure | null {
  return typeof value === "string" && (DELETION_FAILURES as readonly string[]).includes(value)
    ? (value as DeletionFailure)
    : null;
}
