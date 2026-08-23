import { defineRouting } from "next-intl/routing";

/**
 * The locale routing, and the SEO decisions baked into it.
 *
 * `localePrefix: "always"` gives every page one canonical URL per language
 * (`/fr/guides/...` and `/en/guides/...`) instead of letting the default locale
 * live at the bare path. It costs a redirect on `/` and buys two things the
 * marketing zone depends on: an `hreflang` pair that actually resolves, and
 * cache keys that do not vary by `Accept-Language` — a page that renders French
 * or English at the same URL cannot be served statically from a CDN
 * (docs/architecture.md §4).
 *
 * French is the default because the product ships in France first; English
 * exists from the first screen because AGENTS.md §4 requires both.
 */
export const routing = defineRouting({
  locales: ["fr", "en"],
  defaultLocale: "fr",
  localePrefix: "always",

  // Detection only decides where `/` sends a first-time visitor. Once a locale
  // is in the URL it wins, so a shared link always opens in the language it was
  // written in.
  localeDetection: true,
});

export type Locale = (typeof routing.locales)[number];

export const locales = routing.locales;
export const defaultLocale = routing.defaultLocale;
