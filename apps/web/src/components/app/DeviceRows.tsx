import { getFormatter, getTranslations } from "next-intl/server";
import type { Device } from "@/lib/api/types";
import { deviceActivity } from "@/lib/devices/activity";
import { cn } from "@/lib/utils";

/**
 * One device per line: its name, and when it was last seen (W3, the devices
 * preview on My sources and the account section of Settings).
 *
 * "This device" is appended to the name of the one reading the page, from
 * `Device.is_current` — the row the user must not revoke by accident. The
 * right-hand label follows `deviceActivity`: online, active, a relative date,
 * or "unavailable" when the server holds no last activity. Never "offline":
 * nothing announces a disconnection, so that word would be a guess.
 *
 * `revokeHref`, when given, adds a "sign out" link to every row **except the
 * current one**: signing this browser out is a different confirmation, with a
 * cookie to clear (`actions/auth.ts`). The link opens a confirmation rather than
 * posting — a revocation is not undoable, and one click is not a decision.
 */
export async function DeviceRows({
  devices,
  showPlatform = false,
  revokeHref,
  className,
}: {
  devices: readonly Device[];
  /** The platform code beside the name — on the full list, not in the preview. */
  showPlatform?: boolean;
  /** Where the confirmation for revoking a device opens. Absent: no link. */
  revokeHref?: (device: Device) => string;
  className?: string;
}) {
  const t = await getTranslations("App");
  const format = await getFormatter();
  const now = new Date();

  return (
    <ul className={cn("divide-border/60 divide-y", className)}>
      {devices.map((device) => {
        const name = deviceName(device);
        const activity = deviceActivity(device, now);

        return (
          <li key={device.id} className="flex min-h-10 flex-wrap items-center justify-between gap-x-4 gap-y-1 py-2.5">
            <span className="flex min-w-0 items-baseline gap-2.5">
              <span className="truncate text-sm">
                {device.is_current ? t("deviceThisOne", { name }) : name}
              </span>
              {showPlatform ? (
                <span className="text-muted-foreground/70 flex-none font-mono text-[11px]">
                  {device.platform}
                </span>
              ) : null}
            </span>
            <span className="flex flex-none items-center gap-4">
              <span
                className={cn(
                  "font-mono text-xs",
                  activity.kind === "online" ? "text-brand-cyan" : "text-muted-foreground/80",
                )}
              >
                {activity.kind === "online"
                  ? t("deviceOnline")
                  : activity.kind === "active"
                    ? t("deviceActive")
                    : activity.kind === "seen"
                      ? format.relativeTime(activity.at, now)
                      : t("deviceActivityUnavailable")}
              </span>
              {revokeHref && !device.is_current ? (
                <a
                  href={revokeHref(device)}
                  aria-label={t("deviceRevokeOf", { name })}
                  className="text-destructive text-[13px] underline underline-offset-4"
                >
                  {t("deviceRevoke")}
                </a>
              ) : null}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * What a device is called, in the order the contract fills the fields: the
 * name the app registered, else the model, else the platform code — which is
 * always there.
 */
export function deviceName(device: Device): string {
  return device.name ?? device.model ?? device.platform;
}
