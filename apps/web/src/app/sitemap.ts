import type { MetadataRoute } from "next";
import { guideSlugs, guideUpdatedAt } from "@/content/guides";
import { routing } from "@/i18n/routing";
import { siteUrl } from "@/lib/env.public";

/**
 * The sitemap, covering the marketing zone only.
 *
 * `/app` and `/activate` are absent on purpose: one needs a session and the
 * other is a one-shot flow reached from a television screen. Listing either
 * would send crawlers to pages that can only answer with a redirect, and would
 * spend crawl budget that belongs to the guides.
 *
 * Every entry carries `alternates.languages`. That is what tells a search engine
 * the French and English versions are one page in two languages rather than two
 * pages competing for the same query.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const base = siteUrl().replace(/\/$/, "");

  const languagesFor = (path: string) =>
    Object.fromEntries(
      routing.locales.map((locale) => [locale, `${base}/${locale}${path}`]),
    );

  const entry = (
    path: string,
    options: { priority: number; changeFrequency: MetadataRoute.Sitemap[number]["changeFrequency"]; lastModified?: Date },
  ): MetadataRoute.Sitemap => [
    {
      // The default locale's URL is the one listed; the alternates carry the
      // rest. Listing all locales as separate entries would duplicate the same
      // page under two `loc` values.
      url: `${base}/${routing.defaultLocale}${path}`,
      lastModified: options.lastModified ?? new Date(),
      changeFrequency: options.changeFrequency,
      priority: options.priority,
      alternates: { languages: languagesFor(path) },
    },
  ];

  return [
    ...entry("", { priority: 1, changeFrequency: "weekly" }),
    ...entry("/guides", { priority: 0.8, changeFrequency: "weekly" }),
    ...guideSlugs.flatMap((slug) =>
      entry(`/guides/${slug}`, {
        priority: 0.7,
        changeFrequency: "monthly",
        lastModified: new Date(guideUpdatedAt[slug]),
      }),
    ),
  ];
}
