import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { deleteLegalDraft, publishLegalDraft } from "@/actions/admin";
import { AdminNoticeBanner } from "@/components/app/admin/AdminNoticeBanner";
import { LegalDraftForm } from "@/components/app/admin/LegalDraftForm";
import { Unavailable } from "@/components/app/Unavailable";
import { LegalMarkdown } from "@/components/site/LegalMarkdown";
import { Button } from "@/components/ui/button";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { requireAdmin } from "@/lib/admin/require-admin";
import { ADMIN_FAILURES } from "@/lib/admin/notices";
import { api } from "@/lib/api/client";
import { fetchedResource } from "@/lib/api/fetched";
import type { LegalDocumentVersion } from "@/lib/api/types";
import { legalDocument } from "@/lib/legal/documents";
import { legalPlaceholders } from "@/lib/legal/markdown";
import { pageMetadata } from "@/lib/seo/metadata";
import { isUuid } from "@/lib/sources/active-source";

/**
 * One version of a legal document (S16B-08, ADR 0015).
 *
 * A draft is a form, then a publication step in the URL (`?confirm=publish`) that
 * shows both languages as the public will read them and names what is left to
 * complete. Publishing is one more press, with Cancel first in the document, as
 * for the other confirmations of this zone. Any other version is read-only.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/admin/legal/[id]">): Promise<Metadata> {
  const { locale, id } = await params;
  await requireAdmin();
  const t = await getTranslations({ locale, namespace: "Admin" });
  return pageMetadata({
    locale: locale as Locale,
    href: `/app/admin/legal/${id}`,
    title: t("navLegal"),
    description: t("subtitle"),
    index: false,
  });
}

function Preview({ version, label }: { version: LegalDocumentVersion; label: (lang: "fr" | "en") => string }) {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {(["fr", "en"] as const).map((lang) => (
        <section key={lang} lang={lang} aria-label={label(lang)} className="bg-card min-w-0 rounded-2xl px-6 py-5">
          <p className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase">{label(lang)}</p>
          <h3 className="mt-2 text-xl font-semibold tracking-tight">
            {lang === "fr" ? version.title_fr : version.title_en}
          </h3>
          <LegalMarkdown source={lang === "fr" ? version.body_fr : version.body_en} />
        </section>
      ))}
    </div>
  );
}

export default async function AdminLegalVersionPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/admin/legal/[id]">) {
  const { locale: rawLocale, id } = await params;
  const locale = rawLocale as Locale;
  setRequestLocale(locale);
  if (!isUuid(id)) notFound();

  const session = await requireAdmin();
  const query = await searchParams;
  const t = await getTranslations("Admin");
  const legal = await getTranslations("Legal");
  const notices = await getTranslations("Admin.notices");
  const format = await getFormatter();

  const self = hrefFor(locale, `/app/admin/legal/${id}`);
  const result = await fetchedResource(
    () => api(session.accessToken).GET("/admin/legal/versions/{id}", { params: { path: { id } } }),
    "LEGAL_VERSION_NOT_FOUND",
  );
  if (result.state === "gone") notFound();
  if (result.state !== "ok") return <Unavailable retryHref={self} />;

  const version = result.data;
  const { key, href } = legalDocument(version.document_type);
  const isDraft = version.status === "DRAFT";
  const confirming = isDraft && query.confirm === "publish";
  const deleting = isDraft && query.confirm === "delete";
  const placeholders = confirming
    ? [version.title_fr, version.title_en, version.body_fr, version.body_en].flatMap(legalPlaceholders)
    : [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <a href={hrefFor(locale, "/app/admin/legal")} className="text-muted-foreground text-[13px] underline underline-offset-4">
          {t("legalBack")}
        </a>
        <h2 className="mt-2 text-lg font-semibold tracking-tight">
          {legal(`${key}.title`)}
          {" · "}
          {version.version === null || version.version === undefined
            ? t("legalDraftLabel")
            : t("legalVersionLabel", { version: version.version })}
        </h2>
        <p className="text-muted-foreground mt-1 text-[13px]">
          {t(`legalStatus.${version.status}`)}
          {version.published_at
            ? ` · ${format.dateTime(new Date(version.published_at), { dateStyle: "long", timeStyle: "short" })}`
            : ""}
        </p>
      </div>

      <AdminNoticeBanner value={query.notice} />

      {confirming ? (
        <section id="publish-confirmation" aria-labelledby="publish-title" className="flex flex-col gap-4">
          <div className="bg-card rounded-2xl px-6 py-5">
            <h3 id="publish-title" className="font-semibold tracking-tight">
              {t("legalPublishTitle")}
            </h3>
            <p className="text-muted-foreground mt-2 max-w-[70ch] text-sm">
              {t("legalPublishBody", { path: href })}
            </p>
            {placeholders.length > 0 ? (
              <div role="alert" className="border-destructive/40 text-destructive mt-3 rounded-xl border px-4 py-3 text-sm">
                <p>{t("legalPlaceholdersLeft")}</p>
                <ul className="mt-2 list-disc pl-5">
                  {placeholders.map((placeholder, index) => (
                    <li key={index}>{placeholder}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            <div className="mt-4 flex flex-wrap gap-2">
              <Button variant="outline" asChild>
                <a href={self}>{t("cancel")}</a>
              </Button>
              {placeholders.length === 0 ? (
                <form action={publishLegalDraft}>
                  <input type="hidden" name="id" value={version.id} />
                  <Button type="submit">{t("legalPublish")}</Button>
                </form>
              ) : null}
            </div>
          </div>
          <Preview version={version} label={(lang) => t(lang === "fr" ? "languageFr" : "languageEn")} />
        </section>
      ) : isDraft ? (
        <>
          <LegalDraftForm
            id={version.id}
            initial={{
              title_fr: version.title_fr,
              title_en: version.title_en,
              body_fr: version.body_fr,
              body_en: version.body_en,
              change_summary: version.change_summary ?? "",
            }}
            labels={{
              titleFr: t("fieldTitleFr"),
              titleEn: t("fieldTitleEn"),
              bodyFr: t("fieldBodyFr"),
              bodyEn: t("fieldBodyEn"),
              bodyHint: t("fieldBodyHint"),
              changeSummary: t("fieldChangeSummary"),
              save: t("legalSave"),
              review: t("legalReview"),
            }}
            failureMessages={
              Object.fromEntries(ADMIN_FAILURES.map((failure) => [failure, notices(failure)])) as Record<
                (typeof ADMIN_FAILURES)[number],
                string
              >
            }
          />

          <section aria-labelledby="delete-title" id="delete-confirmation" className="border-border border-t pt-5">
            <h3 id="delete-title" className="text-sm font-semibold">
              {t("legalDeleteTitle")}
            </h3>
            {deleting ? (
              <>
                <p className="text-muted-foreground mt-1 text-sm">{t("legalDeleteBody")}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button variant="outline" asChild>
                    <a href={self}>{t("cancel")}</a>
                  </Button>
                  <form action={deleteLegalDraft}>
                    <input type="hidden" name="id" value={version.id} />
                    <Button type="submit" variant="destructive">
                      {t("legalDelete")}
                    </Button>
                  </form>
                </div>
              </>
            ) : (
              <a
                href={`${self}?confirm=delete#delete-confirmation`}
                className="text-muted-foreground mt-1 inline-block text-sm underline underline-offset-4"
              >
                {t("legalDelete")}
              </a>
            )}
          </section>
        </>
      ) : (
        <Preview version={version} label={(lang) => t(lang === "fr" ? "languageFr" : "languageEn")} />
      )}
    </div>
  );
}
