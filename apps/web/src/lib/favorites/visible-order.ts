/**
 * Reordering a favourite group as the active source sees it (US-022, S11-03;
 * S11-00 guarantees G1 and G2; FO-01 to FO-04).
 *
 * <h2>Two orders, and the contract speaks only one of them</h2>
 *
 * A group belongs to the account and holds channels of several sources, but the
 * screen lists only the active source's. `PATCH /me/favorites/{id}` takes
 * `position`, an index into the **full** group. Sending the visible slot as it
 * stands would land the favourite somewhere else entirely as soon as the group
 * alternates sources.
 *
 * The product rule (19 September 2026) is that reordering the visible list only
 * permutes the places this source already occupies: the other sources'
 * favourites keep their exact index. From `[A1, B1, A2, B2, A3]`, moving A3 to
 * the front gives `[A3, B1, A1, B2, A2]`, never `[A3, A1, B1, A2, B2]`.
 *
 * {@link applyVisibleMove} is the same projection as the API's
 * `VisibleFavoriteOrder.applyVisibleMove`, case for case, so that the three
 * surfaces agree on what a move means.
 *
 * <h2>The target is reached by single moves, and that is not atomic</h2>
 *
 * There is no batch endpoint and no permutation endpoint. {@link planMoves} turns
 * the target order into the single `PATCH` moves that produce it, skipping the
 * ones that would change nothing; {@link executeMoves} sends them one after the
 * other and stops at the first one that is not confirmed. What succeeded stays —
 * the group is then valid but partially permuted, which S11-00 states as the
 * explicit non-guarantee — and the caller re-reads before saying anything.
 *
 * Pure: no request, no session. The action fetches and sends; this decides.
 */

/** What the ordering needs of a favourite, so a test does not invent the rest. */
export type OrderedFavorite = { id: string; source_id: string; position: number };

/**
 * The index in the full group of the member at `visibleSlot` among those that
 * match `visible`.
 *
 * @throws RangeError when the slot is outside the visible members — rejected,
 *   never clamped, like the API's `IndexOutOfBoundsException`.
 */
export function fullIndex<T>(
  groupOrder: readonly T[],
  visible: (member: T) => boolean,
  visibleSlot: number,
): number {
  let seen = 0;
  for (let i = 0; i < groupOrder.length; i++) {
    if (!visible(groupOrder[i])) continue;
    if (seen === visibleSlot) return i;
    seen++;
  }
  throw new RangeError(`visible slot ${visibleSlot} out of range for ${seen} visible members`);
}

/**
 * Moves the visible member at `fromSlot` to `toSlot` and returns the resulting
 * full group order. Members that do not match `visible` keep their index.
 *
 * @throws RangeError when either slot is outside the visible members.
 */
export function applyVisibleMove<T>(
  groupOrder: readonly T[],
  visible: (member: T) => boolean,
  fromSlot: number,
  toSlot: number,
): T[] {
  const visibleMembers = groupOrder.filter(visible);
  if (!inRange(fromSlot, visibleMembers.length)) {
    throw new RangeError(`from slot ${fromSlot} out of range`);
  }
  if (!inRange(toSlot, visibleMembers.length)) {
    throw new RangeError(`to slot ${toSlot} out of range`);
  }

  const [moved] = visibleMembers.splice(fromSlot, 1);
  visibleMembers.splice(toSlot, 0, moved);

  let next = 0;
  return groupOrder.map((member) => (visible(member) ? visibleMembers[next++] : member));
}

/** One `PATCH /me/favorites/{id}` with `position` alone. */
export type Move = { id: string; position: number };

/**
 * The single moves that turn `current` into `target`, in the order they must be
 * sent.
 *
 * Walks the target from the front: wherever the current order disagrees, the
 * favourite the target wants there is moved to that index, and the simulation
 * applies the contract's rule (the displaced favourites shift down). The prefix
 * is then final and is never touched again, so every move is sent at most once
 * per favourite, and a slot that is already right costs nothing.
 *
 * @throws Error when the two lists are not permutations of each other — a plan
 *   between two different groups would move favourites nobody asked about.
 */
export function planMoves(current: readonly string[], target: readonly string[]): Move[] {
  if (!samePermutation(current, target)) {
    throw new Error("target order is not a permutation of the current order");
  }

  const working = [...current];
  const moves: Move[] = [];
  for (let position = 0; position < target.length; position++) {
    const id = target[position];
    if (working[position] === id) continue;
    working.splice(working.indexOf(id), 1);
    working.splice(position, 0, id);
    moves.push({ id, position });
  }
  return moves;
}

export type Direction = "up" | "down";

/** What a press of "Move up" or "Move down" amounts to, against a fresh read. */
export type VisibleStep =
  | { kind: "moves"; target: string[]; moves: Move[] }
  /**
   * Nothing to send: the favourite is no longer in the group (removed from
   * another device since the page was drawn), or already at that end of the
   * visible list. The page is re-read and shows which.
   */
  | { kind: "nothing" };

/**
 * Plans moving one favourite a visible slot up or down in its group.
 *
 * `group` is the group **as re-read now**, not as it was drawn: the button names
 * a favourite and a direction, and the server's current order is what it is
 * applied to (G3: the last accepted action decides, never a client's memory).
 * Visibility is the favourite's own source — the view only ever lists the active
 * source, and the moved favourite is one of its rows.
 */
export function planVisibleStep(
  group: readonly OrderedFavorite[],
  favoriteId: string,
  direction: Direction,
): VisibleStep {
  const ordered = [...group].sort((a, b) => a.position - b.position);
  const moved = ordered.find((favorite) => favorite.id === favoriteId);
  if (!moved) return { kind: "nothing" };

  const visible = (favorite: OrderedFavorite) => favorite.source_id === moved.source_id;
  const from = ordered.filter(visible).indexOf(moved);
  const to = direction === "up" ? from - 1 : from + 1;
  const visibleCount = ordered.filter(visible).length;
  if (!inRange(to, visibleCount)) return { kind: "nothing" };

  const current = ordered.map((favorite) => favorite.id);
  const target = applyVisibleMove(ordered, visible, from, to).map((favorite) => favorite.id);
  const moves = planMoves(current, target);
  return { kind: "moves", target, moves };
}

/**
 * How a sequence of moves ended.
 *
 * - `applied`: every move was confirmed (or there was none to send).
 * - `partial`: a move was not confirmed; the ones before it were, and stay. The
 *   group is valid but partially permuted, and nothing announces a success.
 */
export type MovesOutcome = { kind: "applied" } | { kind: "partial"; confirmed: number };

/**
 * Sends the moves one at a time and stops at the first that is not confirmed.
 *
 * Stopping rather than carrying on is deliberate: every later move was planned
 * against an order that now no longer holds, and sending it would move a
 * favourite to an index computed for a different list. A throw — no response —
 * counts as not confirmed, because the move may or may not have happened.
 *
 * @param send answers whether the server confirmed the move (`200`).
 */
export async function executeMoves(
  moves: readonly Move[],
  send: (move: Move) => Promise<boolean>,
): Promise<MovesOutcome> {
  let confirmed = 0;
  for (const move of moves) {
    let ok = false;
    try {
      ok = await send(move);
    } catch {
      ok = false;
    }
    if (!ok) return { kind: "partial", confirmed };
    confirmed++;
  }
  return { kind: "applied" };
}

function inRange(index: number, length: number): boolean {
  return Number.isInteger(index) && index >= 0 && index < length;
}

function samePermutation(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false;
  const sortedA = [...a].sort();
  const sortedB = [...b].sort();
  return sortedA.every((value, index) => value === sortedB[index]) && new Set(a).size === a.length;
}
