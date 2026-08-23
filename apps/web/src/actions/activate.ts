"use server";

import { getLocale } from "next-intl/server";
import { redirect } from "@/i18n/navigation";
import { normaliseActivationCode } from "@/lib/activation/code";
import { api, problemCode } from "@/lib/api/client";
import { getSession } from "@/lib/session/session";

/**
 * Approves a television's activation code (US-05, RFC 8628).
 *
 * A Server Action taking `FormData`, which is what makes `/activate` work with
 * JavaScript disabled: the browser posts the form, this runs, and the answer is
 * an HTTP redirect. There is no client component on that page at all.
 *
 * The result is carried back in the query string rather than in component state
 * for the same reason — state needs hydration, a redirect does not.
 */
export async function approveDeviceCode(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const rawCode = String(formData.get("user_code") ?? "");
  const code = normaliseActivationCode(rawCode);

  const session = await getSession();

  if (!session) {
    // Keep the code through the sign-in detour: whoever is standing in front of
    // their television should not have to read it off the screen twice.
    redirect({
      href: `/login?next=${encodeURIComponent(`/activate?code=${code}`)}`,
      locale,
    });
    return;
  }

  if (code.length === 0) {
    redirect({ href: "/activate?error=VALIDATION_FAILED", locale });
    return;
  }

  // Only the failure half of the response is read: approval answers 204, so
  // there is no body to look at.
  let result: { error?: unknown };

  try {
    result = await api(session.accessToken).POST("/auth/device/approve", {
      body: { user_code: code },
    });
  } catch {
    redirect({ href: `/activate?code=${code}&error=NETWORK`, locale });
    return;
  }

  if (result.error) {
    const failure = problemCode(result.error) ?? "generic";
    redirect({ href: `/activate?code=${code}&error=${failure}`, locale });
    return;
  }

  redirect({ href: "/activate?status=ok", locale });
}
