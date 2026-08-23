/**
 * Normalisation of an activation code (US-05).
 *
 * People read the code off a television across the room and type it with
 * whatever separators they saw — `K7RM-4XPQ`, `k7rm 4xpq`, a trailing space
 * from a paste, a non-breaking space from a copy. The contract says separators
 * are stripped and matching is case-insensitive, so doing it here means the
 * request that goes out is already canonical and a stray dash never costs
 * someone an attempt against a strict rate limit.
 *
 * Capped well above the eight characters the server stores: the cap is there to
 * stop a pathological input reaching the API, not to validate a length the
 * contract owns.
 *
 * The examples above are drawn from the alphabet the server actually uses,
 * which excludes `O`, `I`, `L`, `0` and `1` — nothing that can be misread at
 * three metres. They used to read `LUMO-4X7B`, a code carrying two characters
 * the server cannot emit; an example that could never occur is a quiet
 * invitation to widen the alphabet and reintroduce the ambiguity it exists to
 * avoid. Nothing here validates the alphabet, though: normalising is not
 * validating, and the server owns the pattern (`ApproveDeviceRequest`).
 */
export const MAX_ACTIVATION_CODE_LENGTH = 16;

export function normaliseActivationCode(value: string): string {
  return value
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, MAX_ACTIVATION_CODE_LENGTH);
}
