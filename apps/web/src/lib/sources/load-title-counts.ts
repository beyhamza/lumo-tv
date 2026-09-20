import "server-only";

import { api } from "@/lib/api/client";
import type { Source } from "@/lib/api/types";
import { seriesApply, type TitleCounts } from "./content-counts";

/**
 * How many films and series a source holds, without loading either catalogue.
 *
 * <h2>`size=1`, and only `total_elements` is read</h2>
 *
 * `Source` has no film or series counter and this client invents no field
 * (US-024, "Couverture API"). The paginated listings already carry the total, so
 * each count is one request for a page of **one** row whose row is thrown away.
 * A hundred and forty thousand films cost the same as twelve.
 *
 * <h2>Bounded: at most two requests per source, in parallel, often fewer</h2>
 *
 * - none at all before a first successful ingestion — the listings would answer
 *   `409 SOURCE_NOT_READY`, and asking to be refused is a round trip for nothing;
 * - one for a playlist: series do not apply to it (`adr/0010`), so the series
 *   listing is not asked;
 * - two for an Xtream source.
 *
 * <h2>A failure is `null`, and `null` is not zero</h2>
 *
 * Any outcome other than a body — a refusal, an outage, an exception — leaves
 * that count `null`, which `contentCounts` leaves off the screen. Nothing here
 * throws: a number that could not be read must not take down the page that was
 * going to show it.
 */
export async function loadTitleCounts(
  accessToken: string,
  source: Pick<Source, "id" | "kind" | "last_synced_at">,
): Promise<TitleCounts> {
  if (source.last_synced_at == null) return {};

  const path = { id: source.id };
  const query = { page: 0, size: 1 };

  const [films, series] = await Promise.all([
    total(() => api(accessToken).GET("/sources/{id}/vod", { params: { path, query } })),
    seriesApply(source.kind)
      ? total(() => api(accessToken).GET("/sources/{id}/series", { params: { path, query } }))
      : Promise.resolve(undefined),
  ]);

  return { films, series };
}

/**
 * Above this many sources, My sources stops asking for film and series counts
 * on its **list** and leaves them to each source's own page.
 *
 * The list needs at most two extra requests per source, which is the budget that
 * was set for it — so it shows them. But the list is also the page that reloads
 * itself every five seconds while an import runs, and "two per source" is only
 * bounded if the number of sources is: a paid plan has no source limit. Five
 * sources is ten parallel count requests per render, which is where this stops
 * growing.
 */
export const LIST_TITLE_COUNTS_MAX_SOURCES = 5;

async function total(
  call: () => Promise<{ data?: { total_elements: number } }>,
): Promise<number | null> {
  try {
    const result = await call();
    return result.data ? result.data.total_elements : null;
  } catch {
    return null;
  }
}
