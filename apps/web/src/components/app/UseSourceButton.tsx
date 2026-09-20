import { selectSource } from "@/actions/active-source";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { cn } from "@/lib/utils";

/**
 * "Use this source", from My sources (US-024, US-018).
 *
 * <h2>The switcher's own action, not a second way to switch</h2>
 *
 * A `<form>` posting to `selectSource`, exactly like a row of the rail's
 * `SourceSwitcher`: the choice is a cookie, only a Server Action can write it,
 * and a form is what works with JavaScript disabled. The action checks the id
 * against `GET /sources` itself, so nothing here needs to be trusted.
 *
 * <h2>The user stays where they are</h2>
 *
 * `from` is the page this button is on, and the action's allow-list
 * (`lib/sources/switch-target.ts`) keeps both My sources and a source's own page
 * in place. Choosing a source from the list of sources is not asking to leave
 * it — somebody comparing three subscriptions would be thrown back to the home
 * page three times.
 *
 * @param from the path of the current page **without** its locale, as the pages
 *   know it (`/app/sources`, `/app/sources/{id}`).
 */
export function UseSourceButton({
  sourceId,
  from,
  locale,
  label,
  accessibleLabel,
  className,
}: {
  sourceId: string;
  from: string;
  locale: Locale;
  label: string;
  /** Names the source, for a list where every row carries the same button. */
  accessibleLabel?: string;
  className?: string;
}) {
  return (
    <form action={selectSource}>
      <input type="hidden" name="sourceId" value={sourceId} />
      {/* Locale included, because that is what the action validates against:
          the rail sends the pathname the proxy reported, prefix and all. */}
      <input type="hidden" name="from" value={hrefFor(locale, from)} />
      <button
        type="submit"
        aria-label={accessibleLabel}
        className={cn(
          "bg-secondary text-foreground hover:bg-secondary/80 inline-flex h-9 items-center rounded-full px-4 text-[13px] font-medium",
          className,
        )}
      >
        {label}
      </button>
    </form>
  );
}
