import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { saveAppSettings } from "@/actions/admin";
import { AdminNoticeBanner } from "@/components/app/admin/AdminNoticeBanner";
import { Unavailable } from "@/components/app/Unavailable";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { requireAdmin } from "@/lib/admin/require-admin";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * The application's configuration (S16B-08, ADR 0015 §6). Plan quotas are not
 * here: what a plan allows does not move to a screen without its own decision.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/admin/settings">): Promise<Metadata> {
  const { locale } = await params;
  await requireAdmin();
  const t = await getTranslations({ locale, namespace: "Admin" });
  return pageMetadata({
    locale: locale as Locale,
    href: "/app/admin/settings",
    title: t("navSettings"),
    description: t("subtitle"),
    index: false,
  });
}

export default async function AdminSettingsPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/admin/settings">) {
  const { locale: rawLocale } = await params;
  const locale = rawLocale as Locale;
  setRequestLocale(locale);

  const session = await requireAdmin();
  const query = await searchParams;
  const t = await getTranslations("Admin");

  const settings = await fetched(() => api(session.accessToken).GET("/admin/settings", {}));
  if (settings.state !== "ok") return <Unavailable retryHref={hrefFor(locale, "/app/admin/settings")} />;

  return (
    <div className="flex max-w-xl flex-col gap-6">
      <AdminNoticeBanner value={query.notice} />

      <form action={saveAppSettings} className="bg-card flex flex-col gap-5 rounded-2xl px-6 py-5">
        <div className="flex flex-col gap-2">
          <Label htmlFor="support_email">{t("fieldSupportEmail")}</Label>
          <Input
            id="support_email"
            name="support_email"
            type="email"
            maxLength={254}
            defaultValue={settings.data.support_email ?? ""}
          />
          <p className="text-muted-foreground text-[13px]">{t("fieldSupportEmailHint")}</p>
        </div>

        <div className="flex items-start gap-3">
          <input
            id="registration_open"
            name="registration_open"
            type="checkbox"
            defaultChecked={settings.data.registration_open}
            className="accent-primary mt-0.5 size-4"
          />
          <div>
            <Label htmlFor="registration_open">{t("fieldRegistrationOpen")}</Label>
            <p className="text-muted-foreground mt-1 text-[13px]">{t("fieldRegistrationOpenHint")}</p>
          </div>
        </div>

        <div>
          <Button type="submit">{t("settingsSave")}</Button>
        </div>
      </form>
    </div>
  );
}
