import { setRequestLocale } from "next-intl/server";
import { redirect } from "@/i18n/navigation";
import { DEFAULT_SETTINGS_SECTION, settingsSectionPath } from "@/lib/settings/sections";

/**
 * `/app/settings` is the rail's entry, and it opens on the first section.
 *
 * A redirect rather than a page rendering the account section under this
 * URL: one URL per section, so the list on the left is a set of links and the
 * browser's back button behaves (`lib/settings/sections.ts`).
 */
export default async function SettingsIndexPage({
  params,
}: PageProps<"/[locale]/app/settings">) {
  const { locale } = await params;
  setRequestLocale(locale);

  redirect({ href: settingsSectionPath(DEFAULT_SETTINGS_SECTION), locale });
}
