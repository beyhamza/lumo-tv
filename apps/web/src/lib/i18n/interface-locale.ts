import { routing, type Locale } from "@/i18n/routing";

/**
 * The interface language chosen in Settings → Application (S13-04, Q6).
 *
 * <h2>What it is, and what it is not</h2>
 *
 * On the web the language is the URL's: `/fr/...` or `/en/...`, and a
 * prefixed URL always wins — a shared link opens in the language it was
 * written in. Choosing in Settings **moves to the other locale**, on the same
 * section, and remembers the choice in this cookie for the only requests
 * that carry no language: unprefixed ones (`/`, `/app`), which next-intl would
 * otherwise send by its own cookie, then `Accept-Language`, then French.
 *
 * - **This browser only**: no `PATCH /me` (Q6 — the account's locale is left
 *   as it is), and nothing changes on another device.
 * - **Kept after sign-out**: not an account cookie, so `expireAccountCookies`
 *   does not touch it (Q6: "Choix explicite maintenu après déconnexion").
 * - **A year**, not a session: next-intl's own `NEXT_LOCALE` is a session
 *   cookie that follows the last URL visited; an explicit choice outlives the
 *   browser being closed.
 * - Independent of the playback languages (PS-01): those are initialised once
 *   and never follow the interface afterwards.
 *
 * Marketing pages are prefixed and prerendered; none of them reads this.
 */
export const INTERFACE_LOCALE_COOKIE = "lumo_ui_locale";
export const INTERFACE_LOCALE_MAX_AGE_SECONDS = 365 * 24 * 3600;

/** A cookie or form value narrowed to a supported locale, or null. */
export function explicitLocale(value: unknown): Locale | null {
  return typeof value === "string" && (routing.locales as readonly string[]).includes(value)
    ? (value as Locale)
    : null;
}

/**
 * The remembered choice, for a request whose path carries no locale; null
 * when the path names one (it wins) or nothing valid was remembered.
 */
export function localeForUnprefixed(pathname: string, remembered: unknown): Locale | null {
  const first = pathname.split("/")[1] ?? "";
  if ((routing.locales as readonly string[]).includes(first)) return null;
  return explicitLocale(remembered);
}
