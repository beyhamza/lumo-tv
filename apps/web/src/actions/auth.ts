"use server";

import { getLocale, getTranslations } from "next-intl/server";
import { redirect } from "@/i18n/navigation";
import { api, problemCode } from "@/lib/api/client";
import type { SessionPayload } from "@/lib/session/cookie";
import { safeRedirectTarget } from "@/lib/security/redirect-target";
import { closeSession, getSession, openSession } from "@/lib/session/session";
import { routing } from "@/i18n/routing";

/**
 * Sign-in, registration and sign-out.
 *
 * Server Actions rather than a browser-side call to the API, for the reason that
 * governs this whole zone: the tokens must never be readable from client-side
 * JavaScript. A `fetch` from the browser would hand the refresh token to a
 * script; here the exchange happens on the server and the browser only ever sees
 * an httpOnly cookie (docs/architecture.md §5).
 *
 * A second benefit falls out of it: the forms work with JavaScript disabled.
 * Next posts them to the action and the redirect that follows is a plain HTTP
 * redirect.
 */

export type AuthFormState = {
  /** Already translated, ready to render. Never a raw API message. */
  error?: string;
};

/**
 * How this browser identifies itself to the API.
 *
 * The contract requires a device on sign-in and registration
 * (docs/domain-model.md, `device`): a session is bound to one, and that is what
 * lets a user list and revoke their sessions from the account page.
 */
const WEB_DEVICE = {
  platform: "WEB" as const,
  name: "lumo.tv",
  model: null,
  app_version: "0.1.0",
};

/**
 * Error codes this UI has a translated message for.
 *
 * The contract is explicit that `code` is the only field a client may branch on
 * and that unknown values must degrade gracefully rather than crash — new codes
 * can appear within v1 — hence the fallback rather than an exhaustive switch.
 */
const TRANSLATED_CODES = [
  "VALIDATION_FAILED",
  "INVALID_CREDENTIALS",
  "EMAIL_ALREADY_REGISTERED",
  "PASSWORD_TOO_WEAK",
  "RATE_LIMITED",
  "UNAUTHENTICATED",
] as const;

type TranslatedCode = (typeof TRANSLATED_CODES)[number];

function translatedCode(error: unknown): TranslatedCode | null {
  const code = problemCode(error);
  return code && (TRANSLATED_CODES as readonly string[]).includes(code)
    ? (code as TranslatedCode)
    : null;
}

export async function signIn(
  _previous: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const locale = await getLocale();
  const t = await getTranslations({ locale, namespace: "Errors" });

  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const next = safeRedirectTarget(formData.get("next"), routing.locales);

  if (!email || !password) return { error: t("VALIDATION_FAILED") };

  let result;
  try {
    result = await api().POST("/auth/login", {
      body: { email, password, device: WEB_DEVICE },
    });
  } catch {
    // openapi-fetch resolves HTTP errors; only a transport failure throws.
    return { error: t("network") };
  }

  if (!result.data) {
    const code = translatedCode(result.error);
    return { error: code ? t(code) : t("generic") };
  }

  await openSession(sessionFrom(result.data));
  redirect({ href: next, locale });

  // Unreachable: redirect throws. TypeScript cannot see that through
  // next-intl's wrapper, so the return is here to satisfy the signature.
  return {};
}

export async function signUp(
  _previous: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const locale = await getLocale();
  const t = await getTranslations({ locale, namespace: "Errors" });

  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const displayName = String(formData.get("display_name") ?? "").trim();

  // The same rule the form enforces before submitting (US-01): the button stays
  // disabled below ten characters. Repeated here because a disabled button is a
  // convenience, not a validation.
  if (!email) return { error: t("VALIDATION_FAILED") };
  if (password.length < 10) return { error: t("PASSWORD_TOO_WEAK") };

  let result;
  try {
    result = await api().POST("/auth/register", {
      body: {
        email,
        password,
        display_name: displayName || null,
        locale: locale as "fr" | "en",
        device: WEB_DEVICE,
      },
    });
  } catch {
    return { error: t("network") };
  }

  if (!result.data) {
    const code = translatedCode(result.error);
    return { error: code ? t(code) : t("generic") };
  }

  await openSession(sessionFrom(result.data));
  redirect({ href: "/app", locale });

  // Unreachable, as above.
  return {};
}

export async function signOut(): Promise<void> {
  const locale = await getLocale();
  const session = await getSession();

  if (session) {
    // Best effort: ask the server to revoke the refresh token. The local
    // session goes either way — a sign-out that fails because the network is
    // down must still sign the user out of this browser.
    try {
      await api(session.accessToken).POST("/auth/logout", {
        body: { refresh_token: session.refreshToken },
      });
    } catch {
      // Intentionally ignored.
    }
  }

  await closeSession();
  redirect({ href: "/login", locale });
}

/** Shapes the contract's `AuthSession` into what the cookie stores. */
function sessionFrom(authSession: {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  device_id: string;
  user: { id: string; email: string };
}): SessionPayload {
  return {
    accessToken: authSession.access_token,
    refreshToken: authSession.refresh_token,
    accessTokenExpiresAt: Math.floor(Date.now() / 1000) + authSession.expires_in,
    userId: authSession.user.id,
    deviceId: authSession.device_id,
    email: authSession.user.email,
  };
}
