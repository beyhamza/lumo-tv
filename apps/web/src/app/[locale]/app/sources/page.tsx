import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import {
  EmptyState,
  NotBuiltYet,
  Unavailable,
} from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import type { SourceStatus } from "@/lib/api/types";
import { requireSession } from "@/lib/session/session";

/**
 * The user's sources.
 *
 * The shape every page in this zone follows: the access token comes from the
 * session cookie (never from client JavaScript), and `fetched` separates the
 * three outcomes that must not be confused — data, nothing yet, and no answer.
 *
 * The row type comes from the contract rather than being written out here. A
 * local `{ id: string; label: string; … }` reads fine and hides a rename: an
 * optional property that disappears from the contract is still assignable to an
 * optional property declared locally, so the mismatch would surface at runtime
 * (ADR 0001).
 *
 * Note what is NOT rendered: no `username`, no host. The contract does not
 * return the Xtream password at all — it never leaves the server
 * (docs/domain-model.md, `source`) — and this page has no reason to show the
 * rest.
 */
export default async function SourcesPage({
  params,
}: PageProps<"/[locale]/app/sources">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const format = await getFormatter();

  // Two calls, in parallel: the list, and what the plan allows. The second is
  // what decides whether "add a source" is offered — never a number written into
  // this file. A client carrying its own copy of "FREE means one source" would
  // be computing an access right client-side, which AGENTS.md §1 forbids.
  const [sources, entitlement] = await Promise.all([
    fetched(() => api(session.accessToken).GET("/sources", {})),
    fetched(() => api(session.accessToken).GET("/me/entitlement", {})),
  ]);

  const used = sources.state === "ok" ? sources.data.items.length : 0;
  const max =
    entitlement.state === "ok" ? (entitlement.data.max_sources ?? null) : null;
  // Null means unlimited, not unknown — the contract is explicit, and the two
  // would render very differently.
  const roomLeft = max === null || used < max;

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">
        {t("sourcesTitle")}
      </h1>
      <p className="text-muted-foreground mt-2">{t("sourcesSubtitle")}</p>

      <div className="mt-6">
        {roomLeft ? (
          <a
            href={hrefFor(locale as Locale, "/app/sources/new")}
            className="bg-primary text-primary-foreground inline-flex h-9 items-center rounded-lg px-4 text-sm font-medium"
          >
            {t("sourcesAddCta")}
          </a>
        ) : (
          // Said before the form, not after a refused submission. The server
          // still answers SOURCE_LIMIT_REACHED if someone posts anyway — two
          // tabs, and the first one took the last slot.
          <div className="border-border rounded-xl border border-dashed px-5 py-4">
            <p className="font-medium">{t("sourcesLimitTitle", { max: max ?? 0 })}</p>
            <p className="text-muted-foreground mt-1 text-sm">
              {t("sourcesLimitBody")}
            </p>
            <p className="mt-3">
              <a
                href={hrefFor(locale as Locale, "/app/subscription")}
                className="text-sm underline underline-offset-4"
              >
                {t("sourcesLimitUpgrade")}
              </a>
            </p>
          </div>
        )}
      </div>

      <div className="mt-8">
        {sources.state === "unavailable" ? (
          <Unavailable />
        ) : sources.state === "not-implemented" ? (
          <NotBuiltYet />
        ) : sources.data.items.length === 0 ? (
          <EmptyState title={t("sourcesEmpty")} hint={t("sourcesEmptyHint")} />
        ) : (
          <ul className="space-y-3">
            {sources.data.items.map((source) => (
              <li
                key={source.id}
                className="border-border flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border px-5 py-4"
              >
                <a
                  href={hrefFor(locale as Locale, `/app/sources/${source.id}`)}
                  className="font-medium underline-offset-4 hover:underline"
                >
                  {source.label}
                </a>
                <span className="text-muted-foreground text-sm">
                  {statusLabel(source.status, t)}
                </span>
                {source.last_synced_at ? (
                  <span className="text-muted-foreground ml-auto text-sm">
                    {format.dateTime(new Date(source.last_synced_at), {
                      dateStyle: "medium",
                      timeStyle: "short",
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

function statusLabel(
  status: SourceStatus,
  t: (
    key:
      | "sourceStatusPending"
      | "sourceStatusSyncing"
      | "sourceStatusReady"
      | "sourceStatusError",
  ) => string,
): string {
  switch (status) {
    case "PENDING":
      return t("sourceStatusPending");
    case "SYNCING":
      return t("sourceStatusSyncing");
    case "READY":
      return t("sourceStatusReady");
    // `ERROR` and, should the contract ever add one, anything else: an unknown
    // status must not blank the row out. Typing the parameter as the contract's
    // union is what would make a new value a compile error here rather than a
    // silent default.
    default:
      return t("sourceStatusError");
  }
}
