import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { ReactNode } from "react";
import {
  addMembership,
  moveFavorite,
  removeFromAllFavorites,
  removeMembership,
} from "@/actions/favorites";
import {
  FavoriteGroups,
  defaultGroupLabel,
  groupLabel,
} from "@/components/app/FavoriteGroups";
import { PendingButton } from "@/components/app/PendingButton";
import { EmptyState, Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import type { Channel, Favorite, FavoriteGroup } from "@/lib/api/types";
import { channelStatus, resolveChannels } from "@/lib/catalogue/resolve-channels";
import { stillRemaining } from "@/lib/favorites/global-removal";
import {
  cleanView,
  confirmView,
  readConfirm,
  readNotice,
  type FavoritesNotice,
} from "@/lib/favorites/notice";
import { favoritesView } from "@/lib/favorites/view";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { loadActiveSource } from "@/lib/sources/active-source-store";

/**
 * The favourites of the account, one source at a time (US-12, S6-09, US-018).
 *
 * <h2>One source at a time, and the groups are still the account's (US-018)</h2>
 *
 * Since S8-03 this browser browses one source — the active one — and this screen
 * follows: it lists the favourites **of that source** and names it. The history
 * below still holds where it matters. The route stays at the root of the zone
 * and not under `sources/[id]`, because what it shows is decided by the active
 * source and not by a path; and a group still belongs to the account and may
 * hold channels of two subscriptions. What changed is that the two are no longer
 * *listed* together: a mixed list under one source's name is the confusion
 * US-018 exists to remove.
 *
 * The filter is applied here, after fetching — favourites carry `source_id` and
 * the API has no parameter for it. **Nothing is deleted by it**: switch back and
 * the other source's favourites are there, which the screen says in words so
 * that nobody concludes from a shorter list that something was lost.
 *
 * When a choice of source is still owed, the screen asks for it rather than fall
 * back to the mixed list; when the account has no source, it says how to add
 * one; and when `GET /sources` did not answer, it is unavailable — an outage is
 * not an empty account.
 *
 * <h2>How it got here</h2>
 *
 * <h2>This screen exists because the other one was quietly lying</h2>
 *
 * The web got groups in `S4-09` — the bar, the star, create, rename, delete — and
 * all of it works. But it lives on the channels page **of one source**, and that
 * page filters `favorite.source_id === id`.
 *
 * That filter contradicts the structural point of US-12: a group belongs to the
 * **account** and can hold channels from two subscriptions. A group called
 * "Documentaries" was rendered short of the channels from the other source,
 * **without saying so** — which is the most expensive kind of defect this product
 * has a name for, because nothing looks broken. The phone has assumed it since
 * `S4-04`, with a screen of its own and a "From *[source]*" line under each row.
 *
 * So: `/app/favorites`, at the root of the account zone and **not** under
 * `sources/[id]`. The route is the fix as much as the code is.
 *
 * <h2>The open group is in the URL</h2>
 *
 * `?group=`, like every other piece of state in this zone: shareable, correct
 * under the back button, and **it works with no JavaScript**. The rule does not
 * lapse because the screen is new.
 *
 * <h2>Three requests, and the third is shared with the rail</h2>
 *
 * `GET /me/favorites` answers for the account but carries identifiers only.
 * Names and logos come from `GET /sources/{id}/channels?ids=`, which is what
 * `resolveChannels` is for, and where the `ids` ceiling has to be a loop rather
 * than a cut.
 *
 * `GET /sources` is the third. It used to label each row with where the channel
 * came from; it now decides which source is active and gives the screen its
 * name. It goes through `loadActiveSource`, memoised per request, so the layout's
 * rail and this page share one call and cannot disagree.
 *
 * <h2>No player here</h2>
 *
 * A favourite opens `/app/sources/{sourceId}/channels?play={channelId}` — its own
 * source's page, which already has everything. This screen lists and routes; it
 * does not play. That is what keeps it the size it is.
 *
 * <h2>The group bar stays on the channels page too, and that is deliberate</h2>
 *
 * Two places, two jobs: one **files** where the star is, one **browses** here.
 * That is the phone's split — the star in the list, a Favourites tab beside it —
 * and duplicating the bar on purpose costs less than a round trip per starring.
 * The bar itself is one component (`FavoriteGroups`), so they cannot drift.
 *
 * <h2>Organising happens here (S11-02, S11-03)</h2>
 *
 * **All favourites** is the US-020 aggregation — each channel once, at its first
 * occurrence — and no longer every row of the source: a channel in two groups
 * was listed twice, and its "Remove" took out one membership of two. Its rows
 * offer "Organise" (choose the groups) and "Remove from all my favourites",
 * which asks first (`?confirm=`) and then removes every membership the
 * confirmation listed, keeping what succeeded when one of them fails.
 *
 * **A group** lists its own rows for the active source, with "Move up",
 * "Move down" and "Remove from group". A visible move is translated to the full
 * group so that the other sources keep their places (G1), sent as single moves,
 * and never announced as a success when one of them did not go through (G2).
 *
 * Every action re-reads on the way back (G3) and reports only what it could not
 * confirm, in its redirect (`lib/favorites/notice.ts`). The decisions are in
 * `lib/favorites/`, pure and tested; this file draws them.
 *
 * <h2>A favourite whose channel could not be named stays on screen (S11-05)</h2>
 *
 * Names and logos come from a second request. When a batch of it fails, its
 * favourites used to fall out of the list — an outage drawn as fewer
 * favourites. Now every favourite has a row, whatever its lookup said
 * (`channelStatus`):
 *
 * - **unavailable** — the batch did not answer: "Details unavailable", no play
 *   link, and a banner above the list saying how many and offering to retry (a
 *   plain link to this same view). Organise, move and remove still work: they
 *   address favourites by their own ids, not by the channel.
 * - **gone** — the batch answered without it: "No longer in this source", no
 *   play link, removable. The server cascades a deleted channel's favourites,
 *   so this only shows for a list read just before that cascade; no banner, as
 *   retrying would not bring it back.
 */

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/favorites">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app/favorites",
    title: t("favoritesTitle"),
    description: t("favoritesMetaDescription"),
    index: false,
  });
}

export default async function FavoritesPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/favorites">) {
  const { locale } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");

  const activeGroupId = single(query.group);

  // Through `fetched`, which also absorbs a request that got no answer at all:
  // openapi-fetch throws on a refused connection, and an uncaught throw here
  // was Next's generic English error page in place of this one (BUG-S11-06-03).
  const [favoritesAnswer, groupsAnswer, activeSource] = await Promise.all([
    fetched(() => api(session.accessToken).GET("/me/favorites", {})),
    fetched(() => api(session.accessToken).GET("/me/favorite-groups", {})),
    // Which source this browser is browsing. The layout asked the same question
    // for the rail; this is the same answer, not a second request.
    loadActiveSource(session.accessToken, session.userId),
  ]);

  // An unanswered `GET /sources` is in this branch on purpose. It is not "no
  // source" and it is not "choose one": it proves nothing, so it changes nothing.
  if (
    favoritesAnswer.state !== "ok" ||
    groupsAnswer.state !== "ok" ||
    activeSource.state === "unavailable"
  ) {
    return <Unavailable />;
  }
  const favorites = { data: favoritesAnswer.data };
  const groups = { data: groupsAnswer.data };

  if (activeSource.state === "none") {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("favoritesTitle")}</h1>
        <div className="mt-8">
          <EmptyState title={t("sourcesEmpty")} hint={t("sourcesEmptyHint")} />
        </div>
        <p className="mt-4">
          <a
            href={hrefFor(locale as Locale, "/app/sources/new")}
            className="text-sm underline underline-offset-4"
          >
            {t("sourcesAddCta")}
          </a>
        </p>
      </div>
    );
  }

  if (activeSource.state === "needs-choice") {
    // Not the mixed list "in the meantime". The switcher in the rail is already
    // open on the same question; this says why the screen is waiting for it.
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("favoritesTitle")}</h1>
        <div className="mt-8">
          <EmptyState title={t("favoritesChooseTitle")} hint={t("favoritesChooseBody")} />
        </div>
      </div>
    );
  }

  const source = activeSource.source;
  const allFavorites = favorites.data.items;
  // Sorted once here, so the bar, the group order and the aggregation all read
  // the same order, whatever order the answer came in.
  const favoriteGroups = [...groups.data.items].sort((a, b) => a.position - b.position);
  const defaultName = t("catalogueFavoritesDefaultGroup");
  const nameOf = (groupId: string) => {
    const group = favoriteGroups.find((candidate) => candidate.id === groupId);
    return group ? groupLabel(group, defaultName) : defaultName;
  };

  // The server keeps the order the user arranged; `favoritesView` decides what
  // each view lists (S11-02: All favourites shows each channel once).
  const view = favoritesView(allFavorites, favoriteGroups, source.id, activeGroupId);
  const shown: Favorite[] =
    view.kind === "all" ? view.rows.map((row) => row.favorite) : view.rows;

  // In batches of a hundred, in parallel. A group of three hundred channels is
  // not one request, and a `.slice()` here would truncate it with a `200` and
  // no error.
  const resolution = await resolveChannels(
    shown.map((favorite) => ({
      sourceId: favorite.source_id,
      channelId: favorite.channel_id,
    })),
    async (sourceId, ids, size) => {
      const answer = await api(session.accessToken).GET("/sources/{id}/channels", {
        params: { path: { id: sourceId }, query: { ids, size } },
      });
      // A refusal is a failure, not an empty answer: `[]` here would claim every
      // channel of the batch is gone, and their rows would read as such.
      if (!answer.data) throw new Error("channel lookup failed");
      return answer.data.items;
    },
  );
  const byId = resolution.byId;

  // What one row can say about its channel. `position` names a row whose
  // channel has no name, for the labels of its controls.
  const rowOf = (channelId: string, position: number): Row => {
    const status = channelStatus(resolution, channelId);
    return status.state === "resolved"
      ? { state: "resolved", channel: status.channel, name: status.channel.name }
      : { state: status.state, name: t("favoritesUnnamedChannel", { position }) };
  };
  // Counted once per channel: the banner says how many favourites are shown
  // without their details.
  const unresolved = new Set(
    shown
      .map((favorite) => favorite.channel_id)
      .filter((channelId) => resolution.failed.has(channelId)),
  ).size;

  const activeGroup =
    view.kind === "group"
      ? favoriteGroups.find((group) => group.id === view.groupId)
      : undefined;
  const path = "/app/favorites";
  // What every form posts back to: this view, minus any notice or confirmation.
  const returnTo = cleanView(path, { group: activeGroup?.id });
  const here = () => hrefFor(locale as Locale, returnTo);

  const notice = readNotice(query);
  const confirming = view.kind === "all" ? readConfirm(query) : undefined;

  // How many of the open group's favourites belong to another source: not listed
  // here, and keeping their places when this source's are reordered (FO-01).
  const elsewhere = activeGroup
    ? allFavorites.filter(
        (favorite) => favorite.group_id === activeGroup.id && favorite.source_id !== source.id,
      ).length
    : 0;

  const listClass = "mt-8 divide-border divide-y rounded-xl border";
  const smallButton =
    "text-muted-foreground hover:text-foreground rounded-lg border px-3 py-1.5 text-sm disabled:opacity-40";

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">{t("favoritesTitle")}</h1>
      <p className="text-muted-foreground mt-2">
        {t("favoritesOfSource", { source: source.label })}
      </p>
      <p className="text-muted-foreground mt-1 text-sm">{t("favoritesIntro")}</p>

      <FavoriteGroups
        groups={favoriteGroups}
        activeId={activeGroup?.id}
        hrefForGroup={(groupId) =>
          hrefFor(locale as Locale, cleanView(path, { group: groupId }))
        }
        returnTo={returnTo}
        // Counted across the account, not the active source, and that is not an
        // oversight: this number only feeds the delete warning, and deleting a
        // group moves **every** channel in it — the other sources' included.
        countInGroup={(groupId) =>
          allFavorites.filter((favorite) => favorite.group_id === groupId).length
        }
        defaultGroupName={defaultName}
        labels={{
          all: t("catalogueFavoritesAll"),
          create: t("catalogueGroupCreate"),
          name: t("catalogueGroupName"),
          rename: t("catalogueGroupRename"),
          remove: t("catalogueGroupDelete"),
          confirmRemove: t("catalogueGroupDeleteConfirm"),
        }}
        deleteWarning={(count) =>
          t("catalogueGroupDeleteWarning", {
            count,
            target: defaultGroupLabel(favoriteGroups, defaultName),
          })
        }
        move={{
          earlier: t("catalogueGroupMoveEarlier"),
          later: t("catalogueGroupMoveLater"),
          earlierOf: (group) => t("catalogueGroupMoveEarlierOf", { group }),
          laterOf: (group) => t("catalogueGroupMoveLaterOf", { group }),
        }}
      />

      <Notice
        notice={notice}
        favorites={allFavorites}
        channelName={(channelId) => byId.get(channelId)?.name}
        groupName={nameOf}
        returnTo={returnTo}
        closeHref={here()}
      />

      {unresolved > 0 ? (
        // Partial, not empty: the rows are all below, some without their
        // details. Retrying is reading this same view again.
        <div role="status" className="border-border mt-6 rounded-xl border px-4 py-3">
          <p className="font-medium">{t("favoritesPartialTitle")}</p>
          <p className="text-muted-foreground mt-1 text-sm">
            {t("favoritesPartialBody", { count: unresolved })}
          </p>
          <a href={here()} className="mt-2 inline-block text-sm underline underline-offset-4">
            {t("favoritesRetry")}
          </a>
        </div>
      ) : null}

      {shown.length === 0 ? (
        <div className="border-border mt-8 rounded-xl border border-dashed px-5 py-6">
          <p className="font-medium">
            {activeGroup
              ? t("favoritesGroupEmpty", { group: groupLabel(activeGroup, defaultName) })
              : t("favoritesEmpty")}
          </p>
          {/* Where starring happens, said rather than assumed: the star is on the
              channel list, and an empty screen that does not say where to start
              is a screen somebody leaves. */}
          <p className="text-muted-foreground mt-1 text-sm">{t("favoritesEmptyHint")}</p>
        </div>
      ) : view.kind === "all" ? (
        <ul aria-label={t("favoritesListLabel")} className={listClass}>
          {view.rows.map(({ favorite, memberships }, index) => {
            const channelId = favorite.channel_id;
            const row = rowOf(channelId, index + 1);
            const groupNames = memberships.map((membership) => nameOf(membership.group_id));
            return (
              <li key={channelId} className="px-4 py-3">
                <div className="flex flex-wrap items-center gap-3">
                  <RowHead locale={locale} favorite={favorite} row={row}>
                    <p className="text-muted-foreground truncate text-xs">
                      {t("favoritesInGroups", { groups: groupNames.join(", ") })}
                    </p>
                  </RowHead>
                  <Organise
                    channelId={channelId}
                    name={row.name}
                    memberships={memberships}
                    groups={favoriteGroups}
                    defaultName={defaultName}
                    returnTo={returnTo}
                  />
                  <a
                    href={`${hrefFor(locale as Locale, confirmView(returnTo, channelId))}#confirm-${channelId}`}
                    aria-label={t("favoritesRemoveAllOf", { channel: row.name })}
                    className={smallButton}
                  >
                    {t("favoritesRemoveAll")}
                  </a>
                </div>

                {confirming === channelId ? (
                  <section
                    id={`confirm-${channelId}`}
                    aria-labelledby={`confirm-title-${channelId}`}
                    className="border-destructive mt-3 rounded-xl border px-4 py-3"
                  >
                    <h2 id={`confirm-title-${channelId}`} className="font-medium">
                      {t("favoritesConfirmTitle", { channel: row.name })}
                    </h2>
                    <p className="text-muted-foreground mt-1 text-sm">
                      {t("favoritesConfirmBody", {
                        count: memberships.length,
                        groups: groupNames.join(", "),
                      })}
                    </p>
                    <div className="mt-3 flex flex-wrap items-center gap-3">
                      {/* The ids of the memberships listed above, and only those:
                          one added from another device since is not what was
                          confirmed (FO-12). */}
                      <form action={removeFromAllFavorites}>
                        <input type="hidden" name="channelId" value={channelId} />
                        {memberships.map((membership) => (
                          <input
                            key={membership.id}
                            type="hidden"
                            name="favoriteId"
                            value={membership.id}
                          />
                        ))}
                        <input type="hidden" name="returnTo" value={returnTo} />
                        <PendingButton
                          label={t("favoritesConfirm")}
                          pendingLabel={t("favoritesConfirmPending")}
                          ariaLabel={t("favoritesRemoveAllOf", { channel: row.name })}
                          className="bg-destructive h-9 rounded-lg px-3 text-sm font-medium text-white disabled:opacity-60"
                        />
                      </form>
                      <a href={here()} className="text-sm underline underline-offset-4">
                        {t("favoritesCancel")}
                      </a>
                    </div>
                  </section>
                ) : null}
              </li>
            );
          })}
        </ul>
      ) : (
        <>
          <p className="text-muted-foreground mt-6 text-sm">{t("favoritesGroupOrderHint")}</p>
          {elsewhere > 0 ? (
            <p className="text-muted-foreground mt-1 text-sm">
              {t("catalogueFavoritesElsewhere", { count: elsewhere })}
            </p>
          ) : null}
          <ul aria-label={t("favoritesListLabel")} className={listClass}>
            {view.rows.map((favorite, index) => {
              if (!activeGroup) return null;
              const row = rowOf(favorite.channel_id, index + 1);
              const memberships = allFavorites.filter(
                (candidate) =>
                  candidate.source_id === source.id &&
                  candidate.channel_id === favorite.channel_id,
              );
              const steps = [
                {
                  direction: "up",
                  label: t("favoritesMoveUp"),
                  aria: t("favoritesMoveUpOf", { channel: row.name }),
                  disabled: index === 0,
                },
                {
                  direction: "down",
                  label: t("favoritesMoveDown"),
                  aria: t("favoritesMoveDownOf", { channel: row.name }),
                  disabled: index === view.rows.length - 1,
                },
              ] as const;

              return (
                <li key={favorite.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <RowHead locale={locale} favorite={favorite} row={row} />

                  {steps.map((step) => (
                    <form key={step.direction} action={moveFavorite}>
                      <input type="hidden" name="favoriteId" value={favorite.id} />
                      <input type="hidden" name="groupId" value={activeGroup.id} />
                      <input type="hidden" name="direction" value={step.direction} />
                      <input type="hidden" name="returnTo" value={returnTo} />
                      <button
                        type="submit"
                        disabled={step.disabled}
                        aria-label={step.aria}
                        className={smallButton}
                      >
                        {step.label}
                      </button>
                    </form>
                  ))}

                  <Organise
                    channelId={favorite.channel_id}
                    name={row.name}
                    memberships={memberships}
                    groups={favoriteGroups}
                    defaultName={defaultName}
                    returnTo={returnTo}
                  />

                  {/* This membership only, no confirmation: the others stay
                      (FO-09). */}
                  <form action={removeMembership}>
                    <input type="hidden" name="favoriteId" value={favorite.id} />
                    <input type="hidden" name="returnTo" value={returnTo} />
                    <button
                      type="submit"
                      aria-label={t("favoritesRemoveFromGroupOf", {
                        channel: row.name,
                        group: groupLabel(activeGroup, defaultName),
                      })}
                      className={smallButton}
                    >
                      {t("favoritesRemoveFromGroup")}
                    </button>
                  </form>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}

/**
 * What a row knows about its channel (S11-05). `name` is the channel's, or a
 * stand-in naming the row's position, for the labels of its controls.
 */
type Row =
  | { state: "resolved"; channel: Channel; name: string }
  | { state: "unavailable" | "gone"; name: string };

/**
 * The left part of a row: logo and name opening the player — or, for a channel
 * that could not be named, a blank tile and a sentence, and no link at all: a
 * play link to a channel nobody could confirm exists is a promise this page
 * cannot keep. `children` goes under the name either way.
 */
async function RowHead({
  locale,
  favorite,
  row,
  children,
}: {
  locale: string;
  favorite: Favorite;
  row: Row;
  children?: ReactNode;
}) {
  if (row.state === "resolved") {
    return (
      <>
        <Logo channel={row.channel} />
        <div className="min-w-0 flex-1">
          <ChannelLink locale={locale} favorite={favorite} channel={row.channel} />
          {children}
        </div>
      </>
    );
  }

  const t = await getTranslations("App");
  const [label, hint] =
    row.state === "unavailable"
      ? [t("favoritesDetailsUnavailable"), t("favoritesDetailsUnavailableHint")]
      : [t("favoritesChannelGone"), t("favoritesChannelGoneHint")];
  return (
    <>
      <div aria-hidden="true" className="bg-muted size-10 shrink-0 rounded-lg" />
      <div className="min-w-0 flex-1">
        <p className="text-muted-foreground truncate font-medium">{label}</p>
        <p className="text-muted-foreground truncate text-xs">{hint}</p>
        {children}
      </div>
    </>
  );
}

/** The channel's name, opening its own source's page — which has the player. */
function ChannelLink({
  locale,
  favorite,
  channel,
}: {
  locale: string;
  favorite: Favorite;
  channel: Channel;
}) {
  return (
    <a
      href={hrefFor(
        locale as Locale,
        `/app/sources/${favorite.source_id}/channels?play=${channel.id}`,
      )}
      className="block truncate font-medium underline-offset-4 hover:underline"
    >
      {channel.name}
    </a>
  );
}

/**
 * "Organise": the groups a favourite channel is in, each a toggle (US-022).
 *
 * The same gesture as the starred star on the channels page, drawn the same
 * way — a native disclosure holding one form per group — so that it needs no
 * JavaScript. Ticking files the channel into one more group; unticking removes
 * that membership only.
 */
async function Organise({
  channelId,
  name: channelName,
  memberships,
  groups,
  defaultName,
  returnTo,
}: {
  /** By id: organising works on a row whose channel could not be named. */
  channelId: string;
  name: string;
  memberships: Favorite[];
  groups: FavoriteGroup[];
  defaultName: string;
  returnTo: string;
}) {
  const t = await getTranslations("App");
  if (groups.length === 0) return null;

  return (
    <details className="relative shrink-0">
      <summary
        aria-label={t("favoritesOrganiseOf", { channel: channelName })}
        className="text-muted-foreground hover:text-foreground cursor-pointer list-none rounded-lg border px-3 py-1.5 text-sm [&::-webkit-details-marker]:hidden"
      >
        {t("favoritesOrganise")}
      </summary>
      <div className="border-border bg-background absolute right-0 z-20 mt-1 w-60 rounded-xl border p-1 shadow-lg">
        <ul>
          {groups.map((group) => {
            const membership = memberships.find((candidate) => candidate.group_id === group.id);
            const name = groupLabel(group, defaultName);
            return (
              <li key={group.id}>
                <form action={membership ? removeMembership : addMembership}>
                  {membership ? (
                    <input type="hidden" name="favoriteId" value={membership.id} />
                  ) : (
                    <>
                      <input type="hidden" name="channelId" value={channelId} />
                      <input type="hidden" name="groupId" value={group.id} />
                    </>
                  )}
                  <input type="hidden" name="returnTo" value={returnTo} />
                  <button
                    type="submit"
                    aria-pressed={membership !== undefined}
                    aria-label={
                      membership
                        ? t("favoritesMemberOf", { channel: channelName, group: name })
                        : t("favoritesNotMemberOf", { channel: channelName, group: name })
                    }
                    className="hover:bg-secondary/60 flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm"
                  >
                    <span
                      aria-hidden="true"
                      className={membership ? "text-primary" : "text-transparent"}
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

/**
 * What an action could not confirm, said after the page has re-read.
 *
 * A partial removal names the groups that **really** remain — the memberships
 * the action reported as left, intersected with this fresh read — and offers to
 * retry those, and only those (FO-06, FO-12). When the re-read finds none left,
 * the lost answers had been removals after all (FO-08) and there is nothing to
 * say. "Close" drops the message and nothing else: the channel stays where the
 * read put it, which is not a success either (FO-07).
 */
async function Notice({
  notice,
  favorites,
  channelName,
  groupName,
  returnTo,
  closeHref,
}: {
  notice: FavoritesNotice | null;
  favorites: Favorite[];
  channelName: (channelId: string) => string | undefined;
  groupName: (groupId: string) => string;
  returnTo: string;
  closeHref: string;
}) {
  if (!notice) return null;
  const t = await getTranslations("App");
  const box = "border-border mt-6 rounded-xl border px-4 py-3";

  if (notice.kind !== "removal-partial") {
    const [title, hint] =
      notice.kind === "move-partial"
        ? [t("favoritesMovePartial"), t("favoritesMovePartialHint")]
        : [t("favoritesUnconfirmed"), t("favoritesUnconfirmedHint")];
    return (
      <div role="status" className={box}>
        <p className="font-medium">{title}</p>
        <p className="text-muted-foreground mt-1 text-sm">{hint}</p>
        <a href={closeHref} className="mt-2 inline-block text-sm underline underline-offset-4">
          {t("favoritesDismiss")}
        </a>
      </div>
    );
  }

  const remaining = stillRemaining(
    notice.left,
    favorites.filter((favorite) => favorite.channel_id === notice.channelId),
  );
  if (remaining.length === 0) return null;

  const channel = channelName(notice.channelId) ?? t("favoritesThisChannel");
  return (
    <div role="status" className={box}>
      <p className="font-medium">{t("favoritesRemovalPartial")}</p>
      <p className="text-muted-foreground mt-1 text-sm">
        {t("favoritesRemovalPartialBody", {
          channel,
          count: remaining.length,
          groups: remaining.map((favorite) => groupName(favorite.group_id)).join(", "),
        })}
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <form action={removeFromAllFavorites}>
          <input type="hidden" name="channelId" value={notice.channelId} />
          {remaining.map((favorite) => (
            <input key={favorite.id} type="hidden" name="favoriteId" value={favorite.id} />
          ))}
          <input type="hidden" name="returnTo" value={returnTo} />
          <PendingButton
            label={t("favoritesRetry")}
            pendingLabel={t("favoritesRetryPending")}
            ariaLabel={t("favoritesRetryOf", { channel })}
            className="bg-secondary text-secondary-foreground h-9 rounded-lg px-3 text-sm font-medium disabled:opacity-60"
          />
        </form>
        <a href={closeHref} className="text-sm underline underline-offset-4">
          {t("favoritesDismiss")}
        </a>
      </div>
    </div>
  );
}

/**
 * A channel logo, or the space one would have taken.
 *
 * **No fallback image, ever** (AGENTS.md §1): this product ships no bouquet
 * artwork, and a placeholder that looked like a logo would be a mark we invented
 * for somebody else's catalogue.
 */
function Logo({ channel }: { channel: Channel }) {
  if (!channel.logo_url) {
    return <div className="bg-muted size-10 shrink-0 rounded-lg" />;
  }
  // Not `next/image`: it needs its remote hosts configured one domain at a time,
  // and there is no list of them here — it is whatever panel each person
  // subscribes to.
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={channel.logo_url}
      alt=""
      loading="lazy"
      className="size-10 shrink-0 rounded-lg object-contain"
    />
  );
}

/** The first value of a search parameter, or nothing. Arrays are not a filter. */
function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
