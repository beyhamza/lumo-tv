import type { WatchlistContentType } from "@/lib/api/types";

/**
 * Adding to and removing from "My list", one intention at a time
 * (`adr/0011`, ruling 6; S11-00 G6–G8; WL-10/11/12).
 *
 * <h2>On the web, the intention is the rendered button</h2>
 *
 * The server tells a retry from a new intention by the `Idempotency-Key` the
 * client sends: same key, same recorded outcome, nothing changed. So the key
 * must be minted **once per intention** and travel with every resubmission of
 * it — and on a page that works without JavaScript there is exactly one thing
 * that lives as long as an intention: the form on screen.
 *
 * The key is therefore generated on the server when the form is rendered
 * ({@link newIntentionKey}), carried in a hidden field, and sent **unchanged**
 * by the action. A double click, a resubmitted POST or a retry after a lost
 * response all post the same form, hence the same key, and the server replays
 * instead of acting twice. A new render — after the redirect, or a reload — is
 * a new form, hence a new key: a new intention, ordered by the server.
 *
 * <h2>Nothing is minted in the action</h2>
 *
 * A key generated in the action would be a new key per POST, which is exactly
 * the bug the protocol exists to prevent: a resubmission would read as a new
 * intention. A missing or malformed field is therefore not "repaired" with a
 * fresh key; nothing is sent, and the outcome is unknown.
 *
 * Pure, and in its own module: a `"use server"` file may only export async
 * functions, and this is the logic the tests pin.
 */

/** The hidden field carrying the intention key. */
export const INTENTION_KEY_FIELD = "intentionKey";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

/**
 * One key for one rendered form. Called while rendering, never in the action.
 *
 * `crypto.randomUUID()` is a v4 UUID, which is what the contract's
 * `format: uuid` asks for.
 */
export function newIntentionKey(): string {
  return crypto.randomUUID();
}

export type WatchlistWrite =
  | {
      kind: "add";
      key: string;
      contentType: WatchlistContentType;
      contentId: string;
    }
  | { kind: "remove"; key: string; itemId: string };

/** Reads an add from its form, or null when anything in it is malformed. */
export function readAdd(form: FormData): WatchlistWrite | null {
  const key = form.get(INTENTION_KEY_FIELD);
  const contentType = form.get("contentType");
  const contentId = form.get("contentId");

  if (!isUuid(key) || !isUuid(contentId)) return null;
  if (contentType !== "VOD" && contentType !== "SERIES") return null;

  return { kind: "add", key, contentType, contentId };
}

/** Reads a removal from its form, or null when anything in it is malformed. */
export function readRemove(form: FormData): WatchlistWrite | null {
  const key = form.get(INTENTION_KEY_FIELD);
  const itemId = form.get("itemId");

  if (!isUuid(key) || !isUuid(itemId)) return null;

  return { kind: "remove", key, itemId };
}

/** What the API answered, reduced to what classification needs. */
export type WriteAnswer = { status: number; error?: unknown };

/**
 * What the user may be told.
 *
 * - `applied`: accepted now (`201`, `204`) — or replayed, which is the same
 *   acceptance answered again.
 * - `already`: the list was already in the requested state — `200` on an add,
 *   `404 WATCHLIST_ITEM_NOT_FOUND` on a removal. Another tab or device got
 *   there first; there is nothing to say.
 * - `unknown`: anything else, including no answer at all. The change may or
 *   may not have happened, so the screen says "Change not confirmed" and
 *   re-reads the list before offering the action again. Never success, and
 *   never an automatic retry under a new key — that could overwrite a newer
 *   action accepted in the meantime (WL-12).
 *
 * The first two need no message: both end on a re-read of the list, which
 * shows the truth.
 */
export type WriteOutcome = "applied" | "already" | "unknown";

export function classifyAnswer(kind: WatchlistWrite["kind"], answer: WriteAnswer): WriteOutcome {
  if (kind === "add") {
    if (answer.status === 201) return "applied";
    if (answer.status === 200) return "already";
    return "unknown";
  }

  if (answer.status === 204) return "applied";
  if (answer.status === 404 && problemCode(answer.error) === "WATCHLIST_ITEM_NOT_FOUND") {
    return "already";
  }
  return "unknown";
}

/**
 * Sends one write and classifies the result. A malformed form (`null`) sends
 * nothing; a send that throws — no response at all — is unknown.
 */
export async function performWrite(
  write: WatchlistWrite | null,
  send: (write: WatchlistWrite) => Promise<WriteAnswer>,
): Promise<WriteOutcome> {
  if (!write) return "unknown";
  try {
    return classifyAnswer(write.kind, await send(write));
  } catch {
    return "unknown";
  }
}

/** The query parameter that carries an unconfirmed outcome back to the page. */
export const WRITE_NOTICE_PARAM = "wl";
const UNCONFIRMED = "unconfirmed";

/**
 * Where the action redirects: the view the form came from, with the notice
 * set or cleared.
 *
 * The result of a Server Action travels in its redirect, never in a cookie
 * (`apps/web/AGENTS.md` §4): a form posted without JavaScript receives nothing
 * else. A previous notice is always dropped first, so that a confirmed change
 * made right after an unconfirmed one does not keep the old warning on screen.
 *
 * @param target an in-site path already validated by `safeRedirectTarget`.
 */
export function returnWithNotice(target: string, outcome: WriteOutcome): string {
  const [beforeHash, hash] = splitOnce(target, "#");
  const [path, query] = splitOnce(beforeHash, "?");
  const params = new URLSearchParams(query ?? "");
  params.delete(WRITE_NOTICE_PARAM);
  if (outcome === "unknown") params.set(WRITE_NOTICE_PARAM, UNCONFIRMED);

  const rendered = params.toString();
  return `${path}${rendered ? `?${rendered}` : ""}${hash !== undefined ? `#${hash}` : ""}`;
}

/** Whether the page was reached through an unconfirmed write. */
export function isUnconfirmed(value: string | string[] | undefined): boolean {
  return (Array.isArray(value) ? value[0] : value) === UNCONFIRMED;
}

/**
 * The page's own path and query, minus the notice: what a form on it posts as
 * `returnTo`, so that the action comes back to exactly this view — filter,
 * page, season — and nothing else.
 */
export function currentView(
  path: string,
  query: Record<string, string | string[] | undefined>,
): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (key === WRITE_NOTICE_PARAM) continue;
    const first = Array.isArray(value) ? value[0] : value;
    if (first) params.set(key, first);
  }
  const rendered = params.toString();
  return rendered ? `${path}?${rendered}` : path;
}

function splitOnce(value: string, separator: string): [string, string | undefined] {
  const at = value.indexOf(separator);
  return at < 0 ? [value, undefined] : [value.slice(0, at), value.slice(at + 1)];
}

/** `code` from an RFC 7807 body — the only field a client may branch on. */
function problemCode(error: unknown): string | undefined {
  if (!error || typeof error !== "object") return undefined;
  const code = (error as { code?: unknown }).code;
  return typeof code === "string" ? code : undefined;
}
