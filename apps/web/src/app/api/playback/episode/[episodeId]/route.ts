import { NextResponse } from "next/server";
import { api, problemCode } from "@/lib/api/client";
import { getSession } from "@/lib/session/session";

/**
 * The stream URL of one episode (S6-07), on the pattern of `S3-09`.
 *
 * <h2>Everything written on the channel handler applies here word for word</h2>
 *
 * `GET /episodes/{id}/playback` returns a URL that, on an Xtream source, **contains
 * the user's panel username and password**. It is fetched at the moment playback
 * starts and never rendered into the document — see
 * `app/api/playback/[channelId]/route.ts`, which is the one place that reasoning
 * is written out in full. This is a third route rather than a shared one
 * because the contract has three operations against three different tables, and a
 * handler that took "an id" it could not name is how an episode gets looked up
 * among the films.
 *
 * <h2>Still not a proxy</h2>
 *
 * It hands back a URL. It does not fetch a byte of the episode, and it especially
 * does not relay `Range` requests: seeking a two-gigabyte file through our
 * infrastructure is exactly the traffic ADR 0007 refuses to carry, and it would
 * be far more of it than a live stream ever was.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ episodeId: string }> },
) {
  const { episodeId } = await params;
  const session = await getSession();

  if (!session) {
    return problem(401, "UNAUTHENTICATED");
  }

  let result: Awaited<ReturnType<ReturnType<typeof api>["GET"]>>;

  try {
    result = await api(session.accessToken).GET("/episodes/{id}/playback", {
      params: { path: { id: episodeId } },
    });
  } catch {
    return problem(502, "NETWORK");
  }

  if (result.error || !result.data) {
    // Passed through untouched. A refresh in progress (`SOURCE_NOT_READY` — all
    // it means here since contract lot C4), credentials the provider refused
    // (`SOURCE_AUTH_FAILED`), a subscription that has expired and a panel at its
    // connection limit are four different sentences with different ways out
    // (`lib/playback/refusal.ts`), and collapsing them here would make the
    // player say "playback failed" to all of them.
    const code = problemCode(result.error) ?? "INTERNAL_ERROR";
    return problem(result.response?.status ?? 502, code);
  }

  return NextResponse.json(
    {
      // Sensitive. Never logged on this side, never rendered into a document.
      url: result.data.stream_url,
      maxConnections: result.data.max_connections ?? null,
    },
    {
      status: 200,
      headers: {
        // Not by any cache, not for any duration. A shared cache holding this
        // would be handing one user's credentials to the next one.
        "Cache-Control": "no-store, no-cache, must-revalidate, private",
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
