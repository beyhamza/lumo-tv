"use server";

import { getLocale } from "next-intl/server";
import { revalidatePath } from "next/cache";
import { redirect } from "@/i18n/navigation";
import { api, problemCode } from "@/lib/api/client";
import { requireSession } from "@/lib/session/session";
import { settingsSectionPath } from "@/lib/settings/sections";
import { isUuid } from "@/lib/sources/active-source";

/**
 * Signing another device out of the account (US-025, "Compte et appareils").
 *
 * A Server Action, for the reason that governs this whole zone: the access
 * token lives in an httpOnly cookie and must never be readable from client
 * JavaScript (`apps/web/AGENTS.md` §4). It also makes the form work with
 * JavaScript disabled, which is how the confirmation before it is built.
 *
 * <h2>What "revoke" does, in the contract's words</h2>
 *
 * `DELETE /me/devices/{id}` revokes that device's whole refresh-token chain:
 * it is signed out at its next call to the service, not this instant, and the
 * confirmation says exactly that rather than announcing a disconnection nobody
 * can observe (docs/design/0.2.0/settings.md).
 *
 * <h2>This device is not offered here</h2>
 *
 * The contract allows revoking the calling device and calls it a sign-out. The
 * page never offers it: the row marked "this device" has no revoke link, and
 * signing out of this browser is its own confirmation, through `signOut`,
 * which also clears the cookie. A revoke of one's own device would leave a
 * cookie holding dead tokens, which the proxy would only discover at the next
 * refresh. The id arrives from a hidden field, though, so nothing here relies
 * on that: a forged form revoking its own device gets the contract's outcome.
 *
 * <h2>A revocation that failed is said, not swallowed</h2>
 *
 * Only a revocation the server **confirmed** returns to the plain list. Anything
 * else returns to the confirmation, which says it failed and offers the same two
 * buttons again — the device is still there, and the user must not have to work
 * that out from a list that did not change (same rule as `deleteSource`).
 *
 * `404 DEVICE_NOT_FOUND` counts as confirmed: the device is gone — revoked from
 * the phone a minute ago, or by a double submit — which is what was asked for.
 */
export async function revokeDevice(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const id = String(formData.get("id") ?? "");
  const account = settingsSectionPath("account");

  // The id ends up in a redirect target below. It came from a hidden field,
  // which is the browser's to rewrite, so it is a UUID or it goes nowhere.
  if (!isUuid(id)) {
    redirect({ href: account, locale });
    return;
  }

  let revoked = false;
  try {
    const result = await api(session.accessToken).DELETE("/me/devices/{id}", {
      params: { path: { id } },
    });
    revoked = !result.error || problemCode(result.error) === "DEVICE_NOT_FOUND";
  } catch {
    revoked = false;
  }

  if (!revoked) {
    // Nothing is revalidated: nothing changed.
    redirect({
      href: `${account}?confirm=revoke&device=${id}&revoke=failed#revoke-confirmation`,
      locale,
    });
    return;
  }

  revalidatePath(`/${locale}${account}`);
  redirect({ href: account, locale });
}
