import type { Metadata } from "next";
import { setRequestLocale } from "next-intl/server";
import { LegalDocumentPage, legalMetadata } from "@/components/site/LegalDocumentPage";
import type { Locale } from "@/i18n/routing";

/**
 * The terms of sale, the CGV (S16B-08). Written and published from the
 * administration. Not linked from the footer: nothing is for sale in 0.4.0.
 */
export async function generateMetadata({ params }: PageProps<"/[locale]/terms-of-sale">): Promise<Metadata> {
  const { locale } = await params;
  return legalMetadata(locale as Locale, "TERMS_OF_SALE");
}

export default async function TermsOfSalePage({ params }: PageProps<"/[locale]/terms-of-sale">) {
  const { locale } = await params;
  setRequestLocale(locale);
  return <LegalDocumentPage locale={locale as Locale} type="TERMS_OF_SALE" />;
}
