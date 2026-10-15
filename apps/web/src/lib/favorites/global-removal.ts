/**
 * "Remove from all my favourites" (US-022, S11-03; S11-00 guarantee G2;
 * FO-05 to FO-08, FO-10, FO-12).
 *
 * <h2>Several removals, never one</h2>
 *
 * A channel filed in three groups is three `Favorite` rows, and the contract
 * removes them one `DELETE /me/favorites/{id}` at a time. Nothing makes the
 * three succeed together, so the screen must be able to say "two out of three",
 * and the rule of 19 September 2026 says what follows: keep the removals that
 * succeeded, never recreate them, never announce a global success, and offer to
 * retry **only** what remains.
 *
 * <h2>The intention is the memberships the confirmation showed</h2>
 *
 * The confirmation is drawn from a fresh read and carries the favourite ids it
 * listed. The action deletes those and no others. A membership added from
 * another device after the confirmation was drawn is not part of what the
 * person confirmed (FO-12), so neither the removal nor its retry silently widens
 * to it: the retry offers the **intersection** of what was left and what still
 * exists ({@link stillRemaining}). A channel that is still favourite because of
 * such an addition stays in the list, which is the truth, and a new removal is
 * a new intention.
 *
 * <h2>Outcomes are re-read, not remembered</h2>
 *
 * A lost response may hide a removal that happened (FO-08). The action reports
 * what it could not confirm; the page then re-reads `GET /me/favorites` and
 * keeps only the memberships that really still exist. What the person reads is
 * therefore never a count the action guessed.
 */

/** What the removal needs of a favourite. */
export type MembershipRef = {
  id: string;
  group_id: string;
  source_id: string;
  channel_id: string;
  position: number;
};

/**
 * Every membership of one channel of one source, in the order of the groups
 * then of the positions inside them — the order the confirmation lists them in.
 */
export function membershipsOf<F extends MembershipRef>(
  favorites: readonly F[],
  groups: readonly { id: string; position: number }[],
  sourceId: string,
  channelId: string,
): F[] {
  const rank = new Map(
    [...groups].sort((a, b) => a.position - b.position).map((group, index) => [group.id, index]),
  );
  const rankOf = (favorite: F) => rank.get(favorite.group_id) ?? Number.MAX_SAFE_INTEGER;
  return favorites
    .filter((favorite) => favorite.source_id === sourceId && favorite.channel_id === channelId)
    .sort((a, b) => rankOf(a) - rankOf(b) || a.position - b.position);
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

/**
 * The most memberships one removal carries. A channel is at most once per
 * group, so this is a ceiling on groups an account would plausibly keep — and a
 * bound on how many requests a single form post can make the server send.
 */
export const MAX_MEMBERSHIPS = 100;

/**
 * The favourite ids a confirmation or a retry posted, deduplicated, in order.
 * Anything that is not a uuid is dropped rather than sent; an empty or
 * oversized list sends nothing.
 */
export function readMembershipIds(values: readonly unknown[]): string[] {
  const ids = [...new Set(values.filter(isUuid).map((id) => id.toLowerCase()))];
  return ids.length > MAX_MEMBERSHIPS ? [] : ids;
}

/** What the API answered to one `DELETE`, reduced to what matters here. */
export type DeleteAnswer = { status: number; error?: unknown };

/**
 * One membership's fate.
 *
 * `204` is a removal. `404 FAVORITE_NOT_FOUND` is a removal too: the membership
 * is already gone — another tab, another device, or an earlier attempt whose
 * response was lost — and the person asked for it to be gone. Anything else,
 * including no answer, is not confirmed.
 */
export function classifyDelete(answer: DeleteAnswer): "removed" | "not-confirmed" {
  if (answer.status === 204) return "removed";
  if (answer.status === 404 && problemCode(answer.error) === "FAVORITE_NOT_FOUND") {
    return "removed";
  }
  return "not-confirmed";
}

export type RemovalOutcome = { removed: string[]; left: string[] };

/**
 * Sends one `DELETE` per membership, sequentially, and sorts the results.
 *
 * Unlike a move sequence, a failure does not stop the others: each removal is
 * independent of the rest, nothing was computed against an order, and every
 * one that succeeds is one the person asked for. A throw is "not confirmed".
 */
export async function executeRemoval(
  ids: readonly string[],
  send: (id: string) => Promise<DeleteAnswer>,
): Promise<RemovalOutcome> {
  const removed: string[] = [];
  const left: string[] = [];
  for (const id of ids) {
    let fate: "removed" | "not-confirmed";
    try {
      fate = classifyDelete(await send(id));
    } catch {
      fate = "not-confirmed";
    }
    (fate === "removed" ? removed : left).push(id);
  }
  return { removed, left };
}

/**
 * After re-reading: which of the memberships the action could not confirm do
 * still exist. Never wider than `left` — a membership added since is not part
 * of this intention (FO-12) — and never including one that turns out to be gone
 * (FO-08: the lost response had in fact been a removal).
 */
export function stillRemaining<F extends { id: string }>(
  left: readonly string[],
  favorites: readonly F[],
): F[] {
  const wanted = new Set(left.map((id) => id.toLowerCase()));
  return favorites.filter((favorite) => wanted.has(favorite.id.toLowerCase()));
}

function problemCode(error: unknown): string | undefined {
  if (!error || typeof error !== "object") return undefined;
  const code = (error as { code?: unknown }).code;
  return typeof code === "string" ? code : undefined;
}
