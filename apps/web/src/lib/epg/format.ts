/**
 * A programme's time, as the reader expects to see one (S9-03).
 *
 * <h2>Whose time zone</h2>
 *
 * The product says "les heures s'affichent dans le fuseau de l'appareil"
 * (sprint 9). A Server Component does not know the device's zone: no request
 * header carries it. What it has is the zone next-intl is configured with —
 * `Europe/Paris`, pinned in `src/i18n/request.ts` so that a date rendered on
 * the server and again on the client cannot disagree — and that is the zone
 * the pages pass in here, read through `getTimeZone()`. It is not invented:
 * had no zone been configured, the fallback would be UTC, said as such.
 *
 * A visitor in another zone therefore reads Paris time. That is a limit of
 * rendering on the server and it is recorded, not hidden; the fix is a zone
 * chosen by the user and stored with the account, which is a product decision.
 *
 * <h2>`Intl`, with the zone made explicit</h2>
 *
 * `toLocaleTimeString` without a zone formats in the *process's* zone, which
 * is whatever the host was installed with — the class of bug `AGENTS.md` §7
 * bans it for. `Intl.DateTimeFormat` with an explicit `timeZone` is
 * deterministic wherever the process runs. Formatters are cached: creating one
 * costs more than using it fifty times, and a catalogue page uses it fifty
 * times.
 *
 * The hour cycle is the locale's — `20:05` in French, `8:05 PM` in English —
 * because a time is read the way the reader's language writes it.
 */

/** The zone used when none is configured. Named so that a caller cannot pass "". */
export const FALLBACK_TIME_ZONE = "UTC";

const formatters = new Map<string, Intl.DateTimeFormat>();

/**
 * `20:05` / `8:05 PM`, or `undefined` for an unreadable instant — a line the
 * caller then does not draw, rather than "Invalid Date" under a channel.
 */
export function clockTime(
  instant: Date | string,
  locale: string,
  timeZone: string = FALLBACK_TIME_ZONE,
): string | undefined {
  const date = instant instanceof Date ? instant : new Date(instant);
  if (Number.isNaN(date.getTime())) return undefined;

  const key = `${locale}|${timeZone}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, {
      hour: "numeric",
      minute: "2-digit",
      timeZone,
    });
    formatters.set(key, formatter);
  }
  return formatter.format(date);
}
