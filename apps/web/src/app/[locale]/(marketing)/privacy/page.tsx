import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * The privacy policy (S16B-03), the page Google Play asks for.
 *
 * Marketing zone, so prerendered: no client component, no cookie, links are
 * anchors (`apps/web/AGENTS.md` §2). The copy lives in the `Privacy`
 * namespace; it describes what the code actually stores, and the bracketed
 * passages are what only Hamza can fill in (controller, host, email
 * provider, retention). The draft notice stays until he approves the text.
 */
type Section = { title: string; body?: string[]; items?: string[] };

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/privacy">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Privacy" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/privacy",
    title: t("metaTitle"),
    description: t("metaDescription"),
  });
}

export default async function PrivacyPage({ params }: PageProps<"/[locale]/privacy">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("Privacy");
  const sections = t.raw("sections") as Section[];

  return (
    <article className="mx-auto w-full max-w-3xl px-4 py-16">
      <p role="note" className="border-destructive/40 text-destructive rounded-xl border px-4 py-3 text-sm">
        {t("draftNotice")}
      </p>

      <h1 className="mt-8 text-3xl font-semibold tracking-tight">{t("title")}</h1>
      <p className="text-muted-foreground mt-2 text-sm">{t("updated")}</p>
      <p className="mt-6 max-w-[70ch]">{t("intro")}</p>

      {sections.map((section) => (
        <section key={section.title} className="mt-10">
          <h2 className="text-xl font-semibold tracking-tight">{section.title}</h2>
          {section.body?.map((paragraph) => (
            <p key={paragraph} className="text-muted-foreground mt-3 max-w-[70ch]">
              {paragraph}
            </p>
          ))}
          {section.items ? (
            <ul className="text-muted-foreground mt-3 list-disc space-y-1 pl-6">
              {section.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          ) : null}
        </section>
      ))}

      <p className="mt-12">
        <a
          href={hrefFor(locale as Locale, "/delete-account")}
          className="underline underline-offset-4"
        >
          {t("deletionLink")}
        </a>
      </p>
    </article>
  );
}
