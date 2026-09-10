/**
 * How a device's `last_seen_at` is rendered (docs/design/web-sprint-1.md, W3,
 * "Décisions de rendu à ne pas transformer en champs d'API").
 *
 * The mock-up shows three labels — `en ligne`, `actif`, `il y a 3 j` — and they
 * are two pieces of information, not three:
 *
 * - `online` is "the device reading this page": `Device.is_current`, computed
 *   by the server against the token that made the call;
 * - `active` versus a relative date is a rendering threshold over
 *   `last_seen_at`: **under 24 hours → active**, beyond → the date.
 *
 * There is no `is_online` field and there must not be one: nothing announces a
 * disconnection, so such a field would be wrong half of the time. `never`
 * covers a device that has been registered and has not called since.
 */
export const ACTIVE_WINDOW_MS = 24 * 60 * 60 * 1000;

export type DeviceActivity =
  | { kind: "online" }
  | { kind: "active" }
  | { kind: "seen"; at: Date }
  | { kind: "never" };

export function deviceActivity(
  device: { is_current: boolean; last_seen_at?: string | null },
  now: Date = new Date(),
): DeviceActivity {
  if (device.is_current) return { kind: "online" };
  if (!device.last_seen_at) return { kind: "never" };

  const at = new Date(device.last_seen_at);
  if (Number.isNaN(at.getTime())) return { kind: "never" };

  return now.getTime() - at.getTime() < ACTIVE_WINDOW_MS
    ? { kind: "active" }
    : { kind: "seen", at };
}
