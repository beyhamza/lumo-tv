import { NextResponse } from "next/server";
import { getSession } from "@/lib/session/session";
import { isUuid } from "@/lib/sources/active-source";
import { loadCatalogueTypes, loadSearchSections } from "@/lib/search/load-search";
import {
  filterFromParam,
  normalizeQuery,
  queryTooLong,
  type SearchSections,
} from "@/lib/search/search";

/**
 * One search of the active source's three catalogues (US-021, S10-02).
 *
 * <h2>Why a route handler, when the page renders on the server too</h2>
 *
 * The page is a Server Component and could call the loader itself — and does,
 * for the first render. But a search is interactive: the field settles, the
 * filter changes, a page is asked for. The browser cannot call the API: the
 * access token is in an httpOnly cookie and never reaches JavaScript
 * (`apps/web/AGENTS.md` §4). So the interactive path asks **here**, same
 * origin, and this handler asks the three listings with the token — exactly as
 * `api/sources/[id]/exists` and the `api/playback/*` handlers do.
 *
 * <h2>It composes, it does not invent</h2>
 *
 * No new backend endpoint: `q`, `page` and `size` are the contract's, and this
 * handler runs one listing per requested type. Each section answers with its
 * own status and its own `total_elements`, so a films request that failed does
 * not empty the channels section — an error is never an empty result (Q9,
 * SR-10). The section fields are `null` for a type the filter, or the source,
 * did not ask for; a screen can tell that from a failure.
 *
 * <h2>What it refuses</h2>
 *
 * No session is `401`. An id that cannot name a source is `400` before it
 * costs the API a request. A query past the contract's hundred code points is
 * `400 VALIDATION_FAILED`: refused, never truncated, on the way in as on the
 * way out (Q9, SR-05). An empty query is not a search at all — it answers
 * `invitation: true` with no section loaded, so the full catalogue is never
 * pulled by clearing the field (SR-04).
 */
export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const session = await getSession();
  if (!session) return problem(401, "UNAUTHENTICATED");
  if (!isUuid(id)) return problem(400, "VALIDATION_FAILED");

  const url = new URL(request.url);
  const query = normalizeQuery(url.searchParams.get("q") ?? undefined);
  const filter = filterFromParam(url.searchParams.get("type") ?? undefined);
  const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10) || 0);

  if (queryTooLong(query)) return problem(400, "VALIDATION_FAILED");

  const types = await loadCatalogueTypes(session.accessToken, id);
  const empty: SearchSections = { channels: null, films: null, series: null };
  const sections =
    query.length === 0
      ? empty
      : await loadSearchSections(session.accessToken, id, query, filter, page, types);

  return NextResponse.json(
    { query, filter, page, invitation: query.length === 0, types, sections },
    {
      status: 200,
      headers: {
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
