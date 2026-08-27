import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { api, problemCode } from "@/lib/api/client";
import { GOOGLE_ERROR_CODES, type GoogleErrorCode } from "@/lib/auth/google";
import { WEB_DEVICE } from "@/lib/auth/web-device";
import { sessionFrom } from "@/lib/session/from-auth-session";
import { openSession } from "@/lib/session/session";

/**
 * Where Google posts its ID token (US-03).
 *
 * <h2>Why Google posts here rather than handing the token to our JavaScript</h2>
 *
 * Google Identity Services offers two ways back. The usual one calls a
 * JavaScript callback with the credential; this one — `ux_mode: "redirect"` with
 * a `login_uri` — makes Google's own script submit a form straight to this
 * endpoint. The ID token therefore never exists in a variable this application
 * owns, which is the rule the rest of this zone already follows: the sign-in
 * form posts to a Server Action for exactly that reason
 * (docs/architecture.md §5).
 *
 * The cost is one line in a Google Cloud console — this URL has to be registered
 * as an authorised redirect URI, where the callback flow needs only the origin.
 * See `.env.example`.
 *
 * <h2>The CSRF check is Google's, and it is not optional</h2>
 *
 * This is a cross-site POST with no session and no Server Action wrapper, so
 * none of Next's own protections apply. Google's answer is a double-submit
 * cookie: the same random `g_csrf_token` arrives both as a cookie and as a field
 * in the body. A forged form can carry the field; it cannot carry the cookie.
 * Both must be present and equal, or nothing is sent to the API.
 *
 * <h2>Neither redirect names a locale, deliberately</h2>
 *
 * Google posts to one fixed URL, outside the `[locale]` segment, and a locale in
 * a query parameter would have to be registered in the console as a separate
 * redirect URI. So both answers point at an unprefixed path and let `proxy.ts`
 * do the negotiation it already owns — the same redirect it performs for anybody
 * who types `lumo.tv/app`. Guessing here from `NEXT_LOCALE` would be wrong
 * exactly when the cookie is absent, which is most of the time: next-intl only
 * writes it when the chosen locale differs from `Accept-Language`.
 */
export async function POST(request: Request): Promise<NextResponse> {
  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return back(request, "VALIDATION_FAILED");
  }

  const credential = String(form.get("credential") ?? "");
  const bodyToken = String(form.get("g_csrf_token") ?? "");
  const cookieToken = (await cookies()).get("g_csrf_token")?.value ?? "";

  // Both present and equal. The emptiness check matters: without it, a request
  // carrying neither would compare "" to "" and pass.
  if (!bodyToken || bodyToken !== cookieToken) {
    return back(request, "VALIDATION_FAILED");
  }

  if (!credential) return back(request, "VALIDATION_FAILED");

  let result;
  try {
    // The token goes straight through, unread. The server checks signature,
    // `aud`, `iss` and `exp`, and takes the address out of the token it has just
    // verified — an email a client sends is an email anybody can send (US-03).
    result = await api().POST("/auth/oauth/google", {
      body: { id_token: credential, device: WEB_DEVICE },
    });
  } catch {
    // openapi-fetch resolves HTTP errors; only a transport failure throws.
    return back(request, "NETWORK");
  }

  if (!result.data) {
    const code = problemCode(result.error);
    return back(
      request,
      (GOOGLE_ERROR_CODES as readonly string[]).includes(code ?? "")
        ? (code as GoogleErrorCode)
        : "GENERIC",
    );
  }

  await openSession(sessionFrom(result.data));

  // 303, so the browser follows with a GET. After a 302 a refresh offers to
  // resubmit the credential, which is single-use and spent by then.
  return NextResponse.redirect(new URL("/app", request.url), 303);
}

/**
 * Back to sign-in, saying what happened.
 *
 * The status is one of a closed set and never a value from the request: this
 * endpoint is reached by a cross-site POST, and anything from it that reached a
 * query parameter would be a stranger choosing what the page says.
 */
function back(request: Request, status: GoogleErrorCode): NextResponse {
  const url = new URL("/login", request.url);
  url.searchParams.set("google", status);
  return NextResponse.redirect(url, 303);
}
