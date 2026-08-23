import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { EmptyState, Unavailable } from "@/components/app/Unavailable";
import { api } from "@/lib/api/client";
import { requireSession } from "@/lib/session/session";

/**
 * The devices linked to the account.
 *
 * This is where revoking a stolen television will live (`DELETE /me/devices/{id}`
 * in the contract). It is a read-only list for now rather than a set of buttons
 * that do nothing: a control that looks live and is not is worse than no control.
 */
export default async function DevicesPage({
  params,
}: PageProps<"/[locale]/app/devices">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const format = await getFormatter();

  let devices: Array<{
    id: string;
    platform: string;
    name?: string | null;
    model?: string | null;
    last_seen_at?: string | null;
  }> | null = null;

  try {
    const { data } = await api(session.accessToken).GET("/me/devices", {});
    devices = data?.items ?? null;
  } catch {
    devices = null;
  }

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">
        {t("devicesTitle")}
      </h1>
      <p className="text-muted-foreground mt-2">{t("devicesSubtitle")}</p>

      <div className="mt-8">
        {devices === null ? (
          <Unavailable />
        ) : devices.length === 0 ? (
          <EmptyState title={t("devicesEmpty")} hint={t("devicesEmptyHint")} />
        ) : (
          <ul className="space-y-3">
            {devices.map((device) => (
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
