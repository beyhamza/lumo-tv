/**
 * "Remove from Continue watching", as a form (S12-04, `adr/0012` ruling 3).
 *
 * One form per card, posted to a Server Action — no script, the token never in
 * the browser (`apps/web/AGENTS.md` §3). The action sends `POST /me/progress/hide`
 * with the card's item; for a series card, the episode holding the card's
 * progress, and the server hides the whole series.
 *
 * The result travels back in the redirect (`?cw=`), never in a cookie: a form
 * posted without JavaScript receives nothing else. Either way the page re-reads
 * the rail, so what is drawn is the server's truth — a removal that did not go
 * through leaves its card in place, under a sentence saying so (CW-26), and is
 * never announced as done.
 *
 * Pure and in its own module: a `"use server"` file may only export async
 * functions, and this is the logic the tests pin.
 */

export type HideRequest = {
  sourceId: string;
  itemType: "VOD" | "EPISODE";
  itemRef: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Reads a removal from its form, or null when anything in it is malformed. */
export function readHide(form: FormData): HideRequest | null {
  const sourceId = form.get("sourceId");
  const itemType = form.get("itemType");
  const itemRef = form.get("itemRef");

  if (typeof sourceId !== "string" || !UUID.test(sourceId)) return null;
  if (itemType !== "VOD" && itemType !== "EPISODE") return null;
  // The contract's own bounds on `item_ref`; what we send is always one of our ids.
  if (typeof itemRef !== "string" || itemRef.length < 1 || itemRef.length > 200) return null;

  return { sourceId, itemType, itemRef };
}

/**
 * `hidden` on `204` — the contract's only success, idempotent: hiding a hidden
 * item answers it too. Anything else, including no answer, is `unconfirmed`:
 * the removal may or may not have happened, and the re-read shows which.
 */
export type HideOutcome = "hidden" | "unconfirmed";

export async function performHide(
  request: HideRequest | null,
  send: (request: HideRequest) => Promise<number>,
): Promise<HideOutcome> {
  if (!request) return "unconfirmed";
  try {
    return (await send(request)) === 204 ? "hidden" : "unconfirmed";
  } catch {
    return "unconfirmed";
  }
}

/** The query parameter carrying the outcome back to the home page. */
export const HIDE_NOTICE_PARAM = "cw";

/** Where the action sends the browser: the home page, with its outcome. */
export function homeWithNotice(outcome: HideOutcome): string {
  return `/app?${HIDE_NOTICE_PARAM}=${outcome}`;
}

/** The notice the home page shows, from its query; anything else shows none. */
export function hideNoticeOf(value: string | string[] | undefined): HideOutcome | null {
  const first = Array.isArray(value) ? value[0] : value;
  return first === "hidden" || first === "unconfirmed" ? first : null;
}
