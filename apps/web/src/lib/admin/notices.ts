/**
 * What an administration page says after an action (S16B-08).
 *
 * The result travels in the redirect (`apps/web/AGENTS.md` §4), and the query
 * parameter is the browser's to write: only these values render anything.
 */
export const ADMIN_SUCCESSES = ["draft-saved", "published", "draft-deleted", "settings-saved"] as const;
export const ADMIN_FAILURES = [
  "draft-exists",
  "not-draft",
  "incomplete",
  "placeholders",
  "invalid",
  "failed",
] as const;

export type AdminSuccess = (typeof ADMIN_SUCCESSES)[number];
export type AdminFailure = (typeof ADMIN_FAILURES)[number];
export type AdminNotice = AdminSuccess | AdminFailure;

export function adminNotice(value: unknown): AdminNotice | null {
  const all: readonly string[] = [...ADMIN_SUCCESSES, ...ADMIN_FAILURES];
  return typeof value === "string" && all.includes(value) ? (value as AdminNotice) : null;
}

export function isAdminFailure(notice: AdminNotice): notice is AdminFailure {
  return (ADMIN_FAILURES as readonly string[]).includes(notice);
}

/** An API refusal in the terms of those pages. An unknown code is a plain failure. */
export function adminFailure(code: string | undefined): AdminFailure {
  switch (code) {
    case "LEGAL_DRAFT_ALREADY_EXISTS":
      return "draft-exists";
    case "LEGAL_VERSION_NOT_DRAFT":
      return "not-draft";
    case "LEGAL_VERSION_INCOMPLETE":
      return "incomplete";
    case "VALIDATION_FAILED":
      return "invalid";
    default:
      return "failed";
  }
}
