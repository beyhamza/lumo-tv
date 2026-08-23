import createIntlMiddleware from "next-intl/middleware";
import { NextResponse, type NextRequest } from "next/server";
import { routing } from "@/i18n/routing";
import { sessionCookieName } from "@/lib/env";
import {
  isAccessTokenStale,
  sealSession,
  sessionCookieOptions,
  unsealSession,
} from "@/lib/session/cookie";
import { refreshSession } from "@/lib/session/refresh";

/**
 * Locale negotiation and session handling, before anything renders.
 *
 * This file is `proxy.ts` and not `middleware.ts`: Next.js 16 renamed the
 * convention and deprecated the old name (see
 * `node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/proxy.md`).
 * Same capabilities, and it now runs on the Node.js runtime by default, which is
 * what lets it decrypt the session cookie and call the API.
 *
 * It does three things, in this order:
 *
 * 1. **Locale.** next-intl decides which language a URL belongs to and
 *    redirects `/` to a prefixed path. Nothing else is allowed to guess.
 * 2. **Refresh.** If the access token is about to expire, it is rotated here —
 *    before rendering — because a Server Component cannot set a cookie. This is
 *    what "refresh côté serveur" means in practice, and it is why pages can
 *    treat the session as always fresh.
 * 3. **Protection.** A request for `/app` with no session is sent to sign-in
 *    with a `next` parameter, so the visitor lands back where they were going.
 *
 * Note what does NOT happen here: the marketing zone is untouched — no cookie
 * read, no API call, nothing that would make a static page dynamic.
 */
const handleI18n = createIntlMiddleware(routing);

export async function proxy(request: NextRequest): Promise<NextResponse> {
  const response = handleI18n(request);

  // A locale redirect ends the request; there is no session work to do on a
  // response the browser will immediately replace.
  if (isRedirect(response)) return response;

  const { pathname } = request.nextUrl;
  const locale = localeOf(pathname);
  const isProtected = isProtectedPath(pathname, locale);

  const raw = request.cookies.get(sessionCookieName())?.value;
  const session = await unsealSession(raw);

  if (!session) {
    return isProtected ? redirectToLogin(request, locale) : response;
  }

  if (!isAccessTokenStale(session)) return response;

  const result = await refreshSession(session);

  switch (result.status) {
    case "rotated": {
      const options = sessionCookieOptions();
      response.cookies.set(options.name, await sealSession(result.session), options);
      return response;
    }

    case "rejected": {
      // The refresh token is spent, revoked, or was replayed — in which case the
      // server has already revoked every token this browser holds. Clear the
      // cookie so the visitor is not stuck retrying a dead session.
      const { name, ...options } = sessionCookieOptions();
      const cleared = isProtected ? redirectToLogin(request, locale) : response;
      cleared.cookies.set(name, "", { ...options, maxAge: 0 });
      return cleared;
    }

    case "unavailable":
      // The API is down or unreachable. The session is intact and the page will
      // render its own "service unavailable" state. Signing the user out here
      // would turn a transient outage into a lost session.
      return response;
  }
}

function isRedirect(response: NextResponse): boolean {
  return response.status >= 300 && response.status < 400;
}

function localeOf(pathname: string): string {
  const candidate = pathname.split("/")[1];
  return routing.locales.includes(candidate as (typeof routing.locales)[number])
    ? candidate
    : routing.defaultLocale;
}

/**
 * `/app` and everything under it.
 *
 * `/activate` is deliberately NOT protected: the television's QR code sends
 * people straight there, and a redirect through sign-in before they have even
 * read what the page is for loses them. The page asks for sign-in itself when
 * there is no session, keeping the code in the URL.
 */
function isProtectedPath(pathname: string, locale: string): boolean {
  return (
    pathname === `/${locale}/app` || pathname.startsWith(`/${locale}/app/`)
  );
}

function redirectToLogin(request: NextRequest, locale: string): NextResponse {
  const url = request.nextUrl.clone();
  url.pathname = `/${locale}/login`;
  url.search = "";
  url.searchParams.set(
    "next",
    request.nextUrl.pathname + request.nextUrl.search,
  );
  return NextResponse.redirect(url);
}

export const config = {
  // Everything except Next's own assets and files with an extension. Without a
  // matcher the proxy would also run on CSS, JS and images, where the session
  // work is pure waste and a redirect would break the page it is trying to
  // protect.
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
};
