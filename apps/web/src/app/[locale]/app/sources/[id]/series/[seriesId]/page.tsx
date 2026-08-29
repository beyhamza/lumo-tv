import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { EpisodePlayer } from "@/components/app/EpisodePlayer";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Episode, Season, Series } from "@/lib/api/types";
import { api, problemCode } from "@/lib/api/client";
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

  const detail = await api(session.accessToken).GET("/series/{id}", {
    params: { path: { id: seriesId } },
  });

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

  // A season named in the URL that the panel no longer lists falls back to the
  // first, rather than to an empty episode list that looks like a broken series.
  const requested = Number.parseInt(single(query.season) ?? "", 10);
  const open =
    seasons.find((season) => season.season_number === requested) ?? seasons[0];

  const playing = single(query.play);
  const episode = open?.episodes.find((candidate) => candidate.id === playing);

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
            name={episodeLabel(episode, t)}
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
                  className="min-w-0 flex-1 truncate font-medium underline-offset-4 hover:underline"
                >
                  {episodeLabel(row, t)}
                </a>
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
