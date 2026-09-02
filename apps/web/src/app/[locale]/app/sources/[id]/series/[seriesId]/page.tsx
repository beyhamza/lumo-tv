import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { EpisodePlayer } from "@/components/app/EpisodePlayer";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Episode, Season, Series } from "@/lib/api/types";
import { api, problemCode } from "@/lib/api/client";
import { asClock, isFinished } from "@/lib/playback/progress";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

/**
 * One series, its seasons and its episodes (US-15, S6-07).
 *
 * <h2>The one page of this product that can be slow, and it says so</h2>
 *
 * `GET /series/{id}` fetches the tree from the user's own panel the first time
 * anybody opens it — one call, cached for six hours server-side. A series opened
 * before answers immediately; a series never opened costs a round trip to somebody
 * else's machine, and that machine can be slow or absent.
 *
 * <h2>Two failures that must not be collapsed</h2>
 *
 * `404` means the series does not exist and is final. `503` means the panel did
 * not answer and is worth retrying. Telling somebody their series is gone when
 * their provider merely hiccuped sends them looking in the wrong place, so the two
 * get different sentences — which is exactly why the contract gives them different
 * codes.
 *
 * <h2>Where somebody stopped, per episode (S6-08)</h2>
 *
 * One extra call, made **in parallel with the tree** rather than after it: the two
 * do not depend on each other, and a page that awaited them in turn would add its
 * own latency to a request that already crosses somebody else's machine.
 *
 * `GET /me/progress?itemType=EPISODE` cannot be narrowed to one series — `item_ref`
 * is opaque and a series is not one — so the rows are filtered here. There are a
 * dozen of them.
 *
 * <h2>The open season is in the URL</h2>
 *
 * `?season=2`, like every other piece of state in this zone: shareable, correct
 * under the back button, and **it works with no JavaScript**. Only the player
 * needs scripting.
 */

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/[id]/series/[seriesId]">): Promise<Metadata> {
  const { locale, id, seriesId } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: `/app/sources/${id}/series/${seriesId}`,
    title: t("seriesTitle"),
    description: t("seriesMetaDescription"),
    index: false,
  });
}

import type { Locale } from "@/i18n/routing";

export default async function SeriesDetailPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/sources/[id]/series/[seriesId]">) {
  const { locale, id, seriesId } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");

  const [detail, progress] = await Promise.all([
    api(session.accessToken).GET("/series/{id}", {
      params: { path: { id: seriesId } },
    }),
    // Started, in parallel: it does not depend on the tree, and awaiting the two
    // in turn would add this page's own latency to a request that already crosses
    // somebody else's machine.
    api(session.accessToken).GET("/me/progress", {
      params: { query: { sourceId: id, itemType: "EPISODE", size: 100 } },
    }),
  ]);

  const back = `/app/sources/${id}/series${queryString({
    categoryId: single(query.categoryId),
    q: single(query.q),
    page: single(query.page),
  })}`;

  const failure = problemCode(detail.error);
  if (failure) {
    // The distinction the contract exists to carry: `SERIES_NOT_FOUND` is final,
    // `SOURCE_UNREACHABLE` is worth trying again. Collapsing them would tell
    // somebody their series is gone over a network hiccup.
    const retryable = failure !== "SERIES_NOT_FOUND";
    return (
      <div className="max-w-3xl">
        <BackLink href={hrefFor(locale as Locale, back)} label={t("seriesBackToList")} />
        <div role="alert" className="border-destructive/40 mt-6 rounded-lg border px-4 py-3">
          <p className="font-medium">{tErrors(failure as never)}</p>
          {retryable ? (
            <p className="text-muted-foreground mt-1 text-sm">{t("seriesTreeRetry")}</p>
          ) : null}
        </div>
      </div>
    );
  }
  if (!detail.data) {
    return <Unavailable />;
  }

  const messages = await getMessages();
  const series = detail.data.series;
  const seasons = detail.data.seasons;

  // The season on screen: the one named in the URL, then **the first that has
  // episodes**, then the first.
  //
  // "That has episodes" rather than simply "the first", because panels declare a
  // season 0 — specials, or a bucket they never filled — and it sorts before
  // season 1. A series with eight full seasons opened on an empty season 0, which
  // reads exactly like the series having none. Found on a real catalogue.
  //
  // The empty season is still listed. A season the panel declares is one a viewer
  // should see; it is just not where the page opens. And a season named in the URL
  // is honoured even when empty — that is somebody's own choice.
  const requested = Number.parseInt(single(query.season) ?? "", 10);
  const open =
    seasons.find((season) => season.season_number === requested) ??
    seasons.find((season) => season.episodes.length > 0) ??
    seasons[0];

  // By episode id, because every row below asks the same question about itself
  // and a list would be a scan per row down a fifty-episode season.
  const positions = new Map(
    (progress.data?.items ?? []).map((row) => [row.item_ref, row] as const),
  );

  const playing = single(query.play);
  const episode = open?.episodes.find((candidate) => candidate.id === playing);
  const resumeAt = episode ? startAt(positions.get(episode.id)) : 0;

  return (
    <div className="max-w-3xl">
      <BackLink href={hrefFor(locale as Locale, back)} label={t("seriesBackToList")} />

      <div className="mt-6 flex flex-col gap-6 sm:flex-row">
        <Poster series={series} />

        <div className="flex-1">
          <h1 className="text-2xl font-semibold tracking-tight">{series.name}</h1>
          <p className="text-muted-foreground mt-2 text-sm">
            {facts(series, (count) => t("seriesEpisodeRunTime", { count })).join(" · ")}
          </p>
          {series.plot ? (
            <p className="mt-4 leading-relaxed">{series.plot}</p>
          ) : (
            <p className="text-muted-foreground mt-4 text-sm">{t("seriesSynopsisNone")}</p>
          )}
        </div>
      </div>

      {episode ? (
        <NextIntlClientProvider messages={{ App: messages.App, Errors: messages.Errors }}>
          <EpisodePlayer
            episodeId={episode.id}
            sourceId={id}
            name={episodeLabel(episode, t)}
            resumeFromMs={resumeAt}
            resumeLabel={
              resumeAt > 0 ? t("filmsResumeAt", { at: asClock(resumeAt) }) : null
            }
          />
        </NextIntlClientProvider>
      ) : null}

      {seasons.length === 0 ? (
        <div className="border-border mt-8 rounded-xl border border-dashed px-5 py-6">
          <p className="font-medium">{t("seriesNoSeasons")}</p>
          {/* Rare and real: some panels list a series and answer nothing for it.
              Said as a fact about the provider rather than as a failure of ours,
              because that is what it is. */}
          <p className="text-muted-foreground mt-1 text-sm">{t("seriesNoSeasonsHint")}</p>
        </div>
      ) : (
        <>
          <SeasonTabs
            seasons={seasons}
            openNumber={open?.season_number}
            base={`/app/sources/${id}/series/${seriesId}`}
            context={{
              categoryId: single(query.categoryId),
              q: single(query.q),
              page: single(query.page),
            }}
            locale={locale as Locale}
            label={t("seriesSeasonsLabel")}
            seasonLabel={(n) => t("seriesSeason", { number: n })}
          />

          <ul aria-label={t("seriesEpisodesLabel")} className="mt-6 space-y-2">
            {(open?.episodes ?? []).map((row) => (
              <li
                key={row.id}
                className={`flex items-center gap-3 rounded-xl border px-4 py-3 ${
                  row.id === playing ? "border-primary bg-primary/5" : "border-border"
                }`}
              >
                <span className="text-muted-foreground w-10 shrink-0 text-right text-sm tabular-nums">
                  {row.episode_number}
                </span>
                <div className="min-w-0 flex-1">
                  <a
                    href={hrefFor(
                      locale as Locale,
                      `/app/sources/${id}/series/${seriesId}${queryString({
                        categoryId: single(query.categoryId),
                        q: single(query.q),
                        page: single(query.page),
                        season: String(open?.season_number ?? 0),
                        play: row.id,
                      })}`,
                    )}
                    className="block truncate font-medium underline-offset-4 hover:underline"
                  >
                    {episodeLabel(row, t)}
                  </a>

                  {/* Only where there is a position (S6-08). A bar at zero on every
                      row would say that everybody has started everything. */}
                  {fraction(positions.get(row.id)) !== null ? (
                    <div
                      className="bg-muted mt-1.5 h-1 w-full overflow-hidden rounded-full"
                      role="progressbar"
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={Math.round(fraction(positions.get(row.id))! * 100)}
                      aria-label={t("seriesEpisodeProgress")}
                    >
                      <div
                        className="bg-primary h-full"
                        style={{ width: `${fraction(positions.get(row.id))! * 100}%` }}
                      />
                    </div>
                  ) : null}
                </div>
                {row.duration_seconds ? (
                  <span className="text-muted-foreground shrink-0 text-xs tabular-nums">
                    {t("filmsMinutes", { count: Math.round(row.duration_seconds / 60) })}
                  </span>
                ) : null}
              </li>
            ))}
          </ul>

          {/* The panel's own count, when it disagrees with what it listed. Shown
              rather than reconciled: the list is what somebody can watch, and the
              claim is occasionally the only hint that a season is incomplete. */}
          {open && open.episode_count != null && open.episode_count !== open.episodes.length ? (
            <p className="text-muted-foreground mt-3 text-sm">
              {t("seriesEpisodeCountMismatch", {
                listed: open.episodes.length,
                claimed: open.episode_count,
              })}
            </p>
          ) : null}
        </>
      )}
    </div>
  );
}

/** "Episode 4", or its title when the panel has one. Null far more often than a film's. */
function episodeLabel(
  episode: Episode,
  t: (key: "seriesEpisode", values: Record<string, number>) => string,
): string {
  return episode.name ?? t("seriesEpisode", { number: episode.episode_number });
}

function SeasonTabs({
  seasons,
  openNumber,
  base,
  context,
  locale,
  label,
  seasonLabel,
}: {
  seasons: Season[];
  openNumber?: number;
  base: string;
  context: Record<string, string | undefined>;
  locale: Locale;
  label: string;
  seasonLabel: (n: number) => string;
}) {
  // One season is not a choice: a lone tab above its own episodes says nothing.
  if (seasons.length < 2) return null;

  return (
    <nav aria-label={label} className="mt-8">
      <ul className="flex flex-wrap gap-2 text-sm">
        {seasons.map((season) => (
          <li key={season.season_number}>
            <a
              href={hrefFor(
                locale,
                `${base}${queryString({ ...context, season: String(season.season_number) })}`,
              )}
              aria-current={season.season_number === openNumber ? "page" : undefined}
              className={
                season.season_number === openNumber
                  ? "bg-secondary text-secondary-foreground block rounded-lg px-3 py-1.5 font-medium"
                  : "text-muted-foreground hover:text-foreground block rounded-lg px-3 py-1.5"
              }
            >
              {seasonLabel(season.season_number)}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

function BackLink({ href, label }: { href: string; label: string }) {
  return (
    <p className="text-sm">
      <a href={href} className="text-muted-foreground underline underline-offset-4">
        {label}
      </a>
    </p>
  );
}

function Poster({ series }: { series: Series }) {
  if (!series.poster_url) {
    return (
      <div className="bg-muted text-muted-foreground flex aspect-[2/3] w-40 shrink-0 items-center justify-center rounded-lg p-3 text-center text-xs">
        {series.name}
      </div>
    );
  }

  // Not `next/image`: the host is whatever panel this person subscribes to.
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={series.poster_url}
      alt=""
      className="bg-muted aspect-[2/3] w-40 shrink-0 rounded-lg object-cover"
    />
  );
}

/**
 * Year, typical episode length and rating, absent ones left out.
 *
 * The run time is the panel's own indication and is never used as a duration —
 * that belongs to each episode, and it is what decides whether one was watched to
 * the end.
 */
function facts(series: Series, runTime: (count: number) => string): string[] {
  const out: string[] = [];
  if (series.year) out.push(String(series.year));
  if (series.episode_run_time) out.push(runTime(series.episode_run_time));
  if (series.rating) out.push(series.rating);
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

/**
 * Where to start an episode: the saved position, or its beginning (S6-08).
 *
 * **A finished episode starts over.** Resuming somebody into the credits is not
 * resuming. The threshold is `isFinished`, shared with the films — the day it moves
 * it has to move for both, and a rail that dropped films at 95 % while advancing
 * series at 90 % would be two products.
 */
function startAt(row: ProgressRow | undefined): number {
  if (!row) return 0;
  return isFinished(row.position_ms, row.duration_ms ?? null) ? 0 : row.position_ms;
}

/**
 * How far in, as a fraction, or null when there is nothing honest to draw.
 *
 * **Null without a stated duration**, which is common: a bar needs an end, and one
 * drawn full because the end is unknown is a bar that lies. Null when finished too
 * — a full bar on every episode of a watched season is ink that says nothing about
 * where somebody is.
 */
function fraction(row: ProgressRow | undefined): number | null {
  const duration = row?.duration_ms;
  if (!row || duration == null || duration <= 0) return null;
  if (isFinished(row.position_ms, duration)) return null;
  return Math.min(1, Math.max(0, row.position_ms / duration));
}

type ProgressRow = { position_ms: number; duration_ms?: number | null };