"use server";

import { getLocale } from "next-intl/server";
import { revalidatePath } from "next/cache";
import { redirect } from "@/i18n/navigation";
import { locales } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { safeRedirectTarget } from "@/lib/security/redirect-target";
import { requireSession } from "@/lib/session/session";
import {
  performWrite,
  readAdd,
  readRemove,
  returnWithNotice,
  type WatchlistWrite,
  type WriteAnswer,
  type WriteOutcome,
} from "@/lib/watchlist/write";

/**
 * Adding to and removing from "My list" (US-022, S11-04).
 *
 * Forms posting to Server Actions, like the favourites' star and for the same
 * reasons (`actions/favorites.ts`): the access token never reaches client
 * JavaScript, and the buttons work with JavaScript disabled.
 *
 * <h2>The key comes from the form, never from here</h2>
 *
 * Each form carries the `Idempotency-Key` minted when it was rendered
 * (`lib/watchlist/write.ts`). The action forwards it unchanged, so a double
 * submission is one intention and the server replays it instead of acting
 * twice (`adr/0011`, ruling 6). A missing or malformed key sends nothing.
 *
 * <h2>Unknown is said, the rest is shown</h2>
 *
 * An accepted change, a replay, an add of something already listed and a
 * removal of something already gone all land back on the same view, which
 * re-reads the list and therefore shows the truth: nothing to announce. Any
 * other answer — or none — means the change may or may not have happened, and
 * the view comes back with "Change not confirmed". It re-reads the list too,
 * and its new form is a new intention with a new key; nothing is retried here.
 */
export async function addToWatchlist(formData: FormData): Promise<void> {
  const session = await requireSession();
  const outcome = await performWrite(readAdd(formData), (write) =>
    send(session.accessToken, write),
  );
  await backToView(formData, outcome);
}

export async function removeFromWatchlist(formData: FormData): Promise<void> {
  const session = await requireSession();
  const outcome = await performWrite(readRemove(formData), (write) =>
    send(session.accessToken, write),
  );
  await backToView(formData, outcome);
}

async function send(accessToken: string, write: WatchlistWrite): Promise<WriteAnswer> {
  const client = api(accessToken);
  const header = { "Idempotency-Key": write.key };

  if (write.kind === "add") {
    const { response, error } = await client.POST("/me/watchlist", {
      params: { header },
      body: { content_type: write.contentType, content_id: write.contentId },
    });
    return { status: response.status, error };
  }

  const { response, error } = await client.DELETE("/me/watchlist/{id}", {
    params: { header, path: { id: write.itemId } },
  });
  return { status: response.status, error };
}

/**
 * Back to the exact view the form was on — the list with its filter and page,
 * or a sheet with its own context — validated like every redirect target.
 */
async function backToView(formData: FormData, outcome: WriteOutcome): Promise<void> {
  const locale = await getLocale();
  const target = safeRedirectTarget(formData.get("returnTo"), locales, "/app/watchlist");

  // Both the list and the sheet the change was made from show membership.
  revalidatePath(`/${locale}${target.split(/[?#]/, 1)[0]}`);
  revalidatePath(`/${locale}/app/watchlist`);
  redirect({ href: returnWithNotice(target, outcome), locale });
}
