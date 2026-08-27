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
 * <h2>The default group is not named here</h2>
 *
 * `group_id` is deliberately never sent. The contract creates the account's
 * default group on the first add and names it `Favorites`, in English, because
 * `FavoriteGroup` carries no stable identifier a client could translate on
 * (`docs/design/api-gaps.md`, open decision 2). Choosing a group of our own here
 * would be this layer settling a question that is still open — and the wrong
 * layer to settle it in, since the phone and the television have to agree with
 * whatever is chosen.
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

  await api(session.accessToken).POST("/me/favorites", {
    body: { channel_id: channelId },
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
