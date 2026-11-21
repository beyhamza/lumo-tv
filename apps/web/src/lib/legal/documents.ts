import type { LegalDocumentType } from "@/lib/api/types";

/**
 * The legal documents and where each is read (S16B-08, ADR 0015).
 *
 * `/privacy` is the address given to Google Play: it does not move. `key` names
 * the document's labels in the `Legal` and `Admin` namespaces.
 */
export const LEGAL_DOCUMENTS = [
  { type: "PRIVACY_POLICY", href: "/privacy", key: "privacy" },
  { type: "TERMS_OF_USE", href: "/terms", key: "termsOfUse" },
  { type: "TERMS_OF_SALE", href: "/terms-of-sale", key: "termsOfSale" },
] as const satisfies readonly { type: LegalDocumentType; href: string; key: string }[];

export type LegalDocument = (typeof LEGAL_DOCUMENTS)[number];

export function legalDocument(type: LegalDocumentType): LegalDocument {
  return LEGAL_DOCUMENTS.find((document) => document.type === type)!;
}

/** Narrows a value posted by a form, which is the browser's to write. */
export function legalDocumentType(value: unknown): LegalDocumentType | null {
  return LEGAL_DOCUMENTS.find((document) => document.type === value)?.type ?? null;
}
