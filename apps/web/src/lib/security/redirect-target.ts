/**
 * Validation of a `next` redirect target.
 *
 * Lives in its own module for two reasons: a `"use server"` file may only export
 * async functions, so a helper cannot be exported from the action that uses it —
 * and this is security logic, which means it ships with its tests
 * (AGENTS.md §5).
 *
 * The attack it closes: `lumo.tv/fr/login?next=https://evil.example` would send
 * someone who has just typed their password straight to a page that looks like
 * ours. Anything that is not an in-site path is refused rather than sanitised —
 * refusing lands the visitor on their account page, which is harmless, while
 * sanitising invites a bypass.
 */
export function safeRedirectTarget(
  value: unknown,
  locales: readonly string[],
  fallback = "/app",
): string {
  if (typeof value !== "string") return fallback;
  if (!value.startsWith("/")) return fallback;

  // `//evil.example` is protocol-relative: the browser reads it as a host, not
  // as a local path. `/\evil.example` is treated the same way by several
  // browsers, which is why any backslash disqualifies the value outright.
  if (value.startsWith("//")) return fallback;
  if (value.includes("\\")) return fallback;

  // Control characters — including a newline, which is how header injection
  // starts — have no business in a path this application generated. A hyphen
  // does (`/guides/m3u-playlist`), so it is not in this set.
  for (const character of value) {
    const code = character.codePointAt(0) ?? 0;
    if (code < 0x20 || code === 0x7f) return fallback;
  }

  // The locale prefix is re-added by the locale-aware redirect, so it is
  // stripped here to avoid `/fr/fr/app`.
  const withoutLocale = value.replace(
    new RegExp(`^/(${locales.join("|")})(?=/|$)`),
    "",
  );

  return withoutLocale || fallback;
}
