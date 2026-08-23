/**
 * Normalisation of an activation code (US-05).
 *
 * People read the code off a television across the room and type it with
 * whatever separators they saw — `LUMO-4X7B`, `lumo 4x7b`, a trailing space
 * from a paste, a non-breaking space from a copy. The contract says separators
 * are stripped and matching is case-insensitive, so doing it here means the
 * request that goes out is already canonical and a stray dash never costs
 * someone an attempt against a strict rate limit.
 *
 * Capped well above the eight characters the server stores: the cap is there to
 * stop a pathological input reaching the API, not to validate a length the
 * contract owns.
 */
export const MAX_ACTIVATION_CODE_LENGTH = 16;

export function normaliseActivationCode(value: string): string {
  return value
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, MAX_ACTIVATION_CODE_LENGTH);
}
