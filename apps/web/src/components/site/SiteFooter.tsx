import { getTranslations } from "next-intl/server";

/**
 * The marketing footer.
 *
 * The notice is not decoration: AGENTS.md §1 makes it a product rule that Lumo
 * provides, hosts and resells no content, and the place a visitor looks for that
 * is the footer.
 */
export async function SiteFooter() {
  const t = await getTranslations("Footer");
  const brand = await getTranslations("Brand");

  return (
    <footer className="border-border/60 mt-auto border-t">
      <div className="text-muted-foreground mx-auto w-full max-w-5xl px-4 py-8 text-sm">
        <p className="font-medium">{brand("name")}</p>
        <p className="mt-1">{t("rights")}</p>
      </div>
    </footer>
  );
}
