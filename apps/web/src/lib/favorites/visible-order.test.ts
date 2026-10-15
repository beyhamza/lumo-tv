import { describe, expect, it } from "vitest";
import {
  applyVisibleMove,
  executeMoves,
  fullIndex,
  planMoves,
  planVisibleStep,
  type Move,
} from "./visible-order";

// A* belong to the active source, B* to another one. Abstract bench members, as
// in docs/design/0.2.0/favorite-organization-cases.md — never channel names.
const ACTIVE = (member: string) => member.startsWith("A");

/** Replays single moves with the contract's rule: displaced favourites shift. */
function replay(current: readonly string[], moves: readonly Move[]): string[] {
  const order = [...current];
  for (const move of moves) {
    order.splice(order.indexOf(move.id), 1);
    order.splice(Math.min(move.position, order.length), 0, move.id);
  }
  return order;
}

/** Same cases as the API's VisibleFavoriteOrderTest, so the surfaces agree. */
describe("applyVisibleMove (G1)", () => {
  it("maps a visible slot to the full-group index, skipping other sources", () => {
    const group = ["A1", "B1", "A2", "B2", "A3"];
    expect(fullIndex(group, ACTIVE, 0)).toBe(0);
    expect(fullIndex(group, ACTIVE, 1)).toBe(2);
    expect(fullIndex(group, ACTIVE, 2)).toBe(4);
  });

  it("FO-01: swapping A1 and A2 in A1, B1, A2 leaves B1 in place", () => {
    expect(applyVisibleMove(["A1", "B1", "A2"], ACTIVE, 1, 0)).toEqual(["A2", "B1", "A1"]);
  });

  it("FO-02: moving A3 to the front leaves B1 and B2 at their indexes", () => {
    const reordered = applyVisibleMove(["A1", "B1", "A2", "B2", "A3"], ACTIVE, 2, 0);
    expect(reordered).toEqual(["A3", "B1", "A1", "B2", "A2"]);
    expect(reordered[1]).toBe("B1");
    expect(reordered[3]).toBe("B2");
  });

  it("moving down also leaves the other source alone", () => {
    expect(applyVisibleMove(["A1", "B1", "A2", "B2", "A3"], ACTIVE, 0, 2)).toEqual([
      "A2",
      "B1",
      "A3",
      "B2",
      "A1",
    ]);
  });

  it("a single visible member is a fixed point", () => {
    expect(applyVisibleMove(["B1", "A1", "B2"], ACTIVE, 0, 0)).toEqual(["B1", "A1", "B2"]);
  });

  it("FO-04: a group of one source is a plain list move", () => {
    expect(applyVisibleMove(["A1", "A2", "A3"], ACTIVE, 2, 0)).toEqual(["A3", "A1", "A2"]);
  });

  it("the result is a permutation, never a hole", () => {
    const group = ["A1", "B1", "A2", "B2", "A3"];
    const reordered = applyVisibleMove(group, ACTIVE, 1, 2);
    expect([...reordered].sort()).toEqual([...group].sort());
  });

  it("rejects a slot outside the visible range rather than clamping it", () => {
    expect(() => fullIndex(["A1", "B1", "A2"], ACTIVE, 2)).toThrow(RangeError);
    expect(() => applyVisibleMove(["A1", "B1", "A2"], ACTIVE, 0, 5)).toThrow(RangeError);
  });
});

describe("planMoves", () => {
  it("FO-01: reaches A2, B1, A1 with single full-group moves", () => {
    const current = ["A1", "B1", "A2"];
    const target = ["A2", "B1", "A1"];
    const moves = planMoves(current, target);
    expect(replay(current, moves)).toEqual(target);
  });

  it("FO-02: reaches A3, B1, A1, B2, A2 and B1/B2 end at 1 and 3", () => {
    const current = ["A1", "B1", "A2", "B2", "A3"];
    const target = ["A3", "B1", "A1", "B2", "A2"];
    const moves = planMoves(current, target);
    const after = replay(current, moves);
    expect(after).toEqual(target);
    expect(after.indexOf("B1")).toBe(1);
    expect(after.indexOf("B2")).toBe(3);
  });

  it("sends nothing when the order is already the target", () => {
    expect(planMoves(["A1", "B1"], ["A1", "B1"])).toEqual([]);
  });

  it("never moves a favourite that is already in its final place", () => {
    // Only A1 and A2 swap; the tail is untouched and costs no request.
    const moves = planMoves(["A1", "A2", "B1", "A3"], ["A2", "A1", "B1", "A3"]);
    expect(moves).toEqual([{ id: "A2", position: 0 }]);
  });

  it("refuses a target that is not a permutation of the group", () => {
    expect(() => planMoves(["A1", "B1"], ["A1", "B2"])).toThrow();
    expect(() => planMoves(["A1", "B1"], ["A1"])).toThrow();
  });
});

describe("planVisibleStep", () => {
  // Positions deliberately given out of order: the plan sorts, it does not trust.
  const group = [
    { id: "A2", source_id: "a", position: 2 },
    { id: "A1", source_id: "a", position: 0 },
    { id: "B1", source_id: "b", position: 1 },
    { id: "B2", source_id: "b", position: 3 },
    { id: "A3", source_id: "a", position: 4 },
  ];
  const current = ["A1", "B1", "A2", "B2", "A3"];

  it("moving A2 up lands it in A1's slot and A1 in A2's (FO-01 shape)", () => {
    const step = planVisibleStep(group, "A2", "up");
    expect(step.kind).toBe("moves");
    if (step.kind !== "moves") return;
    expect(step.target).toEqual(["A2", "B1", "A1", "B2", "A3"]);
    expect(replay(current, step.moves)).toEqual(step.target);
  });

  it("moving A1 down keeps B1 and B2 at 1 and 3", () => {
    const step = planVisibleStep(group, "A1", "down");
    if (step.kind !== "moves") throw new Error("expected moves");
    expect(step.target).toEqual(["A2", "B1", "A1", "B2", "A3"]);
  });

  it("does nothing at either end of the visible list", () => {
    expect(planVisibleStep(group, "A1", "up")).toEqual({ kind: "nothing" });
    expect(planVisibleStep(group, "A3", "down")).toEqual({ kind: "nothing" });
  });

  it("does nothing for a favourite that is no longer in the group", () => {
    expect(planVisibleStep(group, "A9", "up")).toEqual({ kind: "nothing" });
  });
});

describe("executeMoves (G2: not atomic)", () => {
  const moves: Move[] = [
    { id: "A3", position: 0 },
    { id: "B1", position: 1 },
    { id: "A1", position: 2 },
  ];

  it("applies every move in order when each is confirmed", async () => {
    const sent: string[] = [];
    const outcome = await executeMoves(moves, async (move) => {
      sent.push(move.id);
      return true;
    });
    expect(outcome).toEqual({ kind: "applied" });
    expect(sent).toEqual(["A3", "B1", "A1"]);
  });

  it("stops at the first refusal, keeps what succeeded, never reports success", async () => {
    const sent: string[] = [];
    const outcome = await executeMoves(moves, async (move) => {
      sent.push(move.id);
      return move.id !== "B1";
    });
    expect(outcome).toEqual({ kind: "partial", confirmed: 1 });
    // The move after the failure was planned against an order that no longer
    // holds; it is not sent.
    expect(sent).toEqual(["A3", "B1"]);
  });

  it("counts a lost response as not confirmed", async () => {
    const outcome = await executeMoves(moves, async () => {
      throw new Error("network");
    });
    expect(outcome).toEqual({ kind: "partial", confirmed: 0 });
  });
});
