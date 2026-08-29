import type { Channel } from "@/lib/api/types";

/**
 * How many identifiers `GET /sources/{id}/channels?ids=` accepts **per request**.
 *
 * The contract's own ceiling, and the reason this module exists rather than a
 * `.slice()` at each call site. See {@link resolveChannels}.
 */
export const IDS_PER_REQUEST = 100;

/**
 * The page size that must be sent with every batch.
 *
 * **`size` defaults to 50.** A batch of a hundred identifiers comes back half
 * answered without it — with a `200` and no error, which is the part that makes
 * it dangerous. The phone paid for this once already (`S4-02`); this is the same
 * bill arriving at a new address.
 */
const PAGE_SIZE = IDS_PER_REQUEST;

/**
 * Resolves channel identifiers into channels, across however many sources (S6-09).
 *
 * <h2>Why this is a module and not four lines in a page</h2>
 *
 * `GET /me/favorites` answers for the **whole account** — that is the structural
 * point of US-12, a group holds channels from two subscriptions — while names and
 * logos come from `GET /sources/{id}/channels?ids=`, which is **one operation per
 * source**. Nothing on the web did that before: the channels page only ever
 * resolved within its own source, and got away with a defensive `.slice(0, 100)`
 * because a rail is bounded by construction.
 *
 * <h2>A loop, not a cut, and that is the whole risk of the task</h2>
 *
 * A group of three hundred channels spread over three sources is **not** three
 * requests. `ids` is capped at a hundred *per request*, so it is batches, per
 * source, in a loop. A `.slice(0, 100)` here would truncate a group in silence —
 * a `200`, no error, and a hundred rows where there should be a hundred and
 * twenty. That is `R-140` of the sprint 4 qualification plan, transposed to the
 * web, and it is exactly the defect this product keeps calling the most expensive
 * kind: nothing looks broken.
 *
 * <h2>The order is the caller's, never the answers'</h2>
 *
 * Batches resolve in parallel and come back in whatever order they come back in.
 * What is returned is a map, and the caller walks its own list against it — the
 * favourites' order, which the server maintains and the user arranged.
 *
 * <h2>A reference that resolves to nothing is simply absent</h2>
 *
 * The contract says an unknown id is left out of the answer rather than being an
 * error. In practice this is close to impossible for a favourite —
 * `favorite.channel_id` carries `REFERENCES channel(id) ON DELETE CASCADE`, and
 * re-synchronisation upserts on `(source_id, external_id)`, so a channel that
 * survives keeps its identifier and one that disappears takes its favourites with
 * it. The guard costs nothing and means a caller never renders a gap.
 *
 * @param fetchChannels one request. Injected so this can be tested without a
 *   server, and so the batching is the only thing under test.
 * @param refs the identifiers to resolve, each with the source that holds it.
 * @returns channels by id. Never throws: a source that fails contributes nothing
 *   and the rest of the screen still renders.
 */
export async function resolveChannels(
  refs: readonly { sourceId: string; channelId: string }[],
  fetchChannels: (sourceId: string, ids: string[], size: number) => Promise<Channel[]>,
): Promise<Map<string, Channel>> {
  const bySource = new Map<string, string[]>();
  for (const ref of refs) {
    // Deduplicated: the same channel can be starred into two groups, and asking
    // for it twice in one batch spends part of the ceiling on nothing.
    const ids = bySource.get(ref.sourceId) ?? [];
    if (!ids.includes(ref.channelId)) ids.push(ref.channelId);
    bySource.set(ref.sourceId, ids);
  }

  const batches: { sourceId: string; ids: string[] }[] = [];
  for (const [sourceId, ids] of bySource) {
    for (let index = 0; index < ids.length; index += IDS_PER_REQUEST) {
      batches.push({ sourceId, ids: ids.slice(index, index + IDS_PER_REQUEST) });
    }
  }

  const answers = await Promise.all(
    batches.map(async ({ sourceId, ids }) => {
      try {
        // `size` explicitly, every time. See PAGE_SIZE.
        return await fetchChannels(sourceId, ids, PAGE_SIZE);
      } catch {
        // One source being unreachable is not the screen being unreachable. Its
        // channels drop out; every other source still renders.
        return [];
      }
    }),
  );

  const byId = new Map<string, Channel>();
  for (const channels of answers) {
    for (const channel of channels) byId.set(channel.id, channel);
  }
  return byId;
}
