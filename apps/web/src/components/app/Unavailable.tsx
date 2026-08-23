import { getTranslations } from "next-intl/server";

/**
 * The states an account page shows when it has no rows to show.
 *
 * Three of them, and keeping them apart is the point. "No data", "not built
 * yet" and "the service is down" ask the user for three different things:
 * add a source, come back later, try again in a minute. Collapsing any two of
 * them turns a missing feature into a phantom outage — which is exactly what
 * these pages did before `fetched()` learned to tell an unrouted 404 from a
 * real failure.
 */

/** The API answered, and there is genuinely nothing yet. */
export function EmptyState({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="border-border rounded-xl border border-dashed px-5 py-6">
      <p className="font-medium">{title}</p>
      <p className="text-muted-foreground mt-1 text-sm">{hint}</p>
    </div>
  );
}

/** The API did not answer: down, restarting, or unreachable from this server. */
export async function Unavailable() {
  const t = await getTranslations("App");

  return (
    <div
      role="status"
      className="border-border rounded-xl border border-dashed px-5 py-6"
    >
      <p className="font-medium">{t("unavailableTitle")}</p>
      <p className="text-muted-foreground mt-1 text-sm">{t("unavailableBody")}</p>
    </div>
  );
}

/**
 * The endpoint is in the contract but has no controller yet.
 *
 * Said plainly rather than dressed up as an error. The endpoints these screens
 * need — `/me/devices`, `/me/entitlement` — are outside sprint 1, which covers
 * auth, sources and catalogue.
 */
export async function NotBuiltYet() {
  const t = await getTranslations("App");

  return (
    <div className="border-border rounded-xl border border-dashed px-5 py-6">
      <p className="font-medium">{t("notImplementedBadge")}</p>
      <p className="text-muted-foreground mt-1 text-sm">{t("notImplementedBody")}</p>
    </div>
  );
}
