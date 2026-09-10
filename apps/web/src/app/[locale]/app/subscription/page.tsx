import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { NotBuiltYet, Unavailable } from "@/components/app/Unavailable";
import { MockBadge } from "@/components/site/MockBadge";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import type { Entitlement } from "@/lib/api/types";
import { requireSession } from "@/lib/session/session";

/**
 * The subscription.
 *
 * Read from `GET /me/entitlement` and from nowhere else. That endpoint is the
 * single source of truth for access rights (docs/architecture.md §5): the client
 * never asks a store, and the web never infers a plan from a Stripe response it
 * happens to have seen. Stripe writes to the `entitlement` table through
 * webhooks; this page reads the result (ADR 0003).
 *
 * The quotas come from the same answer (`max_sources`, `max_devices`, G1) —
 * the one place the free plan's ceiling lives. What this page does not do yet
 * is open a checkout or the billing portal (G3, 80 % served, no webhook): the
 * "manage" action is shown as the mock-up has it and labelled as missing.
 */
export default async function SubscriptionPage({
  params,
}: PageProps<"/[locale]/app/subscription">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const format = await getFormatter();

  const entitlement = await fetched(() =>
    api(session.accessToken).GET("/me/entitlement", {}),
  );

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">{t("subscriptionTitle")}</h1>
      <p className="text-muted-foreground/80 mt-1 text-[13px]">{t("subscriptionSubtitle")}</p>

      <div className="mt-6 max-w-xl">
        {entitlement.state === "unavailable" ? (
          <Unavailable />
        ) : entitlement.state === "not-implemented" ? (
          <NotBuiltYet />
        ) : (
          <div className="bg-card flex flex-col gap-4 rounded-2xl px-6 py-6">
            <div>
              <p className="text-lg font-semibold">
                {entitlement.data.plan === "PREMIUM"
                  ? t("subscriptionPlanPremium")
                  : t("subscriptionPlanFree")}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                {statusLabel(entitlement.data.status, t)}
              </p>
            </div>

            <dl className="text-muted-foreground grid grid-cols-2 gap-3 text-sm">
              <div>
                <dt className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase">
                  {t("subscriptionMaxSources")}
                </dt>
                <dd className="text-foreground mt-1 font-mono">
                  {quota(entitlement.data.max_sources, t)}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase">
                  {t("subscriptionMaxDevices")}
                </dt>
                <dd className="text-foreground mt-1 font-mono">
                  {quota(entitlement.data.max_devices, t)}
                </dd>
              </div>
            </dl>

            {entitlement.data.status === "TRIALING" && entitlement.data.trial_ends_at ? (
              <p className="text-muted-foreground text-sm">
                {t("subscriptionTrialEnds", {
                  date: format.dateTime(new Date(entitlement.data.trial_ends_at), {
                    dateStyle: "long",
                  }),
                })}
              </p>
            ) : entitlement.data.current_period_end ? (
              <p className="text-muted-foreground text-sm">
                {t("subscriptionRenewsOn", {
                  date: format.dateTime(new Date(entitlement.data.current_period_end), {
                    dateStyle: "long",
                  }),
                })}
              </p>
            ) : null}

            {/* Checkout and portal exist in the contract but the payment loop
                is not closed (docs/design/api-gaps.md, G3): no session is
                opened from here yet. */}
            <p className="flex flex-wrap items-center gap-2">
              <span className="bg-secondary text-foreground inline-flex h-10 items-center rounded-full px-5 text-sm opacity-60">
                {t("subscriptionManage")}
              </span>
              <MockBadge />
            </p>
          </div>
        )}
      </div>
    </>
  );
}

type Translate = Awaited<ReturnType<typeof getTranslations<"App">>>;

/** `null` means unlimited, not unknown — the contract is explicit. */
function quota(value: number | null | undefined, t: Translate): string {
  return value == null ? t("subscriptionUnlimited") : String(value);
}

function statusLabel(status: Entitlement["status"], t: Translate): string {
  switch (status) {
    case "ACTIVE":
      return t("subscriptionStatusActive");
    case "TRIALING":
      return t("subscriptionStatusTrialing");
    case "PAST_DUE":
      return t("subscriptionStatusPastDue");
    case "CANCELED":
      return t("subscriptionStatusCanceled");
    case "EXPIRED":
      return t("subscriptionStatusExpired");
    // An enum value this build has never seen: shown as-is rather than blanked.
    default:
      return status;
  }
}
