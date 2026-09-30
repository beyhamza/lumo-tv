import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { errorMessage } from "@/lib/api/error-message";
import type { Channel, Series, VodItem } from "@/lib/api/types";
import { loadActiveSource } from "@/lib/sources/active-source-store";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import {
  PAGE_SIZE,
  PREVIEW_SIZE,
  SEARCH_TYPES,
  filterFromParam,
  normalizeQuery,
  queryTooLong,
  searchResultPath,
  type SearchFilter,
  type SearchSection,
  type SearchSections,
  type SearchType,
} from "@/lib/search/search";
import { loadCatalogueTypes, loadSearchSections } from "@/lib/search/load-search";

/**
 * The unified search (US-021, S10-02).
 *
 * <h2>One field, four tabs, sections the server grouped</h2>
 *
 * The page is a Server Component with a plain GET form, exactly like the
 * channels page's search: submitting it changes the URL, so a search survives a
 * reload and the back button. The access token stays in its httpOnly cookie and
 * the three listings are called from the server (`lib/search`).
 *
 * <h2>What S10-02 leaves to its neighbours</h2>
 *
 * A result is drawn, not yet opened: wiring a card to playback or to a content
 * sheet, and returning with the position kept, is S10-03. The empty field is
 * the invitation here; the fuller no-result wording, a partial error's targeted
 * retry and the offline story are S10-04. What this screen owes today is the
 * field, the tabs, the grouped preview of four, "Voir tous" at page 0 of twenty,
 * and twenty-per-page lists — with the four and the twenty as distinct requests
 * (Q9, SR-06).
 *
 * <h2>S10-03: every row is a link, and the fiche knows the way back</h2>
 *
 * A channel starts playing on its source's page (`?play=`); a film or a series
 * opens its own page carrying the search it came from (`from=search` plus the
 * text, the filter and the page), so the fiche's return link lands on the same
 * search (SR-12). The hrefs are plain `<a>`s: the whole screen still works with
 * no JavaScript.
 *
 * <h2>The tabs list the source's catalogues, not the query's matches</h2>
 *
 * A source with no films has no Films tab; a source with films that the query
 * does not match keeps its tab and answers an empty section. The distinction is
 * SR-11, and it is why the catalogue's composition is read once per render
 * (`loadCatalogueTypes`) rather than inferred from the results.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/search">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app/search",
    title: t("searchTitle"),
    description: t("searchMetaDescription"),
    index: false,
  });
}

export default async function SearchPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/search">) {
  const { locale } = await params;
  const query = await searchParams;
  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");

  const active = await loadActiveSource(session.accessToken, session.userId);
  if (active.state !== "selected") {
    return (
      <div className="border-border rounded-xl border border-dashed px-5 py-6">
        <h1 className="text-2xl font-semibold tracking-tight">{t("searchTitle")}</h1>
        <p className="mt-2 font-medium">{t("searchChooseTitle")}</p>
        <p className="text-muted-foreground mt-1 text-sm">{t("searchChooseBody")}</p>
      </div>
    );
  }
  const sourceId = active.source.id;

  // The query is trimmed once, and everything below reads this value: the field
  // is repopulated from it too, so the URL and what is drawn cannot drift.
  const text = normalizeQuery(single(query.q));
  const tooLong = queryTooLong(text);
  const empty = text.length === 0;

  // Only the types the source actually carries get a tab and a request (SR-11).
  const present = await loadCatalogueTypes(session.accessToken, sourceId);
  const requested = filterFromParam(single(query.type));
  // A `?type=` for a catalogue this source does not have falls back to the
  // grouped view rather than opening a dead list.
  const filter: SearchFilter = requested !== "all" && !present[requested] ? "all" : requested;
  const requestedPage = Math.max(0, Number.parseInt(single(query.page) ?? "0", 10) || 0);

  const sections: SearchSections =
    tooLong || empty
      ? { channels: null, films: null, series: null }
      : await loadSearchSections(
          session.accessToken,
          sourceId,
          text,
          filter,
          requestedPage,
          present,
        );

  const searchHref = (values: { type?: SearchFilter; page?: number }) => {
    const type = values.type ?? filter;
    return hrefFor(
      locale as Locale,
      `/app/search${queryString({
        q: text,
        type: type === "all" ? undefined : type,
        page: values.page && values.page > 0 ? String(values.page) : undefined,
      })}`,
    );
  };

  const retryHref = searchHref({ page: requestedPage });

  const renderSection = (type: SearchType, section: SearchSection<Channel | VodItem | Series> | null, withPagination: boolean) => {
    const animated = section;
    const completed = animated?.status === "ok" ? animated : null;
    const offerSeeAll =
      filter === "all" && completed !== null && Number(completed.totalElements) > PREVIEW_SIZE;
    const totalPages =
      completed === null ? 1 : Math.max(1, Math.ceil(Number(completed.totalElements) / PAGE_SIZE));

    return (
      <Section
        key={type}
        type={type}
        section={animated}
        labels={{
          title: t(titleKey(type)),
          empty: t("searchNoResults"),
          failed: t("searchSectionFailed"),
          retry: t("searchRetry"),
          seeAll: t("searchSeeAll"),
          genericError: errorMessage(undefined, tErrors),
        }}
        seeAllHref={offerSeeAll ? searchHref({ type, page: 0 }) : undefined}
        retryHref={retryHref}
        resultHref={(itemId) =>
          hrefFor(
            locale as Locale,
            searchResultPath(type, itemId, sourceId, {
              query: text,
              filter,
              page: requestedPage,
            }),
          )
        }
        pagination={
          withPagination
            ? {
                previousHref: requestedPage > 0 ? searchHref({ page: requestedPage - 1 }) : undefined,
                nextHref:
                  requestedPage + 1 < totalPages
                    ? searchHref({ page: requestedPage + 1 })
                    : undefined,
                label: t("searchPageOf", { page: requestedPage + 1, total: totalPages }),
                previous: t("searchPrevious"),
                next: t("searchNext"),
              }
            : undefined
        }
      />
    );
  };

  const shown =
    filter === "all" ? SEARCH_TYPES.filter((type) => present[type]) : [filter as SearchType];

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">{t("searchTitle")}</h1>
      <p className="text-muted-foreground mt-1 text-sm">
        {t("searchInSource", { source: active.source.label })}
      </p>

      {/* A plain GET form, as on the channels page. `type` rides in a hidden
          field so submitting the text keeps the open tab; `page` is absent, so
          a new text starts at the first page (Q9). */}
      <form method="get" className="mt-6 flex flex-wrap items-end gap-3">
        {filter !== "all" ? <input type="hidden" name="type" value={filter} /> : null}
        <div className="space-y-1.5">
          <label htmlFor="q" className="text-sm font-medium">
            {t("searchLabel")}
          </label>
          <input
            id="q"
            name="q"
            type="search"
            defaultValue={text}
            autoComplete="off"
            className="border-input bg-background h-9 w-64 rounded-lg border px-3 text-sm"
          />
        </div>
        <button
          type="submit"
          className="bg-secondary text-secondary-foreground h-9 rounded-lg px-4 text-sm font-medium"
        >
          {t("searchSubmit")}
        </button>
        <p className="text-muted-foreground w-full text-sm">{t("searchHint")}</p>
      </form>

      {tooLong ? (
        <p role="alert" className="text-destructive mt-4 text-sm">
          {t("searchTooLong", { max: 100 })}
        </p>
      ) : null}

      <nav aria-label={t("searchFiltersLabel")} className="mt-5">
        <ul className="flex flex-wrap gap-2 text-sm">
          <FilterTab
            href={searchHref({ type: "all", page: 0 })}
            active={filter === "all"}
            label={t("searchFilterAll")}
          />
          {SEARCH_TYPES.filter((type) => present[type]).map((type) => (
            <FilterTab
              key={type}
              href={searchHref({ type, page: 0 })}
              active={filter === type}
              label={t(filterLabelKey(type))}
            />
          ))}
        </ul>
      </nav>

      {tooLong ? null : empty ? (
        <div className="border-border mt-6 rounded-xl border border-dashed px-5 py-6">
          <p className="font-medium">{t("searchInvitation")}</p>
          <p className="text-muted-foreground mt-1 text-sm">{t("searchInvitationHint")}</p>
        </div>
      ) : (
        <div className="mt-6 space-y-8">
          {shown.map((type) =>
            renderSection(type, sectionOf(sections, type), filter !== "all"),
          )}
        </div>
      )}
    </div>
  );
}

function filterLabelKey(
  type: SearchType,
): "searchFilterChannels" | "searchFilterFilms" | "searchFilterSeries" {
  return type === "channels"
    ? "searchFilterChannels"
    : type === "films"
      ? "searchFilterFilms"
      : "searchFilterSeries";
}

function titleKey(
  type: SearchType,
): "searchSectionChannels" | "searchSectionFilms" | "searchSectionSeries" {
  return type === "channels"
    ? "searchSectionChannels"
    : type === "films"
      ? "searchSectionFilms"
      : "searchSectionSeries";
}

function sectionOf(
  sections: SearchSections,
  type: SearchType,
): SearchSection<Channel | VodItem | Series> | null {
  return type === "channels"
    ? sections.channels
    : type === "films"
      ? sections.films
      : sections.series;
}

function FilterTab({
  href,
  active,
  label,
}: {
  href: string;
  active: boolean;
  label: string;
}) {
  return (
    <li>
      <a
        href={href}
        aria-current={active ? "true" : undefined}
        className={
          active
            ? "bg-secondary text-secondary-foreground block rounded-lg px-3 py-1.5 font-medium"
            : "text-muted-foreground hover:text-foreground block rounded-lg px-3 py-1.5"
        }
      >
        {label}
      </a>
    </li>
  );
}

/**
 * One typed section: its title, its rows, and its own trouble.
 *
 * A failure is drawn as a failure and never as an empty list (Q9, SR-10), and
 * the retry reaches this section alone — the other sections of the render were
 * fetched independently and stay on screen.
 */
function Section({
  type,
  section,
  labels,
  seeAllHref,
  retryHref,
  resultHref,
  pagination,
}: {
  type: SearchType;
  section: SearchSection<Channel | VodItem | Series> | null;
  labels: {
    title: string;
    empty: string;
    failed: string;
    retry: string;
    seeAll: string;
    genericError: string;
  };
  seeAllHref?: string;
  retryHref: string;
  /** Where one row opens: a channel's playback or a film/series fiche (S10-03). */
  resultHref: (itemId: string) => string;
  pagination?: {
    previousHref?: string;
    nextHref?: string;
    label: string;
    previous: string;
    next: string;
  };
}) {
  return (
    <section aria-label={labels.title}>
      <h2 className="text-lg font-semibold">{labels.title}</h2>

      {section === null ? null : section.status === "failed" ? (
        <div role="alert" className="border-border mt-3 rounded-xl border border-dashed px-4 py-4">
          <p className="text-sm font-medium">{labels.failed}</p>
          <p className="text-muted-foreground mt-1 text-xs">{labels.genericError}</p>
          <a href={retryHref} className="mt-2 inline-block text-sm underline underline-offset-4">
            {labels.retry}
          </a>
        </div>
      ) : section.items.length === 0 ? (
        <p className="text-muted-foreground mt-3 text-sm">{labels.empty}</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {section.items.map((item) => (
            <li key={item.id}>
              <a
                href={resultHref(item.id)}
                className="border-border flex items-center gap-3 rounded-xl border px-4 py-3 hover:border-foreground/30"
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

      {seeAllHref ? (
        <a
          href={seeAllHref}
          className="mt-3 inline-block text-sm font-medium underline underline-offset-4"
        >
          {labels.seeAll}
        </a>
      ) : null}

      {pagination && (pagination.previousHref || pagination.nextHref) ? (
        <nav aria-label={pagination.label} className="mt-4 flex items-center gap-4 text-sm">
          {pagination.previousHref ? (
            <a href={pagination.previousHref} className="underline underline-offset-4">
              {pagination.previous}
            </a>
          ) : null}
          <span className="text-muted-foreground">{pagination.label}</span>
          {pagination.nextHref ? (
            <a href={pagination.nextHref} className="underline underline-offset-4">
              {pagination.next}
            </a>
          ) : null}
        </nav>
      ) : null}
    </section>
  );
}

/** The line under a film's or series' name. A channel has nothing more to say. */
function Subtitle({ type, item }: { type: SearchType; item: Channel | VodItem | Series }) {
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

/** `?a=1&b=2`, or an empty string. Absent values are omitted, never sent empty. */
function queryString(values: Record<string, string | undefined>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) params.set(key, value);
  }
  const encoded = params.toString();
  return encoded ? `?${encoded}` : "";
}

/** `searchParams` hands back a string, an array, or nothing. Only the first matters. */
function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
