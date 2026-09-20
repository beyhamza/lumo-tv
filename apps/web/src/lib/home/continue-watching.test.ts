import { describe, expect, it } from "vitest";
import type { Series, VodItem } from "@/lib/api/types";
import { continueCards, onePerSeries, resumableRows } from "./continue-watching";

// Bench identifiers and bench titles only: no real film, series or provider is
// named in this repository, tests included.
const SOURCE = "11111111-1111-4111-8111-111111111111";
const OTHER_SOURCE = "22222222-2222-4222-8222-222222222222";

const HOUR = 3_600_000;

function row(
  itemType: "VOD" | "EPISODE",
  itemRef: string,
  updatedAt: string,
  overrides: Partial<{
    source_id: string;
    position_ms: number;
    duration_ms: number | null;
  }> = {},
) {
  return {
    source_id: SOURCE,
    item_type: itemType,
    item_ref: itemRef,
    position_ms: HOUR / 2,
    duration_ms: HOUR as number | null,
    updated_at: updatedAt,
    ...overrides,
  };
}

function episode(id: string, seriesId: string, season = 1, number = 1) {
  return { id, series_id: seriesId, season_number: season, episode_number: number };
}

function film(id: string): VodItem {
  return { id, source_id: SOURCE, name: `Bench film ${id}`, position: 0, is_adult: false };
}

function series(id: string): Series {
  return { id, source_id: SOURCE, name: `Bench series ${id}`, position: 0, is_adult: false };
}

const refs = (rows: { item_ref: string }[]) => rows.map((entry) => entry.item_ref);
const mapOf = <T extends { id: string }>(...items: T[]) =>
  new Map(items.map((item) => [item.id, item]));

/**
 * The Continue rail (US-017, US-019).
 *
 * What is pinned here is what nobody would notice in review: a finished film
 * offered for resuming, three cards for one series, a film from another source,
 * two sorted lists glued together and called sorted.
 */
describe("resumableRows", () => {
  it("merges films and episodes, most recently updated first", () => {
    // As the page hands them over: the film answer, then the episode answer —
    // each sorted, the concatenation not.
    const rows = [
      row("VOD", "film-new", "2026-09-19T20:00:00Z"),
      row("VOD", "film-old", "2026-09-10T20:00:00Z"),
      row("EPISODE", "episode-newest", "2026-09-20T08:00:00Z"),
      row("EPISODE", "episode-middle", "2026-09-15T08:00:00Z"),
    ];

    expect(refs(resumableRows(rows, SOURCE))).toEqual([
      "episode-newest",
      "film-new",
      "episode-middle",
      "film-old",
    ]);
  });

  it("compares instants, not strings", () => {
    // 21:30+02:00 is 19:30Z — earlier than 20:00Z although it sorts after it as text.
    const rows = [
      row("VOD", "film-offset", "2026-09-19T21:30:00+02:00"),
      row("VOD", "film-utc", "2026-09-19T20:00:00Z"),
    ];

    expect(refs(resumableRows(rows, SOURCE))).toEqual(["film-utc", "film-offset"]);
  });

  it("keeps the active source only", () => {
    const rows = [
      row("VOD", "film-elsewhere", "2026-09-20T10:00:00Z", { source_id: OTHER_SOURCE }),
      row("VOD", "film-here", "2026-09-19T10:00:00Z"),
    ];

    expect(refs(resumableRows(rows, SOURCE))).toEqual(["film-here"]);
  });

  it("drops what is finished — past 95 % of a known duration", () => {
    const rows = [
      row("VOD", "film-finished", "2026-09-20T10:00:00Z", { position_ms: HOUR * 0.96 }),
      row("EPISODE", "episode-finished", "2026-09-20T09:00:00Z", { position_ms: HOUR }),
      row("VOD", "film-halfway", "2026-09-19T10:00:00Z"),
    ];

    expect(refs(resumableRows(rows, SOURCE))).toEqual(["film-halfway"]);
  });

  it("never calls finished a row whose duration is unknown", () => {
    // Most panels state no running time. Vanishing from the rail is a loss
    // nobody can recover; lingering is an annoyance.
    const rows = [
      row("VOD", "film-no-duration", "2026-09-19T10:00:00Z", {
        position_ms: HOUR * 5,
        duration_ms: null,
      }),
    ];

    expect(refs(resumableRows(rows, SOURCE))).toEqual(["film-no-duration"]);
  });

  it("sorts an unreadable timestamp last instead of failing", () => {
    const rows = [
      row("VOD", "film-odd", "not-a-date"),
      row("VOD", "film-fine", "2026-09-19T10:00:00Z"),
    ];

    expect(refs(resumableRows(rows, SOURCE))).toEqual(["film-fine", "film-odd"]);
  });

  it("does not reorder the list it was given", () => {
    const rows = [
      row("VOD", "film-old", "2026-09-10T20:00:00Z"),
      row("VOD", "film-new", "2026-09-19T20:00:00Z"),
    ];
    resumableRows(rows, SOURCE);

    expect(refs(rows)).toEqual(["film-old", "film-new"]);
  });
});

describe("onePerSeries", () => {
  it("keeps one card per series: its most recent episode in progress", () => {
    const rows = [
      row("EPISODE", "s1-e3", "2026-09-20T22:00:00Z"),
      row("EPISODE", "s1-e2", "2026-09-20T21:00:00Z"),
      row("EPISODE", "s2-e1", "2026-09-20T20:00:00Z"),
      row("EPISODE", "s1-e1", "2026-09-20T19:00:00Z"),
    ];
    const episodes = mapOf(
      episode("s1-e1", "series-1", 1, 1),
      episode("s1-e2", "series-1", 1, 2),
      episode("s1-e3", "series-1", 1, 3),
      episode("s2-e1", "series-2", 4, 1),
    );

    const picked = onePerSeries(rows, episodes);

    expect(refs(picked.map((candidate) => candidate.row))).toEqual(["s1-e3", "s2-e1"]);
    expect(picked[0]).toMatchObject({ kind: "episode", episode: { episode_number: 3 } });
  });

  it("leaves films where the merge put them", () => {
    const rows = [
      row("EPISODE", "s1-e2", "2026-09-20T22:00:00Z"),
      row("VOD", "film-a", "2026-09-20T21:00:00Z"),
      row("EPISODE", "s1-e1", "2026-09-20T20:00:00Z"),
      row("VOD", "film-b", "2026-09-20T19:00:00Z"),
    ];
    const episodes = mapOf(episode("s1-e1", "series-1"), episode("s1-e2", "series-1", 1, 2));

    expect(onePerSeries(rows, episodes).map((candidate) => candidate.row.item_ref)).toEqual([
      "s1-e2",
      "film-a",
      "film-b",
    ]);
  });

  it("drops an episode the last synchronisation removed, without taking its series' slot", () => {
    const rows = [
      row("EPISODE", "s1-gone", "2026-09-20T22:00:00Z"),
      row("EPISODE", "s1-e1", "2026-09-20T21:00:00Z"),
    ];

    expect(
      onePerSeries(rows, mapOf(episode("s1-e1", "series-1"))).map(
        (candidate) => candidate.row.item_ref,
      ),
    ).toEqual(["s1-e1"]);
  });
});

describe("continueCards", () => {
  const candidates = onePerSeries(
    [
      row("VOD", "film-a", "2026-09-20T22:00:00Z"),
      row("EPISODE", "s1-e1", "2026-09-20T21:00:00Z"),
      row("VOD", "film-gone", "2026-09-20T20:00:00Z"),
      row("VOD", "film-b", "2026-09-20T19:00:00Z"),
    ],
    mapOf(episode("s1-e1", "series-1")),
  );

  it("names each card and keeps the order", () => {
    const cards = continueCards(
      candidates,
      mapOf(film("film-a"), film("film-b")),
      mapOf(series("series-1")),
      12,
    );

    expect(cards.map((card) => card.key)).toEqual([
      "film:film-a",
      "series:series-1",
      "film:film-b",
    ]);
    expect(cards[1]).toMatchObject({
      kind: "episode",
      series: { name: "Bench series series-1" },
      episode: { id: "s1-e1" },
    });
  });

  it("drops what the catalogue no longer holds", () => {
    // `film-gone` and the series are absent from the lookups: dropped by the
    // last re-synchronisation, by contract. No gap, no card without a title.
    const cards = continueCards(candidates, mapOf(film("film-a")), new Map(), 12);

    expect(cards.map((card) => card.key)).toEqual(["film:film-a"]);
  });

  it("caps after dropping, so a missing film does not cost a card", () => {
    const cards = continueCards(
      candidates,
      mapOf(film("film-a"), film("film-b")),
      mapOf(series("series-1")),
      3,
    );

    // Four candidates, one unresolved: three cards, not two.
    expect(cards).toHaveLength(3);
    expect(cards.at(-1)?.key).toBe("film:film-b");
  });

  it("holds twelve cards at most on a busy account", () => {
    const many = Array.from({ length: 30 }, (_, index) =>
      row("VOD", `film-${index}`, new Date(Date.UTC(2026, 8, 20, 0, 30 - index)).toISOString()),
    );
    const cards = continueCards(
      onePerSeries(resumableRows(many, SOURCE), new Map()),
      mapOf(...many.map((entry) => film(entry.item_ref))),
      new Map(),
      12,
    );

    expect(cards).toHaveLength(12);
    expect(cards[0].key).toBe("film:film-0");
    expect(cards[11].key).toBe("film:film-11");
  });
});
