import { setRequestLocale } from "next-intl/server";
import { permanentRedirect } from "@/i18n/navigation";
import { settingsSectionPath } from "@/lib/settings/sections";

/**
 * The devices page moved into Settings, "Account and devices" (US-025,
 * S8-06). This route stays as a permanent redirect: the activation flow used
 * to send people here, and a bookmark to a page that managed their devices
 * should keep managing their devices.
 */
export default async function DevicesPage({ params }: PageProps<"/[locale]/app/devices">) {
  const { locale } = await params;
  setRequestLocale(locale);

  permanentRedirect({ href: settingsSectionPath("account"), locale });
}
