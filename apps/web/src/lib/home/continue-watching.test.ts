import { describe, expect, it } from "vitest";
import type { Series, VodItem } from "@/lib/api/types";
import {
  continueCards,
  continueRows,
  onePerSeries,
  resolveTargets,
  seriesNeedingTree,
} from "./continue-watching";

// Bench identifiers and bench titles only: no real film, series or provider is
// named in this repository, tests included.
const SOURCE = "11111111-1111-4111-8111-111111111111";
const OTHER_SOURCE = "22222222-2222-4222-8222-222222222222";

const HOUR = 3_600_000;

type Row = {
  source_id: string;
  item_type: "VOD" | "EPISODE";
  item_ref: string;
  position_ms: number;
  duration_ms: number | null;
  updated_at: string;
  hidden: boolean;
  in_continue: boolean;
};

function row(
  itemType: "VOD" | "EPISODE",
  itemRef: string,
  updatedAt: string,
  overrides: Partial<Row> = {},
): Row {
  return {
    source_id: SOURCE,
    item_type: itemType,
    item_ref: itemRef,
    position_ms: HOUR / 2,
    duration_ms: HOUR,
    updated_at: updatedAt,
    hidden: false,
    in_continue: true,
    ...overrides,
  };
}

/** A finished episode as the server returns it: out of Continue, not hidden. */
const finished = { position_ms: HOUR * 0.99, in_continue: false };

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

describe("continueRows", () => {
  it("merges films and episodes, most recently updated first", () => {
    const rows = [
      row("VOD", "film-new", "2026-09-19T20:00:00Z"),
      row("VOD", "film-old", "2026-09-10T20:00:00Z"),
      row("EPISODE", "episode-newest", "2026-09-20T08:00:00Z"),
      row("EPISODE", "episode-middle", "2026-09-15T08:00:00Z"),
    ];

    expect(refs(continueRows(rows, SOURCE))).toEqual([
      "episode-newest",
      "film-new",
      "episode-middle",
      "film-old",
    ]);
  });

  it("compares instants, not strings", () => {
    const rows = [
      row("VOD", "film-offset", "2026-09-19T21:30:00+02:00"),
      row("VOD", "film-utc", "2026-09-19T20:00:00Z"),
    ];
    expect(refs(continueRows(rows, SOURCE))).toEqual(["film-utc", "film-offset"]);
  });

  it("keeps the active source only (CW-16)", () => {
    const rows = [
      row("VOD", "film-elsewhere", "2026-09-20T10:00:00Z", { source_id: OTHER_SOURCE }),
      row("VOD", "film-here", "2026-09-19T10:00:00Z"),
    ];
    expect(refs(continueRows(rows, SOURCE))).toEqual(["film-here"]);
  });

  it("follows the server's in_continue, not a threshold of its own", () => {
    const rows = [
      // Under 30 s actually watched (CW-01/02), although well into the film.
      row("VOD", "film-not-yet", "2026-09-20T10:00:00Z", { in_continue: false }),
      // Eligible with no duration (CW-09): the server says so, that is enough.
      row("VOD", "film-eligible", "2026-09-19T10:00:00Z", { duration_ms: null }),
    ];
    expect(refs(continueRows(rows, SOURCE))).toEqual(["film-eligible"]);
  });

  it("drops a finished film but keeps a finished episode for its next one (CW-07, CW-19)", () => {
    const rows = [
      row("VOD", "film-finished", "2026-09-20T10:00:00Z", finished),
      row("EPISODE", "episode-finished", "2026-09-20T09:00:00Z", finished),
    ];
    expect(refs(continueRows(rows, SOURCE))).toEqual(["episode-finished"]);
  });

  it("drops what the user removed (CW-10)", () => {
    const rows = [
      row("VOD", "film-hidden", "2026-09-20T10:00:00Z", { hidden: true, in_continue: false }),
      row("EPISODE", "episode-hidden", "2026-09-20T09:00:00Z", {
        ...finished,
        hidden: true,
      }),
    ];
    expect(continueRows(rows, SOURCE)).toEqual([]);
  });

  it("sorts an unreadable timestamp last instead of failing", () => {
    const rows = [row("VOD", "film-odd", "not-a-date"), row("VOD", "film-fine", "2026-09-19T10:00:00Z")];
    expect(refs(continueRows(rows, SOURCE))).toEqual(["film-fine", "film-odd"]);
  });
});

describe("onePerSeries", () => {
  it("keeps one card per series: its most recent episode", () => {
    const rows = [
      row("EPISODE", "s1-e3", "2026-09-20T22:00:00Z"),
      row("EPISODE", "s1-e2", "2026-09-20T21:00:00Z"),
      row("EPISODE", "s2-e1", "2026-09-20T20:00:00Z"),
    ];
    const episodes = mapOf(
      episode("s1-e2", "series-1", 1, 2),
      episode("s1-e3", "series-1", 1, 3),
      episode("s2-e1", "series-2", 4, 1),
    );

    const picked = onePerSeries(rows, episodes);
    expect(refs(picked.map((candidate) => candidate.row))).toEqual(["s1-e3", "s2-e1"]);
    expect(picked[0]).toMatchObject({ kind: "episode", episode: { episode_number: 3 } });
  });

  it("lets a more recent finished episode decide its series' card", () => {
    const rows = continueRows(
      [
        row("EPISODE", "s1-e2", "2026-09-20T22:00:00Z", finished),
        row("EPISODE", "s1-e1", "2026-09-20T21:00:00Z"),
      ],
      SOURCE,
    );
    const picked = onePerSeries(
      rows,
      mapOf(episode("s1-e1", "series-1", 1, 1), episode("s1-e2", "series-1", 1, 2)),
    );
    expect(picked).toHaveLength(1);
    expect(picked[0]).toMatchObject({ kind: "finished", row: { item_ref: "s1-e2" } });
    expect(seriesNeedingTree(picked)).toEqual(["series-1"]);
  });

  it("drops an episode the last synchronisation removed, without taking its series' slot", () => {
    const rows = [
      row("EPISODE", "s1-gone", "2026-09-20T22:00:00Z"),
      row("EPISODE", "s1-e1", "2026-09-20T21:00:00Z"),
    ];
    expect(
      onePerSeries(rows, mapOf(episode("s1-e1", "series-1"))).map((c) => c.row.item_ref),
    ).toEqual(["s1-e1"]);
  });
});

describe("resolveTargets", () => {
  const tree = [
    episode("e1", "series-1", 1, 1),
    episode("e2", "series-1", 1, 2),
    episode("e3", "series-1", 1, 3),
  ];
  const trees = new Map([["series-1", tree]]);
  const episodes = mapOf(...tree);

  function targetsFor(rows: Row[]) {
    const candidates = onePerSeries(continueRows(rows, SOURCE), episodes);
    return resolveTargets(candidates, trees, rows);
  }

  it("resumes an episode in Continue at its position, hiding by its own ref", () => {
    expect(targetsFor([row("EPISODE", "e2", "2026-09-20T22:00:00Z")])).toEqual([
      {
        kind: "episode",
        episode: tree[1],
        positionMs: HOUR / 2,
        durationMs: HOUR,
        hide: { itemType: "EPISODE", itemRef: "e2" },
      },
    ]);
  });

  it("proposes the next episode, never started, from zero (CW-19)", () => {
    const [target] = targetsFor([row("EPISODE", "e1", "2026-09-20T22:00:00Z", finished)]);
    expect(target).toMatchObject({ kind: "episode", episode: { id: "e2" }, positionMs: 0 });
    // Removing the card hides the series through the episode that has progress.
    expect(target.hide).toEqual({ itemType: "EPISODE", itemRef: "e1" });
  });

  it("resumes the next episode where it was left (CW-12)", () => {
    const [target] = targetsFor([
      row("EPISODE", "e2", "2026-09-01T00:00:00Z", { position_ms: 600_000, in_continue: false }),
      row("EPISODE", "e1", "2026-09-20T22:00:00Z", finished),
    ]);
    // e1 is the most recent; e2 was started earlier and is under the threshold.
    expect(target).toMatchObject({ episode: { id: "e2" }, positionMs: 600_000 });
  });

  it("replays a next episode already finished from the start (CW-21)", () => {
    const [target] = targetsFor([
      row("EPISODE", "e2", "2026-09-01T00:00:00Z", finished),
      row("EPISODE", "e1", "2026-09-20T22:00:00Z", finished),
    ]);
    expect(target).toMatchObject({ episode: { id: "e2" }, positionMs: 0, durationMs: null });
  });

  it("drops a series whose last episode is finished (CW-15)", () => {
    expect(targetsFor([row("EPISODE", "e3", "2026-09-20T22:00:00Z", finished)])).toEqual([]);
  });

  it("drops a finished series whose tree could not be read, rather than guessing", () => {
    const candidates = onePerSeries(
      continueRows([row("EPISODE", "e1", "2026-09-20T22:00:00Z", finished)], SOURCE),
      episodes,
    );
    expect(resolveTargets(candidates, new Map(), [])).toEqual([]);
  });

  it("shows nothing for a series the user removed, even with a next episode", () => {
    expect(
      targetsFor([row("EPISODE", "e1", "2026-09-20T22:00:00Z", { ...finished, hidden: true })]),
    ).toEqual([]);
  });
});

describe("continueCards", () => {
  const rows = [
    row("VOD", "film-a", "2026-09-20T22:00:00Z"),
    row("EPISODE", "s1-e1", "2026-09-20T21:00:00Z"),
    row("VOD", "film-gone", "2026-09-20T20:00:00Z"),
    row("VOD", "film-b", "2026-09-20T19:00:00Z"),
  ];
  const targets = resolveTargets(
    onePerSeries(continueRows(rows, SOURCE), mapOf(episode("s1-e1", "series-1"))),
    new Map(),
    rows,
  );

  it("names each card, keeps the order and carries what to hide", () => {
    const cards = continueCards(
      targets,
      mapOf(film("film-a"), film("film-b")),
      mapOf(series("series-1")),
      12,
    );

    expect(cards.map((card) => card.key)).toEqual([
      "film:film-a",
      "series:series-1",
      "film:film-b",
    ]);
    expect(cards[0].hide).toEqual({ itemType: "VOD", itemRef: "film-a" });
    expect(cards[1]).toMatchObject({
      kind: "episode",
      series: { name: "Bench series series-1" },
      episode: { id: "s1-e1" },
      hide: { itemType: "EPISODE", itemRef: "s1-e1" },
    });
  });

  it("drops what the catalogue no longer holds", () => {
    const cards = continueCards(targets, mapOf(film("film-a")), new Map(), 12);
    expect(cards.map((card) => card.key)).toEqual(["film:film-a"]);
  });

  it("caps after dropping, so a missing film does not cost a card", () => {
    const cards = continueCards(
      targets,
      mapOf(film("film-a"), film("film-b")),
      mapOf(series("series-1")),
      3,
    );
    expect(cards).toHaveLength(3);
    expect(cards.at(-1)?.key).toBe("film:film-b");
  });

  it("holds twelve cards at most on a busy account", () => {
    const many = Array.from({ length: 30 }, (_, index) =>
      row("VOD", `film-${index}`, new Date(Date.UTC(2026, 8, 20, 0, 30 - index)).toISOString()),
    );
    const cards = continueCards(
      resolveTargets(onePerSeries(continueRows(many, SOURCE), new Map()), new Map(), []),
      mapOf(...many.map((entry) => film(entry.item_ref))),
      new Map(),
      12,
    );
    expect(cards).toHaveLength(12);
    expect(cards[0].key).toBe("film:film-0");
    expect(cards[11].key).toBe("film:film-11");
  });
});
