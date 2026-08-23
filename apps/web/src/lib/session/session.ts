import "server-only";

import { cookies } from "next/headers";
import { getLocale } from "next-intl/server";
import { redirect } from "@/i18n/navigation";
import { sessionCookieName } from "@/lib/env";
import {
  sealSession,
  sessionCookieOptions,
  unsealSession,
  type SessionPayload,
} from "./cookie";

/**
 * Reads the session in a Server Component, a Server Action or a Route Handler.
 *
 * Read-only on purpose. Refreshing happens in `proxy.ts`, before rendering
 * starts, because a Server Component cannot set a cookie — Next throws if it
 * tries. By the time this is called the cookie is already fresh.
 */
export async function getSession(): Promise<SessionPayload | null> {
  const store = await cookies();
  return unsealSession(store.get(sessionCookieName())?.value);
}

/**
 * The session, or a redirect to sign-in.
 *
 * The proxy already guards `/app`, so in practice this never redirects. It
 * exists anyway: the guarantee that a page under `/app` cannot render without a
 * session should hold in the page's own types, not only in a matcher somebody
 * could narrow by accident.
 */
export async function requireSession(): Promise<SessionPayload> {
  const session = await getSession();
  if (session) return session;

  const locale = await getLocale();
  redirect({ href: "/login", locale });

  // Unreachable: redirect throws to interrupt rendering. TypeScript cannot see
  // that through next-intl's wrapper, so this line is what lets the signature
  // stay `Promise<SessionPayload>` — which is the point of the function.
  throw new Error("redirect did not interrupt rendering");
}

/** Called by the sign-in and registration actions. */
export async function openSession(session: SessionPayload): Promise<void> {
  const store = await cookies();
  const options = sessionCookieOptions();
  store.set(options.name, await sealSession(session), options);
}

export async function closeSession(): Promise<void> {
  const store = await cookies();
  const { name, ...options } = sessionCookieOptions();
  // Overwrite with an empty, immediately-expired cookie rather than only
  // deleting: the delete alone leaves the old value in place on some proxies
  // that cache Set-Cookie handling.
  store.set(name, "", { ...options, maxAge: 0 });
}
