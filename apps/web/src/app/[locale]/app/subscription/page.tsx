import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { NotBuiltYet, Unavailable } from "@/components/app/Unavailable";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
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
 * The endpoint has no controller yet, so today this renders "not built yet"
 * rather than an outage — see the devices page for why the distinction matters.
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
      <h1 className="text-2xl font-semibold tracking-tight">
        {t("subscriptionTitle")}
      </h1>
      <p className="text-muted-foreground mt-2">{t("subscriptionSubtitle")}</p>

      <div className="mt-8">
        {entitlement.state === "unavailable" ? (
          <Unavailable />
        ) : entitlement.state === "not-implemented" ? (
          <NotBuiltYet />
        ) : (
          <div className="border-border rounded-xl border px-5 py-6">
            <p className="text-lg font-medium">
              {entitlement.data.plan === "PREMIUM"
                ? t("subscriptionPlanPremium")
                : t("subscriptionPlanFree")}
            </p>
            <p className="text-muted-foreground mt-1 text-sm">
              {entitlement.data.status}
            </p>
            {entitlement.data.current_period_end ? (
              <p className="text-muted-foreground mt-3 text-sm">
                {t("subscriptionRenewsOn", {
                  date: format.dateTime(
                    new Date(entitlement.data.current_period_end),
                    { dateStyle: "long" },
                  ),
                })}
              </p>
            ) : null}
          </div>
        )}
      </div>
    </>
  );
}
