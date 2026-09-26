import { getTimeZone, getTranslations, setRequestLocale } from "next-intl/server";
import { ChannelRail, type OnAirLine } from "@/components/app/ChannelRail";
import { ContinueRail, type ContinueRailEntry } from "@/components/app/ContinueRail";
import { SourceNotice } from "@/components/app/SourceNotice";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import type { EpgProgramme } from "@/lib/api/types";
import { type DirectView, directViewHref } from "@/lib/direct/view-memory";
import { clockTime } from "@/lib/epg/format";
import { loadHomeRails, type HomeRails } from "@/lib/home/load-home-rails";
import { requireSession } from "@/lib/session/session";
import { loadActiveSource } from "@/lib/sources/active-source-store";
import { sourceCondition } from "@/lib/sources/source-condition";

/**
 * The home page of the account zone (US-017, US-020, S8-E01 and S8-E02).
 *
 * <h2>Three rails, in the order that was validated</h2>
 *
 * **Continue, Favourites, Live** — for the active source and nothing else
 * (US-018). The disposition was validated on 19 September 2026 and is not this
 * file's to rearrange.
 *
 * A rail with nothing in it is **not rendered**: no heading, no reserved space.
 * The rails own that rule themselves (`ContinueRail`, `ChannelRail`), so this
 * page lists the three unconditionally and cannot forget it for one of them.
 * The one deliberate exception is the Live rail: its two explicit doors
 * (S9-04-07) survive an empty rail, because a source with no history yet is
 * exactly when they are needed.
 *
 * <h2>Every state is a sentence and a way forward, never three empty rows</h2>
 *
 * - `GET /sources` did not answer — unavailable. Not "no source": an outage
 *   proves no deletion.
 * - No source — the way to add the first one.
 * - Several sources, none chosen — asked for. The switcher in the rail is already
 *   open on the same question; this says why the page is waiting.
 * - A source with nothing watched and nothing starred — an invitation to explore,
 *   with the three catalogues one click away.
 * - A source synchronising, or in error — said, **above** the rails rather than
 *   instead of them: since contract lot C4 the previous catalogue stays served in
 *   every status, so what was there yesterday is still there
 *   (`lib/sources/source-condition.ts`).
 *
 * <h2>What is on, under the channel cards (S9-03)</h2>
 *
 * The programme on air and when it ends, under each card of the two channel
 * rails, from **one** grouped request made by the loader alongside the name
 * lookup. A channel with nothing on — no guide on this source, no `tvg_id`, a
 * guide that could not be read — shows its name and nothing else.
 *
 * The Live rail also closes on two **explicit** entries (S9-04-07): *Toutes les
 * chaînes* and *Guide TV*. Each is a link carrying `?view=`, so it primes the
 * remembered view instead of following it (GD-02), and each opens the source
 * with no filter and no search. They are the whole extent of the guide here: no
 * grid yet (S9-05).
 *
 * <h2>What is deliberately absent</h2>
 *
 * No search (sprint 10), no watch list (sprint 11), no "remove from Continue"
 * (sprint 12). Each would be a control that leads nowhere today.
 *
 * <h2>No client component</h2>
 *
 * Everything is a link. Playing a channel is `?play=` on its source's channel
 * page; resuming is the URL the films and series rails already use. The players
 * live on those pages, so this one ships no script of its own.
 */
export default async function HomePage({ params }: PageProps<"/[locale]/app">) {
  const { locale: rawLocale } = await params;
  const locale = rawLocale as Locale;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");

  // The layout asked the same question for the rail; this is the same answer,
  // not a second request (`loadActiveSource` is memoised per request).
  const activeSource = await loadActiveSource(session.accessToken, session.userId);

  if (activeSource.state === "unavailable") {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("homeTitle")}</h1>
        <div className="mt-8">
          <Unavailable />
        </div>
      </div>
    );
  }

  if (activeSource.state === "none") {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("homeTitle")}</h1>
        <div className="border-border mt-8 rounded-2xl border border-dashed px-6 py-8">
          <p className="font-medium">{t("homeNoSourceTitle")}</p>
          <p className="text-muted-foreground mt-1 max-w-[60ch] text-sm">
            {t("sourcesEmptyHint")}
          </p>
          <a
            href={hrefFor(locale, "/app/sources/new")}
            className="bg-primary text-primary-foreground mt-5 inline-flex h-11 items-center rounded-full px-5 text-sm font-semibold"
          >
            {t("sourcesAddCta")}
          </a>
        </div>
      </div>
    );
  }

  if (activeSource.state === "needs-choice") {
    // Not the first source "in the meantime": choosing a catalogue on somebody's
    // behalf is the one thing the active-source rule forbids.
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("homeTitle")}</h1>
        <div className="border-border mt-8 rounded-2xl border border-dashed px-6 py-8">
          <p className="font-medium">{t("homeChooseTitle")}</p>
          <p className="text-muted-foreground mt-1 max-w-[60ch] text-sm">
            {t("homeChooseBody")}
          </p>
        </div>
      </div>
    );
  }

  const source = activeSource.source;
  const condition = sourceCondition(source);

  // Nothing is asked of a source that has never finished an import: every lookup
  // would answer `409 SOURCE_NOT_READY`, and there is no history on a catalogue
  // nobody has seen yet.
  // One clock for the whole render: what is "on now" is decided at this
  // instant, and the hours drawn are relative to it.
  const now = new Date();
  const rails = condition.browsable
    ? await loadHomeRails(session.accessToken, source.id, now)
    : null;
  const onAir = rails ? await onAirLines(rails.onAir, locale, t) : undefined;
  const hasRails =
    rails !== null &&
    rails.continueWatching.length + rails.favorites.length + rails.recents.length > 0;

  const channelsHref = `/app/sources/${source.id}/channels`;
  const playHref = (channelId: string) =>
    // The link the library uses for a favourite, to the letter: the channel's own
    // source page, which has the player. Selecting a card plays (US-020).
    hrefFor(locale, `${channelsHref}?play=${channelId}`);

  // The two explicit doors of the Live rail (S9-04-07): each carries its view in
  // the URL so the proxy primes the per-source memory before the target renders,
  // and each opens the unfiltered catalogue — `directViewHref` sets `view` and
  // nothing else. A bare S8 link to the catalogue still respects the memory.
  const directEntryHref = (view: DirectView) =>
    hrefFor(locale, directViewHref(view, { sourceId: source.id }));

  return (
    <div>
      {/* Only while there is nothing else to look at. A first import ends with
          the page filling up, and nobody should have to reload to see it; under
          rails somebody is reading, a page that reloads itself is a page that
          scrolls back to the top under their thumb. */}
      {condition.notice === "syncing" && !condition.browsable ? (
        <meta httpEquiv="refresh" content="5" />
      ) : null}

      <h1 className="text-2xl font-semibold tracking-tight">{t("homeTitle")}</h1>
      <p className="text-muted-foreground/80 mt-1 text-[13px]">
        {t("homeOfSource", { source: source.label })}
      </p>

      {/* Renders nothing for a source with nothing to announce. Shared with the
          catalogue pages since S8-05: same two facts, same sentences. */}
      <SourceNotice source={source} condition={condition} locale={locale} />

      {rails ? (
        <>
          <ContinueRail
            title={t("homeContinueTitle")}
            entries={continueEntries(rails, source.id, locale, t)}
          />
          <ChannelRail
            prominent
            title={t("homeFavoritesTitle")}
            channels={rails.favorites}
            playHref={playHref}
            onAir={onAir}
            more={[
              {
                href: hrefFor(locale, "/app/favorites"),
                label: t("catalogueFavoritesSeeAll"),
              },
            ]}
          />
          <ChannelRail
            prominent
            title={t("homeLiveTitle")}
            channels={rails.recents}
            playHref={playHref}
            onAir={onAir}
            // A cold account has no recent channel, and the two doors must
            // still be there (S9-04-07): the rail keeps its entries with no card.
            keepEntriesWhenEmpty
            more={[
              { href: directEntryHref("channels"), label: t("homeAllChannels") },
              { href: directEntryHref("guide"), label: t("homeGuideTv") },
            ]}
          />
        </>
      ) : null}

      {rails && !hasRails ? (
        rails.failed ? (
          // Nothing on screen *and* something did not answer: "nothing yet" would
          // be a guess, and the wrong one for anybody who has a history.
          <div className="mt-8">
            <Unavailable />
          </div>
        ) : (
          <Explore sourceId={source.id} locale={locale} />
        )
      ) : null}
    </div>
  );
}

type Translate = Awaited<ReturnType<typeof getTranslations<"App">>>;

/**
 * The two lines under a card, from the programme the loader found on air.
 *
 * Formatted here and not in the rail: the sentence is the page's language and
 * the hour is the zone next-intl is configured with (`getTimeZone()`), which
 * the rail has no business knowing. A programme whose end cannot be read gets
 * no line at all rather than a sentence with "Invalid Date" in it.
 */
async function onAirLines(
  onAir: ReadonlyMap<string, EpgProgramme>,
  locale: Locale,
  t: Translate,
): Promise<Map<string, OnAirLine>> {
  const timeZone = await getTimeZone();
  const lines = new Map<string, OnAirLine>();
  for (const [channelId, programme] of onAir) {
    const ends = clockTime(programme.ends_at, locale, timeZone);
    if (ends === undefined) continue;
    lines.set(channelId, { title: programme.title, until: t("epgUntil", { time: ends }) });
  }
  return lines;
}

/**
 * Cards, from the decisions made in `lib/home/continue-watching.ts`.
 *
 * The URLs are the ones the source's own rails use, on purpose:
 *
 * - a film resumes on **its page**, where the player lives and offers "Resume at
 *   20:14" beside "Start over" (S5-11). That page is also its detail page, hence
 *   no secondary link;
 * - an episode resumes at `?season=…&play=…` on its series' page, which opens
 *   the player on that episode at its saved position; the secondary link is the
 *   same page without them — the series, its seasons, nothing playing.
 */
function continueEntries(
  rails: HomeRails,
  sourceId: string,
  locale: Locale,
  t: Translate,
): ContinueRailEntry[] {
  return rails.continueWatching.map((card) => {
    const progress = {
      positionMs: card.row.position_ms,
      durationMs: card.row.duration_ms ?? null,
    };

    if (card.kind === "film") {
      return {
        key: card.key,
        title: card.film.name,
        posterUrl: card.film.poster_url,
        ...progress,
        resume: {
          href: hrefFor(locale, `/app/sources/${sourceId}/vod/${card.film.id}`),
          label: t("homeResume", { title: card.film.name }),
        },
      };
    }

    const seriesPath = `/app/sources/${sourceId}/series/${card.series.id}`;
    const query = new URLSearchParams({
      season: String(card.episode.season_number),
      play: card.episode.id,
    });

    return {
      key: card.key,
      title: card.series.name,
      caption: t("seriesSeasonEpisode", {
        season: card.episode.season_number,
        episode: card.episode.episode_number,
      }),
      posterUrl: card.series.poster_url,
      ...progress,
      resume: {
        href: hrefFor(locale, `${seriesPath}?${query}`),
        label: t("homeResume", { title: card.series.name }),
      },
      details: {
        href: hrefFor(locale, seriesPath),
        text: t("homeDetails"),
        label: t("homeDetailsOf", { title: card.series.name }),
      },
    };
  });
}

/**
 * A source that is ready and an account that has not used it yet.
 *
 * Always the three catalogues, whatever the source holds — the rule
 * `CatalogueTabs` documents, learned the hard way: a hidden "Films" was read as
 * a missing feature. A playlist has no series and its series page says so.
 */
async function Explore({ sourceId, locale }: { sourceId: string; locale: Locale }) {
  const t = await getTranslations("App");

  const catalogues = [
    { href: `/app/sources/${sourceId}/channels`, label: t("navLive") },
    { href: `/app/sources/${sourceId}/vod`, label: t("navFilms") },
    { href: `/app/sources/${sourceId}/series`, label: t("navSeries") },
  ];

  return (
    <section className="border-border mt-8 rounded-2xl border border-dashed px-6 py-8">
      <h2 className="font-medium">{t("homeExploreTitle")}</h2>
      <p className="text-muted-foreground mt-1 max-w-[60ch] text-sm">{t("homeExploreBody")}</p>
      <ul aria-label={t("homeExploreListLabel")} className="mt-5 flex flex-wrap gap-3">
        {catalogues.map((catalogue) => (
          <li key={catalogue.href}>
            <a
              href={hrefFor(locale, catalogue.href)}
              className="bg-secondary text-secondary-foreground hover:bg-secondary/80 inline-flex h-11 items-center rounded-full px-5 text-sm font-medium"
            >
              {catalogue.label}
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}
