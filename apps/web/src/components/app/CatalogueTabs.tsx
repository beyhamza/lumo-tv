import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";

/**
 * The switch between a source's two catalogues.
 *
 * <h2>What this fixes, and it was reported from real use</h2>
 *
 * Films were reachable only from a source's own page, behind a link at the foot
 * of it. Once somebody was browsing the channels there was **no way across** —
 * they had to go back up two levels and know the link was there. The films had
 * been built, shipped and were effectively invisible.
 *
 * <h2>Why this and not an entry in the header</h2>
 *
 * The header of this zone is account-level and, deliberately, fetches nothing:
 * `/app`, `/app/sources`, `/app/devices`, `/app/subscription` are four static
 * strings, and the layout that draws them renders on every page under `/app`.
 *
 * Films are **per source** — `/app/sources/{id}/vod` — so a header entry would
 * need a source id, which means a lookup in the layout on every page of the zone,
 * for a link. And it would be wrong on an account with two sources: there is no
 * "the" films page to point at.
 *
 * The tabs go where the question is actually asked: on the screen somebody is
 * browsing, about the source they are already in. Channels have the same depth —
 * Sources, then a source, then its catalogue — and this is the step that was
 * missing from it.
 *
 * <h2>The films tab is absent when the source has none</h2>
 *
 * The same rule as everywhere: an empty promise is worse than an absence. Most
 * M3U playlists carry only channels, and a tab onto an empty grid sends somebody
 * looking for a room that is not there.
 *
 * Series are absent for a different reason — no screen exists yet — and a tab
 * would be worse here than anywhere, because it would say the feature is
 * finished.
 */
export function CatalogueTabs({
  sourceId,
  locale,
  active,
  hasFilms,
  label,
  channelsLabel,
  filmsLabel,
}: {
  sourceId: string;
  locale: Locale;
  active: "channels" | "vod";
  hasFilms: boolean;
  label: string;
  channelsLabel: string;
  filmsLabel: string;
}) {
  // One tab is not a choice. On a source with no films this would draw a single
  // "Channels" pill above a list of channels, which says nothing and takes a line.
  if (!hasFilms) return null;

  const tabs = [
    { key: "channels" as const, href: `/app/sources/${sourceId}/channels`, label: channelsLabel },
    { key: "vod" as const, href: `/app/sources/${sourceId}/vod`, label: filmsLabel },
  ];

  return (
    <nav aria-label={label} className="mt-4">
      <ul className="flex gap-2 text-sm">
        {tabs.map((tab) => (
          <li key={tab.key}>
            <a
              href={hrefFor(locale, tab.href)}
              // `aria-current` rather than colour alone: the active tab has to be
              // announced, not merely drawn.
              aria-current={tab.key === active ? "page" : undefined}
              className={
                tab.key === active
                  ? "bg-secondary text-secondary-foreground block rounded-lg px-3 py-1.5 font-medium"
                  : "text-muted-foreground hover:text-foreground block rounded-lg px-3 py-1.5"
              }
            >
              {tab.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
