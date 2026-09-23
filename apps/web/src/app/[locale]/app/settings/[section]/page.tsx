import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { AboutSection } from "@/components/app/settings/AboutSection";
import { AccountSection } from "@/components/app/settings/AccountSection";
import { ApplicationSection } from "@/components/app/settings/ApplicationSection";
import { SettingsNav } from "@/components/app/settings/SettingsNav";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { resolveSettingsSection } from "@/lib/settings/sections";

/**
 * Settings (US-025, S8-06): the list on the left, one section on the right
 * (docs/design/0.2.0/settings.md).
 *
 * The section is the last path segment, narrowed by `resolveSettingsSection`;
 * anything else is a 404. The list and the section are both Server
 * Components, every link is an anchor and every destructive action is a form
 * posting to a Server Action: nothing here needs JavaScript, which is also
 * what keeps the access token on the server (`apps/web/AGENTS.md` §3–4).
 *
 * What the sections show — and, as deliberately, what they do not — is
 * documented on each of them. In one line: no "Playback" (sprint 13), no
 * subscription (removed from 0.2.0), no language control (sprint 13), no
 * privacy or terms link (no page to link to).
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/settings/[section]">): Promise<Metadata> {
  const { locale, section } = await params;
  const t = await getTranslations({ locale, namespace: "Settings" });

  return pageMetadata({
    locale: locale as Locale,
    href: `/app/settings/${section}`,
    title: t("metaTitle"),
    description: t("metaDescription"),
    index: false,
  });
}

export default async function SettingsPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/settings/[section]">) {
  const { locale: rawLocale, section: rawSection } = await params;
  const locale = rawLocale as Locale;
  setRequestLocale(locale);

  const section = resolveSettingsSection(rawSection);
  if (!section) notFound();

  const session = await requireSession();
  const query = await searchParams;
  const t = await getTranslations("Settings");

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground/80 mt-1 text-[13px]">{t("subtitle")}</p>
      </header>

      <div className="grid gap-6 md:grid-cols-[220px_minmax(0,1fr)] md:gap-10">
        <SettingsNav locale={locale} current={section} />

        <div className="min-w-0">
          {section === "account" ? (
            <AccountSection
              locale={locale}
              email={session.email}
              accessToken={session.accessToken}
              query={query}
            />
          ) : section === "application" ? (
            <ApplicationSection locale={locale} />
          ) : (
            <AboutSection locale={locale} />
          )}
        </div>
      </div>
    </div>
  );
}
