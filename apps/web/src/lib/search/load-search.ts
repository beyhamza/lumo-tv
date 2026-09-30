import "server-only";

import { api, problemCode } from "@/lib/api/client";
import { attempt } from "@/lib/catalogue/attempt";
import type { Channel, Series, VodItem } from "@/lib/api/types";
import {
  pageFor,
  pageSizeFor,
  searchSections,
  type SearchFilter,
  type SearchSections,
  type SearchType,
  type SectionResult,
} from "./search";

/**
 * The search's reads from the API, server-side (US-021, S10-02).
 *
 * <h2>Why this lives on the server</h2>
 *
 * The access token is in an httpOnly cookie and never reaches client
 * JavaScript, so the three listings cannot be called from the browser. The
 * page calls this while rendering, and the BFF route
 * (`app/api/sources/[id]/search`) calls the same functions for the
 * interactive path — both leave from the Next.js server, as every
 * authenticated call in this application does.
 *
 * <h2>Each section cannot throw, and that is the point</h2>
 *
 * `attempt()` turns "no answer at all" into a result with neither `data` nor
 * `error`; `problemCode` reads a refusal's machine code. Either way the section
 * becomes a `failed` result and the other two keep their answers — an error is
 * never an empty result (Q9, SR-10).
 */

/** Which of the three catalogues the active source actually holds. */
export interface CatalogueTypes {
  channels: boolean;
  films: boolean;
  series: boolean;
}

/** Maps an openapi-fetch result's `data`/`error` to a section result. */
function sectionFrom<T>(
  data: { items: T[]; total_elements: number } | undefined,
  error: unknown,
): SectionResult<T> {
  if (!data) return { ok: false, code: problemCode(error) };
  return { ok: true, items: data.items, totalElements: data.total_elements };
}

/**
 * One search: the requested types, at the size the filter implies.
 *
 * The grouped view asks at {@link PREVIEW_SIZE} and always page 0; a single
 * type asks at {@link PAGE_SIZE} and honours `?page=` (Q9, SR-06).
 */
export async function loadSearchSections(
  accessToken: string,
  sourceId: string,
  query: string,
  filter: SearchFilter,
  requestedPage: number,
  present: CatalogueTypes,
): Promise<SearchSections> {
  const size = pageSizeFor(filter);
  const page = pageFor(filter, requestedPage);

  return searchSections(filter, page, size, {
    channels: async (p, s) => {
      const result = await attempt(() =>
        api(accessToken).GET("/sources/{id}/channels", {
          params: { path: { id: sourceId }, query: { q: query, page: p, size: s } },
        }),
      );
      return sectionFrom<Channel>(result.data, result.error);
    },
    films: async (p, s) => {
      const result = await attempt(() =>
        api(accessToken).GET("/sources/{id}/vod", {
          params: { path: { id: sourceId }, query: { q: query, page: p, size: s } },
        }),
      );
      return sectionFrom<VodItem>(result.data, result.error);
    },
    series: async (p, s) => {
      const result = await attempt(() =>
        api(accessToken).GET("/sources/{id}/series", {
          params: { path: { id: sourceId }, query: { q: query, page: p, size: s } },
        }),
      );
      return sectionFrom<Series>(result.data, result.error);
    },
  }, present);
}

/**
 * Which catalogues the source holds, so the filters can list only those.
 *
 * <h2>One smallest possible request per type, and no new endpoint</h2>
 *
 * The contract has `channel_count` on a source but no equivalent for films or
 * series, and the categories endpoint is empty for some sources that still
 * carry items. The direct question is therefore the cheapest honest one:
 * `page=0&size=1` on the type, and whether the answer holds anything. It is one
 * request per type, of one row.
 *
 * <h2>An unknown presence does not hide the tab</h2>
 *
 * A probe that did not answer leaves the type **present**. Hiding "Films" on an
 * outage would repeat the mistake this project already paid for once: a missing
 * tab is read as a missing feature, and an empty list at least explains itself
 * (see `CatalogueTabs`). A source with no films still answers `0`, so its tab
 * is genuinely absent.
 */
export async function loadCatalogueTypes(
  accessToken: string,
  sourceId: string,
): Promise<CatalogueTypes> {
  const [channels, films, series] = await Promise.all([
    probe(accessToken, sourceId, "channels"),
    probe(accessToken, sourceId, "films"),
    probe(accessToken, sourceId, "series"),
  ]);
  return { channels, films, series };
}

async function probe(
  accessToken: string,
  sourceId: string,
  type: SearchType,
): Promise<boolean> {
  const params = { path: { id: sourceId }, query: { page: 0, size: 1 } } as const;

  const result =
    type === "channels"
      ? await attempt(() => api(accessToken).GET("/sources/{id}/channels", { params }))
      : type === "films"
        ? await attempt(() => api(accessToken).GET("/sources/{id}/vod", { params }))
        : await attempt(() => api(accessToken).GET("/sources/{id}/series", { params }));

  // No answer: unknown, and unknown keeps the tab. See above.
  if (!result.data) return true;
  return result.data.total_elements > 0;
}
