import type { SyncStep } from "@/lib/api/types";

/** The `App` message naming each ingestion phase. */
export type SyncStepKey =
  | "syncStepConnecting"
  | "syncStepAuthenticated"
  | "syncStepParsingChannels"
  | "syncStepParsingVod"
  | "syncStepParsingSeries"
  | "syncStepFetchingEpg";

/**
 * One case per value, and no `default`.
 *
 * The original fell through to "fetching the guide", which was harmless until the
 * contract grew a fifth phase — and then it labelled the film catalogue as the
 * EPG. Made exhaustive, `PARSING_VOD` became a type error the moment it was
 * generated rather than a wrong word on somebody's screen.
 *
 * It has now paid for itself a second time: `PARSING_SERIES` failed the build in
 * the same commit that added it to the contract. A `default` here would have
 * shipped "fetching the programme guide" over the series list instead.
 *
 * Lifted out of the source page in S8-04, unchanged, when the home page started
 * naming the step too: two copies of this switch would be two places for the
 * next phase to be given the wrong word, and only one of them would fail the
 * build if the other had grown a `default` in the meantime.
 */
export function stepKey(step: SyncStep): SyncStepKey {
  switch (step) {
    case "CONNECTING":
      return "syncStepConnecting";
    case "AUTHENTICATED":
      return "syncStepAuthenticated";
    case "PARSING_CHANNELS":
      return "syncStepParsingChannels";
    case "PARSING_VOD":
      return "syncStepParsingVod";
    case "PARSING_SERIES":
      return "syncStepParsingSeries";
    case "FETCHING_EPG":
      return "syncStepFetchingEpg";
  }
}
