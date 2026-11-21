import type { Metadata } from "next";
import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { createLegalDraft } from "@/actions/admin";
import { AdminNoticeBanner } from "@/components/app/admin/AdminNoticeBanner";
import { Unavailable } from "@/components/app/Unavailable";
import { Button } from "@/components/ui/button";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { requireAdmin } from "@/lib/admin/require-admin";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { LEGAL_DOCUMENTS } from "@/lib/legal/documents";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * The legal documents: for each one, what is in force, the draft, the history
 * (S16B-08, ADR 0015). A document opens a new draft only when it has none.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/admin/legal">): Promise<Metadata> {
  const { locale } = await params;
  await requireAdmin();
  const t = await getTranslations({ locale, namespace: "Admin" });
  return pageMetadata({
    locale: locale as Locale,
    href: "/app/admin/legal",
    title: t("navLegal"),
    description: t("subtitle"),
    index: false,
  });
}

export default async function AdminLegalPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/admin/legal">) {
  const { locale: rawLocale } = await params;
  const locale = rawLocale as Locale;
  setRequestLocale(locale);

  const session = await requireAdmin();
  const query = await searchParams;
  const t = await getTranslations("Admin");
  const legal = await getTranslations("Legal");
  const format = await getFormatter();

  const versions = await fetched(() => api(session.accessToken).GET("/admin/legal/versions", {}));
  if (versions.state !== "ok") return <Unavailable retryHref={hrefFor(locale, "/app/admin/legal")} />;

  return (
    <div className="flex flex-col gap-6">
      <AdminNoticeBanner value={query.notice} />

      {LEGAL_DOCUMENTS.map((document) => {
        const rows = versions.data.items.filter((row) => row.document_type === document.type);
        const hasDraft = rows.some((row) => row.status === "DRAFT");
        const hasPublished = rows.some((row) => row.status === "PUBLISHED");

        return (
          <section key={document.type} aria-labelledby={`legal-${document.key}`} className="bg-card rounded-2xl px-6 py-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <h2 id={`legal-${document.key}`} className="text-lg font-semibold tracking-tight">
                  {legal(`${document.key}.title`)}
                </h2>
                <p className="text-muted-foreground mt-1 text-[13px]">
                  {hasPublished ? (
                    <a
                      href={hrefFor(locale, document.href)}
                      className="font-mono underline underline-offset-4"
                    >
                      {document.href}
                    </a>
                  ) : (
                    t("legalNothingPublished")
                  )}
                </p>
              </div>
              {hasDraft ? null : (
                <form action={createLegalDraft}>
                  <input type="hidden" name="document_type" value={document.type} />
                  <Button type="submit" variant="outline">
                    {t("legalNewDraft")}
                  </Button>
                </form>
              )}
            </div>

            {rows.length > 0 ? (
              <ul className="border-border mt-4 divide-y border-t">
                {rows.map((row) => (
                  <li key={row.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-3 text-sm">
                    <a
                      href={hrefFor(locale, `/app/admin/legal/${row.id}`)}
                      className="font-medium underline underline-offset-4"
                    >
                      {row.version === null || row.version === undefined
                        ? t("legalDraftLabel")
                        : t("legalVersionLabel", { version: row.version })}
                    </a>
                    <span className="text-muted-foreground text-[13px]">
                      {t(`legalStatus.${row.status}`)}
                    </span>
                    <span className="text-muted-foreground/80 text-[13px]">
                      {format.dateTime(new Date(row.published_at ?? row.updated_at), {
                        dateStyle: "medium",
                      })}
                    </span>
                    {row.change_summary ? (
                      <span className="text-muted-foreground min-w-0 text-[13px]">{row.change_summary}</span>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : null}
          </section>
        );
      })}
    </div>
  );
}
