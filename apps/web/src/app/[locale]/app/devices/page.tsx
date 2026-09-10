import { getTranslations, setRequestLocale } from "next-intl/server";
import { DeviceRows } from "@/components/app/DeviceRows";
import {
  EmptyState,
  NotBuiltYet,
  Unavailable,
} from "@/components/app/Unavailable";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { requireSession } from "@/lib/session/session";

/**
 * The devices linked to the account.
 *
 * The same rows as the preview on the sources page, unabridged: the name (with
 * "this device" on the one reading the page, from `Device.is_current`), and
 * when each was last seen — online, active under 24 hours, a relative date
 * beyond (docs/design/web-sprint-1.md, W3).
 *
 * `fetched` keeps the two fallbacks apart: "not built yet" if the endpoint had
 * no controller, "unavailable" if the API is down. That was the audit's C1: a
 * feature nobody had written was being reported as a service failure.
 *
 * Read-only for now rather than shipping a revoke button that does nothing: a
 * control that looks live and is not is worse than no control.
 */
export default async function DevicesPage({
  params,
}: PageProps<"/[locale]/app/devices">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");

  const devices = await fetched(() =>
    api(session.accessToken).GET("/me/devices", {}),
  );

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">{t("devicesTitle")}</h1>
      <p className="text-muted-foreground/80 mt-1 text-[13px]">{t("devicesSubtitle")}</p>

      <div className="mt-6">
        {devices.state === "unavailable" ? (
          <Unavailable />
        ) : devices.state === "not-implemented" ? (
          <NotBuiltYet />
        ) : devices.data.items.length === 0 ? (
          <EmptyState title={t("devicesEmpty")} hint={t("devicesEmptyHint")} />
        ) : (
          <div className="bg-card rounded-2xl px-6 py-3">
            <DeviceRows devices={devices.data.items} showPlatform />
          </div>
        )}
      </div>
    </>
  );
}
