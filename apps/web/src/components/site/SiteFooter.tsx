import { getTranslations } from "next-intl/server";
import { MockBadge } from "@/components/site/MockBadge";
import { Wordmark } from "@/components/site/Wordmark";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";

/**
 * The marketing footer (W1): dark, logotype, `Guides` · `Pricing` ·
 * `Privacy` · `Terms` · `Contact`, and the domain in monospace.
 *
 * Dark inside a light page is the one place the design system allows it — the
 * footer is a full-width band, so the `.dark` scope covers everything it
 * paints and no light strip can show through (globals.css).
 *
 * The notice is not decoration: AGENTS.md §1 makes it a product rule that Lumo
 * provides, hosts and resells no content, and the place a visitor looks for that
 * is the footer.
 *
 * `Privacy`, `Terms` and `Contact` have no route yet
 * (docs/design/web-sprint-1.md, "Delta avec la landing livrée"). They are
 * rendered as the mock-up shows them, unlinked and labelled as missing, rather
 * than as links to a 404.
 */
export async function SiteFooter({ locale }: { locale: Locale }) {
  const t = await getTranslations("Footer");
  const nav = await getTranslations("Nav");
  const brand = await getTranslations("Brand");
  const home = hrefFor(locale, "/");

  return (
    <footer className="dark bg-background text-foreground mt-auto">
      <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center gap-x-8 gap-y-4 px-6 py-10 sm:px-10 lg:px-16">
        <a href={home} className="inline-flex">
          <Wordmark size="sm" />
        </a>

        <nav aria-label={t("navLabel")} className="text-muted-foreground flex flex-wrap items-center gap-x-6 gap-y-2 text-[13px]">
          <a href={hrefFor(locale, "/guides")} className="hover:text-foreground">
            {nav("guides")}
          </a>
          <a href={`${home}#pricing`} className="hover:text-foreground">
            {nav("pricing")}
          </a>
          <span>{t("privacy")}</span>
          <span>{t("terms")}</span>
          <span>{t("contact")}</span>
          <MockBadge />
        </nav>

        <span className="text-muted-foreground/70 ml-auto font-mono text-xs">{brand("domain")}</span>
      </div>

      <p className="text-muted-foreground mx-auto w-full max-w-7xl px-6 pb-8 text-xs sm:px-10 lg:px-16">
        {t("rights")}
      </p>
    </footer>
  );
}
