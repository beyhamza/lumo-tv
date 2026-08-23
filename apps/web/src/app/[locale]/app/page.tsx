import { getTranslations, setRequestLocale } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";

/**
 * The account overview.
 *
 * A placeholder that navigates rather than a dashboard that lies: it links to
 * the three real sections instead of inventing counters the API has not been
 * asked for.
 */
export default async function AppOverviewPage({
  params,
}: PageProps<"/[locale]/app">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("App");

  const sections = [
    { href: "/app/sources", title: t("sourcesTitle"), body: t("sourcesSubtitle") },
    { href: "/app/devices", title: t("devicesTitle"), body: t("devicesSubtitle") },
    {
      href: "/app/subscription",
      title: t("subscriptionTitle"),
      body: t("subscriptionSubtitle"),
    },
  ] as const;

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">
        {t("overviewTitle")}
      </h1>
      <p className="text-muted-foreground mt-2">{t("overviewSubtitle")}</p>

      <div className="mt-8 grid gap-4 sm:grid-cols-3">
        {sections.map((section) => (
          <a
            key={section.href}
            href={hrefFor(locale as Locale, section.href)}
            className="border-border hover:border-foreground/30 rounded-xl border p-5 transition-colors"
          >
            <h2 className="font-medium">{section.title}</h2>
            <p className="text-muted-foreground mt-1 text-sm">{section.body}</p>
          </a>
        ))}
      </div>
    </>
  );
}
