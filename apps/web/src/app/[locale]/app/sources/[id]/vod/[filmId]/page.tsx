import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { FilmPlayer } from "@/components/app/FilmPlayer";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api, problemCode } from "@/lib/api/client";
import { asClock, isFinished } from "@/lib/playback/progress";
import type { VodItem } from "@/lib/api/types";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

/**
 * One film, and the player for it (US-13).
 *
 * <h2>Its own page, not a query parameter on the grid</h2>
 *
 * The channel page plays with `?play=`, because a channel has nothing to say
 * about itself that a row does not already show. A film has a synopsis, a year
 * and a running time, and those are the reason somebody commits ninety minutes.
 * A page of its own is also what makes a film **linkable** — the thing one
 * person sends another — which a query parameter on a filtered grid is not, and
 * it keeps the player's client bundle off the grid entirely.
 *
 * <h2>The synopsis is fetched here and nowhere else</h2>
 *
 * `GET /vod/{id}` is what fills it: on an Xtream panel a synopsis costs one HTTP
 * call **per film** against the user's own server, so a listing never carries
 * one and this page is the only thing that asks. The server remembers the answer,
 * so opening the same film twice costs one request, not two.
 *
 * <h2>Resuming is offered, never imposed (S5-11)</h2>
 *
 * A film with a saved position shows **two** buttons — "Resume at 20:14" and
 * "Start over" — and neither is pressed on anybody's behalf. Automatic resume is
 * a good idea right up until the day somebody wants to see the beginning again,
 * and then it is a feature with no way out.
 *
 * The position is read here, server-side, in the same round trip as the film. A
 * client-side lookup would put the two buttons on screen a moment after the page,
 * moving a target under a cursor that is already going for it.
 *
 * <h2>Back goes where the visitor came from</h2>
 *
 * The grid's category, page and search travel in this URL and come back out in
 * the link below. Somebody who opened a film on page seven of "Action" returns
 * to page seven of "Action" — a return to the head of the catalogue is the whole
 * cost of having looked.
 */

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/[id]/vod/[filmId]">): Promise<Metadata> {
  const { locale, id, filmId } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: `/app/sources/${id}/vod/${filmId}`,
    title: t("filmsTitle"),
    description: t("filmsMetaDescription"),
    // Behind a session. The title is not put in here either: it is somebody
    // else's catalogue, and it has no business in a document a crawler reads.
    index: false,
  });
}

export default async function FilmPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/sources/[id]/vod/[filmId]">) {
  const { locale, id, filmId } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");

  const [film, saved] = await Promise.all([
    api(session.accessToken).GET("/vod/{id}", { params: { path: { id: filmId } } }),
    // The three filters together, because the contract says that combination
    // yields at most one row — and it is exactly why it exists. Asked once, when
    // the page renders, so the buttons below are right on the first paint rather
    // than appearing a moment later under somebody's cursor.
    api(session.accessToken).GET("/me/progress", {
      params: {
        query: { sourceId: id, itemType: "VOD", itemRef: filmId, size: 1 },
      },
    }),
  ]);

  const failure = problemCode(film.error);
  if (failure) {
    return (
      <p role="alert" className="text-destructive text-sm">
        {tErrors(failure as never)}
      </p>
    );
  }
  if (!film.data) {
    return <Unavailable />;
  }

  const messages = await getMessages();
  const row = film.data;

  // Null both when nothing was ever watched and when the film is finished. Both
  // mean one button rather than two: offering to carry on from the credits is
  // not an offer. A failed lookup is also null — the page still plays the film,
  // which is what somebody came for.
  const progress = saved.data?.items?.[0];
  const resumeFromMs =
    progress && !isFinished(progress.position_ms, progress.duration_ms ?? null)
      ? progress.position_ms
      : 0;

  // The grid's own state, carried in and handed straight back. Filter values
  // only — no path and no host, so there is nothing here that could turn into a
  // redirect somewhere else.
  const back = `/app/sources/${id}/vod${queryString({
    categoryId: single(query.categoryId),
    q: single(query.q),
    page: single(query.page),
  })}`;

  return (
    <div className="max-w-3xl">
      <p className="text-sm">
        <a
          href={hrefFor(locale as Locale, back)}
          className="text-muted-foreground underline underline-offset-4"
        >
          {t("filmsBackToList")}
        </a>
      </p>

      <div className="mt-6 flex flex-col gap-6 sm:flex-row">
        <Poster film={row} />

        <div className="flex-1">
          <h1 className="text-2xl font-semibold tracking-tight">{row.name}</h1>

          {/* Joined rather than laid out as labelled rows: three short values,
              most of them absent most of the time, and a table of empty labels
              says less than a line that simply omits what the source withheld. */}
          <p className="text-muted-foreground mt-2 text-sm">
            {facts(row, (count) => t("filmsMinutes", { count })).join(" · ")}
          </p>

          <NextIntlClientProvider
            messages={{ App: messages.App, Errors: messages.Errors }}
          >
            <FilmPlayer
              filmId={row.id}
              sourceId={row.source_id}
              name={row.name}
              resumeFromMs={resumeFromMs}
              resumeLabel={
                resumeFromMs > 0 ? t("filmsResumeAt", { at: asClock(resumeFromMs) }) : null
              }
            />
          </NextIntlClientProvider>
        </div>
      </div>

      <Synopsis plot={row.plot ?? null} none={t("filmsSynopsisNone")} />
    </div>
  );
}

/**
 * The synopsis, or a sentence about its absence.
 *
 * No third state here, unlike the applications: the server has already been
 * asked by the time this page renders, so "still loading" cannot happen. Either
 * the source has a synopsis for this film or it does not, and many do not.
 */
function Synopsis({ plot, none }: { plot: string | null; none: string }) {
  return (
    <p className={plot ? "mt-8 leading-relaxed" : "text-muted-foreground mt-8 text-sm"}>
      {plot ?? none}
    </p>
  );
}

function Poster({ film }: { film: VodItem }) {
  if (!film.poster_url) {
    return (
      <div className="bg-muted text-muted-foreground flex aspect-[2/3] w-40 shrink-0 items-center justify-center rounded-lg p-3 text-center text-xs">
        {film.name}
      </div>
    );
  }

  // Not `next/image`, for the reason given on the grid: the host is whatever
  // panel this person subscribes to, and there is no list to configure.
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={film.poster_url}
      alt=""
      className="bg-muted aspect-[2/3] w-40 shrink-0 rounded-lg object-cover"
    />
  );
}

/**
 * Year, running time and rating, with the absent ones left out.
 *
 * The rating is the source's own text — `7.4`, `PG-13` and `★★★★` all occur —
 * echoed verbatim. Normalising it would be this layer deciding what the provider
 * meant, which is the decision already refused for a channel's quality badge.
 */
function facts(film: VodItem, minutes: (count: number) => string): string[] {
  const out: string[] = [];
  if (film.year) out.push(String(film.year));
  if (film.duration_seconds) out.push(minutes(Math.round(film.duration_seconds / 60)));
  if (film.rating) out.push(film.rating);
  return out;
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
