import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * How to delete some of one's data while keeping the account: the public URL
 * given to Google Play as the data-deletion link (the optional question next
 * to the account-deletion one).
 *
 * Like `/delete-account`, it explains rather than acts: every deletion it
 * lists is an action that already exists behind a session (`DELETE
 * /sources/{id}`, favourites, watchlist, devices). Each item says what goes
 * with it, in the contract's own words, and what stays. Marketing zone, so no
 * client component and no cookie (`apps/web/AGENTS.md` §2).
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/delete-data">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "DataDeletion" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/delete-data",
    title: t("metaTitle"),
    description: t("metaDescription"),
  });
}

type DeletionItem = {
  title: string;
  steps: string[];
  removes: string;
};

export default async function DeleteDataPage({ params }: PageProps<"/[locale]/delete-data">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("DataDeletion");
  const items = t.raw("items") as DeletionItem[];
  const paragraph = "text-muted-foreground mt-3 max-w-[70ch]";
  const link = "underline underline-offset-4";

  return (
    <article className="mx-auto w-full max-w-3xl px-4 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">{t("title")}</h1>
      <p className="mt-6 max-w-[70ch]">{t("intro")}</p>
      <p className={paragraph}>{t("where")}</p>

      {items.map((item) => (
        <section key={item.title} className="mt-10">
          <h2 className="text-xl font-semibold tracking-tight">{item.title}</h2>
          <ol className="text-muted-foreground mt-3 list-decimal space-y-1 pl-6">
            {item.steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
          <p className={paragraph}>{item.removes}</p>
        </section>
      ))}

      {(["kept", "timing", "local", "help"] as const).map((key) => (
        <section key={key} className="mt-10">
          <h2 className="text-xl font-semibold tracking-tight">{t(`${key}Title`)}</h2>
          <p className={paragraph}>{t(`${key}Body`)}</p>
        </section>
      ))}

      <section className="mt-10">
        <h2 className="text-xl font-semibold tracking-tight">{t("everythingTitle")}</h2>
        <p className={paragraph}>{t("everythingBody")}</p>
        <p className="mt-3">
          <a href={hrefFor(locale as Locale, "/delete-account")} className={link}>
            {t("accountLink")}
          </a>
        </p>
      </section>

      <p className="mt-12">
        <a href={hrefFor(locale as Locale, "/privacy")} className={link}>
          {t("privacyLink")}
        </a>
      </p>
    </article>
  );
}
