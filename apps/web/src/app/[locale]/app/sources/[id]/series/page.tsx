import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { CatalogueTabs } from "@/components/app/CatalogueTabs";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api, problemCode } from "@/lib/api/client";
import type { Category, Series } from "@/lib/api/types";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

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
 * <h2>An M3U source always shows an empty grid, and says why</h2>
 *
 * Series are an Xtream feature (`adr/0010`): a playlist declares no season and no
 * episode, and this product does not reconstruct a tree from titles. The tab is
 * here anyway — hiding it is what made somebody conclude the feature did not
 * exist — and the empty state is where the reason is given.
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

  const [categories, series, source] = await Promise.all([
    api(session.accessToken).GET("/sources/{id}/categories", {
      params: { path: { id }, query: { contentType: "SERIES" } },
    }),
    api(session.accessToken).GET("/sources/{id}/series", {
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
    // Only to tell the two empty states apart: "this playlist cannot carry
    // series" and "this panel offers none" are different facts, and one sentence
    // for both would tell an Xtream user their panel cannot do something it can.
    api(session.accessToken).GET("/sources/{id}", { params: { path: { id } } }),
  ]);

  const failure = problemCode(series.error) ?? problemCode(categories.error);

  if (failure === "SOURCE_NOT_READY") {
    return (
      <div className="border-border rounded-xl border border-dashed px-5 py-6">
        <p className="font-medium">{t("catalogueNotReady")}</p>
        <p className="mt-2 text-sm">
          <a
            href={hrefFor(locale as Locale, `/app/sources/${id}`)}
            className="underline underline-offset-4"
          >
            {t("catalogueNotReadyLink")}
          </a>
        </p>
      </div>
    );
  }
  if (failure) {
    return (
      <p role="alert" className="text-destructive text-sm">
        {tErrors(failure as never)}
      </p>
    );
  }
  if (!series.data || !categories.data) {
    return <Unavailable />;
  }

  const totalPages = series.data.total_pages;
  const isPlaylist = source.data?.kind !== "XTREAM";
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
      <p className="text-muted-foreground mt-2">
        {t("seriesCount", { total: series.data.total_elements })}
      </p>

      <CatalogueTabs
        sourceId={id}
        locale={locale as Locale}
        active="series"
        label={t("catalogueTabsLabel")}
        channelsLabel={t("catalogueTitle")}
        filmsLabel={t("filmsTitle")}
        seriesLabel={t("seriesTitle")}
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
          categories={categories.data.items}
          activeId={categoryId}
          sourceId={id}
          locale={locale as Locale}
          search={search}
          allLabel={t("seriesAllCategories")}
        />

        <div>
          {series.data.items.length === 0 ? (
            <div className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">
                {search ? t("seriesNoResults") : t("seriesEmpty")}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                {search
                  ? t("seriesNoResultsHint")
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
              {series.data.items.map((row) => (
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
  activeId,
  sourceId,
  locale,
  search,
  allLabel,
}: {
  categories: Category[];
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
