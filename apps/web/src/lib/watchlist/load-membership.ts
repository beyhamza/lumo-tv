import "server-only";

import { api } from "@/lib/api/client";
import type { WatchlistContentType } from "@/lib/api/types";
import { MEMBERSHIP_PAGE_SIZE, findMembership, type Membership } from "./membership";

/**
 * Whether a film or series of one source is in "My list", read from
 * `GET /me/watchlist` (see `membership.ts` for the walk and its three answers).
 */
export function loadMembership(
  accessToken: string,
  sourceId: string,
  contentType: WatchlistContentType,
  contentId: string,
): Promise<Membership> {
  return findMembership(contentId, async (page) => {
    const answer = await api(accessToken).GET("/me/watchlist", {
      params: {
        query: { sourceId, contentType, page, size: MEMBERSHIP_PAGE_SIZE },
      },
    });
    return answer.data;
  });
}
