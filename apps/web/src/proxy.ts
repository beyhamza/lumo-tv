import createIntlMiddleware from "next-intl/middleware";
import { NextResponse, type NextRequest } from "next/server";
import { routing } from "@/i18n/routing";
import {
  DIRECT_VIEW_MAX_AGE_SECONDS,
  directViewCookieName,
  explicitView,
} from "@/lib/direct/view-memory";
import { sessionCookieName, sessionCookieSecure } from "@/lib/env";
import { PATHNAME_HEADER } from "@/lib/http/pathname-header";
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
  // For the account rail's current-section mark (see lib/http/pathname-header).
  // Set before next-intl builds its response, which copies the request headers
  // into the one it forwards.
  request.headers.set(PATHNAME_HEADER, request.nextUrl.pathname);
  const response = handleI18n(request);

  // A locale redirect ends the request; there is no session work to do on a
  // response the browser will immediately replace.
  if (isRedirect(response)) return response;

  // The Direct view memory (S9-04-06). Written here and not in the page because
  // a Server Component cannot set a cookie, and the view switch is a plain link
  // that has to work without JavaScript: an explicit `?view=` primes the cookie
  // on the way to the render that shows it.
  rememberDirectView(request, response);

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

/**
 * Remembers an explicit Direct view for one source.
 *
 * Only `/app/sources/<id>/channels` carries a view, and only when the URL says
 * one: a bare `/channels` — an S8 link, an Explore door — writes nothing and so
 * keeps the memory (`resolveDirectView`). A value the cookie already holds is
 * not rewritten, so an ordinary switch writes once.
 */
function rememberDirectView(request: NextRequest, response: NextResponse): void {
  const sourceId = viewSourceId(request.nextUrl.pathname);
  if (!sourceId) return;

  const view = explicitView(request.nextUrl.searchParams.get("view") ?? undefined);
  if (!view) return;

  const name = directViewCookieName(sourceId);
  if (!name) return;
  if (request.cookies.get(name)?.value === view) return;

  response.cookies.set(name, view, {
    // Not a secret, but no script has any business with it — and the page reads
    // it on the server, exactly as the active-source cookie is read.
    httpOnly: true,
    sameSite: "lax",
    secure: sessionCookieSecure(),
    // `/` and not `/app`: with `localePrefix: "always"` the zone lives under
    // `/fr/app` and `/en/app`, which share no prefix but `/`
    // (`lib/sources/active-source-store.ts`).
    path: "/",
    maxAge: DIRECT_VIEW_MAX_AGE_SECONDS,
  });
}

/**
 * The source id of `/<locale>/app/sources/<id>/channels`, or null.
 *
 * Split rather than matched with a regular expression so that the locale list
 * stays the single source of truth (`routing.locales`), and so that a trailing
 * slash or a deeper path is simply not the channels page.
 */
function viewSourceId(pathname: string): string | null {
  const segments = pathname.split("/").filter(Boolean);
  if (segments.length !== 5) return null;

  const [maybeLocale, app, sources, sourceId, channels] = segments;
  if (app !== "app" || sources !== "sources" || channels !== "channels") {
    return null;
  }
  if (!routing.locales.includes(maybeLocale as (typeof routing.locales)[number])) {
    return null;
  }
  return sourceId ? decodeURIComponent(sourceId) : null;
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
