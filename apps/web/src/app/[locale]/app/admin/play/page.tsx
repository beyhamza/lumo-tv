import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { requireAdmin } from "@/lib/admin/require-admin";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { siteUrl } from "@/lib/env.public";
import { pageMetadata } from "@/lib/seo/metadata";
import { cn } from "@/lib/utils";

/**
 * What to enter in the Play Console's "Set up your app" tasks, in their order
 * (S16B-08). A reading aid: nothing here talks to Google, and the Console remains
 * what says a task is done.
 *
 * Only what the site can see carries a status: the policy in force and the contact
 * address. Demo credentials are not on this page, nor anywhere in this
 * application: they go in the Console. The detailed answers and their reasons are in
 * `docs/releases/0.4.0/play/securite-des-donnees.md`.
 */
type PlayTask = { id: string; title: string; answer: string; details?: string[] };

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/admin/play">): Promise<Metadata> {
  const { locale } = await params;
  await requireAdmin();
  const t = await getTranslations({ locale, namespace: "Admin" });
  return pageMetadata({
    locale: locale as Locale,
    href: "/app/admin/play",
    title: t("navPlay"),
    description: t("subtitle"),
    index: false,
  });
}

export default async function AdminPlayPage({ params }: PageProps<"/[locale]/app/admin/play">) {
  const { locale: rawLocale } = await params;
  const locale = rawLocale as Locale;
  setRequestLocale(locale);

  const session = await requireAdmin();
  const t = await getTranslations("Admin.play");

  const client = api(session.accessToken);
  const [settings, versions] = await Promise.all([
    fetched(() => client.GET("/admin/settings", {})),
    fetched(() => client.GET("/admin/legal/versions", {})),
  ]);
  if (settings.state !== "ok" || versions.state !== "ok") {
    return <Unavailable retryHref={hrefFor(locale, "/app/admin/play")} />;
  }

  const base = siteUrl().replace(/\/$/, "");
  const privacyPublished = versions.data.items.some(
    (row) => row.document_type === "PRIVACY_POLICY" && row.status === "PUBLISHED",
  );
  const supportEmail = settings.data.support_email ?? null;

  // A task absent from here has nothing the site can check.
  const readiness: Record<string, { ready: boolean; label: string }> = {
    privacy: privacyPublished
      ? { ready: true, label: t("privacyReady") }
      : { ready: false, label: t("privacyMissing") },
    contact: supportEmail
      ? { ready: true, label: t("contactReady", { email: supportEmail }) }
      : { ready: false, label: t("contactMissing") },
  };

  const addresses = [
    { label: t("addressPrivacy"), value: `${base}/${locale}/privacy` },
    { label: t("addressDeletion"), value: `${base}/${locale}/delete-account` },
    { label: t("addressSite"), value: base },
  ];
  const tasks = t.raw("tasks") as PlayTask[];

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <p className="text-muted-foreground text-sm">{t("intro")}</p>

      <section aria-labelledby="play-addresses" className="bg-card rounded-2xl px-6 py-5">
        <h2 id="play-addresses" className="font-semibold tracking-tight">
          {t("addressesTitle")}
        </h2>
        <dl className="mt-3 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[auto_1fr]">
          {addresses.map((address) => (
            <div key={address.label} className="contents">
              <dt className="text-muted-foreground">{address.label}</dt>
              <dd className="font-mono text-[13px] break-all">{address.value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <ol className="flex flex-col gap-3">
        {tasks.map((task) => {
          const status = readiness[task.id];
          return (
            <li key={task.id} className="bg-card rounded-2xl px-6 py-5">
              <h2 className="font-semibold tracking-tight">{task.title}</h2>
              {status ? (
                <p
                  role="status"
                  className={cn("mt-1 text-[13px]", status.ready ? "text-foreground" : "text-destructive")}
                >
                  {status.label}
                </p>
              ) : null}
              <p className="mt-2 text-sm">{task.answer}</p>
              {task.details ? (
                <ul className="text-muted-foreground mt-2 list-disc space-y-1 pl-5 text-[13px]">
                  {task.details.map((detail) => (
                    <li key={detail}>{detail}</li>
                  ))}
                </ul>
              ) : null}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
