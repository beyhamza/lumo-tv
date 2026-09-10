import { getFormatter, getTranslations } from "next-intl/server";
import type { Device } from "@/lib/api/types";
import { deviceActivity } from "@/lib/devices/activity";
import { cn } from "@/lib/utils";

/**
 * One device per line: its name, and when it was last seen (W3, the devices
 * preview and the devices page).
 *
 * "This device" is appended to the name of the one reading the page, from
 * `Device.is_current` — the row the user must not revoke by accident. The
 * right-hand label follows `deviceActivity`: online, active, a relative date,
 * or never.
 */
export async function DeviceRows({
  devices,
  showPlatform = false,
  className,
}: {
  devices: readonly Device[];
  /** The platform code beside the name — on the full page, not in the preview. */
  showPlatform?: boolean;
  className?: string;
}) {
  const t = await getTranslations("App");
  const format = await getFormatter();
  const now = new Date();

  return (
    <ul className={cn("divide-border/60 divide-y", className)}>
      {devices.map((device) => {
        const name = device.name ?? device.model ?? device.platform;
        const activity = deviceActivity(device, now);

        return (
          <li key={device.id} className="flex min-h-10 items-center justify-between gap-4 py-2.5">
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
            <span
              className={cn(
                "flex-none font-mono text-xs",
                activity.kind === "online" ? "text-brand-cyan" : "text-muted-foreground/80",
              )}
            >
              {activity.kind === "online"
                ? t("deviceOnline")
                : activity.kind === "active"
                  ? t("deviceActive")
                  : activity.kind === "seen"
                    ? format.relativeTime(activity.at, now)
                    : t("deviceNever")}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
