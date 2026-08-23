import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { EmptyState, Unavailable } from "@/components/app/Unavailable";
import { api } from "@/lib/api/client";
import { requireSession } from "@/lib/session/session";

/**
 * The user's sources.
 *
 * The first page in this codebase that reads real data through the generated
 * client, and it shows the shape the others follow: the access token comes from
 * the session cookie (never from client JavaScript), a transport failure and an
 * API error both land in the same "unavailable" state, and an empty list is
 * told apart from a failed one.
 *
 * Note what is NOT rendered: no `username`, no host credentials of any kind. The
 * contract does not return the Xtream password at all — it never leaves the
 * server (docs/domain-model.md, `source`) — and this page has no reason to show
 * the rest.
 */
export default async function SourcesPage({
  params,
}: PageProps<"/[locale]/app/sources">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const format = await getFormatter();

  let sources: Array<{
    id: string;
    label: string;
    status: string;
    channel_count?: number | null;
    last_synced_at?: string | null;
  }> | null = null;

  try {
    const { data } = await api(session.accessToken).GET("/sources", {});
    sources = data?.items ?? null;
  } catch {
    sources = null;
  }

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">
        {t("sourcesTitle")}
      </h1>
      <p className="text-muted-foreground mt-2">{t("sourcesSubtitle")}</p>

      <div className="mt-8">
        {sources === null ? (
          <Unavailable />
        ) : sources.length === 0 ? (
          <EmptyState title={t("sourcesEmpty")} hint={t("sourcesEmptyHint")} />
        ) : (
          <ul className="space-y-3">
            {sources.map((source) => (
              <li
                key={source.id}
                className="border-border flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border px-5 py-4"
              >
                <span className="font-medium">{source.label}</span>
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
  status: string,
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
    default:
      // Includes ERROR and any status added to the contract later: an unknown
      // value must not blank the row out.
      return t("sourceStatusError");
  }
}
