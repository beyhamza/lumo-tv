"use server";

import { getLocale } from "next-intl/server";
import { revalidatePath } from "next/cache";
import { redirect } from "@/i18n/navigation";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { isUuid } from "@/lib/sources/active-source";
import { rememberActiveSource } from "@/lib/sources/active-source-store";
import { sourceSwitchTarget } from "@/lib/sources/switch-target";
import { requireSession } from "@/lib/session/session";

/**
 * Switching the source this browser browses (US-018, S8-E04).
 *
 * <h2>A Server Action because it is the only place that can</h2>
 *
 * The choice is a cookie, and a cookie can be written from a Server Action or a
 * Route Handler — not while rendering. So every row of the switcher is a
 * `<form>` posting here, which is also what makes it work with JavaScript
 * disabled, like everything else in this zone (`apps/web/AGENTS.md` §3).
 *
 * <h2>Applied at once, without a confirmation</h2>
 *
 * Nothing is lost by switching: favourites and progress stay attached to their
 * source and are there again on the way back. A confirmation would be asking
 * permission for something that has no consequence.
 *
 * <h2>The id is checked against `GET /sources`, not merely parsed</h2>
 *
 * A well-formed UUID that is not one of the caller's sources would not leak
 * anything — the API refuses every read of it — but it would store a choice the
 * resolver then has to ignore on every render. Refusing it here keeps the cookie
 * to values that were true when they were written. And when the list cannot be
 * fetched, nothing is written either: ownership could not be established, and
 * "I could not check" is not "yes".
 *
 * Nothing is reported back on a refusal, for the reason `favorites.ts` gives: the
 * page the user lands on re-renders the switcher from what is actually stored. A
 * check mark that did not move is a truthful answer.
 */
export async function selectSource(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();

  const requested = formData.get("sourceId");

  // Refusals land on My sources: it lists what the account really has, which is
  // the useful thing to look at when the source one asked for is not among them.
  if (!isUuid(requested)) {
    redirect({ href: "/app/sources", locale });
    return;
  }

  const sources = await fetched(() => api(session.accessToken).GET("/sources", {}));
  const chosen =
    sources.state === "ok"
      ? sources.data.items.find(
          (source) => source.id.toLowerCase() === requested.toLowerCase(),
        )
      : undefined;

  if (!chosen) {
    redirect({ href: "/app/sources", locale });
    return;
  }

  // The list's own spelling of the id, not the form's: what goes into the
  // cookie is a value the API produced.
  await rememberActiveSource(session.userId, chosen.id);

  // The rail is in the zone's layout and every page under it may depend on the
  // active source, so the whole subtree is stale — not one path.
  revalidatePath(`/${locale}/app`, "layout");
  redirect({ href: sourceSwitchTarget(formData.get("from"), locale, chosen.id), locale });
}
