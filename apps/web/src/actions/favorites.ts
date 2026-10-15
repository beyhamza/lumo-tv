"use server";

import { getLocale } from "next-intl/server";
import { revalidatePath } from "next/cache";
import { redirect } from "@/i18n/navigation";
import { locales } from "@/i18n/routing";
import { api, problemCode } from "@/lib/api/client";
import { executeRemoval, isUuid, readMembershipIds } from "@/lib/favorites/global-removal";
import { withNotice, type FavoritesNotice } from "@/lib/favorites/notice";
import { executeMoves, planVisibleStep, type Direction } from "@/lib/favorites/visible-order";
import { safeRedirectTarget } from "@/lib/security/redirect-target";
import { requireSession } from "@/lib/session/session";

/**
 * Starring and unstarring a channel, from the catalogue.
 *
 * <h2>A form, not a fetch</h2>
 *
 * Two Server Actions rather than one client-side toggle, for the reason that
 * governs this whole zone (`apps/web/AGENTS.md` §3 and §4): the access token
 * lives in an httpOnly cookie and never reaches client JavaScript. The star is
 * therefore a submit button inside a `<form>`, and it works with JavaScript
 * disabled — the page comes back with the star filled in.
 *
 * <h2>The group is chosen now, because the contract can say which is the default</h2>
 *
 * `group_id` used to be deliberately absent: `FavoriteGroup` carried no stable
 * identifier a client could translate on, so no client could tell which group was
 * the account's default, and choosing one here would have been this layer settling
 * a question the phone and the television also had to answer
 * (`docs/design/api-gaps.md`, decision 2).
 *
 * That decision is taken — `FavoriteGroup.is_default` — so a group is chosen on
 * the page and this action passes what it was given. Omitted, the field still
 * means what it always did: the server files it in the default group, creating
 * it on the first add.
 *
 * **Not a `<select>`, which an earlier version of this comment claimed.** A
 * dropdown on every row is fifty controls for a choice that is the same on all
 * of them. What the page draws instead is the phone's gesture: one press files
 * an unstarred channel, and a starred one opens the group list — see
 * `FavoriteControl`.
 *
 * <h2>Why nothing is reported back</h2>
 *
 * `FAVORITE_ALREADY_EXISTS` on an add and `FAVORITE_NOT_FOUND` on a remove both
 * mean the channel is already in the state the user asked for — a double
 * submission, or a second tab. There is nothing to say and nothing to do.
 *
 * The rest is silent too, and that is a choice rather than an oversight: the
 * page re-renders from `GET /me/favorites` on the way back, so the star shows
 * what is actually stored. A star that did not move is a truthful answer; an
 * error banner sitting next to a star that contradicts it is not.
 */
export async function addFavorite(formData: FormData): Promise<void> {
  const session = await requireSession();
  const channelId = String(formData.get("channelId") ?? "");
  const groupId = String(formData.get("groupId") ?? "");

  await api(session.accessToken).POST("/me/favorites", {
    body: {
      channel_id: channelId,
      // Absent rather than empty: the contract reads a missing `group_id` as
      // "the default group", and an empty string is a malformed uuid.
      ...(groupId ? { group_id: groupId } : {}),
    },
  });

  await backToCatalogue(formData);
}

/**
 * Creating, renaming and deleting a group.
 *
 * <h2>Deleting keeps the channels, and the page has to say so</h2>
 *
 * The server moves a deleted group's favourites into the default group rather
 * than removing them — deleting a shelf and throwing away the books are two
 * different actions. Nothing on screen distinguishes them, so the confirmation
 * does, with the count. That wording lives in the page; this action only carries
 * it out.
 *
 * <h2>Silent, like the star, and for the same reason</h2>
 *
 * The page re-renders from `GET /me/favorite-groups` on the way back, so what it
 * shows is what is stored. A name that did not change is a truthful answer; an
 * error banner beside a list that contradicts it is not.
 *
 * The one refusal worth naming is `FAVORITE_GROUP_NOT_DELETABLE`, and it cannot
 * be reached from here: the page draws no delete control on the default group.
 */
export async function createFavoriteGroup(formData: FormData): Promise<void> {
  const session = await requireSession();
  const name = String(formData.get("name") ?? "").trim();

  if (name.length > 0) {
    await api(session.accessToken).POST("/me/favorite-groups", {
      body: { name },
    });
  }

  await backToCatalogue(formData);
}

export async function renameFavoriteGroup(formData: FormData): Promise<void> {
  const session = await requireSession();
  const groupId = String(formData.get("groupId") ?? "");
  const name = String(formData.get("name") ?? "").trim();

  if (name.length > 0) {
    await api(session.accessToken).PATCH("/me/favorite-groups/{id}", {
      params: { path: { id: groupId } },
      body: { name },
    });
  }

  await backToCatalogue(formData);
}

export async function deleteFavoriteGroup(formData: FormData): Promise<void> {
  const session = await requireSession();
  const groupId = String(formData.get("groupId") ?? "");

  await api(session.accessToken).DELETE("/me/favorite-groups/{id}", {
    params: { path: { id: groupId } },
  });

  await backToCatalogue(formData);
}

export async function removeFavorite(formData: FormData): Promise<void> {
  const session = await requireSession();
  const favoriteId = String(formData.get("favoriteId") ?? "");

  await api(session.accessToken).DELETE("/me/favorites/{id}", {
    params: { path: { id: favoriteId } },
  });

  await backToCatalogue(formData);
}

/**
 * Returns to the exact catalogue view the star was clicked from.
 *
 * Category, search and page number all live in the URL on that screen, so
 * returning to a bare path would silently reset the three: starring a channel on
 * page 7 of "Sport" would land the user back on page 1 of everything.
 *
 * `returnTo` is validated like every other redirect target
 * ({@link safeRedirectTarget}). It arrives in a form field — which is to say
 * from the browser — and is not to be trusted just because this application is
 * what put it there.
 */
async function backToCatalogue(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const target = safeRedirectTarget(formData.get("returnTo"), locales, "/app/sources");

  // Without the query: `revalidatePath` takes a path, and this screen's
  // category, search and page number all live in the query string.
  revalidatePath(`/${locale}${target.split("?")[0]}`);
  // A notice from an earlier action on the favourites page is not about this
  // one, so it does not survive the round trip.
  redirect({ href: withNotice(target, null), locale });
}

/**
 * "Move up" / "Move down" on a favourite, in a group as the active source sees
 * it (US-022, S11-03; S11-00 G1 to G3).
 *
 * <h2>Planned against a fresh read, not against the page</h2>
 *
 * The form names a favourite and a direction — not positions. The group is
 * re-read here, and the visible step is translated into full-group moves against
 * **that** order (`lib/favorites/visible-order.ts`): whatever another device did
 * since the page was drawn is the order this applies to, so the last accepted
 * action decides (G3), and the other sources' favourites keep their indexes (G1).
 *
 * <h2>Not atomic, and not presented as such</h2>
 *
 * The moves go one `PATCH` at a time and stop at the first one not confirmed.
 * What succeeded stays; the page comes back re-read with "Order not fully
 * applied" — never a success (G2).
 */
export async function moveFavorite(formData: FormData): Promise<void> {
  const session = await requireSession();
  const client = api(session.accessToken);
  const favoriteId = formData.get("favoriteId");
  const groupId = formData.get("groupId");
  const direction = readDirection(formData.get("direction"));

  let notice: FavoritesNotice | null = null;
  if (isUuid(favoriteId) && isUuid(groupId) && direction) {
    const group = await attempt(() =>
      client.GET("/me/favorites", { params: { query: { groupId } } }),
    );
    if (!group?.data) {
      notice = { kind: "unconfirmed" };
    } else {
      const step = planVisibleStep(group.data.items, favoriteId, direction);
      if (step.kind === "moves") {
        const outcome = await executeMoves(step.moves, async (move) => {
          const { response } = await client.PATCH("/me/favorites/{id}", {
            params: { path: { id: move.id } },
            body: { position: move.position },
          });
          return response.status === 200;
        });
        if (outcome.kind === "partial") notice = { kind: "move-partial" };
      }
    }
  }

  await backToFavorites(formData, notice);
}

/**
 * "Remove from all my favourites", once confirmed (US-022, S11-03; G2,
 * FO-05 to FO-08, FO-12).
 *
 * Deletes exactly the memberships the confirmation listed — or, on a retry, the
 * ones the page found still remaining — one `DELETE` each, in sequence.
 * `404 FAVORITE_NOT_FOUND` is a membership already gone, hence done. Whatever
 * could not be confirmed travels back in the redirect; the page re-reads and
 * offers to retry those that really remain, and only those
 * (`lib/favorites/global-removal.ts`).
 */
export async function removeFromAllFavorites(formData: FormData): Promise<void> {
  const session = await requireSession();
  const client = api(session.accessToken);
  const channelId = formData.get("channelId");
  const ids = readMembershipIds(formData.getAll("favoriteId"));

  let notice: FavoritesNotice | null = null;
  if (isUuid(channelId) && ids.length > 0) {
    const outcome = await executeRemoval(ids, async (id) => {
      const { response, error } = await client.DELETE("/me/favorites/{id}", {
        params: { path: { id } },
      });
      return { status: response.status, error };
    });
    if (outcome.left.length > 0) {
      notice = { kind: "removal-partial", channelId, left: outcome.left };
    }
  }

  await backToFavorites(formData, notice);
}

/**
 * Removing one membership from the favourites page — "Remove from this group",
 * or unticking a group under "Organise". No confirmation: the other memberships
 * stay (FO-09). Already gone is done; anything else is said.
 */
export async function removeMembership(formData: FormData): Promise<void> {
  const session = await requireSession();
  const favoriteId = formData.get("favoriteId");

  let notice: FavoritesNotice | null = null;
  if (isUuid(favoriteId)) {
    const outcome = await executeRemoval([favoriteId], async (id) => {
      const { response, error } = await api(session.accessToken).DELETE(
        "/me/favorites/{id}",
        { params: { path: { id } } },
      );
      return { status: response.status, error };
    });
    if (outcome.left.length > 0) notice = { kind: "unconfirmed" };
  }

  await backToFavorites(formData, notice);
}

/**
 * Filing a favourite channel into one more group, from "Organise". Already in
 * that group (`409 FAVORITE_ALREADY_EXISTS`) is the state that was asked for.
 */
export async function addMembership(formData: FormData): Promise<void> {
  const session = await requireSession();
  const channelId = formData.get("channelId");
  const groupId = formData.get("groupId");

  let notice: FavoritesNotice | null = { kind: "unconfirmed" };
  if (isUuid(channelId) && isUuid(groupId)) {
    const answer = await attempt(() =>
      api(session.accessToken).POST("/me/favorites", {
        body: { channel_id: channelId, group_id: groupId },
      }),
    );
    const status = answer?.response.status;
    if (
      status === 201 ||
      (status === 409 && problemCode(answer?.error) === "FAVORITE_ALREADY_EXISTS")
    ) {
      notice = null;
    }
  }

  await backToFavorites(formData, notice);
}

/**
 * "Move earlier" / "Move later" on a group. The group list belongs to the
 * account and is not filtered, so this is one `PATCH` to the neighbouring index
 * — computed from a fresh read of the groups, not from the page.
 */
export async function moveFavoriteGroup(formData: FormData): Promise<void> {
  const session = await requireSession();
  const client = api(session.accessToken);
  const groupId = formData.get("groupId");
  const direction = readDirection(formData.get("direction"));

  let notice: FavoritesNotice | null = null;
  if (isUuid(groupId) && direction) {
    const groups = await attempt(() => client.GET("/me/favorite-groups", {}));
    if (!groups?.data) {
      notice = { kind: "unconfirmed" };
    } else {
      const ordered = [...groups.data.items].sort((a, b) => a.position - b.position);
      const from = ordered.findIndex((group) => group.id === groupId);
      const to = direction === "up" ? from - 1 : from + 1;
      if (from >= 0 && to >= 0 && to < ordered.length) {
        const answer = await attempt(() =>
          client.PATCH("/me/favorite-groups/{id}", {
            params: { path: { id: groupId } },
            body: { position: to },
          }),
        );
        if (answer?.response.status !== 200) notice = { kind: "unconfirmed" };
      }
    }
  }

  await backToFavorites(formData, notice);
}

function readDirection(value: FormDataEntryValue | null): Direction | null {
  return value === "up" || value === "down" ? value : null;
}

/** A request that threw — no response at all — is `null`, never a guess. */
async function attempt<T>(call: () => Promise<T>): Promise<T | null> {
  try {
    return await call();
  } catch {
    return null;
  }
}

/**
 * Back to the favourites view the form was on, which re-renders from
 * `GET /me/favorites` (G3: never local state), with the notice when something
 * was not confirmed.
 */
async function backToFavorites(
  formData: FormData,
  notice: FavoritesNotice | null,
): Promise<void> {
  const locale = await getLocale();
  const target = safeRedirectTarget(formData.get("returnTo"), locales, "/app/favorites");

  revalidatePath(`/${locale}${target.split(/[?#]/, 1)[0]}`);
  redirect({ href: withNotice(target, notice), locale });
}
