import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { removeFavorite } from "@/actions/favorites";
import {
  FavoriteGroups,
  defaultGroupLabel,
  groupLabel,
} from "@/components/app/FavoriteGroups";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import type { Channel, Favorite, Source } from "@/lib/api/types";
import { resolveChannels } from "@/lib/catalogue/resolve-channels";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

/**
 * The favourites of the whole account (US-12, S6-09).
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
 * <h2>Three requests, and the third is the one that is easy to forget</h2>
 *
 * `GET /me/favorites` answers for the account but carries identifiers only.
 * Names and logos come from `GET /sources/{id}/channels?ids=` — one operation per
 * source — which is what `resolveChannels` is for, and where the `ids` ceiling has
 * to be a loop rather than a cut.
 *
 * `GET /sources` is the third, and it is not optional: the labels are in neither
 * of the other two answers, and without them this screen still would not say
 * where a channel comes from. That would be shipping the same defect under a new
 * route.
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

  const [favorites, groups, sources] = await Promise.all([
    api(session.accessToken).GET("/me/favorites", {}),
    api(session.accessToken).GET("/me/favorite-groups", {}),
    // The "From *[source]*" line, and nothing else. Neither of the other two
    // answers carries a source's label, and without it this screen would not say
    // where a channel comes from — which is the defect it exists to fix.
    api(session.accessToken).GET("/sources", {}),
  ]);

  if (!favorites.data || !groups.data) {
    return <Unavailable />;
  }

  const allFavorites = favorites.data.items;
  const favoriteGroups = groups.data.items;
  const sourceNames = new Map(
    (sources.data?.items ?? []).map((source: Source) => [source.id, source.label]),
  );

  // The server keeps the order the user arranged, and nothing here re-sorts it.
  const shown = activeGroupId
    ? allFavorites.filter((favorite) => favorite.group_id === activeGroupId)
    : allFavorites;

  // One request per source, in batches of a hundred, in parallel. A group of
  // three hundred channels across three sources is not three requests, and a
  // `.slice()` here would truncate it with a `200` and no error.
  const byId = await resolveChannels(
    shown.map((favorite) => ({
      sourceId: favorite.source_id,
      channelId: favorite.channel_id,
    })),
    async (sourceId, ids, size) => {
      const answer = await api(session.accessToken).GET("/sources/{id}/channels", {
        params: { path: { id: sourceId }, query: { ids, size } },
      });
      return answer.data?.items ?? [];
    },
  );

  // Walked against the favourites' order rather than the answers'. The batches
  // came back in whatever order they came back in; this list is the user's.
  const rows = shown
    .map((favorite) => ({ favorite, channel: byId.get(favorite.channel_id) }))
    .filter((row): row is { favorite: Favorite; channel: Channel } => row.channel != null);

  const activeGroup = favoriteGroups.find((group) => group.id === activeGroupId);
  const returnTo = `/app/favorites${queryString({ group: activeGroupId })}`;

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">{t("favoritesTitle")}</h1>
      <p className="text-muted-foreground mt-2">{t("favoritesIntro")}</p>

      <FavoriteGroups
        groups={favoriteGroups}
        activeId={activeGroup?.id}
        hrefForGroup={(groupId) =>
          hrefFor(locale as Locale, `/app/favorites${queryString({ group: groupId })}`)
        }
        returnTo={returnTo}
        countInGroup={(groupId) =>
          allFavorites.filter((favorite) => favorite.group_id === groupId).length
        }
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
            target: defaultGroupLabel(
              favoriteGroups,
              t("catalogueFavoritesDefaultGroup"),
            ),
          })
        }
      />

      {rows.length === 0 ? (
        <div className="border-border mt-8 rounded-xl border border-dashed px-5 py-6">
          <p className="font-medium">
            {activeGroup
              ? t("favoritesGroupEmpty", {
                  group: groupLabel(activeGroup, t("catalogueFavoritesDefaultGroup")),
                })
              : t("favoritesEmpty")}
          </p>
          {/* Where starring happens, said rather than assumed: the star is on the
              channel list, and an empty screen that does not say where to start
              is a screen somebody leaves. */}
          <p className="text-muted-foreground mt-1 text-sm">{t("favoritesEmptyHint")}</p>
        </div>
      ) : (
        <ul
          aria-label={t("favoritesListLabel")}
          className="mt-8 divide-border divide-y rounded-xl border"
        >
          {rows.map(({ favorite, channel }) => (
            <li key={favorite.id} className="flex items-center gap-3 px-4 py-3">
              <Logo channel={channel} />

              <div className="min-w-0 flex-1">
                <a
                  href={hrefFor(
                    locale as Locale,
                    // Its own source's page, which already has the player, the
                    // categories and the rails. This screen lists and routes.
                    `/app/sources/${favorite.source_id}/channels?play=${channel.id}`,
                  )}
                  className="block truncate font-medium underline-offset-4 hover:underline"
                >
                  {channel.name}
                </a>
                {/* The line the phone has had since S4-04, and the reason this
                    screen exists: a group holds channels from two subscriptions,
                    so which one a channel came from is not obvious. */}
                <p className="text-muted-foreground truncate text-xs">
                  {t("favoritesFromSource", {
                    source: sourceNames.get(favorite.source_id) ?? t("favoritesUnknownSource"),
                  })}
                </p>
              </div>

              <form action={removeFavorite} className="shrink-0">
                <input type="hidden" name="favoriteId" value={favorite.id} />
                <input type="hidden" name="returnTo" value={returnTo} />
                <button
                  type="submit"
                  className="text-muted-foreground hover:text-foreground rounded-lg border px-3 py-1.5 text-sm"
                  aria-label={t("favoritesRemoveOf", { channel: channel.name })}
                >
                  {t("favoritesRemove")}
                </button>
              </form>
            </li>
          ))}
        </ul>
      )}
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

/** Only the parameters that have a value, so a bare URL stays bare. */
function queryString(values: Record<string, string | undefined>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) params.set(key, value);
  }
  const query = params.toString();
  return query ? `?${query}` : "";
}
