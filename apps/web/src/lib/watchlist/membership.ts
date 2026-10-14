import type { WatchlistPage } from "@/lib/api/types";

/**
 * Whether one film or one series is in "My list", for the button on its sheet
 * (US-022, S11-E06).
 *
 * The contract has no "is this one in the list?" endpoint, and does not need
 * one: `GET /me/watchlist` narrowed to the source and the content type, read
 * page by page at the contract's largest size, and matched on `content_id`.
 * A list of a few hundred titles is one or two requests.
 *
 * Three answers, not two. A page that failed to load proves nothing about the
 * pages it would have contained, so a lookup that did not finish is `unknown` —
 * and the sheet offers no button rather than an "Add" that may be a lie.
 */
export type Membership =
  | { state: "in"; itemId: string }
  | { state: "out" }
  | { state: "unknown" };

/** The contract's ceiling on `size`. */
export const MEMBERSHIP_PAGE_SIZE = 200;

/**
 * A bound on the walk, so that a server answering inconsistent page counts
 * cannot keep a render looping. 50 × 200 is ten thousand titles of one type in
 * one source; past that the answer is "unknown", which costs one button.
 */
const MAX_PAGES = 50;

/**
 * @param contentId the film's `VodItem.id` or the series' `Series.id`.
 * @param loadPage one page of `GET /me/watchlist?sourceId=…&contentType=…`
 *   at {@link MEMBERSHIP_PAGE_SIZE}, or `undefined` when the request failed.
 */
export async function findMembership(
  contentId: string,
  loadPage: (page: number) => Promise<WatchlistPage | undefined>,
): Promise<Membership> {
  for (let page = 0; page < MAX_PAGES; page++) {
    let answer: WatchlistPage | undefined;
    try {
      answer = await loadPage(page);
    } catch {
      return { state: "unknown" };
    }
    if (!answer) return { state: "unknown" };

    // An unavailable item has no `content_id`, so it can never match — and it
    // should not: the sheet being open proves the content is in the catalogue.
    const match = answer.items.find((item) => item.content_id === contentId);
    if (match) return { state: "in", itemId: match.id };

    if (answer.items.length === 0 || page + 1 >= answer.total_pages) {
      return { state: "out" };
    }
  }
  return { state: "unknown" };
}
