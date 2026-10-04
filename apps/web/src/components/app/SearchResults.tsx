"use client";

import { useCallback, useState, useSyncExternalStore } from "react";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import type { Channel, Series, VodItem } from "@/lib/api/types";
import {
  PAGE_SIZE,
  PREVIEW_SIZE,
  formatPageOf,
  isOffline,
  searchPath,
  searchResultPath,
  searchSectionOf,
  searchVerdict,
  type CataloguePresent,
  type SearchFilter,
  type SearchSection,
  type SearchSections,
  type SearchType,
} from "@/lib/search/search";

/**
 * The search results, with a retry that reaches one section (US-021, S10-04).
 *
 * <h2>Why this one piece is a client component</h2>
 *
 * The page renders the field, the tabs and a first result set on the server.
 * But a partial failure needs an interaction the server cannot give without a
 * full navigation: re-read **one** section and keep the other two exactly as
 * they are. Refreshing the whole page would re-read all three and could lose
 * what already answered, which is what Q9 forbids (SR-10). So the results hold
 * their own state and merge one field back in, through the same BFF route
 * (`?only=<type>`); the initial HTML is still server-rendered, and without
 * JavaScript the page simply keeps its first render.
 *
 * <h2>An error is never an empty set</h2>
 *
 * The component draws what {@link searchVerdict} says, not what looks empty:
 * a failed section shows its own message and its own retry, the sections that
 * answered stay on screen, and "no result" is shown only when every requested
 * section answered and none held an item.
 */

type AnyItem = Channel | VodItem | Series;

/** The labels the server already translated; no key resolution on the client. */
export interface SearchResultsLabels {
  channels: string;
  films: string;
  series: string;
  empty: string;
  emptyHint: string;
  failed: string;
  retry: string;
  retrying: string;
  seeAll: string;
  genericError: string;
  previous: string;
  next: string;
  /** `"Page {page} sur {total}"`, placeholders replaced in the client. */
  pageOfTemplate: string;
  /** `Aucun résultat pour « … » dans …`, already interpolated by the server. */
  noResultsFor: string;
  clear: string;
  /** Already localised `/app/search`, without the query. */
  clearHref: string;
  offline: string;
}

export interface SearchResultsProps {
  sourceId: string;
  locale: Locale;
  query: string;
  filter: SearchFilter;
  page: number;
  present: CataloguePresent;
  sections: SearchSections;
  labels: SearchResultsLabels;
}

/** The shape the BFF answers, narrowed to what a retry reads. */
interface SearchResponse {
  sections?: Partial<SearchSections>;
}

export function SearchResults({
  sourceId,
  locale,
  query,
  filter,
  page,
  present,
  sections,
  labels,
}: SearchResultsProps) {
  // The parent keys this component on the search context, so a new text, filter
  // or page remounts it with the server's fresh sections: the URL stays the
  // source of truth and no effect has to copy props into state (S10-02/03).
  const [current, setCurrent] = useState(sections);
  const [retrying, setRetrying] = useState<SearchType | null>(null);
  const online = useSyncExternalStore(subscribeToNetwork, onlineSnapshot, () => true);
  const offline = isOffline({ onLine: online });

  const retry = useCallback(
    async (type: SearchType) => {
      setRetrying(type);
      try {
        const url = `/api/sources/${encodeURIComponent(sourceId)}/search?q=${encodeURIComponent(
          query,
        )}&type=${filter}&page=${page}&only=${type}`;
        const response = await fetch(url, { headers: { accept: "application/json" } });
        if (!response.ok) return; // keep the failure on screen; nothing is faked
        const body = (await response.json()) as SearchResponse;
        const section = body.sections?.[type];
        if (!section) return;
        setCurrent((previous) => merge(previous, type, section));
      } catch {
        // Network failure: the section keeps its failure, no empty result.
      } finally {
        setRetrying(null);
      }
    },
    [sourceId, query, filter, page],
  );

  const verdict = searchVerdict(current, filter, present);
  const sectionLabels = [labels.channels, labels.films, labels.series] as const;
  const labelFor = (type: SearchType) => sectionLabels[indexOf(type)];

  return (
    <div className="mt-6 space-y-8">
      {offline ? (
        <p
          role="status"
          className="border-border text-muted-foreground rounded-xl border border-dashed px-4 py-3 text-sm"
        >
          {labels.offline}
        </p>
      ) : null}

      {verdict.noResults ? (
        <div className="border-border rounded-xl border border-dashed px-5 py-6">
          <p className="font-medium">{labels.noResultsFor}</p>
          <p className="text-muted-foreground mt-1 text-sm">{labels.emptyHint}</p>
          <a
            href={labels.clearHref}
            className="mt-3 inline-block text-sm font-medium underline underline-offset-4"
          >
            {labels.clear}
          </a>
        </div>
      ) : (
        verdict.requested.map((type) => {
          const section = searchSectionOf(current, type);
          const completed = section?.status === "ok" ? section : null;
          const offerSeeAll =
            filter === "all" && completed !== null && completed.totalElements > PREVIEW_SIZE;
          const totalPages =
            completed === null
              ? 1
              : Math.max(1, Math.ceil(completed.totalElements / PAGE_SIZE));

          return (
            <section key={type} aria-label={labelFor(type)}>
              <h2 className="text-lg font-semibold">{labelFor(type)}</h2>

              {section === null ? null : section.status === "failed" ? (
                <div
                  role="alert"
                  className="border-border mt-3 rounded-xl border border-dashed px-4 py-4"
                >
                  <p className="text-sm font-medium">{labels.failed}</p>
                  <p className="text-muted-foreground mt-1 text-xs">{labels.genericError}</p>
                  <button
                    type="button"
                    onClick={() => retry(type)}
                    disabled={retrying !== null}
                    className="mt-2 text-sm underline underline-offset-4 disabled:opacity-60"
                  >
                    {retrying === type ? labels.retrying : labels.retry}
                  </button>
                </div>
              ) : section.items.length === 0 ? (
                <p className="text-muted-foreground mt-3 text-sm">{labels.empty}</p>
              ) : (
                <ul className="mt-3 space-y-2">
                  {section.items.map((item) => (
                    <li key={item.id}>
                      <a
                        href={hrefFor(
                          locale,
                          searchResultPath(type, item.id, sourceId, { query, filter, page }),
                        )}
                        className="border-border hover:border-foreground/30 flex items-center gap-3 rounded-xl border px-4 py-3"
                      >
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-medium">{item.name}</span>
                          <Subtitle type={type} item={item} />
                        </span>
                      </a>
                    </li>
                  ))}
                </ul>
              )}

              {offerSeeAll ? (
                <a
                  href={hrefFor(locale, searchPath({ q: query, type, page: 0 }))}
                  className="mt-3 inline-block text-sm font-medium underline underline-offset-4"
                >
                  {labels.seeAll}
                </a>
              ) : null}

              {filter !== "all" &&
              completed !== null &&
              (page > 0 || page + 1 < totalPages) ? (
                <nav
                  aria-label={formatPageOf(labels.pageOfTemplate, page + 1, totalPages)}
                  className="mt-4 flex items-center gap-4 text-sm"
                >
                  {page > 0 ? (
                    <a
                      href={hrefFor(locale, searchPath({ q: query, type: filter, page: page - 1 }))}
                      className="underline underline-offset-4"
                    >
                      {labels.previous}
                    </a>
                  ) : null}
                  <span className="text-muted-foreground">
                    {formatPageOf(labels.pageOfTemplate, page + 1, totalPages)}
                  </span>
                  {page + 1 < totalPages ? (
                    <a
                      href={hrefFor(locale, searchPath({ q: query, type: filter, page: page + 1 }))}
                      className="underline underline-offset-4"
                    >
                      {labels.next}
                    </a>
                  ) : null}
                </nav>
              ) : null}
            </section>
          );
        })
      )}
    </div>
  );
}

/** Replaces only the retried field, leaving the other sections untouched. */
function merge(
  previous: SearchSections,
  type: SearchType,
  section: SearchSection<AnyItem>,
): SearchSections {
  const next = { ...previous };
  if (type === "channels") next.channels = section as SearchSection<Channel>;
  else if (type === "films") next.films = section as SearchSection<VodItem>;
  else next.series = section as SearchSection<Series>;
  return next;
}

function indexOf(type: SearchType): 0 | 1 | 2 {
  return type === "channels" ? 0 : type === "films" ? 1 : 2;
}

/** The browser's network status, as an external store (no state in an effect). */
function subscribeToNetwork(callback: () => void): () => void {
  window.addEventListener("online", callback);
  window.addEventListener("offline", callback);
  return () => {
    window.removeEventListener("online", callback);
    window.removeEventListener("offline", callback);
  };
}

function onlineSnapshot(): boolean {
  return navigator.onLine;
}

/** The line under a film's or series' name. A channel has nothing more to say. */
function Subtitle({ type, item }: { type: SearchType; item: AnyItem }) {
  if (type === "films") {
    const film = item as VodItem;
    return film.year ? (
      <span className="text-muted-foreground block truncate text-xs">{film.year}</span>
    ) : null;
  }
  if (type === "series") {
    const series = item as Series;
    return series.episode_run_time ? (
      <span className="text-muted-foreground block truncate text-xs">
        {series.episode_run_time} min
      </span>
    ) : null;
  }
  return null;
}
