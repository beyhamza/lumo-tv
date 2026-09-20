import type { Source } from "@/lib/api/types";

/**
 * What the home page has to say about the active source, and whether it has a
 * catalogue to show under it (US-017, "États validés").
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
 */
export type SourceCondition = {
  notice: "syncing" | "error" | null;
  browsable: boolean;
};

export function sourceCondition(
  source: Pick<Source, "status" | "last_synced_at">,
): SourceCondition {
  const browsable = source.status === "READY" || source.last_synced_at != null;

  switch (source.status) {
    case "PENDING":
    case "SYNCING":
      return { notice: "syncing", browsable };
    case "ERROR":
      return { notice: "error", browsable };
    case "READY":
      return { notice: null, browsable };
  }
}
