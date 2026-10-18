import { getTranslations } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { SETTINGS_ENTRIES, type SettingsSection } from "@/lib/settings/sections";
import { cn } from "@/lib/utils";

/**
 * The list on the left of Settings (docs/design/0.2.0/settings.md): the five
 * rubriques of US-025, in the validated order (`SETTINGS_ENTRIES`).
 *
 * "My sources" is a link out to `/app/sources`, not a section: the screen
 * exists (US-024) and this list opens it rather than copying it. It is never
 * marked current here, because on that page this list is not on screen.
 *
 * Plain anchors, and a `<nav>` with its own name so a screen reader can tell
 * it from the account rail beside it.
 */
const LABELS = {
  account: "sectionAccount",
  sources: "sectionSources",
  playback: "sectionPlayback",
  application: "sectionApplication",
  about: "sectionAbout",
} as const;

export async function SettingsNav({
  locale,
  current,
}: {
  locale: Locale;
  current: SettingsSection;
}) {
  const t = await getTranslations("Settings");

  return (
    <nav aria-label={t("navLabel")} className="flex flex-row flex-wrap gap-1.5 md:flex-col">
      {SETTINGS_ENTRIES.map((entry) => {
        const isCurrent = entry.section === current;
        return (
          <a
            key={entry.href}
            href={hrefFor(locale, entry.href)}
            aria-current={isCurrent ? "page" : undefined}
            className={cn(
              "flex h-10 items-center rounded-xl px-3.5 text-sm",
              isCurrent
                ? "bg-secondary text-foreground font-medium"
                : "text-muted-foreground hover:bg-card hover:text-foreground",
            )}
          >
            {t(LABELS[entry.key])}
          </a>
        );
      })}
    </nav>
  );
}
