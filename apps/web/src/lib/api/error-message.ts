/**
 * The API error codes this UI has a translated message for.
 *
 * The contract is explicit on two points: `code` is the only field a client may
 * branch on, and an unrecognised value must degrade gracefully rather than
 * crash, because new codes can be added within v1. Hence a list plus a
 * fallback, never an exhaustive switch.
 *
 * One list rather than one per page: four screens map codes to copy, and four
 * copies of this array would drift the first time a code is added.
 */
export const TRANSLATED_ERROR_CODES = [
  "VALIDATION_FAILED",
  "INVALID_CREDENTIALS",
  "EMAIL_ALREADY_REGISTERED",
  "PASSWORD_TOO_WEAK",
  "RATE_LIMITED",
  "UNAUTHENTICATED",
  "VERIFICATION_TOKEN_INVALID",
  "VERIFICATION_TOKEN_EXPIRED",
  "RESET_TOKEN_INVALID",
  "RESET_TOKEN_EXPIRED",
  "DEVICE_CODE_NOT_FOUND",
  "DEVICE_CODE_EXPIRED",
  "DEVICE_CODE_ALREADY_USED",
  // Sources and ingestion. Every IngestionErrorCode is here, and that is the
  // point of the enumeration: the contract says no client may fall back to a
  // generic message on this surface, because the user's next action differs
  // completely between "the server is down" and "your password is wrong".
  "SOURCE_NOT_FOUND",
  "SOURCE_NOT_READY",
  "SOURCE_SYNC_IN_PROGRESS",
  "SOURCE_SYNC_RATE_LIMITED",
  "SOURCE_UNREACHABLE",
  "SOURCE_AUTH_FAILED",
  "SOURCE_EXPIRED",
  "SOURCE_MAX_CONNECTIONS",
  "SOURCE_INVALID_FORMAT",
  "SOURCE_EMPTY",
  "SOURCE_TOO_LARGE",
  // Plan limits. Distinct from CONFLICT so the screen can offer the way out.
  "SOURCE_LIMIT_REACHED",
  "DEVICE_LIMIT_REACHED",
  "CHANNEL_NOT_FOUND",
] as const;

export type TranslatedErrorCode = (typeof TRANSLATED_ERROR_CODES)[number];

/** Every key `errorMessage` can hand back, all of them in the `Errors` namespace. */
export type ErrorMessageKey = TranslatedErrorCode | "network" | "generic";

/**
 * Turns whatever came back — an API code, the sentinel `NETWORK`, or something
 * nobody has seen before — into a key of the `Errors` namespace.
 *
 * @param t the `Errors` translator, passed in so this stays free of next-intl
 * and usable from a Server Component without a provider.
 */
export function errorMessage(
  code: string | undefined,
  t: (key: ErrorMessageKey) => string,
): string {
  if (code && (TRANSLATED_ERROR_CODES as readonly string[]).includes(code)) {
    return t(code as TranslatedErrorCode);
  }
  return code === "NETWORK" ? t("network") : t("generic");
}
