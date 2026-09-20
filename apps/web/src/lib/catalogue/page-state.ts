/**
 * What a catalogue page — channels, films, series — does with the answers to its
 * two main requests: the listing, and the categories beside it (US-024,
 * "Indisponibilité": a partly available catalogue keeps its available parts and
 * **names** the ones that did not load, without presenting them as empty).
 *
 * <h2>Before this, one failure took the whole page</h2>
 *
 * The three pages computed `problemCode(listing) ?? problemCode(categories)` and
 * rendered an error for either. A categories request that failed hid forty
 * thousand films that had loaded fine; and the other shape of the same mistake
 * — treating a failed request as `items: []` — would draw "No film in this
 * category", which is a statement about the catalogue made from no information.
 *
 * <h2>The decisions</h2>
 *
 * - `SOURCE_NOT_FOUND` from either: the source is gone, there is no page.
 * - `SOURCE_NOT_READY` from either: no catalogue has **ever** been ingested —
 *   the only meaning that code has left since contract lot C4. Both requests sit
 *   behind the same server-side guard, so one saying it is enough.
 * - both failed otherwise: the listing's code, or "unavailable" when there is no
 *   code to show (no response at all).
 * - exactly one failed: the page renders, and `listingFailed` /
 *   `categoriesFailed` tell it which part to label "could not be loaded".
 *
 * Pure, so the three pages share one rule and the rule has tests.
 */
export type RequestOutcome = {
  /** True when the request produced a body. */
  ok: boolean;
  /** The RFC 7807 `code` of a refusal, when there was one. */
  code?: string;
};

export type CataloguePageState =
  | { kind: "not-ready" }
  | { kind: "error"; code: string }
  | { kind: "unavailable" }
  | { kind: "ok"; listingFailed: boolean; categoriesFailed: boolean };

export function cataloguePageState(
  listing: RequestOutcome,
  categories: RequestOutcome,
): CataloguePageState {
  const codes = [listing, categories]
    .filter((outcome) => !outcome.ok)
    .map((outcome) => outcome.code);

  if (codes.includes("SOURCE_NOT_FOUND")) return { kind: "error", code: "SOURCE_NOT_FOUND" };
  if (codes.includes("SOURCE_NOT_READY")) return { kind: "not-ready" };

  if (!listing.ok && !categories.ok) {
    const code = listing.code ?? categories.code;
    return code ? { kind: "error", code } : { kind: "unavailable" };
  }

  return { kind: "ok", listingFailed: !listing.ok, categoriesFailed: !categories.ok };
}
