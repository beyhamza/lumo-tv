import { safeRedirectTarget } from "@/lib/security/redirect-target";
import { isUuid } from "./active-source";

const FALLBACK = "/app";

const ID = "[0-9a-fA-F-]{36}";

/** `/app/sources/{id}/{channels|vod|series}` and one optional detail segment. */
const CATALOGUE = new RegExp(`^/app/sources/${ID}/(channels|vod|series)(?:/[^/]+)?$`);

const MANAGED_SOURCE = new RegExp(`^/app/sources/(${ID})$`);

/**
 * The pages that do not depend on a source id in their path.
 *
 * A page added to the zone and forgotten here sends a switch made from it to the
 * account home instead of leaving the user in place: a small annoyance, and the
 * safe direction for a list like this one to be wrong in.
 */
const STAY: readonly string[] = [
  "/app",
  "/app/sources",
  "/app/sources/new",
  "/app/favorites",
  "/app/devices",
  "/app/subscription",
];

/**
 * Where the browser lands after switching the active source (US-018).
 *
 * The rule is "keep the section, change the source":
 *
 * - from a catalogue — Direct, Films, Séries — to **the same catalogue** of the
 *   new source. Somebody browsing films who switches source wants the other
 *   source's films, not a trip back to a home screen;
 * - from a film or a series page to the matching **catalogue**, never to the
 *   page itself: that identifier belongs to the old source, and under the new
 *   one it is a 404 at best;
 * - from anywhere else in the account zone — favourites, devices, My sources —
 *   to where the user already is. Those pages re-render around the new source
 *   on their own;
 * - the query string never survives. A category, a search or a page number are
 *   the old source's, and carrying `?category=` across would open the new
 *   catalogue on an empty list with nothing on screen to explain why.
 *
 * <h2>This is a redirect target, so it is an allow-list</h2>
 *
 * `from` arrives in a hidden form field: from the browser, however much it was
 * this application that put it there. It gets the care `next` gets on the
 * sign-in page ({@link safeRedirectTarget}), and then more: instead of being
 * cleaned up and followed, it is only ever **matched** against the routes this
 * zone has. Nothing typed by the caller reaches the returned path except a
 * source id that has been checked to be a UUID — the rest is constants. An
 * unknown path, another locale's path or another site's URL all land on the
 * account home, which is harmless.
 *
 * Another locale is refused rather than followed because the action redirects
 * within the locale it was posted to; a French form claiming to come from
 * `/en/…` is not something this application renders.
 *
 * Pure, and in its own module, for the two reasons `redirect-target.ts` gives: a
 * `"use server"` file can only export async functions, and security logic ships
 * with its tests.
 *
 * @param from the pathname the switcher was rendered on, locale included
 *   (`/fr/app/sources/…/vod`), as the proxy reported it.
 * @param locale the locale the action runs in.
 * @param sourceId the source being switched to, already known to be the
 *   caller's.
 * @returns a path without its locale prefix, for next-intl's `redirect`.
 */
export function sourceSwitchTarget(
  from: unknown,
  locale: string,
  sourceId: string,
): string {
  if (!isUuid(sourceId)) return FALLBACK;
  if (typeof from !== "string") return FALLBACK;

  // Filters go first, so that what is matched below is a bare path.
  const pathname = from.split(/[?#]/, 1)[0];

  // The prefix is required, not merely tolerated: the layout always sends it,
  // so a value without one was not produced by the layout.
  if (pathname !== `/${locale}` && !pathname.startsWith(`/${locale}/`)) {
    return FALLBACK;
  }

  // Only the current locale is offered for stripping, so `/en/app/…` posted to
  // a French action keeps its prefix — and then matches nothing below.
  const path = safeRedirectTarget(pathname, [locale], FALLBACK);

  const catalogue = CATALOGUE.exec(path);
  if (catalogue) {
    // Group 1 is one of three literals. The old source id and whatever detail
    // segment followed are dropped on the floor.
    return `/app/sources/${sourceId}/${catalogue[1]}`;
  }

  if (STAY.includes(path)) return path;

  // One source's own page under My sources. It manages *that* source whichever
  // one is being browsed, so switching does not move it.
  const managed = MANAGED_SOURCE.exec(path);
  if (managed && isUuid(managed[1])) return `/app/sources/${managed[1]}`;

  return FALLBACK;
}
