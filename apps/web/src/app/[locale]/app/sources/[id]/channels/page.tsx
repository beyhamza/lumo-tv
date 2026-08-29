import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import {
  addFavorite,
  createFavoriteGroup,
  deleteFavoriteGroup,
  removeFavorite,
  renameFavoriteGroup,
} from "@/actions/favorites";
import { CatalogueTabs } from "@/components/app/CatalogueTabs";
import { ChannelPlayer } from "@/components/app/ChannelPlayer";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api, problemCode } from "@/lib/api/client";
import type { Category, Channel, FavoriteGroup } from "@/lib/api/types";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

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
  // into "unavailable". A source still importing is not an outage.
  const [categories, channels, favorites, recents, groups, filmCategories] = await Promise.all([
    api(session.accessToken).GET("/sources/{id}/categories", {
      params: { path: { id }, query: { contentType: "LIVE" } },
    }),
    api(session.accessToken).GET("/sources/{id}/channels", {
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
    // Every favourite of the account, not only this source's: the contract has
    // no filter by source, and the list is small by nature — it is what one
    // person starred by hand. Its failure is deliberately not part of `failure`
    // below: a catalogue that refuses to render because a star could not be
    // read would be trading the whole screen for its smallest control.
    api(session.accessToken).GET("/me/favorites", {}),
    // Watched on any device, which is the whole value of asking the server
    // rather than remembering locally: what was started on the phone this
    // morning is at the top of this rail now.
    api(session.accessToken).GET("/me/recent-channels", {
      params: { query: { limit: RAIL_SIZE } },
    }),
    // The account's groups. Like the favourites above, a failure here does not
    // take the catalogue down: the rail loses its group bar, the channel list is
    // untouched.
    api(session.accessToken).GET("/me/favorite-groups", {}),
    // Whether this source has films, and it buys exactly one thing: the tab
    // that lets somebody cross from here to them. One request against a list
    // counted in tens. Its failure hides the tab rather than the catalogue —
    // which is the same answer as "this source has no films", and the right one:
    // a tab drawn on a guess is a door onto a room nobody has confirmed.
    api(session.accessToken).GET("/sources/{id}/categories", {
      params: { path: { id }, query: { contentType: "VOD" } },
    }),
  ]);

  const failure = problemCode(channels.error) ?? problemCode(categories.error);

  if (failure === "SOURCE_NOT_READY") {
    return (
      <NotReady
        id={id}
        locale={locale as Locale}
        title={t("catalogueNotReady")}
        link={t("catalogueNotReadyLink")}
      />
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
  if (!channels.data || !categories.data) {
    return <Unavailable />;
  }

  const totalPages = channels.data.total_pages;
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
  const byId = new Map(channels.data.items.map((channel) => [channel.id, channel]));
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

  if (missing.length > 0) {
    const resolved = await api(session.accessToken).GET("/sources/{id}/channels", {
      params: { path: { id }, query: { ids: missing, size: missing.length } },
    });
    // An identifier the last re-synchronisation dropped is simply not in the
    // answer, by contract. It falls out of the rail below rather than rendering
    // as a gap, which is the honest outcome: the channel is gone.
    for (const channel of resolved.data?.items ?? []) byId.set(channel.id, channel);
  }

  const nowPlaying = playing ? byId.get(playing) : undefined;
  const railFavorites = railOf(starred.slice(0, railSize), byId);
  const railRecents = railOf(watched.slice(0, RAIL_SIZE), byId);

  // Where a star sends the user back to: this exact view, category, search, page
  // and player included. Built from the same values the links are built from, so
  // the two cannot drift apart.
  const returnTo = `/app/sources/${id}/channels${queryString({
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
        categoryId,
        q: search,
        page: page > 0 ? String(page) : undefined,
        play: channelId,
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
      <p className="text-muted-foreground mt-2">
        {t("catalogueCount", { total: channels.data.total_elements })}
      </p>

      <CatalogueTabs
        sourceId={id}
        locale={locale as Locale}
        active="channels"
        hasFilms={(filmCategories.data?.items.length ?? 0) > 0}
        label={t("catalogueTabsLabel")}
        channelsLabel={t("catalogueTitle")}
        filmsLabel={t("filmsTitle")}
      />

      <Rail
        title={t("catalogueRecentTitle")}
        channels={railRecents}
        playHref={playHref}
      />
      <FavoriteGroups
        groups={favoriteGroups}
        activeId={activeGroup?.id}
        sourceId={id}
        locale={locale as Locale}
        categoryId={categoryId}
        search={search}
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

      <Rail
        title={activeGroup ? groupLabel(activeGroup, t("catalogueFavoritesDefaultGroup")) : t("catalogueFavoritesTitle")}
        channels={railFavorites}
        playHref={playHref}
      />

      {/* A plain GET form. Submitting it changes the URL, which is what every
          other control on this page does too. `page` is deliberately absent:
          a new search starts at the first page. */}
      <form method="get" className="mt-6 flex flex-wrap items-end gap-3">
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
          categories={categories.data.items}
          activeId={categoryId}
          sourceId={id}
          locale={locale as Locale}
          search={search}
          allLabel={t("catalogueAllCategories")}
        />

        <div>
          {nowPlaying ? (
            <NextIntlClientProvider
              messages={{ App: messages.App, Errors: messages.Errors }}
            >
              <ChannelPlayer
                channelId={nowPlaying.id}
                name={nowPlaying.name}
                quality={nowPlaying.quality}
              />
            </NextIntlClientProvider>
          ) : null}

          {channels.data.items.length === 0 ? (
            <div className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">
                {search ? t("catalogueNoResults") : t("catalogueEmpty")}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                {search ? t("catalogueNoResultsHint") : t("catalogueEmptyHint")}
              </p>
            </div>
          ) : (
            <ul aria-label={t("catalogueTitle")} className="space-y-2">
              {channels.data.items.map((channel) => (
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

/**
 * One rail: a row of channels to get back to in a click.
 *
 * <h2>It scrolls with CSS, and with nothing else</h2>
 *
 * `overflow-x: auto` and no JavaScript at all — no carousel, no arrows, no
 * measured widths. A touch screen flicks it, a trackpad swipes it, a keyboard
 * reaches every card because they are links in a list. The page-wide rule that
 * this screen works without JavaScript is not suspended for decoration.
 *
 * <h2>An empty rail is no rail</h2>
 *
 * Nothing starred yet, or nothing watched yet on this source, renders nothing:
 * an empty strip with a heading above it is a promise that something belongs
 * there, and the star on the rows below is where that starts.
 */
/**
 * The favourite groups: which one the rail shows, and what can be done to them.
 *
 * <h2>Links to filter, forms to change</h2>
 *
 * Same split as the rest of the screen. Choosing a group is a link, because it is
 * a view — it belongs in the URL, it survives a reload, the back button undoes it.
 * Creating, renaming and deleting are `<form>`s pointed at Server Actions,
 * because they change something and because the access token is not in the
 * browser (`AGENTS.md` §4). Neither needs JavaScript.
 *
 * <h2>Deleting says what it will do, with the number</h2>
 *
 * The server moves a deleted group's favourites into the default group rather
 * than removing them. "Are you sure?" would tell this person nothing they do not
 * already know; the count and the destination let them predict the state they
 * will be in. Same wording as the phone, same number.
 *
 * <h2>The default group has no delete control</h2>
 *
 * The server refuses it — it is where the others empty into — and a control whose
 * only possible answer is an error teaches somebody that the application is
 * broken.
 *
 * <h2>Nothing here when there is nothing to organise</h2>
 *
 * No groups and no favourites means no bar: an account that has never starred
 * anything does not need a filter over an empty rail. The creation form appears
 * with the first group, which is created by the first star.
 */
function FavoriteGroups({
  groups,
  activeId,
  sourceId,
  locale,
  categoryId,
  search,
  returnTo,
  countInGroup,
  defaultGroupName,
  labels,
  deleteWarning,
}: {
  groups: FavoriteGroup[];
  activeId?: string;
  sourceId: string;
  locale: Locale;
  categoryId?: string;
  search?: string;
  returnTo: string;
  countInGroup: (groupId: string) => number;
  defaultGroupName: string;
  labels: {
    all: string;
    create: string;
    name: string;
    rename: string;
    remove: string;
  };
  deleteWarning: (count: number) => string;
}) {
  if (groups.length === 0) return null;

  const hrefForGroup = (groupId?: string) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/channels${queryString({
        categoryId,
        q: search,
        group: groupId,
      })}`,
    );

  const active = groups.find((group) => group.id === activeId);

  return (
    <section className="mt-6">
      <nav aria-label={labels.all} className="flex flex-wrap items-center gap-2">
        <GroupLink href={hrefForGroup()} active={activeId === undefined}>
          {labels.all}
        </GroupLink>
        {groups.map((group) => (
          <GroupLink
            key={group.id}
            href={hrefForGroup(group.id)}
            active={group.id === activeId}
          >
            {groupLabel(group, defaultGroupName)}
          </GroupLink>
        ))}
      </nav>

      <div className="mt-3 flex flex-wrap items-end gap-4">
        {/* Renaming and deleting act on the group that is open, so there is one
            of each rather than a control per chip — a bar that carried three
            buttons per group would be unreadable at the width a phone gives it. */}
        {active ? (
          <>
            <form action={renameFavoriteGroup} className="flex items-end gap-2">
              <input type="hidden" name="groupId" value={active.id} />
              <input type="hidden" name="returnTo" value={returnTo} />
              <div className="space-y-1.5">
                <label
                  htmlFor="group-name"
                  className="text-muted-foreground text-xs font-medium"
                >
                  {labels.name}
                </label>
                <input
                  id="group-name"
                  name="name"
                  type="text"
                  required
                  maxLength={100}
                  defaultValue={groupLabel(active, defaultGroupName)}
                  className="border-input bg-background h-9 rounded-lg border px-3 text-sm"
                />
              </div>
              <button
                type="submit"
                className="bg-secondary text-secondary-foreground h-9 rounded-lg px-3 text-sm font-medium"
              >
                {labels.rename}
              </button>
            </form>

            {active.is_default ? null : (
              <form action={deleteFavoriteGroup} className="space-y-1.5">
                <input type="hidden" name="groupId" value={active.id} />
                <input type="hidden" name="returnTo" value={returnTo} />
                <p className="text-muted-foreground max-w-md text-xs">
                  {deleteWarning(countInGroup(active.id))}
                </p>
                <button
                  type="submit"
                  className="border-destructive text-destructive h-9 rounded-lg border px-3 text-sm font-medium"
                >
                  {labels.remove}
                </button>
              </form>
            )}
          </>
        ) : null}

        <form action={createFavoriteGroup} className="flex items-end gap-2">
          <input type="hidden" name="returnTo" value={returnTo} />
          <div className="space-y-1.5">
            <label
              htmlFor="new-group-name"
              className="text-muted-foreground text-xs font-medium"
            >
              {labels.create}
            </label>
            <input
              id="new-group-name"
              name="name"
              type="text"
              required
              maxLength={100}
              className="border-input bg-background h-9 rounded-lg border px-3 text-sm"
            />
          </div>
          <button
            type="submit"
            className="bg-secondary text-secondary-foreground h-9 rounded-lg px-3 text-sm font-medium"
          >
            {labels.create}
          </button>
        </form>
      </div>
    </section>
  );
}

function GroupLink({
  href,
  active,
  children,
}: {
  href: string;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <a
      href={href}
      aria-current={active ? "true" : undefined}
      className={`rounded-lg border px-3 py-1.5 text-sm ${
        active
          ? "border-primary bg-primary/10 font-medium"
          : "border-border text-muted-foreground hover:bg-secondary/60"
      }`}
    >
      {children}
    </a>
  );
}

/**
 * What to call a group on screen.
 *
 * The server names the group it creates on the first add, and names it
 * `Favorites`, in English. `is_default` is what lets a client translate it; the
 * second half of the condition is what stops the translation overriding the user
 * once they have renamed it — here, or on their phone.
 */
function groupLabel(group: FavoriteGroup, translated: string): string {
  return group.is_default && group.name === SERVER_DEFAULT_GROUP_NAME
    ? translated
    : group.name;
}

/** Where a deleted group's channels go, named as the user sees it. */
function defaultGroupLabel(groups: FavoriteGroup[], translated: string): string {
  const fallback = groups.find((group) => group.is_default);
  return fallback ? groupLabel(fallback, translated) : translated;
}

/** The name the server gives the default group, verbatim. */
const SERVER_DEFAULT_GROUP_NAME = "Favorites";

function Rail({
  title,
  channels,
  playHref,
}: {
  title: string;
  channels: Channel[];
  /** Builds the link that plays one channel without leaving the current view. */
  playHref: (channelId: string) => string;
}) {
  if (channels.length === 0) return null;

  return (
    <section className="mt-6">
      <h2 className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
        {title}
      </h2>
      <ul
        aria-label={title}
        className="mt-2 flex gap-3 overflow-x-auto pb-2"
      >
        {channels.map((channel) => (
          <li key={channel.id} className="shrink-0">
            <a
              href={playHref(channel.id)}
              className="border-border hover:bg-secondary/60 flex w-40 items-center gap-2 rounded-xl border px-3 py-2"
            >
              <Logo channel={channel} />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                {channel.name}
              </span>
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}

function NotReady({
  id,
  locale,
  title,
  link,
}: {
  id: string;
  locale: Locale;
  title: string;
  link: string;
}) {
  return (
    <div className="border-border rounded-xl border border-dashed px-5 py-6">
      <p className="font-medium">{title}</p>
      <p className="mt-3">
        <a
          href={hrefFor(locale, `/app/sources/${id}`)}
          className="text-sm underline underline-offset-4"
        >
          {link}
        </a>
      </p>
    </div>
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
  const link = (categoryId?: string) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/channels${queryString({ categoryId, q: search })}`,
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

      <Logo channel={channel} />

      {/* A link, so playing a channel is a URL like every other state on this
          page: it survives a reload, it can be shared, and the back button
          closes the player. */}
      <a href={href} className="min-w-0 flex-1 truncate font-medium underline-offset-4 hover:underline">
        {channel.name}
      </a>

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
/**
 * The logo the user's own playlist advertises, or nothing.
 *
 * <b>Lumo ships no fallback artwork</b> (AGENTS.md §1): a channel with no logo
 * gets its initial, never a bundled image of ours.
 *
 * A plain `<img>`, deliberately, and not `next/image`. The optimiser would fetch
 * every provider logo through our own server, which is the same posture question
 * as relaying a stream (ADR 0007) for a far smaller benefit. The cost of the
 * plain tag is that a logo served over `http` will not load on an `https` page —
 * which is honest: it is the provider's choice, and the initial takes its place.
 */
function Logo({ channel }: { channel: Channel }) {
  if (!channel.logo_url) {
    return (
      <span className="bg-secondary text-muted-foreground flex h-8 w-8 shrink-0 items-center justify-center rounded text-xs">
        {channel.name.slice(0, 1).toUpperCase()}
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={channel.logo_url}
      alt=""
      width={32}
      height={32}
      loading="lazy"
      decoding="async"
      // The provider learns nothing about which of their channels is being
      // looked at from which page.
      referrerPolicy="no-referrer"
      className="h-8 w-8 shrink-0 rounded object-contain"
    />
  );
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
  const link = (target: number) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/channels${queryString({
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
