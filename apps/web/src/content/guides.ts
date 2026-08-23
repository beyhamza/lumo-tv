/**
 * The guide catalogue.
 *
 * The copy itself lives in `src/messages/*.json` under the `Guides` namespace,
 * like every other string in the product (AGENTS.md §4) — this file holds only
 * what is not translatable: which guides exist, and when each was last revised.
 *
 * Slugs are intentionally generic and provider-neutral. The marketing zone is
 * the front door for searches like "configurer une playlist M3U"
 * (docs/architecture.md §4), and naming a provider would cross the product rule
 * in AGENTS.md §1.
 *
 * A CMS would be the next step. Three static entries are enough to prove the
 * rendering strategy, and the shape below is what a CMS would have to return.
 */
export const guideSlugs = [
  "m3u-playlist",
  "xtream-codes",
  "android-tv",
] as const;

export type GuideSlug = (typeof guideSlugs)[number];

/**
 * Feeds `dateModified` in the structured data and `lastModified` in the
 * sitemap. Search engines use it to decide what is worth recrawling, so it has
 * to be a real revision date, not the build date.
 */
export const guideUpdatedAt: Record<GuideSlug, string> = {
  "m3u-playlist": "2026-08-23",
  "xtream-codes": "2026-08-23",
  "android-tv": "2026-08-23",
};

export function isGuideSlug(value: string): value is GuideSlug {
  return (guideSlugs as readonly string[]).includes(value);
}
