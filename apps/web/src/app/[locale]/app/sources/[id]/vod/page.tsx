import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api, problemCode } from "@/lib/api/client";
import type { Category, VodItem } from "@/lib/api/types";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

/**
 * The films of one source (US-13).
 *
 * <h2>The channel page's mechanics, taken as they stand</h2>
 *
 * Category, page and query all live in the URL. That is what makes this screen
 * work **without JavaScript**, shareable, and correct when the browser's back
 * button is pressed — the three properties `channels` was built around, and none
 * of them survives a client-side grid with its own state.
 *
 * <h2>What a film changes: the card, and where `OK` goes</h2>
 *
 * A channel is a row of text because a channel is chosen by a name somebody
 * already knows. **A film is chosen by looking**, so the card is a poster and the
 * grid is a grid.
 *
 * And choosing a film opens **its own page** rather than starting playback. A
 * film needs a year, a running time and a synopsis before anybody commits ninety
 * minutes to it, and none of those fit on a card. That page is also where the
 * player lives, which keeps this one free of any client bundle at all.
 *
 * <h2>No poster is a title on a flat card, never a placeholder image</h2>
 *
 * Lumo ships no artwork (CLAUDE.md, règle 2). It is also the truthful rendering:
 * many panels advertise posters over `http`, which this page is served over
 * `https`, so "the source gave no poster" and "the browser refused it" are one
 * outcome from where the visitor is sitting.
 */

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/[id]/vod">): Promise<Metadata> {
  const { locale, id } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: `/app/sources/${id}/vod`,
    title: t("filmsTitle"),
    description: t("filmsMetaDescription"),
    // Behind a session, and nothing here belongs in an index.
    index: false,
  });
}

/** The contract's cap for this listing. */
const PAGE_SIZE = 48;

export default async function VodPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/sources/[id]/vod">) {
  const { locale, id } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");

  const categoryId = single(query.categoryId);
  const search = single(query.q);
  const page = Math.max(0, Number.parseInt(single(query.page) ?? "0", 10) || 0);

  const [categories, films] = await Promise.all([
    api(session.accessToken).GET("/sources/{id}/categories", {
      params: { path: { id }, query: { contentType: "VOD" } },
    }),
    api(session.accessToken).GET("/sources/{id}/vod", {
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
  ]);

  const failure = problemCode(films.error) ?? problemCode(categories.error);

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
    // Includes SOURCE_NOT_FOUND. The message comes from the code, never from an
    // HTTP status.
    return (
      <p role="alert" className="text-destructive text-sm">
        {tErrors(failure as never)}
      </p>
    );
  }
  if (!films.data || !categories.data) {
    return <Unavailable />;
  }

  const totalPages = films.data.total_pages;

  // Carried into each film's own page so its "back to the films" link returns to
  // the exact view it was opened from — this category, this page, this search.
  // They are filter values and nothing else: no path, no host, nothing that
  // could become a redirect somewhere.
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

      <h1 className="mt-4 text-2xl font-semibold tracking-tight">{t("filmsTitle")}</h1>
      <p className="text-muted-foreground mt-2">
        {t("filmsCount", { total: films.data.total_elements })}
      </p>

      {/* A plain GET form, like the channel page's. Submitting it changes the
          URL, which is what every other control here does. `page` is absent on
          purpose: a new search starts at the first page. */}
      <form method="get" className="mt-6 flex flex-wrap items-end gap-3">
        {categoryId ? (
          <input type="hidden" name="categoryId" value={categoryId} />
        ) : null}
        <div className="space-y-1.5">
          <label htmlFor="q" className="text-sm font-medium">
            {t("filmsSearchLabel")}
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
        {/* Said plainly: the server matches a substring of the title, case
            insensitively, and nothing more. */}
        <p className="text-muted-foreground w-full text-sm">
          {t("filmsSearchHint")}
        </p>
      </form>

      <div className="mt-8 grid gap-8 md:grid-cols-[14rem_1fr]">
        <CategoryList
          categories={categories.data.items}
          activeId={categoryId}
          sourceId={id}
          locale={locale as Locale}
          search={search}
          allLabel={t("filmsAllCategories")}
        />

        <div>
          {films.data.items.length === 0 ? (
            <div className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">
                {search ? t("filmsNoResults") : t("filmsEmpty")}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                {search ? t("filmsNoResultsHint") : t("filmsEmptyHint")}
              </p>
            </div>
          ) : (
            <ul
              // Named, because the categories next to it are a list too and
              // assistive technology has to be able to say which is which.
              aria-label={t("filmsTitle")}
              className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4"
            >
              {films.data.items.map((film) => (
                <FilmCard
                  key={film.id}
                  film={film}
                  href={hrefFor(
                    locale as Locale,
                    `/app/sources/${id}/vod/${film.id}${queryString(context)}`,
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

/**
 * One film: its poster, its title, its year.
 *
 * The whole card is one link. Two — a poster and a title pointing at the same
 * place — would be two stops for a keyboard and two announcements for a screen
 * reader, for one destination.
 */
function FilmCard({ film, href }: { film: VodItem; href: string }) {
  return (
    <li>
      <a
        href={href}
        className="focus-visible:ring-ring block rounded-lg focus-visible:ring-2 focus-visible:outline-none"
      >
        <Poster film={film} />
        <p className="mt-2 line-clamp-2 text-sm font-medium">{film.name}</p>
        {film.year ? (
          <p className="text-muted-foreground text-xs">{film.year}</p>
        ) : null}
      </a>
    </li>
  );
}

/**
 * The poster the user's own source advertises, or the film's title on a plain
 * card.
 *
 * `aspect-[2/3]` on the container rather than on the image: the grid has to lay
 * out before any poster has arrived, and cards that resized as their pictures
 * loaded would make the whole page jump under a cursor that is already moving.
 *
 * `alt=""` because the title is right underneath, in the same link. A screen
 * reader announcing "poster of X" before "X" is noise.
 */
function Poster({ film }: { film: VodItem }) {
  if (!film.poster_url) {
    return (
      <div className="bg-muted text-muted-foreground flex aspect-[2/3] items-center justify-center rounded-lg p-3 text-center text-xs">
        {film.name}
      </div>
    );
  }

  // Not `next/image`: it needs its remote hosts configured one domain at a
  // time, and there is no list of them here — it is whatever panel each person
  // subscribes to. The same reasoning already applies to channel logos.
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={film.poster_url}
      alt=""
      loading="lazy"
      className="bg-muted aspect-[2/3] w-full rounded-lg object-cover"
    />
  );
}

/** The film categories, as links. The channel page's list, unchanged in shape. */
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
      `/app/sources/${sourceId}/vod${queryString({ categoryId, q: search })}`,
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
              {/* Null when the server did not count. An absent count is no
                  number at all, never a zero. */}
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

/** Two links. Everything on this page is a link, and paging is no exception. */
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
      `/app/sources/${sourceId}/vod${queryString({
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

/**
 * A query string with the empty values left out.
 *
 * Shared shape with the channel page's helper, kept local rather than lifted:
 * the two build different paths, and a `lib/` function whose whole body is
 * `URLSearchParams` is a dependency for nothing.
 */
function queryString(values: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) search.set(key, value);
  }
  const rendered = search.toString();
  return rendered ? `?${rendered}` : "";
}

/** `searchParams` hands back `string | string[]`. A repeated key takes the first. */
function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
