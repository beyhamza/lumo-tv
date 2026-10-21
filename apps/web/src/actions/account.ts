"use server";

import { getLocale } from "next-intl/server";
import { redirect } from "@/i18n/navigation";
import { api, problemCode } from "@/lib/api/client";
import { deletionOutcome } from "@/lib/settings/account-deletion";
import { closeSession, requireSession } from "@/lib/session/session";
import { settingsSectionPath } from "@/lib/settings/sections";

/**
 * Deleting the account (S16B-02, `POST /me/delete`, ADR 0014).
 *
 * A Server Action like every form of this zone: the password and the access
 * token stay on the server, and the confirmation works without JavaScript.
 *
 * Only a deletion the server **confirmed** ends the session here, and then it
 * ends it locally without calling `/auth/logout`: the refresh token went with
 * the account, so there is nothing left to revoke. Anything else returns to
 * the confirmation with the reason in the redirect (`apps/web/AGENTS.md` §4),
 * and says that nothing was deleted.
 *
 * `401` is the one refusal that also ends the session: the account is gone
 * already (deleted from another device a moment ago), or the session is.
 * Either way, this browser holds tokens for nothing.
 */
export async function deleteAccount(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const password = String(formData.get("password") ?? "");
  const account = settingsSectionPath("account");

  let outcome;
  if (!password) {
    outcome = deletionOutcome({ status: 403 });
  } else {
    try {
      const result = await api(session.accessToken).POST("/me/delete", {
        body: { password },
      });
      outcome = deletionOutcome({
        status: result.response.status,
        code: problemCode(result.error),
      });
    } catch {
      outcome = deletionOutcome({ status: 0 });
    }
  }

  if (outcome === "deleted" || outcome === "signed-out") {
    await closeSession();
    redirect({
      href: outcome === "deleted" ? "/login?status=account-deleted" : "/login",
      locale,
    });
    return;
  }

  redirect({
    href: `${account}?confirm=delete&delete=${outcome}#delete-confirmation`,
    locale,
  });
}
