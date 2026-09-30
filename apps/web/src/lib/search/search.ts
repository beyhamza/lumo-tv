import type { Channel, Series, VodItem } from "@/lib/api/types";

/**
 * The unified search, as rules rather than as a screen (US-021, S10-02).
 *
 * <h2>Server-side, like the contract's three listings</h2>
 *
 * Every rule here concerns the three existing paginated listings — `q`,
 * `page`, `size` — and none of them needs a new endpoint. Search is a
 * composition of those listings, one request per requested type, and the web
 * asks the API from the server because the access token is in an httpOnly
 * cookie (`apps/web/AGENTS.md` §4).
 *
 * <h2>What is deliberately not here</h2>
 *
 * No local catalogue search: Room's `pagedBySearch` is the phone's offline
 * fallback (S10-04), and the web never had a local catalogue to fall back to.
 * No ranking: the order is the server's, and the design doc is explicit that no
 * relevance is announced. No history: a new session starts empty.
 */

/** The three catalogues a search can look through, in display order. */
export const SEARCH_TYPES = ["channels", "films", "series"] as const;

export type SearchType = (typeof SEARCH_TYPES)[number];

/**
 * The four filter tabs: `all` is the grouped view, the other three are one
 * list. Not booleans, because a search has exactly one of them at a time.
 */
export type SearchFilter = "all" | SearchType;

/**
 * The contract's ceiling on `q`, in Unicode code points.
 *
 * Aligned with the Android client and the server validator rather than with
 * Java's `String.length()`: an emoji is one character to the person typing it,
 * and a UTF-16 count would refuse at the wrong length (Q9, SR-05).
 */
export const MAX_QUERY_LENGTH = 100;

/** Four hits per type on the grouped view (Q9). */
export const PREVIEW_SIZE = 4;

/** Twenty hits per page in a single type's list (Q9). */
export const PAGE_SIZE = 20;

/**
 * The preview of four and the pages of twenty are **distinct requests**.
 *
 * This is the whole reason the sizes are named constants rather than literals
 * at two call sites: "Voir tous" starts a page 0 at `PAGE_SIZE`, and must never
 * reuse an index computed for `PREVIEW_SIZE` (Q9, SR-06).
 */
export function pageSizeFor(filter: SearchFilter): number {
  return filter === "all" ? PREVIEW_SIZE : PAGE_SIZE;
}

/** The grouped view shows page 0 always; a list honours its page. */
export function pageFor(filter: SearchFilter, requested: number): number {
  return filter === "all" ? 0 : Math.max(0, requested);
}

/** `?type=` — anything unknown is the grouped view, never an error. */
export function filterFromParam(value: string | undefined): SearchFilter {
  return value === "channels" || value === "films" || value === "series" ? value : "all";
}

/** Whether [filter] asks for [type]. */
export function wants(filter: SearchFilter, type: SearchType): boolean {
  return filter === "all" || filter === type;
}

/**
 * Leading and trailing spaces are removed before the request is built; the
 * empty field they can leave behind loads nothing at all (Q9, SR-04).
 */
export function normalizeQuery(raw: string | undefined): string {
  return (raw ?? "").trim();
}

/**
 * A query's length in Unicode code points, not UTF-16 units.
 *
 * Iterating the string counts an astral character — an emoji, a rarer script —
 * as the one character it renders as. The Android client counts the same way,
 * so the two refuse at the same length (Q9, SR-05).
 */
export function codePointLength(value: string): number {
  return [...value].length;
}

/** True when the query exceeds [MAX_QUERY_LENGTH] code points. */
export function queryTooLong(value: string): boolean {
  return codePointLength(value) > MAX_QUERY_LENGTH;
}

/**
 * The search screen's context, as a fiche carries it through and gives it back
 * (US-021, SR-12).
 */
export interface SearchContext {
  query: string;
  filter: SearchFilter;
  page: number;
}

/**
 * The provenance marker a film's or series' page accepts to return to a search.
 *
 * Restricted on purpose: the fiche only ever acts on this exact, known value,
 * and rebuilds the return path from `/app/search` and three whitelisted
 * parameters. An arbitrary `?from=`, a path or a host has nowhere to redirect
 * to, because none of them is ever read (Q9, SR-12).
 */
export const SEARCH_ORIGIN = "search";

/** True only for the exact marker this application writes. */
export function openedFromSearch(value: string | undefined): boolean {
  return value === SEARCH_ORIGIN;
}

/**
 * Where one result opens.
 *
 * A channel has nothing to say about itself and starts playing on its source's
 * page (`?play=`, the pattern of the home rails). A film or a series opens its
 * own page and carries the search it came from: the marker makes the fiche's
 * return link point back at `/app/search`, and the three parameters make that
 * link the exact search — text, filter and page (US-021, SR-12).
 */
export function searchResultPath(
  type: SearchType,
  itemId: string,
  sourceId: string,
  context: SearchContext,
): string {
  if (type === "channels") {
    return `/app/sources/${sourceId}/channels?play=${encodeURIComponent(itemId)}`;
  }
  const base =
    type === "films"
      ? `/app/sources/${sourceId}/vod/${encodeURIComponent(itemId)}`
      : `/app/sources/${sourceId}/series/${encodeURIComponent(itemId)}`;
  return `${base}${queryParams({ from: SEARCH_ORIGIN, ...contextParams(context) })}`;
}

/**
 * The fiche's way back to the search it was opened from.
 *
 * Built from `/app/search` and the three whitelisted parameters only — never
 * from a path or a host that could have been put in the query (SR-12).
 */
export function searchReturnPath(values: {
  q?: string;
  type?: string;
  page?: string;
}): string {
  return `/app/search${queryParams(values)}`;
}

/** The three search parameters, absent values omitted so `all`/page 0 stay out. */
function contextParams(context: SearchContext): Record<string, string | undefined> {
  return {
    q: context.query,
    type: context.filter === "all" ? undefined : context.filter,
    page: context.page > 0 ? String(context.page) : undefined,
  };
}

/** `?a=1&b=2`, or an empty string. Absent values are omitted, never sent empty. */
function queryParams(values: Record<string, string | undefined>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) params.set(key, value);
  }
  const encoded = params.toString();
  return encoded ? `?${encoded}` : "";
}

/** What one type's request answered: the page, or a failure to name. */
export type SectionResult<T> =
  | { ok: true; items: T[]; totalElements: number }
  | { ok: false; code?: string };

/**
 * One typed section as the screen reads it.
 *
 * A section that was asked for and failed is `failed`, **never** an empty `ok`:
 * Q9 is explicit that an error is not an absence, and the two are rendered
 * differently (SR-10). A section the filter did not ask for is `null` at the
 * level of {@link SearchSections}, which is distinct again.
 */
export type SearchSection<T> =
  | { status: "ok"; items: T[]; totalElements: number }
  | { status: "failed"; code?: string };

/** One search's answer: a field per type, `null` when the filter skipped it. */
export interface SearchSections {
  channels: SearchSection<Channel> | null;
  films: SearchSection<VodItem> | null;
  series: SearchSection<Series> | null;
}

/** Which of the three catalogues a source holds, for the tab set (SR-11). */
export type CataloguePresent = Record<SearchType, boolean>;

/** The three calls, injected so the composition is testable without a server. */
export interface SearchFetchers {
  channels: (page: number, size: number) => Promise<SectionResult<Channel>>;
  films: (page: number, size: number) => Promise<SectionResult<VodItem>>;
  series: (page: number, size: number) => Promise<SectionResult<Series>>;
}

/**
 * Runs one section, if the filter and the source ask for it.
 *
 * Never throws: a rejection is the same outcome as a refusal with no code to
 * show, which is "failed, nothing to name". A screen then keeps the sections
 * that did answer (Q9).
 */
export async function runSection<T>(
  wanted: boolean,
  fetch: () => Promise<SectionResult<T>>,
): Promise<SearchSection<T> | null> {
  if (!wanted) return null;
  try {
    const result = await fetch();
    return result.ok
      ? { status: "ok", items: result.items, totalElements: result.totalElements }
      : { status: "failed", ...(result.code ? { code: result.code } : {}) };
  } catch {
    return { status: "failed" };
  }
}

/**
 * One search over the requested types, each independent of the others.
 *
 * <h2>Together, because one slow type must not hold up the others</h2>
 *
 * The three listings are independent, so they are started together and awaited
 * in a fixed order: the assembly is deterministic even though the network is
 * not. `Promise.all` here is safe because {@link runSection} absorbs every
 * failure, so the aggregate cannot reject.
 *
 * <h2>[present] is the source's catalogue, not the query's matches</h2>
 *
 * A source that carries no films has no Films tab (SR-11), and
 * {@link searchSections} must not spend a request on it. A source that *has*
 * films but none matching the query keeps its tab and answers an empty `ok`
 * section.
 */
export async function searchSections(
  filter: SearchFilter,
  page: number,
  size: number,
  fetchers: SearchFetchers,
  present: CataloguePresent,
): Promise<SearchSections> {
  const asked = (type: SearchType) => present[type] && wants(filter, type);

  const [channels, films, series] = await Promise.all([
    runSection(asked("channels"), () => fetchers.channels(page, size)),
    runSection(asked("films"), () => fetchers.films(page, size)),
    runSection(asked("series"), () => fetchers.series(page, size)),
  ]);

  return { channels, films, series };
}
