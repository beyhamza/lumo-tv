import type { EpgImportStatus } from "@/lib/api/types";

/**
 * How much to trust a guide that was just read (C1, D3 and D4 — S9-03).
 *
 * <h2>It dates the import, never the content</h2>
 *
 * `EpgImportStatus` says when *this server* last imported the guide. Nothing
 * says when the provider last updated it — an XMLTV file carries no
 * publication date — so the interface says "last guide import" and never
 * "programmes up to date", and this function names its states after the
 * import, not after the programmes.
 *
 * <h2>The 24-hour rule, to the millisecond</h2>
 *
 * US-16 and D4: **strictly more than** 24 hours since the last successful
 * import is old; 24 hours exactly is not yet. It informs, it blocks nothing —
 * a stale guide is still drawn, with its date beside it.
 *
 * <h2>Whose clock</h2>
 *
 * The age starts as `generated_at − last_successful_import_at`, both written
 * by the same server clock, then grows with the time elapsed *here* since the
 * answer was produced. Neither the moment this process fetched the answer nor
 * `generated_at` alone stands in for the import date (D3).
 *
 * When the server's own two dates disagree — an answer produced *before* the
 * import it reports — the age cannot be trusted. It is reported as zero and
 * flagged `unreliable` rather than hidden behind a reassuring "just now": D4
 * asks that a clock inconsistency not be masked by a mention of freshness.
 *
 * <h2>An attempt in trouble is said first</h2>
 *
 * A running, failed or interrupted attempt is reported before any date, even
 * when the last success is recent (D3): the stored guide may be half of two
 * imports at that moment, and a fresh-looking date would vouch for it.
 */

/** Strictly more than this since the last successful import is stale (D4). */
export const STALE_AFTER_MS = 24 * 60 * 60 * 1000;

export type EpgFreshness =
  /** The source has no guide URL: every list is empty by construction. */
  | { kind: "not-configured" }
  /** No status at all, or none recorded under the current configuration. */
  | { kind: "unknown" }
  | { kind: "fresh"; ageMs: number; importedAt: Date; unreliable: boolean }
  | { kind: "stale"; ageMs: number; importedAt: Date; unreliable: boolean }
  | { kind: "attempt-running"; lastImportedAt: Date | null }
  | { kind: "attempt-failed"; lastImportedAt: Date | null }
  | { kind: "attempt-interrupted"; lastImportedAt: Date | null };

/**
 * @param status the import record the answer carried, or `undefined` when the
 *   answer had none — an `EpgProgrammeList` from a client older than the
 *   field, or no answer at all.
 * @param generatedAt `EpgGrid.generated_at`: the server's clock when it
 *   produced the answer.
 * @param now this process's clock at the moment of rendering.
 */
export function epgFreshness(
  status: EpgImportStatus | undefined,
  generatedAt: string,
  now: Date,
): EpgFreshness {
  if (status === undefined) return { kind: "unknown" };
  if (!status.configured) return { kind: "not-configured" };

  const importedAt = instant(status.last_successful_import_at);

  switch (status.last_attempt_status) {
    case "RUNNING":
      return { kind: "attempt-running", lastImportedAt: importedAt };
    case "FAILED":
      return { kind: "attempt-failed", lastImportedAt: importedAt };
    case "INTERRUPTED":
      return { kind: "attempt-interrupted", lastImportedAt: importedAt };
    case "UNKNOWN":
    case "SUCCEEDED":
      break;
  }

  if (importedAt === null) return { kind: "unknown" };

  // An unreadable `generated_at` falls back to this clock: the age is then
  // measured across two machines, which is the situation the flag describes.
  const generated = instant(generatedAt);
  const reference = generated ?? now;
  const rewound = reference.getTime() < importedAt.getTime();
  const base = rewound ? 0 : reference.getTime() - importedAt.getTime();
  // Time elapsed here since the answer; a negative value is the two clocks
  // disagreeing by a moment and counts as nothing rather than as a rewind.
  const elapsed = Math.max(0, now.getTime() - reference.getTime());
  const ageMs = base + elapsed;
  const unreliable = generated === null || rewound;

  return ageMs > STALE_AFTER_MS
    ? { kind: "stale", ageMs, importedAt, unreliable }
    : { kind: "fresh", ageMs, importedAt, unreliable };
}

/** A date, or `null` for an absent or unreadable one. */
function instant(value: string | null | undefined): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}
