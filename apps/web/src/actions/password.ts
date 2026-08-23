"use server";

import { getLocale } from "next-intl/server";
import { redirect } from "@/i18n/navigation";
import { api, problemCode } from "@/lib/api/client";

/**
 * Email verification and password reset — the two flows a user enters from a
 * link in their inbox (US-01, US-02).
 *
 * Server Actions posting `FormData`, like `/activate`, so these pages need no
 * client JavaScript at all: the browser posts, this runs, the answer is an HTTP
 * redirect carrying the outcome in the query string.
 *
 * All three actions are deliberately silent about what exists. `requestReset`
 * always reports success — the contract answers `202` whether or not the email
 * is registered, and this layer must not undo that by rendering a different
 * page for a known address.
 */

/**
 * Confirms an email address.
 *
 * A Server Action rather than a call during render, and that is the whole
 * reason `/verify-email` shows a button instead of verifying on arrival: the
 * token is single-use, and mail providers fetch the links they deliver to scan
 * them. Verifying on GET means a scanner burns the token before the user clicks,
 * and the person who actually opens the link is told it is invalid.
 */
export async function confirmEmail(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const token = String(formData.get("token") ?? "").trim();

  if (!token) {
    redirect({ href: "/verify-email?error=VERIFICATION_TOKEN_INVALID", locale });
    return;
  }

  let result: { error?: unknown };
  try {
    result = await api().GET("/auth/verify-email", {
      params: { query: { token } },
    });
  } catch {
    redirect({ href: `/verify-email?token=${encodeURIComponent(token)}&error=NETWORK`, locale });
    return;
  }

  if (result.error) {
    const failure = problemCode(result.error) ?? "generic";
    redirect({ href: `/verify-email?error=${failure}`, locale });
    return;
  }

  redirect({ href: "/verify-email?status=ok", locale });
}

/** Asks for a reset email. Answers the same way whatever the address is. */
export async function requestReset(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const email = String(formData.get("email") ?? "").trim();

  if (!email) {
    redirect({ href: "/forgot-password?error=VALIDATION_FAILED", locale });
    return;
  }

  try {
    const result = await api().POST("/auth/password/forgot", { body: { email } });
    // Rate limiting is the one failure worth surfacing: it is about this
    // browser, not about the account, so it leaks nothing.
    if (problemCode(result.error) === "RATE_LIMITED") {
      redirect({ href: "/forgot-password?error=RATE_LIMITED", locale });
      return;
    }
  } catch {
    redirect({ href: "/forgot-password?error=NETWORK", locale });
    return;
  }

  redirect({ href: "/forgot-password?status=sent", locale });
}

/** Sets a new password from the token in the email link. */
export async function resetPassword(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const token = String(formData.get("token") ?? "").trim();
  const password = String(formData.get("password") ?? "");

  const back = (error: string) =>
    `/reset-password?token=${encodeURIComponent(token)}&error=${error}`;

  if (!token) {
    redirect({ href: "/reset-password?error=RESET_TOKEN_INVALID", locale });
    return;
  }
  // The same rule the field advertises. A `minLength` attribute is a courtesy
  // to the browser, never a validation (US-01).
  if (password.length < 10) {
    redirect({ href: back("PASSWORD_TOO_WEAK"), locale });
    return;
  }

  let result: { error?: unknown };
  try {
    result = await api().POST("/auth/password/reset", { body: { token, password } });
  } catch {
    redirect({ href: back("NETWORK"), locale });
    return;
  }

  if (result.error) {
    redirect({ href: back(problemCode(result.error) ?? "generic"), locale });
    return;
  }

  // Every session of the account has just been revoked server-side, this
  // browser's included, so there is nowhere to go but sign-in.
  redirect({ href: "/login?status=password-reset", locale });
}
