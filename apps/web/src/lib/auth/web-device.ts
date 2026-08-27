import type { DeviceRegistration } from "@/lib/api/types";

/**
 * How this browser identifies itself to the API.
 *
 * The contract requires a device on every call that opens a session — sign-in,
 * registration and Google (docs/domain-model.md, `device`): a session is bound
 * to one, and that is what lets a user list and revoke their sessions from the
 * account page.
 *
 * One constant rather than one per caller, because a second copy would drift and
 * the account page would list "lumo.tv" and "Lumo Web" as two different devices.
 */
export const WEB_DEVICE: DeviceRegistration = {
  platform: "WEB",
  name: "lumo.tv",
  model: null,
  app_version: "0.1.0",
};
