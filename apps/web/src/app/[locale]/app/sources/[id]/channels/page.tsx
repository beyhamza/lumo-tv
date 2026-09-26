import type { Metadata } from "next";
import { cookies } from "next/headers";
import { NextIntlClientProvider } from "next-intl";
import {
  getFormatter,
  getMessages,
  getTimeZone,
  getTranslations,
  setRequestLocale,
} from "next-intl/server";
import { addFavorite, removeFavorite } from "@/actions/favorites";
import { CatalogueNotReady } from "@/components/app/CatalogueNotReady";
import { CatalogueTabs } from "@/components/app/CatalogueTabs";
import { ChannelLogo, ChannelRail, type OnAirLine } from "@/components/app/ChannelRail";
import {
  FavoriteGroups,
  defaultGroupLabel,
  groupLabel,
} from "@/components/app/FavoriteGroups";
import { ChannelPlayer } from "@/components/app/ChannelPlayer";
import { DirectViews } from "@/components/app/DirectViews";
import { EpgGrid } from "@/components/app/EpgGrid";
import { SourceNotice } from "@/components/app/SourceNotice";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/error-message";
import type { Category, Channel, FavoriteGroup } from "@/lib/api/types";
import { attempt, outcomeOf } from "@/lib/catalogue/attempt";
import { cataloguePageState } from "@/lib/catalogue/page-state";
import { explicitView, resolveDirectView, storedDirectView, type DirectView } from "@/lib/direct/view-memory";
import { DAYS_BEFORE, epgDayWindow } from "@/lib/epg/day-window";
import { clockTime } from "@/lib/epg/format";
import { epgFreshness } from "@/lib/epg/freshness";
import { NOW_WINDOW_MS, loadEpgWindow, type EpgWindow } from "@/lib/epg/load-epg-window";
import { onAirByChannel } from "@/lib/epg/now";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { sourceCondition } from "@/lib/sources/source-condition";

/**
 * The channels of one source (US-08).
 *
 * <h2>Everything that filters is a link, and everything that searches is a GET</h2>
 *
 * Category, page and query all live in the URL. That is not a preference: it is
 * what makes this screen work without JavaScript, shareable, and correct when
 * the browser's back button is pressed. A client-side grid with its own state
 * would lose all three.
 *
 * <h2>The catalogue is large</h2>
 *
 * Fifteen thousand channels is an ordinary source. The page size is capped
 * server-side and the pagination is server-side too — nothing here holds a full
 * catalogue in memory, on either end.
 *
 * <h2>Two lists side by side</h2>
 *
 * The categories are a `<nav>` of links and the channels are a list of rows, so
 * both answer to `listitem`. The channel list therefore carries an accessible
 * name: assistive technology needs it to announce which list is which, and it is
 * the same name the end-to-end suite scopes its assertions with — the first
 * version of those expected two rows and counted six.
 *
 * <h2>Two rails, and the one request that fills them</h2>
 *
 * `Favorite` and `RecentChannel` carry identifiers only — `channel_id`,
 * `source_id`, nothing else. That is deliberate: a name copied onto them would
 * be a name the next ingestion has already changed. Android resolves those
 * identifiers against its local catalogue; this client has none, which is why
 * `GET /sources/{id}/channels` grew an `ids` filter.
 *
 * So the rails cost **one** extra round trip, not one per entry and not a walk
 * through the catalogue: the two rails and the channel being played are unioned
 * into a single lookup, minus whatever the current page already carries.
 *
 * Both rails are scoped to this source, because this page is. A favourite on
 * another source belongs to that source's catalogue, and linking to it from here
 * would produce a `?play=` this screen cannot honour.
 *
 * <h2>The catalogue is there whenever one exists (contract lot C4)</h2>
 *
 * The listings answer `200` in every status once an ingestion has succeeded. So
 * this page also fetches the source, and says **above** the catalogue what it is
 * going through — refreshing, with the server's real step; or failed, with the
 * cause and "this catalogue may be out of date" (`SourceNotice`, shared with the
 * home page). `409 SOURCE_NOT_READY` is left meaning one thing: no catalogue has
 * ever been ingested (`CatalogueNotReady`).
 *
 * <h2>A part that failed is named, never drawn as empty</h2>
 *
 * `cataloguePageState` decides from the two main requests. When only one of them
 * failed the other is kept, and the missing part says "could not be loaded":
 * "No channel in this category" over a request that never answered would be a
 * statement about somebody's subscription made from no information (US-024).
 *
 * <h2>What is on, under each channel of the page (S9-03)</h2>
 *
 * The programme on air and when it ends, under each row of the listing, from
 * **one** grouped guide request for the page's channel ids — the page is
 * bounded by `PAGE_SIZE`, so one request it is, and it rides alongside the
 * name lookup rather than after it. The rule of S7-03 applies to the letter: a
 * channel with nothing on shows its name as before, and a guide that could not
 * be read shows the page as it was before sprint 9. No "programme unavailable",
 * no reserved space.
 *
 * The header says when the guide was last imported, and **only when that is
 * worth saying** (D4): more than twenty-four hours ago, or an import that
 * failed or was interrupted. "Last guide import", never "programmes up to
 * date" — the server dates its import, not the provider's listings (D3). A
 * fresh guide, an unknown one, or a source with no guide at all say nothing:
 * "imported three hours ago" on every visit is noise, and "no guide" under a
 * catalogue somebody came to browse is a sentence about a feature they did not
 * ask for.
 *
 * <h2>The Guide view draws the time grid (S9-05-02)</h2>
 *
 * The Guide is now the hour grid of {@link EpgGrid}: channels as rows, hours as
 * columns, five day tabs (J−1→J+3) and **Maintenant**. It reads **one** grouped
 * window — the displayed day — with a single `loadEpgWindow` call, so the number
 * of `/epg` requests never follows the number of channels. Times are printed in
 * the zone next-intl is configured with, and every position and comparison is a
 * fraction of the day's two instants, never a local hour (GD-12). A slot with
 * no programme says « Aucun programme disponible sur ce créneau »; a read that
 * did not answer, or a source with no guide configured at all, draws nothing
 * (S7-03).
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/[id]/channels">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app/sources",
    title: t("catalogueTitle"),
    description: t("sourcesSubtitle"),
    index: false,
  });
}

/** Matches the contract's default. The server caps anything larger at 200. */
const PAGE_SIZE = 50;

/**
 * Entries per rail.
 *
 * A rail is a reminder, not a list: what is worth glancing at before deciding to
 * scroll. Twelve of each also keeps the union of the two — plus the channel being
 * played — well inside the hundred identifiers `ids` accepts, so the lookup below
 * never needs a second page.
 */
const RAIL_SIZE = 12;

/**
 * The contract's cap on `?ids=`, and the size of the largest group this page can
 * show whole. Sending more is a `400`, by contract.
 */
const ID_LOOKUP_MAX = 100;

export default async function ChannelsPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/sources/[id]/channels">) {
  const { locale, id } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  // Which of Direct's two views opens (S9-04-06): an explicit `?view=` primes the
  // per-source memory cookie the proxy wrote before this render, and the memory
  // primes the default (GD-02). A bare `/channels` — an S8 link, the Explore bar
  // — passes no view and therefore opens what this browser last used.
  const cookieStore = await cookies();
  const directView = resolveDirectView(
    storedDirectView((name) => cookieStore.get(name)?.value, id),
    explicitView(single(query.view)),
  );

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");

  const categoryId = single(query.categoryId);
  const search = single(query.q);
  const page = Math.max(0, Number.parseInt(single(query.page) ?? "0", 10) || 0);
  const playing = single(query.play);
  // Which favourite group the rail shows. In the URL like everything else on
  // this screen, so it survives a reload, can be shared, and comes back with the
  // back button (S4-09).
  const group = single(query.group);

  // Not through `fetched()`: this screen has to tell three failures apart, and
  // that helper deliberately collapses everything that is not an unrouted 404
  // into "unavailable". A source with no catalogue yet is not an outage.
  //
  // Each through `attempt()`, so that one request with no answer at all cannot
  // reject the whole `Promise.all` and take the parts that did answer with it.
  const token = session.accessToken;
  const [categories, channels, source, favorites, recents, groups] = await Promise.all([
    attempt(() =>
      api(token).GET("/sources/{id}/categories", {
        params: { path: { id }, query: { contentType: "LIVE" } },
      }),
    ),
    attempt(() =>
      api(token).GET("/sources/{id}/channels", {
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
    // What the source is going through, for the notice above the catalogue. Its
    // failure costs the notice and nothing else.
    attempt(() => api(token).GET("/sources/{id}", { params: { path: { id } } })),
    // Every favourite of the account, not only this source's: the contract has
    // no filter by source, and the list is small by nature — it is what one
    // person starred by hand. Its failure is deliberately not part of `failure`
    // below: a catalogue that refuses to render because a star could not be
    // read would be trading the whole screen for its smallest control.
    attempt(() => api(token).GET("/me/favorites", {})),
    // Watched on any device, which is the whole value of asking the server
    // rather than remembering locally: what was started on the phone this
    // morning is at the top of this rail now.
    attempt(() =>
      api(token).GET("/me/recent-channels", {
        params: { query: { limit: RAIL_SIZE } },
      }),
    ),
    // The account's groups. Like the favourites above, a failure here does not
    // take the catalogue down: the rail loses its group bar, the channel list is
    // untouched.
    attempt(() => api(token).GET("/me/favorite-groups", {})),
  ]);

  const state = cataloguePageState(outcomeOf(channels), outcomeOf(categories));

  if (state.kind === "not-ready") {
    return (
      <CatalogueNotReady sourceId={id} source={source.data ?? null} locale={locale as Locale} />
    );
  }
  if (state.kind === "error") {
    // Includes SOURCE_NOT_FOUND. The message comes from the code, never from an
    // HTTP status, and a code nobody has seen degrades to the generic sentence.
    return (
      <p role="alert" className="text-destructive text-sm">
        {errorMessage(state.code, tErrors)}
      </p>
    );
  }
  if (state.kind === "unavailable") {
    return <Unavailable />;
  }

  // From here on either request may still have failed — one of them, never both.
  // `undefined` is kept as such so that nothing below can mistake "did not load"
  // for "loaded, and empty".
  const listing = channels.data;
  const listed = listing?.items ?? [];
  const totalPages = listing?.total_pages ?? 0;
  const messages = await getMessages();

  // Both lists come back for the whole account; this screen is one source.
  const allStarred = (favorites.data?.items ?? [])
    .filter((favorite) => favorite.source_id === id)
    .sort((a, b) => a.position - b.position);

  const favoriteGroups = groups.data?.items ?? [];
  // A group named in the URL that no longer exists — deleted from the phone, or
  // a stale bookmark — falls back to every favourite rather than to an empty
  // rail that looks like a bug.
  const activeGroup = favoriteGroups.find((candidate) => candidate.id === group);
  const starred = activeGroup
    ? allStarred.filter((favorite) => favorite.group_id === activeGroup.id)
    : allStarred;

  // How many favourites a deletion would move, counted across the whole account
  // and not just this source: the server moves all of them, and a confirmation
  // that counted only what this page can see would understate what happens.
  const countInGroup = (groupId: string) =>
    (favorites.data?.items ?? []).filter((favorite) => favorite.group_id === groupId)
      .length;

  // How many of the open group live on **another** source, and are therefore not
  // in the rail below (S6-09).
  //
  // This page is one source and its rail can only resolve names and logos within
  // it, so the truncation is structural and stays. What was wrong was that it was
  // **silent**: a group holds channels from several subscriptions (US-12), and a
  // "Documentaries" rail short of half its channels with nothing said is the
  // expensive kind of defect — nothing looks broken. `/app/favorites` is where the
  // whole group lives; this number is the sentence that points at it.
  const elsewhere = (favorites.data?.items ?? []).filter(
    (favorite) =>
      favorite.source_id !== id &&
      (activeGroup ? favorite.group_id === activeGroup.id : true),
  ).length;
  const watched = (recents.data?.items ?? []).filter(
    (recent) => recent.source_id === id,
  );

  // Channel id → (group id → favourite id).
  //
  // Not "is this channel starred", which is what this used to be, and the
  // difference was a defect rather than a refinement. A channel can be in several
  // groups — that is the structural point of US-12 — so a control that only knew
  // *whether* it was starred had one action available, and that action was
  // "remove". There was no way, from the web, to put an already-starred channel
  // into a second group.
  //
  // The favourite's own id is the value because `DELETE /me/favorites/{id}` takes
  // it: removing from one group has to name the row in that group and no other.
  //
  // Built from every favourite of this source, never from the filtered rail:
  // narrowing the rail to "Documentaire" must not empty the stars on channels
  // filed elsewhere.
  const groupsByChannel = new Map<string, Map<string, string>>();
  for (const favorite of allStarred) {
    const memberships = groupsByChannel.get(favorite.channel_id) ?? new Map();
    memberships.set(favorite.group_id, favorite.id);
    groupsByChannel.set(favorite.channel_id, memberships);
  }

  // Everything this page has to name but was not handed by the query above: the
  // two rails, and the channel being played — which since `ids` exists no longer
  // has to be on the current page for its heading to be right.
  const byId = new Map(listed.map((channel) => [channel.id, channel]));
  // A chosen group shows all of itself, not a rail's worth: picking
  // "Documentaire" and getting twelve of its thirty channels would make the
  // choice look broken. Capped at the contract's `ids` limit, which is also the
  // point past which a strip stops being readable.
  const railSize = activeGroup ? ID_LOOKUP_MAX : RAIL_SIZE;
  const wanted = [
    ...starred.slice(0, railSize).map((favorite) => favorite.channel_id),
    ...watched.slice(0, RAIL_SIZE).map((recent) => recent.channel_id),
    ...(playing ? [playing] : []),
  ];
  const missing = [...new Set(wanted.filter((channelId) => !byId.has(channelId)))]
    // The contract caps `ids` at 100 and answers a longer list with a 400. One
    // call is enough here by construction — the rails and the group are each
    // bounded by that same number — so this is a guard rather than a paging loop.
    .slice(0, ID_LOOKUP_MAX);

  // One clock for the render: what is "on now" is decided at this instant, and
  // the guide's age is measured from it. The same instant and the zone next-intl
  // is configured with give the Guide's five days (S9-05-01b), so the grid and
  // the "on now" lines never disagree about which day is which.
  const timeZone = await getTimeZone();
  const now = new Date();
  const days = epgDayWindow(now, timeZone);
  const today = days[DAYS_BEFORE];
  // `?day=` is a calendar day of the window; anything else — absent, a stale
  // bookmark, another day entirely — opens on today. A day outside J−1→J+3 is
  // not a day this screen promised to have.
  const activeDay = days.find((day) => day.date === single(query.day)) ?? today;

  // The Guide reads the whole day it draws; the Chaînes view keeps the short
  // "on now" window its rows use. Either way it is **one** grouped request for
  // the screen, never one per channel (S9-03, S9-05-02).
  const guideWindow =
    directView === "guide"
      ? { from: activeDay.from, to: activeDay.to }
      : { from: now, to: new Date(now.getTime() + NOW_WINDOW_MS) };

  const [resolved, guide] = await Promise.all([
    missing.length > 0
      ? attempt(() =>
          api(token).GET("/sources/{id}/channels", {
            params: { path: { id }, query: { ids: missing, size: missing.length } },
          }),
        )
      : undefined,
    // The page's channels over the displayed day (Guide) or the next three
    // hours (Chaînes), in one request (S9-03, S9-05-02). Only the listing's: the
    // rails' cards are not asked for, so that a rail does not show a programme
    // under the three channels that happen to be on this page and nothing under
    // the nine that are not. Never throws.
    loadEpgWindow(
      token,
      id,
      listed.map((channel) => channel.id),
      guideWindow.from,
      guideWindow.to,
    ),
  ]);
  // An identifier the last re-synchronisation dropped is simply not in the
  // answer, by contract. It falls out of the rail below rather than rendering
  // as a gap, which is the honest outcome: the channel is gone.
  for (const channel of resolved?.data?.items ?? []) byId.set(channel.id, channel);

  const nowPlaying = playing ? byId.get(playing) : undefined;
  const railFavorites = railOf(starred.slice(0, railSize), byId);
  const railRecents = railOf(watched.slice(0, RAIL_SIZE), byId);

  const guideLines = await onAirLines(guide, now, locale as Locale, t);
  const guideNotice = await guideImportNotice(guide, now, t);

  // Where a star sends the user back to: this exact view, category, search, page
  // and player included. Built from the same values the links are built from, so
  // the two cannot drift apart.
  const returnTo = `/app/sources/${id}/channels${queryString({
    view: directView,
    categoryId,
    q: search,
    page: page > 0 ? String(page) : undefined,
    play: playing,
    group: activeGroup?.id,
  })}`;

  // Playing a channel is a URL like every other state here, and it keeps the
  // view it was started from — a rail card on page 7 of "Sport" leaves the user
  // on page 7 of "Sport", now with a player above the list.
  const playHref = (channelId: string) =>
    hrefFor(
      locale as Locale,
      `/app/sources/${id}/channels${queryString({
        view: directView,
        categoryId,
        q: search,
        page: page > 0 ? String(page) : undefined,
        play: channelId,
      })}`,
    );

  // Day navigation in the Guide (S9-05-02). The same URL with `?day=`, keeping
  // the view, the filter, the search and the page like every other link here —
  // moving in time must not lose what the person was looking at (GD-01).
  const guideDayHref = (day: string) =>
    hrefFor(
      locale as Locale,
      `/app/sources/${id}/channels${queryString({
        view: "guide",
        categoryId,
        q: search,
        page: page > 0 ? String(page) : undefined,
        play: playing,
        group: activeGroup?.id,
        day,
      })}`,
    );
  // The grid's way back out to the channel list (GD-06), keeping the same
  // filter and search — the Guide is one presentation of this catalogue, not a
  // separate screen.
  const channelsViewHref = hrefFor(
    locale as Locale,
    `/app/sources/${id}/channels${queryString({
      view: "channels",
      categoryId,
      q: search,
      page: page > 0 ? String(page) : undefined,
      play: playing,
      group: activeGroup?.id,
    })}`,
  );

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

      <h1 className="mt-4 text-2xl font-semibold tracking-tight">
        {t("catalogueTitle")}
      </h1>
      {/* No total when the listing did not load: it is not zero, it is unknown. */}
      {listing ? (
        <p className="text-muted-foreground mt-2">
          {t("catalogueCount", { total: listing.total_elements })}
        </p>
      ) : null}
      {/* Only when the guide's import is worth a sentence: old, failed or
          interrupted. See the header comment; a fresh guide says nothing. */}
      {guideNotice ? (
        <p className="text-muted-foreground/80 mt-1 text-[13px]">{guideNotice}</p>
      ) : null}

      {/* Always the three, and no request to decide it. An earlier version paid
          one call to hide the films tab on a source that had none; hiding it is
          what made somebody with a hundred and forty thousand films conclude the
          feature did not exist. An empty catalogue says so in its own list. */}
      <CatalogueTabs
        sourceId={id}
        locale={locale as Locale}
        active="channels"
        label={t("catalogueTabsLabel")}
        channelsLabel={t("catalogueTitle")}
        filmsLabel={t("filmsTitle")}
        seriesLabel={t("seriesTitle")}
      />

      {/* The two views of Direct (S9-04-06). Links, not buttons: this zone works
          without JavaScript, and the view is a URL like every other state here. */}
      <DirectViews
        locale={locale as Locale}
        sourceId={id}
        active={directView}
        query={{
          categoryId,
          q: search,
          page,
          group: activeGroup?.id,
          play: playing,
        }}
        label={t("directViewsLabel")}
        channelsLabel={t("directViewChannels")}
        guideLabel={t("directViewGuide")}
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

      <ChannelRail
        title={t("catalogueRecentTitle")}
        channels={railRecents}
        playHref={playHref}
      />
      <FavoriteGroups
        groups={favoriteGroups}
        activeId={activeGroup?.id}
        hrefForGroup={(groupId) =>
          hrefFor(
            locale as Locale,
            `/app/sources/${id}/channels${queryString({
              view: directView,
              categoryId,
              q: search,
              group: groupId,
            })}`,
          )
        }
        returnTo={returnTo}
        countInGroup={countInGroup}
        defaultGroupName={t("catalogueFavoritesDefaultGroup")}
        labels={{
          all: t("catalogueFavoritesAll"),
          create: t("catalogueGroupCreate"),
          name: t("catalogueGroupName"),
          rename: t("catalogueGroupRename"),
          remove: t("catalogueGroupDelete"),
        }}
        deleteWarning={(count) =>
          t("catalogueGroupDeleteWarning", {
            count,
            target: defaultGroupLabel(favoriteGroups, t("catalogueFavoritesDefaultGroup")),
          })
        }
      />

      <ChannelRail
        title={activeGroup ? groupLabel(activeGroup, t("catalogueFavoritesDefaultGroup")) : t("catalogueFavoritesTitle")}
        channels={railFavorites}
        playHref={playHref}
      />

      {/* Said rather than hidden. See `elsewhere` above: the rail is limited to
          this source by construction, and the whole account's favourites are one
          link away. */}
      {elsewhere > 0 ? (
        <p className="text-muted-foreground mt-2 text-sm">
          {t("catalogueFavoritesElsewhere", { count: elsewhere })}{" "}
          <a
            href={hrefFor(locale as Locale, "/app/favorites")}
            className="underline underline-offset-4"
          >
            {t("catalogueFavoritesSeeAll")}
          </a>
        </p>
      ) : null}

      {/* A plain GET form. Submitting it changes the URL, which is what every
          other control on this page does too. `page` is deliberately absent:
          a new search starts at the first page. */}
      <form method="get" className="mt-6 flex flex-wrap items-end gap-3">
        {/* The view survives a search: a GET that dropped it would send the
            person back to Chaînes on the next submit (GD-01). */}
        <input type="hidden" name="view" value={directView} />
        {categoryId ? (
          <input type="hidden" name="categoryId" value={categoryId} />
        ) : null}
        <div className="space-y-1.5">
          <label htmlFor="q" className="text-sm font-medium">
            {t("catalogueSearchLabel")}
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
        {/* Said plainly, because the server does a case-insensitive substring
            match and nothing more. Promising tolerance for typos would be a
            promise the query does not keep. */}
        <p className="text-muted-foreground w-full text-sm">
          {t("catalogueSearchHint")}
        </p>
      </form>

      <div className="mt-8 grid gap-8 md:grid-cols-[14rem_1fr]">
        <CategoryList
          categories={categories.data?.items ?? []}
          failedLabel={state.categoriesFailed ? t("catalogueCategoriesFailed") : undefined}
          activeId={categoryId}
          sourceId={id}
          locale={locale as Locale}
          view={directView}
          search={search}
          allLabel={t("catalogueAllCategories")}
        />

        {/* `min-w-0` on this grid item: a grid item defaults to `min-width: auto`,
            which lets the 3 000 px-wide guide table stretch the column instead of
            being bounded by it — the page then scrolls sideways and takes the day
            tabs and "Maintenant" with it, so `.overflow-x-auto` never engages.
            Bounding the item is what makes the grid's own horizontal scroll work
            (BUG-S9-05-02-01). */}
        <div className="min-w-0">
          {nowPlaying ? (
            <NextIntlClientProvider
              messages={{ App: messages.App, Errors: messages.Errors }}
            >
              <ChannelPlayer
                channelId={nowPlaying.id}
                sourceId={id}
                name={nowPlaying.name}
                quality={nowPlaying.quality}
                sourceHref={hrefFor(locale as Locale, `/app/sources/${id}`)}
                continueHref={hrefFor(locale as Locale, "/app")}
              />
            </NextIntlClientProvider>
          ) : null}

          {state.listingFailed ? (
            // Not the empty state: nothing is known about this list.
            <div role="alert" className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">{t("catalogueListFailed")}</p>
              <p className="text-muted-foreground mt-1 text-sm">{t("catalogueListFailedHint")}</p>
            </div>
          ) : listed.length === 0 ? (
            <div className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">
                {search ? t("catalogueNoResults") : t("catalogueEmpty")}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                {search ? t("catalogueNoResultsHint") : t("catalogueEmptyHint")}
              </p>
            </div>
          ) : directView === "guide" ? (
            <EpgGrid
              window={guide}
              channels={listed}
              days={days}
              activeDate={activeDay.date}
              todayDate={today.date}
              timeZone={timeZone}
              locale={locale as Locale}
              labels={{
                grid: t("directGuideTitle"),
                days: t("directGuideDays"),
                today: t("directDayToday"),
                nowButton: t("directNowButton"),
                emptySlot: t("directGuideEmptySlot"),
                seeChannels: t("sourceOpenCatalogue"),
              }}
              dayHref={guideDayHref}
              nowHref={guideDayHref(today.date)}
              channelsHref={channelsViewHref}
              playHref={playHref}
            />
          ) : (
            <ul aria-label={t("catalogueTitle")} className="space-y-2">
              {listed.map((channel) => (
                <ChannelRow
                  key={channel.id}
                  channel={channel}
                  playing={channel.id === playing}
                  memberships={groupsByChannel.get(channel.id)}
                  groups={favoriteGroups}
                  defaultGroupName={t("catalogueFavoritesDefaultGroup")}
                  returnTo={returnTo}
                  addLabel={activeGroup ? t("catalogueFavoriteAddTo", { group: groupLabel(activeGroup, t("catalogueFavoritesDefaultGroup")) }) : t("catalogueFavoriteAdd")}
                  removeLabel={t("catalogueFavoriteRemove")}
                  groupsLabel={t("catalogueFavoriteGroupsOf", { channel: channel.name })}
                  addToLabel={(group) => t("catalogueFavoriteAddTo", { group })}
                  removeFromLabel={(group) => t("catalogueFavoriteRemoveFrom", { group })}
                  groupId={activeGroup?.id}
                  href={playHref(channel.id)}
                  onAir={guideLines.get(channel.id)}
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
              view={directView}
              categoryId={categoryId}
              search={search}
              previousLabel={t("cataloguePrevious")}
              nextLabel={t("catalogueNext")}
              positionLabel={t("cataloguePageOf", {
                page: page + 1,
                total: totalPages,
              })}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
}

/**
 * Turns a list of identifier-carrying entries into channels, in *their* order.
 *
 * The order matters and it is not the catalogue's: favourites come out by
 * `position`, recently watched by how recently. `ids` deliberately does not
 * reorder its answer — the caller holds the order it wants — so the reordering
 * is here, driven by the list that was asked for rather than by the list that
 * came back.
 *
 * An entry whose channel could not be resolved is dropped. That is a channel the
 * last ingestion removed from the playlist, and a rail is not the place to
 * announce it.
 */
function railOf(
  entries: readonly { channel_id: string }[],
  byId: ReadonlyMap<string, Channel>,
): Channel[] {
  return entries
    .map((entry) => byId.get(entry.channel_id))
    .filter((channel): channel is Channel => channel !== undefined);
}

function CategoryList({
  categories,
  failedLabel,
  activeId,
  sourceId,
  locale,
  view,
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
  /** Kept in every link, so a category does not drop the open view (GD-01). */
  view: DirectView;
  search?: string;
  allLabel: string;
}) {
  const link = (categoryId?: string) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/channels${queryString({ view, categoryId, q: search })}`,
    );

  return (
    <nav aria-label={allLabel}>
      <ul className="space-y-1">
        <li>
          <a
            href={link()}
            className={`block rounded-lg px-3 py-2 text-sm ${
              activeId ? "text-muted-foreground hover:bg-secondary/60" : "bg-secondary font-medium"
            }`}
          >
            {allLabel}
          </a>
        </li>
        {categories.map((category) => (
          <li key={category.id}>
            <a
              href={link(category.id)}
              className={`flex items-center justify-between rounded-lg px-3 py-2 text-sm ${
                activeId === category.id
                  ? "bg-secondary font-medium"
                  : "text-muted-foreground hover:bg-secondary/60"
              }`}
            >
              <span>{category.name}</span>
              {category.channel_count != null ? (
                <span className="text-muted-foreground text-xs">
                  {category.channel_count}
                </span>
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

/**
 * One channel.
 *
 * `number` is the provider's, and it is not `position` — that one is a display
 * index reassigned at every ingestion. This is the number the user knows by
 * heart, and it is null for the many playlists that carry none.
 *
 * `quality` is echoed exactly as the source wrote it. A badge reading `fhd` in
 * lower case is a source that wrote `fhd`, and normalising it here would be this
 * layer deciding what the provider meant.
 */
function ChannelRow({
  channel,
  href,
  playing,
  onAir,
  memberships,
  groups,
  defaultGroupName,
  returnTo,
  addLabel,
  removeLabel,
  groupsLabel,
  addToLabel,
  removeFromLabel,
  groupId,
}: {
  channel: Channel;
  href: string;
  playing: boolean;
  /** What is on, and until when — or nothing, and then nothing is drawn. */
  onAir?: OnAirLine;
  memberships?: Map<string, string>;
  groups: FavoriteGroup[];
  defaultGroupName: string;
  returnTo: string;
  addLabel: string;
  removeLabel: string;
  groupsLabel: string;
  addToLabel: (group: string) => string;
  removeFromLabel: (group: string) => string;
  groupId?: string;
}) {
  return (
    <li
      className={`flex items-center gap-3 rounded-xl border px-4 py-3 ${
        playing ? "border-primary bg-primary/5" : "border-border"
      }`}
    >
      <span className="text-muted-foreground w-10 shrink-0 text-right text-sm tabular-nums">
        {channel.number ?? ""}
      </span>

      <ChannelLogo channel={channel} />

      <span className="min-w-0 flex-1">
        {/* A link, so playing a channel is a URL like every other state on this
            page: it survives a reload, it can be shared, and the back button
            closes the player. */}
        <a href={href} className="block truncate font-medium underline-offset-4 hover:underline">
          {channel.name}
        </a>
        {/* The programme on air, or nothing at all (S7-03). */}
        {onAir ? (
          <span className="text-muted-foreground block truncate text-xs">
            {onAir.title} · {onAir.until}
          </span>
        ) : null}
      </span>

      {channel.quality ? (
        <span className="border-border text-muted-foreground shrink-0 rounded border px-1.5 py-0.5 text-xs">
          {channel.quality}
        </span>
      ) : null}

      <FavoriteControl
        channelId={channel.id}
        memberships={memberships}
        groups={groups}
        defaultGroupName={defaultGroupName}
        returnTo={returnTo}
        addLabel={addLabel}
        removeLabel={removeLabel}
        groupsLabel={groupsLabel}
        addToLabel={addToLabel}
        removeFromLabel={removeFromLabel}
        groupId={groupId}
      />
    </li>
  );
}

/**
 * The control that puts this channel into the account's favourites, and into
 * which groups.
 *
 * <h2>The gesture this replaces, and why it was wrong</h2>
 *
 * It used to be one toggle: filled meant "starred somewhere", and pressing it
 * removed. That made an already-starred channel impossible to put into a second
 * group from the web — and a channel belonging to several groups is the whole
 * structural point of US-12, the reason a group belongs to the account rather
 * than to a source.
 *
 * Nothing failed when it was wrong. The star was in the right state, the page
 * came back, and the channel was simply not where the person had tried to put
 * it. `sprint-04-recette.md` R-182 even passes: it checks that stars stay filled
 * when a group is open, which is right about the *display* and is exactly what
 * made the *action* ambiguous.
 *
 * <h2>The phone's rule, applied here</h2>
 *
 * <ul>
 *   <li><b>Not starred</b> — one press, no question. It goes into the group the
 *       bar has open, or into the default group when the bar is on "all". This
 *       is the frequent gesture and it stays one click.
 *   <li><b>Already starred</b> — the group list opens instead of removing. A
 *       channel filed in two groups has no single thing a press could undo, and
 *       guessing would take it out of a group nobody mentioned.
 * </ul>
 *
 * That is `LiveViewModel.onFavoriteClicked` word for word. The two surfaces
 * disagreeing about what a star does would be worse than either of them being
 * imperfect.
 *
 * <h2>`<details>`, because this zone has no JavaScript</h2>
 *
 * The list is a native disclosure element and each row inside it is its own
 * `<form>` and submit. No client component, no popover library, and it works
 * with scripting disabled — which is the rule of this zone (`AGENTS.md` §3) and
 * not a nicety: the access token is in an httpOnly cookie, so every one of these
 * calls has to leave from the server anyway.
 *
 * Adding takes the channel's id and a group; removing takes the *favourite's*
 * id, which names the row in one group and no other. They are different values
 * and different endpoints, which is why the hidden field is named by the
 * operation rather than reused.
 */
function FavoriteControl({
  channelId,
  memberships,
  groups,
  defaultGroupName,
  returnTo,
  addLabel,
  removeLabel,
  groupsLabel,
  addToLabel,
  removeFromLabel,
  groupId,
}: {
  channelId: string;
  /** Group id → favourite id, for the groups this channel is in. */
  memberships?: Map<string, string>;
  groups: FavoriteGroup[];
  defaultGroupName: string;
  returnTo: string;
  addLabel: string;
  removeLabel: string;
  groupsLabel: string;
  addToLabel: (group: string) => string;
  removeFromLabel: (group: string) => string;
  /** The group open in the bar above, or undefined for the default group. */
  groupId?: string;
}) {
  const starred = memberships !== undefined && memberships.size > 0;

  // Not starred, or no group list to offer — the second happens when
  // `GET /me/favorite-groups` failed, and a control that opened onto an empty
  // list would be worse than the plain toggle it replaced.
  if (!starred || groups.length === 0) {
    return (
      <form action={starred ? removeFavorite : addFavorite} className="shrink-0">
        {starred ? (
          <input
            type="hidden"
            name="favoriteId"
            value={[...(memberships?.values() ?? [])][0]}
          />
        ) : (
          <input type="hidden" name="channelId" value={channelId} />
        )}
        {/* The group the bar has open, so starring files where the person is
            already looking. Omitted, the server files it in the default group. */}
        {!starred && groupId ? (
          <input type="hidden" name="groupId" value={groupId} />
        ) : null}
        <input type="hidden" name="returnTo" value={returnTo} />
        <button
          type="submit"
          aria-pressed={starred}
          aria-label={starred ? removeLabel : addLabel}
          title={starred ? removeLabel : addLabel}
          className={buttonClass(starred)}
        >
          <Star filled={starred} />
        </button>
      </form>
    );
  }

  return (
    <details className="relative shrink-0">
      {/* `list-none` on both selectors: browsers disagree about which one draws
          the disclosure triangle, and a triangle beside a star is two affordances
          for one control. */}
      <summary
        aria-label={groupsLabel}
        title={groupsLabel}
        className={`${buttonClass(true)} cursor-pointer list-none [&::-webkit-details-marker]:hidden`}
      >
        <Star filled />
      </summary>

      <div className="border-border bg-background absolute right-0 z-20 mt-1 w-56 rounded-xl border p-1 shadow-lg">
        <ul>
          {groups.map((group) => {
            const favoriteId = memberships.get(group.id);
            const name = groupLabel(group, defaultGroupName);
            const label = favoriteId ? removeFromLabel(name) : addToLabel(name);

            return (
              <li key={group.id}>
                <form action={favoriteId ? removeFavorite : addFavorite}>
                  {favoriteId ? (
                    <input type="hidden" name="favoriteId" value={favoriteId} />
                  ) : (
                    <>
                      <input type="hidden" name="channelId" value={channelId} />
                      <input type="hidden" name="groupId" value={group.id} />
                    </>
                  )}
                  <input type="hidden" name="returnTo" value={returnTo} />
                  <button
                    type="submit"
                    aria-pressed={favoriteId !== undefined}
                    aria-label={label}
                    className="hover:bg-secondary/60 flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm"
                  >
                    {/* The tick is state, not a second target: the whole row is
                        the button, exactly as on the television, where a
                        focusable checkbox would double the length of the
                        journey through a list somebody is trying to leave. */}
                    <span
                      aria-hidden="true"
                      className={favoriteId ? "text-primary" : "text-transparent"}
                    >
                      ✓
                    </span>
                    <span className="truncate">{name}</span>
                  </button>
                </form>
              </li>
            );
          })}
        </ul>
      </div>
    </details>
  );
}

function buttonClass(filled: boolean): string {
  return `flex h-8 w-8 items-center justify-center rounded-lg ${
    filled
      ? "text-primary"
      : "text-muted-foreground hover:bg-secondary/60 hover:text-foreground"
  }`;
}

/**
 * The star itself.
 *
 * Inline rather than an icon package: the whole difference between the two states
 * is `fill`, and a dependency for one path is a dependency too many.
 */
function Star({ filled = false }: { filled?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={18}
      height={18}
      aria-hidden="true"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinejoin="round"
    >
      <path d="M12 3.5l2.6 5.3 5.9.85-4.25 4.15 1 5.85L12 16.9l-5.25 2.75 1-5.85L3.5 9.65l5.9-.85z" />
    </svg>
  );
}

function Pagination({
  page,
  totalPages,
  sourceId,
  locale,
  view,
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
  view: DirectView;
  categoryId?: string;
  search?: string;
  previousLabel: string;
  nextLabel: string;
  positionLabel: string;
}) {
  const link = (target: number) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/channels${queryString({
        view,
        categoryId,
        q: search,
        page: target > 0 ? String(target) : undefined,
      })}`,
    );

  return (
    <nav className="mt-6 flex items-center gap-4 text-sm" aria-label={positionLabel}>
      {page > 0 ? (
        <a href={link(page - 1)} className="underline underline-offset-4">
          {previousLabel}
        </a>
      ) : null}
      <span className="text-muted-foreground">{positionLabel}</span>
      {page + 1 < totalPages ? (
        <a href={link(page + 1)} className="underline underline-offset-4">
          {nextLabel}
        </a>
      ) : null}
    </nav>
  );
}

type Translate = Awaited<ReturnType<typeof getTranslations<"App">>>;

/**
 * The line under each channel, from the guide read for the page.
 *
 * Formatted here and not in the row: the sentence is the page's language and
 * the hour is the zone next-intl is configured with (`getTimeZone()`), the
 * same decision the home page makes for its rails. A programme whose end
 * cannot be read gets no line rather than one with "Invalid Date" in it.
 */
async function onAirLines(
  guide: EpgWindow,
  now: Date,
  locale: Locale,
  t: Translate,
): Promise<Map<string, OnAirLine>> {
  const lines = new Map<string, OnAirLine>();
  if (guide.state !== "ok") return lines;

  const timeZone = await getTimeZone();
  for (const [channelId, programme] of onAirByChannel(guide.grid.channels, now)) {
    const ends = clockTime(programme.ends_at, locale, timeZone);
    if (ends === undefined) continue;
    lines.set(channelId, { title: programme.title, until: t("epgUntil", { time: ends }) });
  }
  return lines;
}

/**
 * What the header says about the guide's import, or nothing (D3, D4).
 *
 * Three cases earn a sentence — stale, failed, interrupted — and every other
 * one earns silence: fresh (noise), unknown and not configured (a sentence
 * about a feature nobody asked for, on a page that is about channels), running
 * (over in a minute, and the page has no way to follow it).
 */
async function guideImportNotice(
  guide: EpgWindow,
  now: Date,
  t: Translate,
): Promise<string | undefined> {
  if (guide.state !== "ok") return undefined;

  const freshness = epgFreshness(guide.grid.epg, guide.grid.generated_at, now);
  switch (freshness.kind) {
    case "stale": {
      // "2 days ago" from the server's import date, against this render's
      // clock — `relativeTime` on the formatter every date of this zone goes
      // through (`AGENTS.md` §7).
      const format = await getFormatter();
      return t("epgLastImport", { ago: format.relativeTime(freshness.importedAt, now) });
    }
    case "attempt-failed":
      return t("epgImportFailed");
    case "attempt-interrupted":
      return t("epgImportInterrupted");
    default:
      return undefined;
  }
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
