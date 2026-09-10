import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { DeviceRows } from "@/components/app/DeviceRows";
import { NotBuiltYet, Unavailable } from "@/components/app/Unavailable";
import { MockBadge } from "@/components/site/MockBadge";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/error-message";
import { fetched } from "@/lib/api/fetched";
import type { Source } from "@/lib/api/types";
import { requireSession } from "@/lib/session/session";
import { cn } from "@/lib/utils";

/**
 * The user's sources (W3, docs/design/web-sprint-1.md).
 *
 * The shape every page in this zone follows: the access token comes from the
 * session cookie (never from client JavaScript), and `fetched` separates the
 * three outcomes that must not be confused — data, nothing yet, and no answer.
 *
 * One row per source: the kind badge (`m3u` cyan, `xtr` violet), the label, a
 * metadata line — channel count and last successful sync, or the cause of the
 * failure and how old it is — the status pill, and the way in. On a row in
 * error the way in is a `Fix` button in place of the menu: the way out of an
 * error is a button, not a menu entry.
 *
 * `PENDING` / `SYNCING` rows refresh the page every few seconds with a
 * `<meta http-equiv="refresh">`, like the source's own page: it costs no
 * JavaScript and stops on its own once nothing is running.
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
  const tErrors = await getTranslations("Errors");
  const format = await getFormatter();
  const now = new Date();

  // Three calls, in parallel: the list, what the plan allows, and the devices
  // for the preview panel. The second is what decides whether "add a source"
  // is offered — never a number written into this file. A client carrying its
  // own copy of "FREE means one source" would be computing an access right
  // client-side, which AGENTS.md §1 forbids.
  const [sources, entitlement, devices] = await Promise.all([
    fetched(() => api(session.accessToken).GET("/sources", {})),
    fetched(() => api(session.accessToken).GET("/me/entitlement", {})),
    fetched(() => api(session.accessToken).GET("/me/devices", {})),
  ]);

  const rows = sources.state === "ok" ? sources.data.items : [];
  const used = rows.length;
  const max =
    entitlement.state === "ok" ? (entitlement.data.max_sources ?? null) : null;
  // Null means unlimited, not unknown — the contract is explicit, and the two
  // would render very differently.
  const roomLeft = max === null || used < max;
  const running = rows.some((row) => row.status === "PENDING" || row.status === "SYNCING");
  const empty = sources.state === "ok" && rows.length === 0;

  return (
    <div className="flex flex-col gap-6">
      {running ? <meta httpEquiv="refresh" content="5" /> : null}

      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t("sourcesTitle")}</h1>
          <p className="text-muted-foreground/80 mt-1 text-[13px]">{t("sourcesSubtitle")}</p>
        </div>
        {roomLeft ? (
          <a
            href={hrefFor(locale as Locale, "/app/sources/new")}
            className="bg-primary text-primary-foreground inline-flex h-11 items-center rounded-full px-5 text-sm font-semibold"
          >
            {t("sourcesAddCta")}
          </a>
        ) : null}
      </header>

      {sources.state === "unavailable" ? (
        <Unavailable />
      ) : sources.state === "not-implemented" ? (
        <NotBuiltYet />
      ) : rows.length > 0 ? (
        <ul className="flex flex-col gap-3">
          {rows.map((row) => (
            <SourceRow
              key={row.id}
              row={row}
              locale={locale as Locale}
              t={t}
              tErrors={tErrors}
              format={format}
              now={now}
            />
          ))}
        </ul>
      ) : null}

      <div className={cn("grid gap-6", empty ? "grid-cols-1" : "lg:grid-cols-[1fr_380px]")}>
        {roomLeft ? (
          <a
            href={hrefFor(locale as Locale, "/app/sources/new")}
            className="border-border hover:border-muted-foreground/40 flex min-h-[220px] flex-col items-center justify-center gap-3 rounded-2xl border border-dashed p-6 text-center transition-colors"
          >
            <span aria-hidden="true" className="text-muted-foreground/80 text-xl">
              ⊕
            </span>
            {empty ? <span className="text-sm font-medium">{t("sourcesEmpty")}</span> : null}
            <span className="text-muted-foreground max-w-[36ch] text-sm leading-normal font-light">
              {empty ? t("sourcesEmptyHint") : t("sourcesAddInvite")}
            </span>
          </a>
        ) : (
          // Said before the form, not after a refused submission. The server
          // still answers SOURCE_LIMIT_REACHED if someone posts anyway — two
          // tabs, and the first one took the last slot.
          <div className="border-border flex min-h-[220px] flex-col items-center justify-center gap-2 rounded-2xl border border-dashed p-6 text-center">
            <p className="font-medium">{t("sourcesLimitTitle", { max: max ?? 0 })}</p>
            <p className="text-muted-foreground max-w-[40ch] text-sm font-light">
              {t("sourcesLimitBody")}
            </p>
            <a
              href={hrefFor(locale as Locale, "/app/subscription")}
              className="text-brand-cyan mt-2 text-sm font-medium"
            >
              {t("sourcesLimitUpgrade")}
            </a>
          </div>
        )}

        <section
          aria-labelledby="devices-preview"
          className="bg-card flex flex-col gap-3.5 rounded-2xl p-6"
        >
          <h2
            id="devices-preview"
            className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase"
          >
            {t("devicesPreviewTitle")}
          </h2>

          {devices.state === "ok" ? (
            devices.data.items.length > 0 ? (
              <DeviceRows devices={devices.data.items.slice(0, 3)} />
            ) : (
              <p className="text-muted-foreground text-sm font-light">{t("devicesEmpty")}</p>
            )
          ) : (
            <p className="text-muted-foreground flex flex-wrap items-center gap-2 text-sm font-light">
              {devices.state === "unavailable" ? t("unavailableTitle") : t("notImplementedBadge")}
              <MockBadge />
            </p>
          )}

          <a
            href={hrefFor(locale as Locale, "/app/devices")}
            className="text-brand-cyan mt-auto pt-2 text-[13px] font-medium"
          >
            {t("devicesManageLink")}
          </a>
        </section>
      </div>
    </div>
  );
}

type Translate = Awaited<ReturnType<typeof getTranslations<"App">>>;
type Format = Awaited<ReturnType<typeof getFormatter>>;

/**
 * One source. The metadata line is the only part that changes shape: numbers
 * when it is ready, a running step when it is syncing, the cause when it
 * failed. `text-muted` carries the numbers because the pill carries the state —
 * the design system allows the muted colour only when it is not the sole
 * bearer of the information.
 */
function SourceRow({
  row,
  locale,
  t,
  tErrors,
  format,
  now,
}: {
  row: Source;
  locale: Locale;
  t: Translate;
  tErrors: Parameters<typeof errorMessage>[1];
  format: Format;
  now: Date;
}) {
  const failed = row.status === "ERROR";
  const running = row.status === "PENDING" || row.status === "SYNCING";
  const detail = hrefFor(locale, `/app/sources/${row.id}`);
  const isM3u = row.kind === "M3U_URL";

  return (
    <li
      className={cn(
        "bg-card flex flex-wrap items-center gap-x-5 gap-y-3 rounded-2xl px-6 py-5",
        failed && "border-destructive/30 border",
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "flex size-10 flex-none items-center justify-center rounded-xl font-mono text-xs",
          isM3u ? "bg-brand-cyan/12 text-brand-cyan" : "bg-brand-violet/14 text-brand-violet",
        )}
      >
        {isM3u ? t("sourceKindBadgeM3u") : t("sourceKindBadgeXtream")}
      </span>

      <div className="min-w-0 flex-1">
        <a href={detail} className="block truncate text-[15px] font-semibold underline-offset-4 hover:underline">
          {row.label}
        </a>
        {failed ? (
          <p className="text-destructive mt-0.5 text-xs">
            {errorMessage(row.error_code ?? undefined, tErrors)}
            {row.last_error_at
              ? ` — ${t("sourceErrorSince", { when: format.relativeTime(new Date(row.last_error_at), now) })}`
              : null}
          </p>
        ) : running ? (
          <p
            role="progressbar"
            aria-label={t("sourceSyncingTitle")}
            className="text-muted-foreground/80 mt-0.5 font-mono text-xs"
          >
            {t("sourceSyncingTitle")}…
          </p>
        ) : (
          <p className="text-muted-foreground/80 mt-0.5 font-mono text-xs">
            {row.channel_count != null && row.last_synced_at
              ? t("sourceMeta", {
                  channels: format.number(row.channel_count),
                  when: format.relativeTime(new Date(row.last_synced_at), now),
                })
              : row.last_synced_at
                ? t("sourceMetaChecked", {
                    when: format.relativeTime(new Date(row.last_synced_at), now),
                  })
                : t("sourceMetaNever")}
          </p>
        )}
      </div>

      <StatusPill status={row.status} t={t} />

      {failed ? (
        <a
          href={detail}
          className="bg-secondary text-foreground inline-flex h-9 items-center rounded-full px-4 text-[13px]"
        >
          {t("sourceFix")}
        </a>
      ) : (
        <a
          href={detail}
          aria-label={t("sourceManageOf", { label: row.label })}
          className="text-muted-foreground/80 hover:text-foreground px-1 text-lg leading-none"
        >
          ⋯
        </a>
      )}
    </li>
  );
}

function StatusPill({ status, t }: { status: Source["status"]; t: Translate }) {
  // `ERROR` and, should the contract ever add one, anything else: an unknown
  // status must not blank the pill out. The parameter is typed as the
  // contract's union so a new value is a compile error here, not a default.
  const label =
    status === "PENDING"
      ? t("sourceStatusPending")
      : status === "SYNCING"
        ? t("sourceStatusSyncing")
        : status === "READY"
          ? t("sourceStatusReady")
          : t("sourceStatusError");

  const tone =
    status === "READY"
      ? "bg-brand-cyan/10 text-brand-cyan"
      : status === "ERROR"
        ? "bg-destructive/12 text-destructive"
        : "bg-secondary text-muted-foreground";

  return (
    <span
      className={cn(
        "inline-flex h-[26px] items-center gap-1.5 rounded-full px-3 text-xs font-medium",
        tone,
      )}
    >
      <span aria-hidden="true" className="size-1.5 rounded-full bg-current" />
      {label}
    </span>
  );
}
