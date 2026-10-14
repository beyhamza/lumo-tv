import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { EmptyState, Unavailable } from "@/components/app/Unavailable";
import { RemoveFromWatchlist, WatchlistUnconfirmed } from "@/components/app/WatchlistControls";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import type { WatchlistItem } from "@/lib/api/types";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { loadActiveSource } from "@/lib/sources/active-source-store";
import {
  WATCHLIST_PAGE_SIZE,
  contentTypeOf,
  filterChoices,
  filterFromParam,
  pageFromParam,
  watchlistView,
  type WatchlistFilter,
} from "@/lib/watchlist/list";
import { currentView, isUnconfirmed, WRITE_NOTICE_PARAM } from "@/lib/watchlist/write";

/**
 * "My list": the films and series this account saved to watch later, for the
 * active source (US-022, S11-04, S11-E05).
 *
 * <h2>The active source, handled like Favourites</h2>
 *
 * The list is the account's and shared across devices; what this browser shows
 * is the slice of the source it is browsing (`sourceId`, WL-14). No source →
 * how to add one; a choice owed → ask for it; `GET /sources` unanswered →
 * unavailable, because an outage proves nothing about the list.
 *
 * <h2>The server's order, the server's pages, the server's types</h2>
 *
 * Most recently added first, as the contract serves it — nothing here
 * re-sorts. Filter (`?type=`) and page (`?page=`) live in the URL, so the
 * screen works without JavaScript and survives the back button. The selector
 * is built from `content_types`, which covers the whole list of the source:
 * one type present, no selector (WL-17).
 *
 * <h2>Four states that must not be confused</h2>
 *
 * A confirmed empty list says so and points at the catalogues; a filter with
 * nothing in it keeps the filters and offers "All"; a failed read says it
 * failed and offers to retry — never an empty list; and an item whose content
 * left the catalogue stays, marked unavailable, removable without a sheet
 * (WL-01→03). There is no client-side loading state to flash: the page renders
 * once the answer is in.
 */

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/watchlist">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app/watchlist",
    title: t("watchlistTitle"),
    description: t("watchlistMetaDescription"),
    index: false,
  });
}

export default async function WatchlistPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/watchlist">) {
  const { locale } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const loc = locale as Locale;

  const filter = filterFromParam(single(query.type));
  const page = pageFromParam(single(query.page));
  const unconfirmed = isUnconfirmed(query[WRITE_NOTICE_PARAM]);

  // Shared with the layout's rail (memoised per request): they cannot disagree.
  const activeSource = await loadActiveSource(session.accessToken, session.userId);

  if (activeSource.state === "unavailable") {
    return <Unavailable />;
  }

  if (activeSource.state === "none") {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("watchlistTitle")}</h1>
        <div className="mt-8">
          <EmptyState title={t("sourcesEmpty")} hint={t("sourcesEmptyHint")} />
        </div>
        <p className="mt-4">
          <a
            href={hrefFor(loc, "/app/sources/new")}
            className="text-sm underline underline-offset-4"
          >
            {t("sourcesAddCta")}
          </a>
        </p>
      </div>
    );
  }

  if (activeSource.state === "needs-choice") {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("watchlistTitle")}</h1>
        <div className="mt-8">
          <EmptyState title={t("watchlistChooseTitle")} hint={t("watchlistChooseBody")} />
        </div>
      </div>
    );
  }

  const source = activeSource.source;
  const contentType = contentTypeOf(filter);

  const answer = await fetched(() =>
    api(session.accessToken).GET("/me/watchlist", {
      params: {
        query: {
          sourceId: source.id,
          ...(contentType ? { contentType } : {}),
          page,
          size: WATCHLIST_PAGE_SIZE,
        },
      },
    }),
  );

  // The view as it stands, for the forms on it and for the filter links.
  const view = (values: { type?: WatchlistFilter; page?: number }) => {
    const type = values.type ?? filter;
    const target = values.page ?? page;
    return currentView("/app/watchlist", {
      type: type === "all" ? undefined : type,
      page: target > 0 ? String(target) : undefined,
    });
  };
  const returnTo = view({});

  const header = (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">{t("watchlistTitle")}</h1>
      <p className="text-muted-foreground mt-2">
        {t("watchlistOfSource", { source: source.label })}
      </p>
      <WatchlistUnconfirmed show={unconfirmed} />
    </>
  );

  if (answer.state !== "ok") {
    // Not the empty state: nothing is known about this list. The retry is the
    // same view, read again — without the notice, which belonged to a write.
    return (
      <div>
        {header}
        <div role="alert" className="border-border mt-8 rounded-xl border border-dashed px-5 py-6">
          <p className="font-medium">{t("watchlistLoadFailed")}</p>
          <p className="text-muted-foreground mt-1 text-sm">{t("watchlistLoadFailedHint")}</p>
          <p className="mt-3">
            <a href={hrefFor(loc, returnTo)} className="text-sm underline underline-offset-4">
              {t("watchlistRetry")}
            </a>
          </p>
        </div>
      </div>
    );
  }

  const listing = answer.data;
  const state = watchlistView(listing, filter);
  const choices = filterChoices(listing.content_types);

  const filterLabel: Record<WatchlistFilter, string> = {
    all: t("watchlistFilterAll"),
    films: t("watchlistFilterFilms"),
    series: t("watchlistFilterSeries"),
  };

  return (
    <div>
      {header}

      {state === "items" ? (
        <p className="text-muted-foreground mt-1 text-sm">
          {t("watchlistCount", { total: listing.total_elements })}
        </p>
      ) : null}

      {choices.length > 0 ? (
        <nav aria-label={t("watchlistFiltersLabel")} className="mt-6">
          <ul className="flex flex-wrap gap-2 text-sm">
            {choices.map((choice) => (
              <li key={choice}>
                <a
                  href={hrefFor(loc, view({ type: choice, page: 0 }))}
                  aria-current={choice === filter ? "page" : undefined}
                  className={
                    choice === filter
                      ? "bg-secondary text-secondary-foreground focus-visible:ring-ring block rounded-lg px-3 py-1.5 font-medium focus-visible:ring-2 focus-visible:outline-none"
                      : "text-muted-foreground hover:text-foreground focus-visible:ring-ring block rounded-lg px-3 py-1.5 focus-visible:ring-2 focus-visible:outline-none"
                  }
                >
                  {filterLabel[choice]}
                </a>
              </li>
            ))}
          </ul>
        </nav>
      ) : null}

      <div className="mt-8">
        {state === "empty" ? (
          <div className="border-border rounded-xl border border-dashed px-5 py-6">
            <p className="font-medium">{t("watchlistEmpty")}</p>
            <p className="text-muted-foreground mt-1 text-sm">{t("watchlistEmptyHint")}</p>
            <p className="mt-3 flex flex-wrap gap-4 text-sm">
              <a
                href={hrefFor(loc, `/app/sources/${source.id}/vod`)}
                className="underline underline-offset-4"
              >
                {t("watchlistBrowseFilms")}
              </a>
              <a
                href={hrefFor(loc, `/app/sources/${source.id}/series`)}
                className="underline underline-offset-4"
              >
                {t("watchlistBrowseSeries")}
              </a>
            </p>
          </div>
        ) : state === "filtered-empty" ? (
          <div className="border-border rounded-xl border border-dashed px-5 py-6">
            <p className="font-medium">
              {filter === "series"
                ? t("watchlistFilteredEmptySeries")
                : t("watchlistFilteredEmptyFilms")}
            </p>
            <p className="text-muted-foreground mt-1 text-sm">{t("watchlistFilteredEmptyHint")}</p>
            <p className="mt-3">
              <a
                href={hrefFor(loc, view({ type: "all", page: 0 }))}
                className="text-sm underline underline-offset-4"
              >
                {t("watchlistShowAll")}
              </a>
            </p>
          </div>
        ) : state === "out-of-range" ? (
          <div className="border-border rounded-xl border border-dashed px-5 py-6">
            <p className="font-medium">{t("watchlistOutOfRange")}</p>
            <p className="mt-3">
              <a
                href={hrefFor(loc, view({ page: 0 }))}
                className="text-sm underline underline-offset-4"
              >
                {t("watchlistFirstPage")}
              </a>
            </p>
          </div>
        ) : (
          <ul
            aria-label={t("watchlistListLabel")}
            className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6"
          >
            {listing.items.map((item) => (
              <WatchlistCard
                key={item.id}
                item={item}
                locale={loc}
                returnTo={returnTo}
                typeLabel={
                  item.content_type === "VOD" ? t("watchlistTypeFilm") : t("watchlistTypeSeries")
                }
                unavailableLabel={t("watchlistUnavailable")}
                unavailableHint={t("watchlistUnavailableHint")}
              />
            ))}
          </ul>
        )}
      </div>

      {state === "items" && listing.total_pages > 1 ? (
        <nav
          aria-label={t("cataloguePageOf", { page: page + 1, total: listing.total_pages })}
          className="mt-8 flex items-center gap-4 text-sm"
        >
          {page > 0 ? (
            <a href={hrefFor(loc, view({ page: page - 1 }))} className="underline underline-offset-4">
              {t("cataloguePrevious")}
            </a>
          ) : null}
          <span className="text-muted-foreground">
            {t("cataloguePageOf", { page: page + 1, total: listing.total_pages })}
          </span>
          {page + 1 < listing.total_pages ? (
            <a href={hrefFor(loc, view({ page: page + 1 }))} className="underline underline-offset-4">
              {t("catalogueNext")}
            </a>
          ) : null}
        </nav>
      ) : null}
    </div>
  );
}

/**
 * One saved title.
 *
 * Available: the whole card is one link to its sheet, on its own source.
 * Unavailable: no link at all — there is no sheet to open, and a fake one would
 * be worse than none — but the known title, its type, "Unavailable", and the
 * removal, addressed by the item's own id (WL-01→03).
 */
function WatchlistCard({
  item,
  locale,
  returnTo,
  typeLabel,
  unavailableLabel,
  unavailableHint,
}: {
  item: WatchlistItem;
  locale: Locale;
  returnTo: string;
  typeLabel: string;
  unavailableLabel: string;
  unavailableHint: string;
}) {
  const href =
    item.available && item.content_id
      ? hrefFor(
          locale,
          item.content_type === "VOD"
            ? `/app/sources/${item.source_id}/vod/${item.content_id}`
            : `/app/sources/${item.source_id}/series/${item.content_id}`,
        )
      : null;

  if (href) {
    return (
      <li>
        <a
          href={href}
          className="focus-visible:ring-ring block rounded-lg focus-visible:ring-2 focus-visible:outline-none"
        >
          <Poster url={item.poster_url ?? null} title={item.title} />
          <p className="mt-2 line-clamp-2 text-sm font-medium">{item.title}</p>
          <p className="text-muted-foreground text-xs">{typeLabel}</p>
        </a>
      </li>
    );
  }

  return (
    <li>
      {/* No poster even when one was recorded: it was the source's, it most
          likely no longer loads, and the placeholder says the same thing
          without a broken image. */}
      <Poster url={null} title={item.title} dimmed />
      <p className="mt-2 line-clamp-2 text-sm font-medium">{item.title}</p>
      <p className="text-muted-foreground text-xs">
        {typeLabel} · <span className="text-foreground font-medium">{unavailableLabel}</span>
      </p>
      <p className="text-muted-foreground mt-1 text-xs">{unavailableHint}</p>
      <div className="mt-2">
        <RemoveFromWatchlist itemId={item.id} title={item.title} returnTo={returnTo} />
      </div>
    </li>
  );
}

/**
 * The poster the source advertises, laid over a flat card bearing the title.
 *
 * Over, not instead of: when the image does not load — a dead link, or an
 * `http` poster refused on an `https` page — an `<img alt="">` draws nothing,
 * and the card underneath shows through. A placeholder for a broken poster
 * with no JavaScript, and no artwork of our own (AGENTS.md §1).
 *
 * The title inside the card is hidden from assistive technology: it is read
 * right below, in the same link.
 */
function Poster({ url, title, dimmed = false }: { url: string | null; title: string; dimmed?: boolean }) {
  return (
    <div
      className={`bg-muted text-muted-foreground relative flex aspect-[2/3] items-center justify-center overflow-hidden rounded-lg p-3 text-center text-xs ${
        dimmed ? "opacity-60" : ""
      }`}
    >
      <span aria-hidden="true" className="line-clamp-4">
        {title}
      </span>
      {url ? (
        // Not `next/image`: the host is whatever panel this person subscribes to.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={url}
          alt=""
          loading="lazy"
          className="absolute inset-0 h-full w-full object-cover"
        />
      ) : null}
    </div>
  );
}

function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
