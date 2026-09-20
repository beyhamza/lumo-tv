import { NextResponse } from "next/server";
import { api, problemCode } from "@/lib/api/client";
import { getSession } from "@/lib/session/session";

/**
 * The stream URL, handed to the player and to nothing else (S3-09, ADR 0007).
 *
 * <h2>Why this is a route handler and not a server-rendered prop</h2>
 *
 * `GET /channels/{id}/playback` returns a URL that, for an Xtream source,
 * **contains the user's panel username and password**. It is the most sensitive
 * response this API produces, and the contract types it `format: password` for
 * that reason.
 *
 * If the catalogue page fetched it while rendering, that URL would land in the
 * HTML: in an attribute, in a hydration payload, or in a serialised server
 * state. It would then be in the page source, in any HTML cache, and in the
 * scroll-back of anyone who has ever pressed "view source". Fetching it here, at
 * the moment playback starts, keeps it out of the document entirely — the only
 * place it exists on this side is one `fetch` response and the variable the
 * player holds it in.
 *
 * <h2>What this does not solve</h2>
 *
 * The URL still reaches client JavaScript, so it is visible in the browser's
 * network panel. That is the user's own credential on the user's own machine,
 * which is acceptable; it also means an XSS on this site walks away with it,
 * which is why nothing about the player is server-rendered and why the session
 * cookie stays `httpOnly`.
 *
 * <h2>Not a proxy</h2>
 *
 * This hands back a URL. It does not fetch the manifest, and it does not relay a
 * single byte of media: ADR 0007 refuses that outright, and the day someone adds
 * "just a small proxy" here is the day we become a broadcaster.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ channelId: string }> },
) {
  const { channelId } = await params;
  const session = await getSession();

  if (!session) {
    return problem(401, "UNAUTHENTICATED");
  }

  let result: Awaited<
    ReturnType<ReturnType<typeof api>["GET"]>
  >;

  try {
    result = await api(session.accessToken).GET("/channels/{id}/playback", {
      params: { path: { id: channelId } },
    });
  } catch {
    return problem(502, "NETWORK");
  }

  if (result.error || !result.data) {
    // The code is passed through untouched. A refresh in progress
    // (`SOURCE_NOT_READY` — since contract lot C4 that is all it means here),
    // credentials the provider refused (`SOURCE_AUTH_FAILED`), a subscription
    // that has expired and a panel at its connection limit are four different
    // sentences with different ways out (`lib/playback/refusal.ts`), and
    // collapsing them here would make the player say "playback failed" to all.
    const code = problemCode(result.error) ?? "INTERNAL_ERROR";
    return problem(result.response?.status ?? 502, code);
  }

  return NextResponse.json(
    {
      // Sensitive. Never logged on this side, never rendered into a document.
      url: result.data.stream_url,
      // Echoed so the player can explain a stream the panel refuses.
      maxConnections: result.data.max_connections ?? null,
    },
    {
      status: 200,
      headers: {
        // Not by any cache, not for any duration. A shared cache holding this
        // would be handing one user's credentials to the next one.
        "Cache-Control": "no-store, no-cache, must-revalidate, private",
        // Nothing about this response should end up in a search index or a
        // preview, even by accident.
        "X-Robots-Tag": "noindex, nofollow",
      },
    },
  );
}

function problem(status: number, code: string) {
  return NextResponse.json(
    { code },
    { status, headers: { "Cache-Control": "no-store" } },
  );
}
