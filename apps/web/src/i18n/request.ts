import { hasLocale } from "next-intl";
import { getRequestConfig } from "next-intl/server";
import { routing } from "./routing";

/**
 * Resolves the messages for a request.
 *
 * The messages are imported, not fetched: they are part of the bundle, so a
 * marketing page can be prerendered at build time and served from a CDN with no
 * runtime lookup (docs/architecture.md §4).
 *
 * An unknown locale falls back to the default rather than throwing. A stale link
 * to `/de/...` should show the site in French, not a 500.
 */
export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  const locale = hasLocale(routing.locales, requested)
    ? requested
    : routing.defaultLocale;

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
    // Pinned so a date rendered on the server and re-rendered on the client
    // cannot disagree, which is a hydration error that only appears for
    // visitors in another time zone.
    timeZone: "Europe/Paris",
  };
});
