import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";

/**
 * The switch between a source's three catalogues.
 *
 * <h2>Always the three, and that reverses a rule this project had written down</h2>
 *
 * Films and series used to be hidden when a source had none — `S5-08` for the
 * phone tab, `adr/0010` ruling 3 for the television, and the first version of this
 * component. The argument was that an empty promise is worse than an absence.
 *
 * **Use disproved it.** The owner of a panel with a hundred and forty thousand
 * films could not find them, concluded the feature did not exist, and reported it
 * as missing. The absence was indistinguishable from a bug — which is the failure
 * the rule was supposed to prevent, arriving through the door it left open.
 *
 * So the tabs are always here, and an empty catalogue says so **in the list**,
 * where somebody who went looking finds an answer instead of nothing. That is the
 * one thing an absence can never do: explain itself.
 *
 * The old rule was not silly, and its remaining half still holds: a *promise* is
 * bad. A tab that says "Films" and opens onto a sentence explaining that this
 * source carries none is not a promise, it is a reply.
 */
export function CatalogueTabs({
  sourceId,
  locale,
  active,
  label,
  channelsLabel,
  filmsLabel,
  seriesLabel,
}: {
  sourceId: string;
  locale: Locale;
  active: "channels" | "vod" | "series";
  label: string;
  channelsLabel: string;
  filmsLabel: string;
  seriesLabel: string;
}) {
  const tabs = [
    { key: "channels" as const, href: `/app/sources/${sourceId}/channels`, label: channelsLabel },
    { key: "vod" as const, href: `/app/sources/${sourceId}/vod`, label: filmsLabel },
    { key: "series" as const, href: `/app/sources/${sourceId}/series`, label: seriesLabel },
  ];

  return (
    <nav aria-label={label} className="mt-4">
      <ul className="flex gap-2 text-sm">
        {tabs.map((tab) => (
          <li key={tab.key}>
            <a
              href={hrefFor(locale, tab.href)}
              // `aria-current` rather than colour alone: the open tab has to be
              // announced, not merely drawn.
              aria-current={tab.key === active ? "page" : undefined}
              className={
                tab.key === active
                  ? "bg-secondary text-secondary-foreground block rounded-lg px-3 py-1.5 font-medium"
                  : "text-muted-foreground hover:text-foreground block rounded-lg px-3 py-1.5"
              }
            >
              {tab.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
