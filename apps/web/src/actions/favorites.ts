"use server";

import { getLocale } from "next-intl/server";
import { revalidatePath } from "next/cache";
import { redirect } from "@/i18n/navigation";
import { locales } from "@/i18n/routing";
import { api } from "@/lib/api/client";
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
  redirect({ href: target, locale });
}
