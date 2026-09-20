import type { Source } from "@/lib/api/types";

/**
 * What a source holds — channels, films, series — **with the numbers that are
 * known and none of the others** (US-024, contract lot C4 — P3).
 *
 * <h2>An unknown value is never a zero</h2>
 *
 * "0 films" is a statement about somebody's subscription. It is true when the
 * server counted and found none, and false in every other case this function
 * sees: no catalogue ingested yet, a count request that failed, a count nobody
 * asked for. Each of those yields **no entry at all**, so the screen has nothing
 * to render a zero from. A real zero — the server answered `total_elements: 0` —
 * is kept and shown: that one is a fact.
 *
 * <h2>Where the numbers come from</h2>
 *
 * - channels and categories: `Source.channel_count` / `category_count`, null
 *   until a first ingestion has succeeded;
 * - films and series: `Source` carries no such field, and none is invented. They
 *   are `total_elements` of the paginated listings asked with `size=1`
 *   (`load-title-counts.ts`), `null` when that request failed and `undefined`
 *   when it was never made.
 *
 * <h2>A playlist has no series line</h2>
 *
 * Series are an Xtream feature (`adr/0010`): an M3U source cannot carry any, so
 * its series count is not a number that happens to be zero — it is a question
 * that does not apply. The entry is left out whatever was passed in, which also
 * keeps a caller from having to remember the rule.
 */
export type ContentCount =
  | { type: "channels"; count: number; categories: number | null }
  | { type: "films"; count: number }
  | { type: "series"; count: number };

export type TitleCounts = {
  /** `total_elements` of the films listing; null or absent when unknown. */
  films?: number | null;
  /** `total_elements` of the series listing; null or absent when unknown. */
  series?: number | null;
};

export function contentCounts(
  source: Pick<Source, "kind" | "last_synced_at" | "channel_count" | "category_count">,
  titles: TitleCounts = {},
): ContentCount[] {
  // No ingestion has ever succeeded: there is no catalogue to describe, and the
  // listings would answer `409 SOURCE_NOT_READY`. Nothing is known.
  if (source.last_synced_at == null) return [];

  const counts: ContentCount[] = [];

  if (isCount(source.channel_count)) {
    counts.push({
      type: "channels",
      count: source.channel_count,
      categories: isCount(source.category_count) ? source.category_count : null,
    });
  }
  if (isCount(titles.films)) {
    counts.push({ type: "films", count: titles.films });
  }
  if (seriesApply(source.kind) && isCount(titles.series)) {
    counts.push({ type: "series", count: titles.series });
  }
  return counts;
}

/** Whether a series count means anything for this kind of source (`adr/0010`). */
export function seriesApply(kind: Source["kind"]): boolean {
  return kind === "XTREAM";
}

/** A number the server could have counted: finite, whole, not negative. */
function isCount(value: number | null | undefined): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 0;
}

/**
 * The `App` message that says one entry, and its values.
 *
 * Returned as data rather than as a string so this module stays free of
 * next-intl and testable; the two screens that show counts — My sources and a
 * source's own page — both finish the job with `t(message.key, message.values)`,
 * which is what keeps "1 248 chaînes" spelt the same on both. Every message is
 * an ICU plural: "1 films" is the kind of thing nobody files and everybody sees.
 */
export type CountMessage =
  | { key: "sourceCounts"; values: { channels: number; categories: number } }
  | {
      key: "sourceCountChannels" | "sourceCountFilms" | "sourceCountSeries";
      values: { count: number };
    };

export function countMessage(entry: ContentCount): CountMessage {
  switch (entry.type) {
    case "channels":
      return entry.categories === null
        ? { key: "sourceCountChannels", values: { count: entry.count } }
        : {
            key: "sourceCounts",
            values: { channels: entry.count, categories: entry.categories },
          };
    case "films":
      return { key: "sourceCountFilms", values: { count: entry.count } };
    case "series":
      return { key: "sourceCountSeries", values: { count: entry.count } };
  }
}
