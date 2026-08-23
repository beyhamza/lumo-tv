import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
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
 * `GET /me/devices` is in the contract and has no controller yet — sprint 1
 * covers auth, sources and catalogue — so this page renders "not built yet"
 * rather than the outage state it used to show. That was the audit's C1: a
 * feature nobody had written was being reported as a service failure, which
 * told the user to come back in a minute and told us nothing at all.
 *
 * It is not a hard-coded placeholder: the day the endpoint answers, this page
 * lists devices with no change here.
 *
 * Read-only for now rather than shipping a revoke button that does nothing
 * (`DELETE /me/devices/{id}` is in the same unimplemented group): a control that
 * looks live and is not is worse than no control.
 */
export default async function DevicesPage({
  params,
}: PageProps<"/[locale]/app/devices">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const format = await getFormatter();

  const devices = await fetched(() =>
    api(session.accessToken).GET("/me/devices", {}),
  );

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">
        {t("devicesTitle")}
      </h1>
      <p className="text-muted-foreground mt-2">{t("devicesSubtitle")}</p>

      <div className="mt-8">
        {devices.state === "unavailable" ? (
          <Unavailable />
        ) : devices.state === "not-implemented" ? (
          <NotBuiltYet />
        ) : devices.data.items.length === 0 ? (
          <EmptyState title={t("devicesEmpty")} hint={t("devicesEmptyHint")} />
        ) : (
          <ul className="space-y-3">
            {devices.data.items.map((device) => (
              <li
                key={device.id}
                className="border-border flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border px-5 py-4"
              >
                <span className="font-medium">
                  {device.name ?? device.model ?? device.platform}
                </span>
                <span className="text-muted-foreground text-sm">
                  {device.platform}
                </span>
                {device.last_seen_at ? (
                  <span className="text-muted-foreground ml-auto text-sm">
                    {t("devicesLastSeen", {
                      date: format.dateTime(new Date(device.last_seen_at), {
                        dateStyle: "medium",
                      }),
                    })}
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
