import { isUuid } from "@/lib/sources/active-source";

/**
 * Which of Direct's two views this browser opens on a source (US-16, S9-04-06).
 *
 * <h2>Two views, one route</h2>
 *
 * Chaînes and Guide are two presentations of the same catalogue, so they share
 * one route — `/app/sources/{id}/channels` — and the view travels in
 * `?view=channels|guide`. A second route would duplicate the filters, the
 * search, the rails and the source notice, and the copy would drift. The rule
 * of S9-04-06 is explicit: reuse the existing route, never a `/direct` twin.
 *
 * <h2>What is remembered, and what is not</h2>
 *
 * Only the **view** survives between sessions, per device and per source
 * (design/0.2.0/direct-guide.md, GD-02). Search and filter deliberately do
 * not: reopening a source starts from Toutes and an empty search, however the
 * last visit ended.
 *
 * The memory is a cookie, one per source (`lumo_direct_view_<sourceId>`), for
 * the same reason the active source is (`lib/sources/active-source.ts`): it is
 * per device, it must be readable while the server renders, and it grants
 * nothing — a forged value is one of the two views and nothing else.
 *
 * <h2>An explicit `?view=` primes the memory</h2>
 *
 * The write happens in the proxy (`src/proxy.ts`), not here and not in the
 * page: a Server Component cannot set a cookie, and the switch is a plain link
 * that must work with JavaScript disabled. The proxy sees the request before
 * anything renders, writes the cookie when `?view=` is explicit, and the page
 * reads it back on its next visit. Home's *Toutes les chaînes* and *Guide TV*
 * entries are the same mechanism at work; a bare `/channels` — an S8 link, the
 * Explore bar — carries no `?view=` and therefore respects the memory.
 *
 * <h2>Why this file imports almost nothing</h2>
 *
 * The rules below are pure over pure values and ship with their tests
 * (AGENTS.md §5). Everything that touches `cookies()` stays in the page and
 * the proxy, which import these functions.
 */

export type DirectView = "channels" | "guide";

/** The first view of Direct, for every source and every device. */
export const DEFAULT_DIRECT_VIEW: DirectView = "channels";

export function isDirectView(value: unknown): value is DirectView {
  return value === "channels" || value === "guide";
}

export const DIRECT_VIEW_COOKIE_PREFIX = "lumo_direct_view_";

/**
 * About a year, like the active-source cookie: a preference that expires is a
 * question asked twice.
 */
export const DIRECT_VIEW_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

/**
 * `lumo_direct_view_<sourceId>`, or null when the id is not a UUID.
 *
 * Scoped by source because that is what the design remembers: two subscriptions
 * are two catalogues, and the last view of one says nothing about the other.
 * An id that is not a UUID never becomes a cookie *name* — it is refused rather
 * than sanitised, exactly as `activeSourceCookieName` does.
 */
export function directViewCookieName(sourceId: string): string | null {
  return isUuid(sourceId)
    ? `${DIRECT_VIEW_COOKIE_PREFIX}${sourceId.toLowerCase()}`
    : null;
}

/**
 * The view this browser remembers for a source, or null.
 *
 * `read` is `cookies().get(name)?.value` in production and a `Map` in the
 * tests. Anything that is not one of the two views reads as "nothing stored": a
 * hand-edited cookie gets the same treatment as no cookie.
 */
export function storedDirectView(
  read: (name: string) => string | undefined,
  sourceId: string,
): DirectView | null {
  const name = directViewCookieName(sourceId);
  if (!name) return null;

  const value = read(name);
  return isDirectView(value) ? value : null;
}

/**
 * The `?view=` value, when it is one of the two views.
 *
 * `searchParams` hands back a string, an array, or nothing; only the first
 * matters, as everywhere on this page. An unknown value is not an error and not
 * a view: it falls back to the memory, then to Chaînes.
 */
export function explicitView(
  value: string | string[] | undefined,
): DirectView | undefined {
  const first = Array.isArray(value) ? value[0] : value;
  return isDirectView(first) ? first : undefined;
}

/**
 * The view the page renders: the explicit one primes the memory, the memory
 * primes the default.
 *
 * `explicit ?? stored ?? "channels"`, in that order and no other. A bare
 * `/channels` — an S8 link, the Explore bar — passes no explicit view and
 * therefore opens what this browser last used (GD-02); a first visit opens
 * Chaînes, which is also what an old S8 link opens on a fresh browser.
 */
export function resolveDirectView(
  stored: DirectView | null,
  explicit: DirectView | undefined,
): DirectView {
  return explicit ?? stored ?? DEFAULT_DIRECT_VIEW;
}

/**
 * `/app/sources/<id>/channels?view=…`, the path only.
 *
 * The locale prefix is `hrefFor`'s job (`@/i18n/navigation`), and the current
 * filter, search, page and player ride along so that switching views keeps them
 * — which is GD-01. Absent values are omitted, never sent empty, as the page's
 * own `queryString` does.
 */
export function directViewHref(
  view: DirectView,
  query: {
    sourceId: string;
    categoryId?: string;
    q?: string;
    page?: number;
    group?: string;
    play?: string;
  },
): string {
  const params = new URLSearchParams();
  params.set("view", view);
  if (query.categoryId) params.set("categoryId", query.categoryId);
  if (query.q) params.set("q", query.q);
  if (query.page !== undefined && query.page > 0) {
    params.set("page", String(query.page));
  }
  if (query.group) params.set("group", query.group);
  if (query.play) params.set("play", query.play);

  return `/app/sources/${query.sourceId}/channels?${params.toString()}`;
}
