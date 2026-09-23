import { getTranslations } from "next-intl/server";
import { signOut } from "@/actions/auth";
import { revokeDevice } from "@/actions/devices";
import { DeviceRows, deviceName } from "@/components/app/DeviceRows";
import { NotBuiltYet, Unavailable } from "@/components/app/Unavailable";
import { Button } from "@/components/ui/button";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import type { Device } from "@/lib/api/types";
import { settingsSectionPath } from "@/lib/settings/sections";
import { isUuid } from "@/lib/sources/active-source";

/**
 * "Account and devices" (US-025, S8-06): who is signed in, from where, and
 * the two ways out.
 *
 * <h2>Three blocks, from `GET /me` and `GET /me/devices`</h2>
 *
 * - **the account** — email and display name, read-only. The contract lets
 *   `PATCH /me` change the name and the locale, and no form is offered for
 *   either: editing the profile is outside this lot, and a field that saves
 *   nothing is worse than no field;
 * - **the devices** — this one first, identified from `Device.is_current` (the
 *   server computes it against the token that made the call), then the others,
 *   each with its last activity when the server holds one and "unavailable"
 *   otherwise. Never "online" for another device: nothing announces a
 *   disconnection, so that would be a claim (`lib/devices/activity.ts`);
 * - **signing out** — of this browser.
 *
 * The two requests are independent and asked together; each falls back on its
 * own, so an outage on one does not blank the other. The email is shown from
 * the session even when `GET /me` did not answer: it was verified at sign-in,
 * and a page that hides what it knows is not calmer, only emptier.
 *
 * <h2>Two confirmations, both in the URL</h2>
 *
 * Revoking a device and signing out are each behind a step that works without
 * JavaScript, on the pattern of the source deletion (`?confirm=`): the link
 * opens it, the page renders it, Cancel is a link back and the destructive
 * button is a form posting to a Server Action. **Cancel comes first** in the
 * document, so the first Tab from the anchor lands on it.
 *
 * The revoke confirmation names the device and says what the contract
 * promises — signed out at its next call to the service — and it only opens on
 * a device that is in the list and is not this one. A `device=` naming
 * anything else is ignored: the id is a query parameter, and a confirmation
 * for a device that is not the user's would be a confirmation for nothing.
 *
 * Signing out says what changes and what does not: this browser, and no other
 * device. It announces no deletion, because nothing is deleted
 * (docs/design/0.2.0/settings.md).
 */
export async function AccountSection({
  locale,
  email,
  accessToken,
  query,
}: {
  locale: Locale;
  /** From the session: the address the account was opened with. */
  email: string;
  accessToken: string;
  query: {
    confirm?: string | string[];
    device?: string | string[];
    revoke?: string | string[];
  };
}) {
  const t = await getTranslations("Settings");
  const account = hrefFor(locale, settingsSectionPath("account"));

  const [me, devices] = await Promise.all([
    fetched(() => api(accessToken).GET("/me", {})),
    fetched(() => api(accessToken).GET("/me/devices", {})),
  ]);

  const displayName = me.state === "ok" ? me.data.display_name : null;
  const rows = devices.state === "ok" ? devices.data.items : [];
  const current = rows.filter((device) => device.is_current);
  const others = rows.filter((device) => !device.is_current);

  // Believed only once it names one of the other devices of the list: the
  // parameter is the browser's to write.
  const revoking =
    query.confirm === "revoke" && typeof query.device === "string" && isUuid(query.device)
      ? (others.find((device) => device.id === query.device) ?? null)
      : null;
  const signingOut = query.confirm === "signout";

  const revokeHref = (device: Device) =>
    `${account}?confirm=revoke&device=${device.id}#revoke-confirmation`;

  const labelClass =
    "text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase sm:pt-0.5";

  return (
    <div className="flex flex-col gap-8">
      <section aria-labelledby="settings-account-title">
        <h2 id="settings-account-title" className="text-lg font-semibold tracking-tight">
          {t("accountTitle")}
        </h2>
        <dl className="bg-card mt-3 grid gap-x-6 gap-y-3 rounded-2xl px-6 py-5 text-sm sm:grid-cols-[auto_1fr]">
          <dt className={labelClass}>{t("accountEmail")}</dt>
          <dd className="break-all">{email}</dd>
          {displayName ? (
            <>
              <dt className={labelClass}>{t("accountName")}</dt>
              <dd>{displayName}</dd>
            </>
          ) : null}
        </dl>
        {me.state !== "ok" ? (
          <p role="status" className="text-muted-foreground mt-2 text-sm">
            {t("accountUnavailable")}
          </p>
        ) : null}
      </section>

      <section aria-labelledby="settings-devices-title">
        <h2 id="settings-devices-title" className="text-lg font-semibold tracking-tight">
          {t("devicesTitle")}
        </h2>
        <p className="text-muted-foreground/80 mt-1 text-[13px]">{t("devicesIntro")}</p>

        <div className="mt-3">
          {devices.state === "unavailable" ? (
            <Unavailable />
          ) : devices.state === "not-implemented" ? (
            <NotBuiltYet />
          ) : (
            <div className="flex flex-col gap-4">
              <div className="bg-card rounded-2xl px-6 py-3">
                <h3 className="text-muted-foreground/80 pt-2 text-[11px] font-semibold tracking-[0.1em] uppercase">
                  {t("devicesThisDevice")}
                </h3>
                {current.length > 0 ? (
                  <DeviceRows devices={current} showPlatform />
                ) : (
                  // The list answered and none of its rows is the caller. It
                  // happens when this browser's device was revoked elsewhere
                  // and the token has not been refreshed since; said, not
                  // hidden.
                  <p className="text-muted-foreground py-2.5 text-sm">
                    {t("devicesThisDeviceUnknown")}
                  </p>
                )}
              </div>

              <div className="bg-card rounded-2xl px-6 py-3">
                <h3 className="text-muted-foreground/80 pt-2 text-[11px] font-semibold tracking-[0.1em] uppercase">
                  {t("devicesOthers")}
                </h3>
                {others.length > 0 ? (
                  <DeviceRows devices={others} showPlatform revokeHref={revokeHref} />
                ) : (
                  <p className="text-muted-foreground py-2.5 text-sm">{t("devicesNone")}</p>
                )}
              </div>

              {revoking ? (
                <section
                  id="revoke-confirmation"
                  aria-labelledby="revoke-confirmation-title"
                  className="border-destructive/40 scroll-mt-6 space-y-3 rounded-xl border px-5 py-4"
                >
                  <h3 id="revoke-confirmation-title" className="font-medium">
                    {t("revokeConfirmTitle", { name: deviceName(revoking) })}
                  </h3>

                  {query.revoke === "failed" ? (
                    <p role="alert" className="text-destructive text-sm">
                      {t("revokeFailed")}
                    </p>
                  ) : null}

                  <p className="text-muted-foreground text-sm">{t("revokeConfirmBody")}</p>

                  <div className="flex flex-wrap items-center gap-3">
                    {/* First on purpose. See the documentation above. */}
                    <a
                      href={account}
                      className="bg-secondary text-secondary-foreground inline-flex h-8 items-center rounded-lg px-2.5 text-sm font-medium"
                    >
                      {t("confirmCancel")}
                    </a>
                    <form action={revokeDevice}>
                      <input type="hidden" name="id" value={revoking.id} />
                      <Button type="submit" variant="destructive">
                        {t("revokeConfirm")}
                      </Button>
                    </form>
                  </div>
                </section>
              ) : null}
            </div>
          )}
        </div>
      </section>

      <section aria-labelledby="settings-signout-title">
        <h2 id="settings-signout-title" className="text-lg font-semibold tracking-tight">
          {t("signOutTitle")}
        </h2>

        {signingOut ? (
          <section
            id="signout-confirmation"
            aria-labelledby="signout-confirmation-title"
            className="border-destructive/40 mt-3 scroll-mt-6 space-y-3 rounded-xl border px-5 py-4"
          >
            <h3 id="signout-confirmation-title" className="font-medium">
              {t("signOutConfirmTitle")}
            </h3>
            <p className="text-muted-foreground text-sm">{t("signOutConfirmBody")}</p>
            <div className="flex flex-wrap items-center gap-3">
              <a
                href={account}
                className="bg-secondary text-secondary-foreground inline-flex h-8 items-center rounded-lg px-2.5 text-sm font-medium"
              >
                {t("confirmCancel")}
              </a>
              <form action={signOut}>
                <Button type="submit" variant="destructive">
                  {t("signOutConfirm")}
                </Button>
              </form>
            </div>
          </section>
        ) : (
          <p className="mt-3">
            <a
              href={`${account}?confirm=signout#signout-confirmation`}
              className="text-destructive text-sm underline underline-offset-4"
            >
              {t("signOutLink")}
            </a>
          </p>
        )}
      </section>
    </div>
  );
}
