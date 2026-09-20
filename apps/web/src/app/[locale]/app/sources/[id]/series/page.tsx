import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { CatalogueNotReady } from "@/components/app/CatalogueNotReady";
import { CatalogueTabs } from "@/components/app/CatalogueTabs";
import { SourceNotice } from "@/components/app/SourceNotice";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/error-message";
import type { Category, Episode, Series } from "@/lib/api/types";
import { attempt, outcomeOf } from "@/lib/catalogue/attempt";
import { cataloguePageState } from "@/lib/catalogue/page-state";
import { isFinished } from "@/lib/playback/progress";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { seriesApply } from "@/lib/sources/content-counts";
import { sourceCondition } from "@/lib/sources/source-condition";

/**
 * The series of one source (US-15, S6-07).
 *
 * <h2>The films grid, one level up</h2>
 *
 * Category, page and query in the URL; posters rather than a list, because a
 * series is chosen by looking exactly as a film is; no synopsis in the listing.
 * The divergence would be the defect — a viewer who has learnt one of the two
 * catalogues has learnt the other.
 *
 * <h2>Where a series differs, and it is one line</h2>
 *
 * Opening a film gives a film. Opening a series gives a **tree**, and that tree
 * costs a call to the user's own panel — see the detail page. Nothing on this
 * screen triggers it: a grid that loaded a tree per card would be eight hundred
 * requests against somebody's provider for one scroll.
 *
 * <h2>The "continue watching" rail, and it is a rail of series (S6-08)</h2>
 *
 * Progress is recorded on an **episode**; resuming is thought about in **series**.
 * Turning one into the other takes two resolutions the contract was shaped for:
 * `GET /sources/{id}/episodes?ids=` — a resolver, not a listing, and this is what
 * it exists for — gives each row its `series_id`, and
 * `GET /sources/{id}/series?ids=` turns those into posters.
 *
 * **One card per series, never one per episode.** Somebody who watched three
 * episodes last night has three rows and wants one card; a rail showing three has
 * understood the data and not the use. The most recently touched row wins, which is
 * the order the server already returns.
 *
 * <h2>Where a card goes, and the one row of the table this page cannot answer</h2>
 *
 * - **Started, under the threshold** — straight to that episode, at its position.
 *   The card carries `?season=…&play=…`, so it is one click and the URL is
 *   shareable like every other piece of state in this zone.
 * - **Past the threshold** — to the series, and no further. What somebody wants
 *   next is the *following* episode, and which episode follows is on the other
 *   side of a tree this page has not fetched. Fetching one per card is the
 *   request-per-poster the whole design refuses, so the answer is deferred by one
 *   click to the page that has the tree. **Sending them back into the credits
 *   would be a wrong answer; this is a shorter one.**
 * - **Finished with nothing after it** — still shown here, and the series page is
 *   what discovers there is nothing after it. The applications drop such a series
 *   from the rail because they hold the tree; this page does not, and inventing an
 *   answer it cannot check would be worse than one extra card.
 *
 * <h2>An M3U source always shows an empty grid, and says why</h2>
 *
 * Series are an Xtream feature (`adr/0010`): a playlist declares no season and no
 * episode, and this product does not reconstruct a tree from titles. The tab is
 * here anyway — hiding it is what made somebody conclude the feature did not
 * exist — and the empty state is where the reason is given.
 *
 * <h2>The catalogue is there whenever one exists (contract lot C4)</h2>
 *
 * Exactly as on the channel page, which is where the reasoning is written out:
 * the source is fetched alongside the listing so that a refresh in progress or a
 * failed one is said **above** the catalogue (`SourceNotice`);
 * `409 SOURCE_NOT_READY` only ever means "no catalogue yet"
 * (`CatalogueNotReady`); and a request that failed is labelled "could not be
 * loaded", never drawn as an empty list (`cataloguePageState`).
 */

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/[id]/series">): Promise<Metadata> {
  const { locale, id } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: `/app/sources/${id}/series`,
    title: t("seriesTitle"),
    description: t("seriesMetaDescription"),
    index: false,
  });
}

const PAGE_SIZE = 48;

export default async function SeriesPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/sources/[id]/series">) {
  const { locale, id } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");

  const categoryId = single(query.categoryId);
  const search = single(query.q);
  const page = Math.max(0, Number.parseInt(single(query.page) ?? "0", 10) || 0);

  // Each through `attempt()`: a request with no answer at all must not reject
  // the whole `Promise.all` and take the parts that did answer with it.
  const token = session.accessToken;
  const [categories, series, source, progress] = await Promise.all([
    attempt(() =>
      api(token).GET("/sources/{id}/categories", {
        params: { path: { id }, query: { contentType: "SERIES" } },
      }),
    ),
    attempt(() =>
      api(token).GET("/sources/{id}/series", {
        params: {
          path: { id },
          query: {
            ...(categoryId ? { categoryId } : {}),
            ...(search ? { q: search } : {}),
            page,
            size: PAGE_SIZE,
          },
        },
      }),
    ),
    // Twice useful. It tells the two empty states apart — "this playlist cannot
    // carry series" and "this panel offers none" are different facts, and one
    // sentence for both would tell an Xtream user their panel cannot do
    // something it can — and it is what the notice above the catalogue is
    // written from (C4).
    attempt(() => api(token).GET("/sources/{id}", { params: { path: { id } } })),
    // The rail (S6-08). Already ordered most recently updated first, which the
    // contract says is the order this rail wants, so nothing here re-sorts it.
    // Its failure is deliberately not part of `failure` below: a catalogue that
    // refused to render because a rail could not be read would trade the whole
    // screen for its smallest part.
    attempt(() =>
      api(token).GET("/me/progress", {
        params: { query: { sourceId: id, itemType: "EPISODE", size: RAIL_SIZE * 4 } },
      }),
    ),
  ]);

  const state = cataloguePageState(outcomeOf(series), outcomeOf(categories));

  if (state.kind === "not-ready") {
    return (
      <CatalogueNotReady sourceId={id} source={source.data ?? null} locale={locale as Locale} />
    );
  }
  if (state.kind === "error") {
    return (
      <p role="alert" className="text-destructive text-sm">
        {errorMessage(state.code, tErrors)}
      </p>
    );
  }
  if (state.kind === "unavailable") {
    return <Unavailable />;
  }

  // Either request may still have failed — one of them, never both. `undefined`
  // stays `undefined`, so nothing below can take "did not load" for "empty".
  const listing = series.data;
  const listed = listing?.items ?? [];
  const totalPages = listing?.total_pages ?? 0;
  // Three answers, not two. When the source request failed the kind is unknown,
  // and both explanations of an empty grid — "a playlist cannot carry series",
  // "this provider offers none" — would be a guess: neither is given then.
  const isPlaylist = source.data ? !seriesApply(source.data.kind) : null;
  const rail = await continueWatching({
    token,
    sourceId: id,
    rows: progress.data?.items ?? [],
    onPage: listed,
  });
  const context = { categoryId, q: search, page: page > 0 ? String(page) : undefined };

  return (
    <div>
      <p className="text-sm">
        <a
          href={hrefFor(locale as Locale, `/app/sources/${id}`)}
          className="text-muted-foreground underline underline-offset-4"
        >
          {t("catalogueBackToSource")}
        </a>
      </p>

      <h1 className="mt-4 text-2xl font-semibold tracking-tight">{t("seriesTitle")}</h1>
      {/* No total when the listing did not load — it is unknown, not zero — and
          none for a playlist, where a series count does not apply (adr/0010):
          the empty state below says why instead. */}
      {listing && isPlaylist !== true ? (
        <p className="text-muted-foreground mt-2">
          {t("seriesCount", { total: listing.total_elements })}
        </p>
      ) : null}

      <CatalogueTabs
        sourceId={id}
        locale={locale as Locale}
        active="series"
        label={t("catalogueTabsLabel")}
        channelsLabel={t("catalogueTitle")}
        filmsLabel={t("filmsTitle")}
        seriesLabel={t("seriesTitle")}
      />

      {/* Above the catalogue, not instead of it (C4). Renders nothing for a
          source that is simply ready. */}
      {source.data ? (
        <SourceNotice
          source={source.data}
          condition={sourceCondition(source.data)}
          locale={locale as Locale}
        />
      ) : null}

      <ContinueWatching
        entries={rail}
        title={t("seriesContinueWatching")}
        href={(entry) =>
          hrefFor(
            locale as Locale,
            `/app/sources/${id}/series/${entry.series.id}${
              // Only where this page can name the episode. See its documentation:
              // a finished one would send somebody back into the credits.
              entry.resume
                ? queryString({
                    season: String(entry.episode.season_number),
                    play: entry.episode.id,
                  })
                : ""
            }`,
          )
        }
        episodeLabel={(seasonNumber, episodeNumber) =>
          t("seriesSeasonEpisode", { season: seasonNumber, episode: episodeNumber })
        }
      />

      <form method="get" className="mt-6 flex flex-wrap items-end gap-3">
        {categoryId ? (
          <input type="hidden" name="categoryId" value={categoryId} />
        ) : null}
        <div className="space-y-1.5">
          <label htmlFor="q" className="text-sm font-medium">
            {t("seriesSearchLabel")}
          </label>
          <input
            id="q"
            name="q"
            type="search"
            defaultValue={search ?? ""}
            className="border-input bg-background h-9 rounded-lg border px-3 text-sm"
          />
        </div>
        <button
          type="submit"
          className="bg-secondary text-secondary-foreground h-9 rounded-lg px-4 text-sm font-medium"
        >
          {t("catalogueSearchSubmit")}
        </button>
        <p className="text-muted-foreground w-full text-sm">{t("seriesSearchHint")}</p>
      </form>

      <div className="mt-8 grid gap-8 md:grid-cols-[14rem_1fr]">
        <CategoryList
          categories={categories.data?.items ?? []}
          failedLabel={state.categoriesFailed ? t("catalogueCategoriesFailed") : undefined}
          activeId={categoryId}
          sourceId={id}
          locale={locale as Locale}
          search={search}
          allLabel={t("seriesAllCategories")}
        />

        <div>
          {state.listingFailed ? (
            // Not the empty state: nothing is known about this list.
            <div role="alert" className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">{t("catalogueListFailed")}</p>
              <p className="text-muted-foreground mt-1 text-sm">{t("catalogueListFailedHint")}</p>
            </div>
          ) : listed.length === 0 ? (
            <div className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">
                {search ? t("seriesNoResults") : t("seriesEmpty")}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                {search
                  ? t("seriesNoResultsHint")
                  : isPlaylist === null
                    ? null
                    : isPlaylist
                      ? t("seriesEmptyPlaylist")
                      : t("seriesEmptyPanel")}
              </p>
            </div>
          ) : (
            <ul
              aria-label={t("seriesTitle")}
              className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4"
            >
              {listed.map((row) => (
                <SeriesCard
                  key={row.id}
                  series={row}
                  href={hrefFor(
                    locale as Locale,
                    `/app/sources/${id}/series/${row.id}${queryString(context)}`,
                  )}
                />
              ))}
            </ul>
          )}

          {totalPages > 1 ? (
            <Pagination
              page={page}
              totalPages={totalPages}
              sourceId={id}
              locale={locale as Locale}
              categoryId={categoryId}
              search={search}
              previousLabel={t("cataloguePrevious")}
              nextLabel={t("catalogueNext")}
              positionLabel={t("cataloguePageOf", { page: page + 1, total: totalPages })}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
}

function SeriesCard({ series, href }: { series: Series; href: string }) {
  return (
    <li>
      <a
        href={href}
        className="focus-visible:ring-ring block rounded-lg focus-visible:ring-2 focus-visible:outline-none"
      >
        <Poster series={series} />
        <p className="mt-2 line-clamp-2 text-sm font-medium">{series.name}</p>
        {series.year ? (
          <p className="text-muted-foreground text-xs">{series.year}</p>
        ) : null}
      </a>
    </li>
  );
}

/** The poster the source advertises, or the title on a plain card. See the films grid. */
function Poster({ series }: { series: Series }) {
  if (!series.poster_url) {
    return (
      <div className="bg-muted text-muted-foreground flex aspect-[2/3] items-center justify-center rounded-lg p-3 text-center text-xs">
        {series.name}
      </div>
    );
  }

  // Not `next/image`, for the reason given on the films grid: the host is
  // whatever panel this person subscribes to, and there is no list to configure.
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={series.poster_url}
      alt=""
      loading="lazy"
      className="bg-muted aspect-[2/3] w-full rounded-lg object-cover"
    />
  );
}

function CategoryList({
  categories,
  failedLabel,
  activeId,
  sourceId,
  locale,
  search,
  allLabel,
}: {
  categories: Category[];
  /**
   * Set when the categories request failed. "All" still works — it is a link to
   * this page without a filter — and the sentence stands where the list would
   * be, so an absent list is not read as a source with no categories.
   */
  failedLabel?: string;
  activeId?: string;
  sourceId: string;
  locale: Locale;
  search?: string;
  allLabel: string;
}) {
  const href = (categoryId?: string) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/series${queryString({ categoryId, q: search })}`,
    );

  return (
    <nav aria-label={allLabel}>
      <ul className="space-y-1 text-sm">
        <li>
          <a
            href={href()}
            aria-current={activeId ? undefined : "page"}
            className={linkClass(!activeId)}
          >
            {allLabel}
          </a>
        </li>
        {categories.map((category) => (
          <li key={category.id}>
            <a
              href={href(category.id)}
              aria-current={activeId === category.id ? "page" : undefined}
              className={linkClass(activeId === category.id)}
            >
              {category.name}
              {category.channel_count != null ? (
                <span className="text-muted-foreground"> ({category.channel_count})</span>
              ) : null}
            </a>
          </li>
        ))}
      </ul>
      {failedLabel ? (
        <p role="alert" className="text-muted-foreground mt-2 px-3 text-sm">
          {failedLabel}
        </p>
      ) : null}
    </nav>
  );
}

function linkClass(active: boolean): string {
  return active
    ? "bg-secondary text-secondary-foreground block rounded-lg px-3 py-2 font-medium"
    : "text-muted-foreground hover:text-foreground block rounded-lg px-3 py-2";
}

function Pagination({
  page,
  totalPages,
  sourceId,
  locale,
  categoryId,
  search,
  previousLabel,
  nextLabel,
  positionLabel,
}: {
  page: number;
  totalPages: number;
  sourceId: string;
  locale: Locale;
  categoryId?: string;
  search?: string;
  previousLabel: string;
  nextLabel: string;
  positionLabel: string;
}) {
  const href = (target: number) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/series${queryString({
        categoryId,
        q: search,
        page: target > 0 ? String(target) : undefined,
      })}`,
    );

  return (
    <nav aria-label={positionLabel} className="mt-8 flex items-center gap-4 text-sm">
      {page > 0 ? (
        <a href={href(page - 1)} className="underline underline-offset-4">
          {previousLabel}
        </a>
      ) : null}
      <span className="text-muted-foreground">{positionLabel}</span>
      {page + 1 < totalPages ? (
        <a href={href(page + 1)} className="underline underline-offset-4">
          {nextLabel}
        </a>
      ) : null}
    </nav>
  );
}

function queryString(values: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) search.set(key, value);
  }
  const rendered = search.toString();
  return rendered ? `?${rendered}` : "";
}

function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** How many the rail holds. A shortcut, not a second catalogue. */
const RAIL_SIZE = 12;

/**
 * Turns saved episode positions into one card per series (S6-08).
 *
 * <h2>Two resolutions, and both are what the contract was shaped for</h2>
 *
 * `GET /me/progress` carries identifiers and positions — not posters, not titles,
 * and not the series an episode belongs to. So: episodes by id (a **resolver**,
 * where `ids` is required precisely so it can never become a listing), then the
 * series those name, minus any already on this page.
 *
 * <h2>What is dropped, and why each is dropped rather than drawn</h2>
 *
 * - **Finished episodes with nothing after them.** This page has no tree, so it
 *   cannot know what follows an episode somebody finished. It keeps the series on
 *   the strength of the row and lets the series page decide — which it can, because
 *   it has the tree. A card that opened onto the credits would be the wrong answer;
 *   a card that opens onto the series is a shorter one.
 * - **Episodes the last re-synchronisation dropped.** Absent from the resolver by
 *   contract, so they fall out rather than rendering as a gap.
 * - **Everything after the first row of a series.** `distinctBy` on the series id,
 *   in the server's order, which is the "one card, most recent" rule in one step.
 */
async function continueWatching({
  token,
  sourceId,
  rows,
  onPage,
}: {
  token: string;
  sourceId: string;
  rows: { item_ref: string; position_ms: number; duration_ms?: number | null }[];
  onPage: Series[];
}): Promise<RailEntry[]> {
  const refs = rows.map((row) => row.item_ref).slice(0, RAIL_SIZE * 4);
  if (refs.length === 0) return [];

  const episodes = await attempt(() =>
    api(token).GET("/sources/{id}/episodes", {
      params: { path: { id: sourceId }, query: { ids: refs, size: refs.length } },
    }),
  );
  const byId = new Map((episodes.data?.items ?? []).map((row) => [row.id, row]));

  // In the order the rows came in — most recently touched first — and one per
  // series. A rail showing three episodes of one series has understood the data
  // and not the use.
  const seen = new Set<string>();
  const picked: { episode: Episode; resume: boolean }[] = [];
  for (const row of rows) {
    const episode = byId.get(row.item_ref);
    if (!episode || seen.has(episode.series_id)) continue;
    seen.add(episode.series_id);
    picked.push({
      episode,
      // Under the threshold is the only case this page can act on directly.
      resume: !isFinished(row.position_ms, row.duration_ms ?? null),
    });
    if (picked.length === RAIL_SIZE) break;
  }
  if (picked.length === 0) return [];

  // The grid may already carry some of them; only the rest costs a request.
  const known = new Map(onPage.map((item) => [item.id, item]));
  const missing = picked
    .map(({ episode }) => episode.series_id)
    .filter((id) => !known.has(id));

  if (missing.length > 0) {
    const resolved = await attempt(() =>
      api(token).GET("/sources/{id}/series", {
        params: { path: { id: sourceId }, query: { ids: missing, size: missing.length } },
      }),
    );
    for (const item of resolved.data?.items ?? []) known.set(item.id, item);
  }

  return picked
    .map(({ episode, resume }) => ({
      series: known.get(episode.series_id),
      episode,
      resume,
    }))
    .filter((entry): entry is RailEntry => entry.series != null);
}

/**
 * A card.
 *
 * @param resume true when the row is under the threshold, which is the only case
 *   where this page can name the episode to open. See the page documentation.
 */
type RailEntry = { series: Series; episode: Episode; resume: boolean };

/**
 * The rail, at the head of the catalogue.
 *
 * Absent when empty rather than drawn with a "nothing yet" placeholder: a heading
 * over an empty row on a first visit is a promise about a feature nobody has used,
 * taking space from the catalogue they came for.
 *
 * Each card says which episode it will land on, because "continue" without saying
 * what is being continued is a link somebody follows to find out.
 */
function ContinueWatching({
  entries,
  title,
  href,
  episodeLabel,
}: {
  entries: RailEntry[];
  title: string;
  href: (entry: RailEntry) => string;
  episodeLabel: (season: number, episode: number) => string;
}) {
  if (entries.length === 0) return null;

  return (
    <section className="mt-8">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      <ul aria-label={title} className="mt-3 flex gap-4 overflow-x-auto pb-2">
        {entries.map((entry) => (
          <li key={entry.series.id} className="w-28 shrink-0">
            <a
              href={href(entry)}
              className="focus-visible:ring-ring block rounded-lg focus-visible:ring-2 focus-visible:outline-none"
            >
              <RailPoster series={entry.series} />
              <p className="mt-2 truncate text-xs font-medium">{entry.series.name}</p>
              <p className="text-muted-foreground truncate text-xs">
                {episodeLabel(entry.episode.season_number, entry.episode.episode_number)}
              </p>
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * A poster, or the space one would have taken.
 *
 * **No fallback image, ever** (AGENTS.md §1): this product ships no artwork, and a
 * placeholder that looked like a poster would be a picture we invented for somebody
 * else's catalogue.
 */
function RailPoster({ series }: { series: Series }) {
  if (!series.poster_url) {
    return <div className="bg-muted aspect-[2/3] w-full rounded-lg" />;
  }
  // Not `next/image`: it needs its remote hosts configured one domain at a time,
  // and there is no list of them here — it is whatever panel each person
  // subscribes to. The same reasoning already applies to film posters.
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={series.poster_url}
      alt=""
      loading="lazy"
      className="aspect-[2/3] w-full rounded-lg object-cover"
    />
  );
}
