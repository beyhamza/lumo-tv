import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * How to delete one's account (S16B-02): the public URL given to Google Play
 * as the account-deletion link.
 *
 * It explains rather than acts. Deleting needs a signed-in session and the
 * password (`POST /me/delete`, ADR 0014), which this prerendered page cannot
 * hold; it says where the action is on each surface and what it removes, in
 * the contract's own list. Marketing zone, so no client component and no
 * cookie (`apps/web/AGENTS.md` §2).
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/delete-account">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "AccountDeletion" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/delete-account",
    title: t("metaTitle"),
    description: t("metaDescription"),
  });
}

export default async function DeleteAccountPage({
  params,
}: PageProps<"/[locale]/delete-account">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("AccountDeletion");
  const steps = (key: "webSteps" | "appSteps") => t.raw(key) as string[];

  const paragraph = "text-muted-foreground mt-3 max-w-[70ch]";

  return (
    <article className="mx-auto w-full max-w-3xl px-4 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">{t("title")}</h1>
      <p className="mt-6 max-w-[70ch]">{t("intro")}</p>

      <section className="mt-10">
        <h2 className="text-xl font-semibold tracking-tight">{t("stepsTitle")}</h2>

        <h3 className="mt-6 font-medium">{t("webTitle")}</h3>
        <ol className="text-muted-foreground mt-2 list-decimal space-y-1 pl-6">
          {steps("webSteps").map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>

        <h3 className="mt-6 font-medium">{t("appTitle")}</h3>
        <ol className="text-muted-foreground mt-2 list-decimal space-y-1 pl-6">
          {steps("appSteps").map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>

        <p className={paragraph}>{t("noPassword")}</p>
      </section>

      <section className="mt-10">
        <h2 className="text-xl font-semibold tracking-tight">{t("deletedTitle")}</h2>
        <ul className="text-muted-foreground mt-3 list-disc space-y-1 pl-6">
          {(t.raw("deletedItems") as string[]).map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
        <p className={paragraph}>{t("deletedBody")}</p>
      </section>

      {(["devices", "backups", "provider", "help"] as const).map((key) => (
        <section key={key} className="mt-10">
          <h2 className="text-xl font-semibold tracking-tight">{t(`${key}Title`)}</h2>
          <p className={paragraph}>{t(`${key}Body`)}</p>
        </section>
      ))}

      <p className="mt-12">
        <a href={hrefFor(locale as Locale, "/privacy")} className="underline underline-offset-4">
          {t("privacyLink")}
        </a>
      </p>
    </article>
  );
}
