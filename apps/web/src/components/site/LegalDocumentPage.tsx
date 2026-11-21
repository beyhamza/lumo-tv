import type { Metadata } from "next";
import { getFormatter, getTranslations } from "next-intl/server";
import { LegalMarkdown } from "@/components/site/LegalMarkdown";
import type { Locale } from "@/i18n/routing";
import type { LegalDocumentType, LegalDocumentVersion } from "@/lib/api/types";
import { legalDocument } from "@/lib/legal/documents";
import { loadPublishedLegal } from "@/lib/legal/published";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * A public legal page (S16B-08, ADR 0015): the version in force, in the
 * visitor's language. Marketing zone: no client component, no cookie.
 *
 * With nothing published the page says so and stays out of the index.
 */
export function legalTitle(version: LegalDocumentVersion, locale: Locale): string {
  return locale === "fr" ? version.title_fr : version.title_en;
}

export async function legalMetadata(locale: Locale, type: LegalDocumentType): Promise<Metadata> {
  const { key, href } = legalDocument(type);
  const t = await getTranslations({ locale, namespace: "Legal" });
  const published = await loadPublishedLegal(type);

  return pageMetadata({
    locale,
    href,
    title: published.state === "published" ? legalTitle(published.version, locale) : t(`${key}.title`),
    description: t(`${key}.description`),
    index: published.state === "published",
  });
}

export async function PublishedLegalArticle({
  version,
  locale,
  children,
}: {
  version: LegalDocumentVersion;
  locale: Locale;
  children?: React.ReactNode;
}) {
  const t = await getTranslations("Legal");
  const format = await getFormatter();

  return (
    <article className="mx-auto w-full max-w-3xl px-4 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">{legalTitle(version, locale)}</h1>
      {version.published_at ? (
        <p className="text-muted-foreground mt-2 text-sm">
          {t("versionLine", {
            version: version.version ?? 0,
            date: format.dateTime(new Date(version.published_at), { dateStyle: "long" }),
          })}
        </p>
      ) : null}
      <div className="mt-6">
        <LegalMarkdown source={locale === "fr" ? version.body_fr : version.body_en} />
      </div>
      {children}
    </article>
  );
}

export async function LegalDocumentPage({ locale, type }: { locale: Locale; type: LegalDocumentType }) {
  const published = await loadPublishedLegal(type);
  if (published.state === "published") {
    return <PublishedLegalArticle version={published.version} locale={locale} />;
  }

  const t = await getTranslations("Legal");
  const { key } = legalDocument(type);
  return (
    <article className="mx-auto w-full max-w-3xl px-4 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">{t(`${key}.title`)}</h1>
      <p role="status" className="text-muted-foreground mt-6 max-w-[70ch]">
        {t("notPublished")}
      </p>
    </article>
  );
}
