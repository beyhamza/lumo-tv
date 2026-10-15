import { isUuid, MAX_MEMBERSHIPS } from "./global-removal";

/**
 * What a favourites action tells the page it redirects to (S11-03).
 *
 * The result of a Server Action travels in its redirect, never in a cookie
 * (`apps/web/AGENTS.md` §4): a form posted without JavaScript receives nothing
 * else. Same shape as the watchlist's notice (`lib/watchlist/write.ts`), with
 * the one addition a partial removal needs — which memberships the action
 * could not confirm, so that the page can re-read and offer to retry those and
 * nothing wider.
 *
 * Only failures are carried. A change that went through needs no message: the
 * page re-reads `GET /me/favorites` on the way back and shows it.
 */
export type FavoritesNotice =
  /** A reorder stopped part-way: the group is valid but partially permuted. */
  | { kind: "move-partial" }
  /** A single change could not be confirmed; the page shows the current state. */
  | { kind: "unconfirmed" }
  /** A global removal left memberships it could not confirm as removed. */
  | { kind: "removal-partial"; channelId: string; left: string[] };

export const NOTICE_PARAM = "fav";
const CHANNEL_PARAM = "channel";
const LEFT_PARAM = "left";
/** The channel whose global removal is being confirmed — a view, not a notice. */
export const CONFIRM_PARAM = "confirm";

/** Every parameter an action owns. A new outcome replaces the previous one. */
const ACTION_PARAMS = [NOTICE_PARAM, CHANNEL_PARAM, LEFT_PARAM, CONFIRM_PARAM];

/**
 * The view the form came from, with any previous notice and confirmation
 * dropped, and the new notice set when there is one.
 *
 * @param target an in-site path already validated by `safeRedirectTarget`.
 */
export function withNotice(target: string, notice: FavoritesNotice | null): string {
  const [beforeHash, hash] = splitOnce(target, "#");
  const [path, query] = splitOnce(beforeHash, "?");
  const params = new URLSearchParams(query ?? "");
  for (const key of ACTION_PARAMS) params.delete(key);

  if (notice) {
    params.set(NOTICE_PARAM, notice.kind);
    if (notice.kind === "removal-partial") {
      params.set(CHANNEL_PARAM, notice.channelId);
      params.set(LEFT_PARAM, notice.left.join(","));
    }
  }

  const rendered = params.toString();
  return `${path}${rendered ? `?${rendered}` : ""}${hash !== undefined ? `#${hash}` : ""}`;
}

type Query = Record<string, string | string[] | undefined>;

/**
 * The notice the page was reached with, or null. Anything malformed — a
 * hand-edited URL — reads as no notice rather than as a made-up one.
 */
export function readNotice(query: Query): FavoritesNotice | null {
  const kind = first(query[NOTICE_PARAM]);
  if (kind === "move-partial" || kind === "unconfirmed") return { kind };
  if (kind !== "removal-partial") return null;

  const channelId = first(query[CHANNEL_PARAM]);
  const left = (first(query[LEFT_PARAM]) ?? "").split(",").filter(isUuid);
  if (!isUuid(channelId) || left.length === 0 || left.length > MAX_MEMBERSHIPS) return null;
  return { kind, channelId, left };
}

/** The channel whose removal from every group is awaiting confirmation. */
export function readConfirm(query: Query): string | undefined {
  const channelId = first(query[CONFIRM_PARAM]);
  return isUuid(channelId) ? channelId : undefined;
}

/**
 * The page's own path and query minus everything an action owns: what a form
 * posts as `returnTo`, and what "Close" and "Cancel" link to.
 */
export function cleanView(path: string, query: Query): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (ACTION_PARAMS.includes(key)) continue;
    const value0 = first(value);
    if (value0) params.set(key, value0);
  }
  const rendered = params.toString();
  return rendered ? `${path}?${rendered}` : path;
}

/**
 * The link that opens the confirmation of "Remove from all my favourites" for
 * one channel: the clean view plus `confirm`. A link and not a form, because
 * asking is a view — it works without JavaScript and the back button undoes it.
 *
 * @param view a clean view, as {@link cleanView} returns it.
 */
export function confirmView(view: string, channelId: string): string {
  const [path, query] = splitOnce(view, "?");
  const params = new URLSearchParams(query ?? "");
  params.set(CONFIRM_PARAM, channelId);
  return `${path}?${params.toString()}`;
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function splitOnce(value: string, separator: string): [string, string | undefined] {
  const at = value.indexOf(separator);
  return at < 0 ? [value, undefined] : [value.slice(0, at), value.slice(at + 1)];
}
