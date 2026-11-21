import type { Metadata } from "next";
import { setRequestLocale } from "next-intl/server";
import { LegalDocumentPage, legalMetadata } from "@/components/site/LegalDocumentPage";
import type { Locale } from "@/i18n/routing";

/** The terms of use, the CGU (S16B-08). Written and published from the administration. */
export async function generateMetadata({ params }: PageProps<"/[locale]/terms">): Promise<Metadata> {
  const { locale } = await params;
  return legalMetadata(locale as Locale, "TERMS_OF_USE");
}

export default async function TermsPage({ params }: PageProps<"/[locale]/terms">) {
  const { locale } = await params;
  setRequestLocale(locale);
  return <LegalDocumentPage locale={locale as Locale} type="TERMS_OF_USE" />;
}
