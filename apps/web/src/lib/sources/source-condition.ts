import type { Source } from "@/lib/api/types";

/**
 * What a screen has to say about a source, and whether it has a catalogue to
 * show under it (US-017 "États validés", US-024, contract lot C4).
 *
 * <h2>Two questions, and they are independent</h2>
 *
 * - **Is there something to announce?** A synchronisation running, or a failure.
 * - **Is there a catalogue to browse?** Since contract lot C4 the API serves the
 *   previous catalogue in *every* status once one ingestion has succeeded;
 *   `409 SOURCE_NOT_READY` now only means "no catalogue yet".
 *
 * Reading the second one off `status` alone is the mistake this function exists
 * to prevent: a source re-synchronising for the hundredth time would lose its
 * rails for a minute every night, and one whose password expired yesterday would
 * lose them until somebody fixed it — while the API was perfectly willing to
 * serve both.
 *
 * `last_synced_at` is the witness: non-null means an ingestion completed at
 * least once. `READY` implies it, and is checked anyway so that this does not
 * depend on the two fields never disagreeing.
 *
 * <h2>`stale`: the catalogue on screen may be an old one</h2>
 *
 * True when the last attempt **failed** and a previous catalogue is what is
 * being shown (US-024, "Indisponibilité"). Not while a refresh runs: that one is
 * about to be replaced and says so in its own words; and not on a first import
 * that failed, where there is no catalogue to be old.
 *
 * <h2>Why this lives under `sources/` and not `home/`</h2>
 *
 * It was written for the home page (S8-04) and moved here, unchanged in its
 * rules, when the three catalogue pages and the source page started asking the
 * same two questions (S8-05). A second copy would be a second place to get the
 * `status`-versus-`last_synced_at` distinction wrong.
 */
export type SourceCondition = {
  notice: "syncing" | "error" | null;
  browsable: boolean;
  stale: boolean;
};

export function sourceCondition(
  source: Pick<Source, "status" | "last_synced_at">,
): SourceCondition {
  const browsable = source.status === "READY" || source.last_synced_at != null;

  switch (source.status) {
    case "PENDING":
    case "SYNCING":
      return { notice: "syncing", browsable, stale: false };
    case "ERROR":
      return { notice: "error", browsable, stale: browsable };
    case "READY":
      return { notice: null, browsable, stale: false };
  }
}
