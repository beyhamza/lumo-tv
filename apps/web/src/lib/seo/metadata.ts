import type { Metadata } from "next";
import { getPathname } from "@/i18n/navigation";
import { routing, type Locale } from "@/i18n/routing";
import { siteUrl } from "@/lib/env.public";

/**
 * Canonical URL and `hreflang` alternates for one page.
 *
 * Both are required, and for different reasons. The canonical says which URL is
 * the page; the alternates say which URLs are the same page in another
 * language. Ship one without the other and Google either picks a canonical
 * itself — often the wrong locale — or treats the two translations as duplicate
 * content competing with each other.
 *
 * `x-default` points at French, the default locale: it is what a search engine
 * serves to a visitor whose language matches neither.
 *
 * The paths are produced by next-intl's `getPathname` rather than string
 * concatenation, so a future localised pathname (`/fr/guides` versus
 * `/en/guides`) stays correct here without this file changing.
 */
export function alternates(
  href: Parameters<typeof getPathname>[0]["href"],
): NonNullable<Metadata["alternates"]> {
  const base = siteUrl().replace(/\/$/, "");

  const languages = Object.fromEntries(
    routing.locales.map((locale) => [
      locale,
      `${base}${getPathname({ href, locale })}`,
    ]),
  ) as Record<Locale, string>;

  return {
    languages: {
      ...languages,
      "x-default": `${base}${getPathname({ href, locale: routing.defaultLocale })}`,
    },
  };
}

/**
 * Metadata for a page, with its canonical, its alternates and its Open Graph
 * tags derived from one title and one description.
 *
 * Centralised so that adding a page cannot mean forgetting the alternates —
 * which is invisible in review and expensive in search results.
 */
export function pageMetadata(options: {
  locale: Locale;
  href: Parameters<typeof getPathname>[0]["href"];
  title: string;
  description: string;
  /** Marketing pages are indexable; the app and activation zones are not. */
  index?: boolean;
}): Metadata {
  const base = siteUrl().replace(/\/$/, "");
  const canonical = `${base}${getPathname({ href: options.href, locale: options.locale })}`;
  const index = options.index ?? true;

  return {
    title: options.title,
    description: options.description,
    alternates: {
      canonical,
      ...alternates(options.href),
    },
    robots: index
      ? undefined
      : { index: false, follow: false, nocache: true },
    openGraph: {
      type: "website",
      url: canonical,
      title: options.title,
      description: options.description,
      locale: options.locale === "fr" ? "fr_FR" : "en_GB",
      siteName: "Lumo TV",
    },
    twitter: {
      card: "summary_large_image",
      title: options.title,
      description: options.description,
    },
  };
}
