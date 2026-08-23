import { createNavigation } from "next-intl/navigation";
import { routing, type Locale } from "./routing";

/**
 * Locale-aware navigation helpers.
 *
 * **`Link` is not exported here, on purpose.** next-intl's `Link` renders a
 * `"use client"` component that calls `useLocale()`, so using it in a Server
 * Component requires a `NextIntlClientProvider` above it — and mounting that
 * provider at the root would put a client boundary on the prerendered marketing
 * pages, which must ship no client JavaScript at all
 * (docs/architecture.md §4). Server Components link with a plain anchor and
 * {@link hrefFor}; the trade is one full page load per marketing navigation,
 * which is the right trade on content pages that are meant to be entered from a
 * search result.
 *
 * A client component under a provider can import `Link` from
 * `next-intl/navigation` directly. None does yet.
 *
 * `redirect` and `getPathname` carry no React at all and are safe anywhere on
 * the server.
 */
const navigation = createNavigation(routing);

export const { redirect, permanentRedirect, getPathname } = navigation;

/**
 * The URL of an internal path in a given locale: `/guides` → `/fr/guides`.
 *
 * Goes through next-intl rather than string concatenation so that localised
 * pathnames (`/fr/guides` versus `/en/guides`) would keep working if the routing
 * config ever gains them.
 */
export function hrefFor(locale: Locale, path: string): string {
  return getPathname({ href: path, locale });
}
