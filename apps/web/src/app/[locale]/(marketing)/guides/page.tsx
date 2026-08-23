import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { guideSlugs } from "@/content/guides";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { JsonLd, breadcrumbJsonLd } from "@/lib/seo/json-ld";
import { pageMetadata } from "@/lib/seo/metadata";

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/guides">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Guides" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/guides",
    title: t("metaTitle"),
    description: t("metaDescription"),
  });
}

export default async function GuidesIndexPage({
  params,
}: PageProps<"/[locale]/guides">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("Guides");
  const nav = await getTranslations("Nav");

  return (
    <>
      <JsonLd
        data={breadcrumbJsonLd([
          { name: nav("home"), path: `/${locale}` },
          { name: t("indexTitle"), path: `/${locale}/guides` },
        ])}
      />

      <div className="mx-auto w-full max-w-5xl px-4 py-16">
        <h1 className="text-3xl font-semibold tracking-tight">
          {t("indexTitle")}
        </h1>
        <p className="text-muted-foreground mt-3 max-w-2xl">{t("indexIntro")}</p>

        <ul className="mt-10 grid gap-6 sm:grid-cols-2">
          {guideSlugs.map((slug) => (
            <li
              key={slug}
              className="border-border rounded-xl border p-6"
            >
              <h2 className="font-medium">
                <a href={hrefFor(locale as Locale, `/guides/${slug}`)} className="underline-offset-4 hover:underline">
                  {t(`${slug}.title`)}
                </a>
              </h2>
              <p className="text-muted-foreground mt-2 text-sm">
                {t(`${slug}.description`)}
              </p>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
