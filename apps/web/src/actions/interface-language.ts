"use server";

import { getLocale } from "next-intl/server";
import { cookies } from "next/headers";
import { redirect } from "@/i18n/navigation";
import { sessionCookieSecure } from "@/lib/env";
import {
  INTERFACE_LOCALE_COOKIE,
  INTERFACE_LOCALE_MAX_AGE_SECONDS,
  explicitLocale,
} from "@/lib/i18n/interface-locale";
import { settingsSectionPath } from "@/lib/settings/sections";

/**
 * Settings → Application: switch the interface language (S13-04, Q6).
 *
 * A Server Action posted by a plain form, so it works without JavaScript: it
 * remembers the choice for this browser (`lib/i18n/interface-locale.ts`) and
 * sends the visitor to the same section in the chosen language — the useful
 * path kept, the URL's language changed. No `PATCH /me`: the account's own
 * locale is not this choice (Q6).
 *
 * An unknown value changes nothing and returns to the section as it was.
 */
export async function chooseInterfaceLanguage(formData: FormData): Promise<void> {
  const current = await getLocale();
  const chosen = explicitLocale(formData.get("locale"));
  const section = settingsSectionPath("application");

  if (!chosen) {
    redirect({ href: section, locale: current });
    return;
  }

  (await cookies()).set(INTERFACE_LOCALE_COOKIE, chosen, {
    // Read by the proxy only; no script has a use for it.
    httpOnly: true,
    sameSite: "lax",
    secure: sessionCookieSecure(),
    path: "/",
    maxAge: INTERFACE_LOCALE_MAX_AGE_SECONDS,
  });

  redirect({ href: section, locale: chosen });
}
