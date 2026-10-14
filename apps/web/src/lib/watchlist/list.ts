import type { WatchlistContentType, WatchlistPage } from "@/lib/api/types";

/**
 * The "My list" screen as rules rather than as markup (US-022, S11-04).
 *
 * Pure, so that what decides between "your list is empty", "nothing in this
 * filter" and "here are your items" is tested rather than read off a page. The
 * screen itself only draws what these functions answer.
 */

/** The three filters, as they appear in the URL (`?type=`). `all` is no parameter. */
export type WatchlistFilter = "all" | "films" | "series";

/** Display order of the selector. */
export const WATCHLIST_FILTERS: readonly WatchlistFilter[] = ["all", "films", "series"];

/** One page of the list. The contract caps `size` at 200; a grid of 48 reads well. */
export const WATCHLIST_PAGE_SIZE = 48;

/** `?type=` — anything unknown is "all", never an error. */
export function filterFromParam(value: string | undefined): WatchlistFilter {
  return value === "films" || value === "series" ? value : "all";
}

/** What the filter asks the server for. `all` asks for nothing in particular. */
export function contentTypeOf(filter: WatchlistFilter): WatchlistContentType | undefined {
  if (filter === "films") return "VOD";
  if (filter === "series") return "SERIES";
  return undefined;
}

/** The filter that shows one content type. */
export function filterOf(type: WatchlistContentType): WatchlistFilter {
  return type === "VOD" ? "films" : "series";
}

/**
 * The selector's entries, or none at all (WL-17).
 *
 * Built from `WatchlistPage.content_types` — the types present in the whole
 * list of the source, unavailable items included — and never from the items of
 * the page on screen, which cannot say whether the other type exists further on.
 *
 * Fewer than two types is no choice: "All" and "Films" over a list of films
 * show the same thing, and a selector that changes nothing is furniture.
 */
export function filterChoices(
  contentTypes: readonly WatchlistContentType[],
): readonly WatchlistFilter[] {
  return new Set(contentTypes).size >= 2 ? WATCHLIST_FILTERS : [];
}

/** `?page=` — zero-based, like the contract. Garbage is the first page. */
export function pageFromParam(value: string | undefined): number {
  const parsed = Number.parseInt(value ?? "", 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

/**
 * Which of the screen's states one answer of `GET /me/watchlist` is.
 *
 * - `empty`: the source's list holds nothing at all — `content_types` is the
 *   whole list's, whatever the filter, so an empty one is a confirmed empty
 *   list and the screen may say "Your list is empty".
 * - `filtered-empty`: the list has items, none of this type. The filters stay
 *   and "All" is offered; the screen must not claim the list is empty.
 * - `out-of-range`: a page past the end — a stale link, or the last item of
 *   the last page removed elsewhere. The way back is the first page.
 * - `items`: something to draw.
 *
 * A failed request never reaches this function: no answer is not an empty
 * page, and the screen says "could not be loaded" instead.
 */
export type WatchlistView = "empty" | "filtered-empty" | "out-of-range" | "items";

export function watchlistView(page: WatchlistPage, filter: WatchlistFilter): WatchlistView {
  if (page.items.length > 0) return "items";
  if (page.content_types.length === 0) return "empty";
  if (page.total_elements > 0) return "out-of-range";
  // Types are present but none matched. With "all" that cannot happen unless
  // the list changed between two counts on the server; the honest reading is
  // still "nothing here right now", never "your list is empty".
  return filter === "all" ? "out-of-range" : "filtered-empty";
}
