import { headers } from "next/headers";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { requireAdmin } from "@/lib/admin/require-admin";
import { PATHNAME_HEADER } from "@/lib/http/pathname-header";
import { cn } from "@/lib/utils";

/**
 * The administration (S16B-08, ADR 0015): a 404 for anyone but an administrator
 * (`requireAdmin`). Plain links and Server Components, like the rest of the zone.
 */
export default async function AdminLayout({ children, params }: LayoutProps<"/[locale]/app/admin">) {
  const { locale: rawLocale } = await params;
  const locale = rawLocale as Locale;
  setRequestLocale(locale);

  await requireAdmin();
  const t = await getTranslations("Admin");
  const pathname = (await headers()).get(PATHNAME_HEADER) ?? "";

  const entries = [
    { href: hrefFor(locale, "/app/admin/legal"), label: t("navLegal") },
    { href: hrefFor(locale, "/app/admin/settings"), label: t("navSettings") },
    { href: hrefFor(locale, "/app/admin/play"), label: t("navPlay") },
  ];

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground/80 mt-1 text-[13px]">{t("subtitle")}</p>
      </header>

      <nav aria-label={t("navLabel")} className="flex flex-row flex-wrap gap-1.5">
        {entries.map((entry) => {
          const isCurrent = pathname.startsWith(entry.href);
          return (
            <a
              key={entry.href}
              href={entry.href}
              aria-current={isCurrent ? "page" : undefined}
              className={cn(
                "flex h-10 items-center rounded-xl px-3.5 text-sm",
                isCurrent
                  ? "bg-secondary text-foreground font-medium"
                  : "text-muted-foreground hover:bg-card hover:text-foreground",
              )}
            >
              {entry.label}
            </a>
          );
        })}
      </nav>

      {children}
    </div>
  );
}
