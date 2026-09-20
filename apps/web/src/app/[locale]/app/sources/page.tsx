import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { DeviceRows } from "@/components/app/DeviceRows";
import { Unavailable } from "@/components/app/Unavailable";
import { UseSourceButton } from "@/components/app/UseSourceButton";
import { MockBadge } from "@/components/site/MockBadge";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/error-message";
import { fetched } from "@/lib/api/fetched";
import type { Source } from "@/lib/api/types";
import { requireSession } from "@/lib/session/session";
import { loadActiveSource } from "@/lib/sources/active-source-store";
import {
  contentCounts,
  countMessage,
  type ContentCount,
  type TitleCounts,
} from "@/lib/sources/content-counts";
import {
  LIST_TITLE_COUNTS_MAX_SOURCES,
  loadTitleCounts,
} from "@/lib/sources/load-title-counts";
import { sourceCondition } from "@/lib/sources/source-condition";
import { stepKey } from "@/lib/sources/sync-step";
import { cn } from "@/lib/utils";

/**
 * The user's sources (W3, docs/design/web-sprint-1.md).
 *
 * The shape every page in this zone follows: the access token comes from the
 * session cookie (never from client JavaScript), and `fetched` separates the
 * three outcomes that must not be confused — data, nothing yet, and no answer.
 *
 * One row per source (US-024, S8-E05): the kind badge (`m3u` cyan, `xtr`
 * violet), the label, what is happening to it — the real step of a running
 * synchronisation, or the cause of a failure and how old it is — what it holds,
 * the status pill, whether this browser is using it, and the way in. On a row in
 * error the way in is a `Fix` button in place of the menu: the way out of an
 * error is a button, not a menu entry.
 *
 * <h2>What a source holds is shown whenever it is known — in every status</h2>
 *
 * Since contract lot C4 a source that is refreshing, or whose last refresh
 * failed, still has its previous catalogue and its counts. The row used to
 * replace them with "Importing…" or the error; it now says both.
 *
 * <h2>Film and series counts: here too, within a budget</h2>
 *
 * `Source` carries channels and categories only. Films and series are
 * `total_elements` of their listings asked with `size=1` — at most **two extra
 * requests per source** (one for a playlist, none before a first import), all in
 * parallel (`lib/sources/load-title-counts.ts`). That is within what was allowed
 * for this list, so the list shows them. Past `LIST_TITLE_COUNTS_MAX_SOURCES`
 * sources it stops asking and leaves those two numbers to each source's own
 * page: this is also the page that reloads itself while an import runs, and a
 * paid plan has no source limit.
 *
 * A number that is not known is **absent**, never zero (`content-counts.ts`).
 *
 * <h2>"Use this source"</h2>
 *
 * On every source this browser is not browsing; the one it is browsing is marked
 * instead. The form posts to the switcher's own action and the user stays on
 * this page. When `GET /sources` did not answer there is no list to mark.
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
  //
  // The list comes through `loadActiveSource`: the layout already asked for it
  // to draw the rail, the answer is memoised per request, and it says which
  // source this browser is browsing — which a second `GET /sources` would not.
  const [sources, entitlement, devices] = await Promise.all([
    loadActiveSource(session.accessToken, session.userId),
    fetched(() => api(session.accessToken).GET("/me/entitlement", {})),
    fetched(() => api(session.accessToken).GET("/me/devices", {})),
  ]);

  const listed = sources.state !== "unavailable";
  const rows = listed ? sources.sources : [];
  const activeId = listed && sources.state === "selected" ? sources.source.id : null;

  // Films and series, per source and in parallel — see the budget above. Keyed
  // by source id; a source with no entry simply shows no such numbers.
  const titleCounts = new Map<string, TitleCounts>(
    rows.length <= LIST_TITLE_COUNTS_MAX_SOURCES
      ? await Promise.all(
          rows.map(
            async (row) =>
              [row.id, await loadTitleCounts(session.accessToken, row)] as const,
          ),
        )
      : [],
  );
  const used = rows.length;
  const max =
    entitlement.state === "ok" ? (entitlement.data.max_sources ?? null) : null;
  // Null means unlimited, not unknown — the contract is explicit, and the two
  // would render very differently.
  const roomLeft = max === null || used < max;
  const running = rows.some((row) => row.status === "PENDING" || row.status === "SYNCING");
  const empty = listed && rows.length === 0;

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

      {!listed ? (
        <Unavailable />
      ) : rows.length > 0 ? (
        <ul className="flex flex-col gap-3">
          {rows.map((row) => (
            <SourceRow
              key={row.id}
              row={row}
              counts={contentCounts(row, titleCounts.get(row.id))}
              active={row.id === activeId}
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
 * One source.
 *
 * Two lines under the label, and the first can be absent:
 *
 * - **what is happening** — the server's real step while a synchronisation runs
 *   (never a percentage: it reports none), or the cause of a failure and its
 *   age. Nothing for a source that is simply ready;
 * - **what it holds** — its kind in words, the counts that are known, and the
 *   last *successful* synchronisation. The numbers are there in every status
 *   once a catalogue exists, because the catalogue is (C4).
 *
 * `text-muted` carries the numbers because the pill carries the state — the
 * design system allows the muted colour only when it is not the sole bearer of
 * the information. The kind is said in words as well as on the badge: the badge
 * is `aria-hidden`, and "M3U or Xtream" is one of the three things US-024 says a
 * row must present.
 */
function SourceRow({
  row,
  counts,
  active,
  locale,
  t,
  tErrors,
  format,
  now,
}: {
  row: Source;
  counts: ContentCount[];
  active: boolean;
  locale: Locale;
  t: Translate;
  tErrors: Parameters<typeof errorMessage>[1];
  format: Format;
  now: Date;
}) {
  const condition = sourceCondition(row);
  const failed = condition.notice === "error";
  const running = condition.notice === "syncing";
  const detail = hrefFor(locale, `/app/sources/${row.id}`);
  const isM3u = row.kind !== "XTREAM";

  const holdings = [
    isM3u ? t("sourceKindM3u") : t("sourceKindXtream"),
    ...counts.map((entry) => {
      const message = countMessage(entry);
      return t(message.key, message.values);
    }),
    row.last_synced_at
      ? t("sourceMetaChecked", { when: format.relativeTime(new Date(row.last_synced_at), now) })
      : // Said only when nothing else explains the absence of numbers: a source
        // that is importing or failed has its own line for that.
        condition.notice === null
        ? t("sourceMetaNever")
        : null,
  ].filter((part): part is string => part !== null);

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
          <p role="status" className="text-muted-foreground/80 mt-0.5 font-mono text-xs">
            {condition.browsable ? t("sourceRefreshingTitle") : t("sourceSyncingTitle")}
            {" — "}
            {row.sync_step ? t(stepKey(row.sync_step)) : t("sourceStatusPending")}
          </p>
        ) : null}
        <p className="text-muted-foreground/80 mt-0.5 font-mono text-xs">
          {holdings.join(" · ")}
        </p>
      </div>

      <StatusPill status={row.status} t={t} />

      {active ? (
        <span className="text-brand-cyan text-[13px] font-medium">{t("sourceActiveHere")}</span>
      ) : (
        <UseSourceButton
          sourceId={row.id}
          from="/app/sources"
          locale={locale}
          label={t("sourceUse")}
          accessibleLabel={t("sourceUseOf", { label: row.label })}
        />
      )}

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
