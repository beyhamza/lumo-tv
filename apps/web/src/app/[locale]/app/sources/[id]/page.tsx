import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { deleteSource, syncSource, updateSource } from "@/actions/sources";
import { Unavailable } from "@/components/app/Unavailable";
import { UseSourceButton } from "@/components/app/UseSourceButton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/error-message";
import { fetched } from "@/lib/api/fetched";
import type { Source, SyncStep } from "@/lib/api/types";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { loadActiveSource } from "@/lib/sources/active-source-store";
import { contentCounts, countMessage, type ContentCount } from "@/lib/sources/content-counts";
import { loadTitleCounts } from "@/lib/sources/load-title-counts";
import { syncLimitNotice, type DisplayedWait } from "@/lib/sources/retry-after";
import { sourceCondition, type SourceCondition } from "@/lib/sources/source-condition";
import { stepKey } from "@/lib/sources/sync-step";

/**
 * A translator, as the panels below need it.
 *
 * Written out rather than inferred because these are Server Components taking
 * the translator as a parameter: next-intl's own type is bound to a namespace
 * and does not survive being passed down.
 */
type Translate = (key: string, values?: Record<string, string | number>) => string;
type Format = Awaited<ReturnType<typeof getFormatter>>;

/**
 * One source: what it is doing, why it failed, what it holds, and what can be
 * done to it (US-024, S8-E05 to S8-E08).
 *
 * <h2>Two independent things on one page</h2>
 *
 * - **What is happening**: `PENDING` / `SYNCING` — the wait, with the step the
 *   server is on (M1); `ERROR` — which failure, how old it is, and the one action
 *   that helps; `READY` — nothing to announce.
 * - **What there is to browse**: the catalogue, its counts and the date it was
 *   last refreshed — shown **whenever one exists**, whatever the status. Since
 *   contract lot C4 a source that is refreshing, or whose last refresh failed,
 *   still serves its previous catalogue; this page used to hide it in both cases
 *   and show a checklist or an error instead of it.
 *
 * `lib/sources/source-condition.ts` answers both, and is the same function the
 * home page and the catalogue pages ask.
 *
 * <h2>The wait refreshes without a line of JavaScript</h2>
 *
 * A `<meta http-equiv="refresh">` rendered only while ingestion is running. It
 * costs nothing, works with scripting disabled, and stops on its own: the tag is
 * absent from the next render, because the status changed. A client-side poller
 * would need a client component, a provider and an effect to do the same thing
 * worse.
 *
 * The server releases a source stuck in `SYNCING` after thirty minutes, so this
 * loop has an end even when an ingestion worker dies mid-flight.
 *
 * Nothing here blocks: the rail and its source switcher are on screen the whole
 * time, so somebody who added a second source can go back to browsing the first
 * while this one imports (US-024, "Après ajout").
 *
 * <h2>What the actions report, and how</h2>
 *
 * A form posted without JavaScript gets nothing back from a Server Action but
 * its redirect, so the outcomes that need a sentence come back in the query
 * string: `sync=limited` with `retryAt=` (the server's `Retry-After`, never a
 * duration of ours — `lib/sources/retry-after.ts`), `sync=failed`,
 * `delete=failed`. Each is validated before it is believed, and each can only
 * ever make this page say something to its own visitor.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/[id]">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app/sources",
    title: t("sourcesTitle"),
    description: t("sourcesSubtitle"),
    index: false,
  });
}

export default async function SourcePage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/sources/[id]">) {
  const { locale, id } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");
  const format = await getFormatter();

  const [source, activeSource] = await Promise.all([
    fetched(() =>
      api(session.accessToken).GET("/sources/{id}", { params: { path: { id } } }),
    ),
    // The layout asked the same question for the rail; this is the same answer,
    // not a second request (`loadActiveSource` is memoised per request).
    loadActiveSource(session.accessToken, session.userId),
  ]);

  if (source.state === "unavailable") {
    // Distinguished from "no such source" on purpose: one asks the user to come
    // back in a minute, the other tells them the source is gone. Collapsing them
    // is how a restart becomes a phantom deletion.
    return <Unavailable />;
  }
  if (source.state === "not-implemented") {
    notFound();
  }

  const row = source.data;
  const condition = sourceCondition(row);
  const running = condition.notice === "syncing";

  // At most two requests of one row each, in parallel, and none before a first
  // successful import (`load-title-counts.ts`). A count that fails is absent
  // from `counts`, never a zero.
  const counts = contentCounts(row, await loadTitleCounts(session.accessToken, row));

  // `undefined` when `GET /sources` did not answer: neither "active" nor "use
  // this source" is offered then, because either would be a guess.
  const activeId =
    activeSource.state === "unavailable"
      ? undefined
      : activeSource.state === "selected"
        ? activeSource.source.id
        : null;

  // Ignored while a refresh runs: whatever was refused a moment ago, the thing
  // asked for is now happening, and the checklist below is the better answer.
  const now = new Date();
  const limited = running ? null : syncLimitNotice(query.sync, query.retryAt, now.getTime());
  const syncFailed = !running && query.sync === "failed";

  return (
    <div className="max-w-2xl">
      {running ? <meta httpEquiv="refresh" content="3" /> : null}

      <p className="text-sm">
        <a
          href={hrefFor(locale as Locale, "/app/sources")}
          className="text-muted-foreground underline underline-offset-4"
        >
          {t("sourceBackToList")}
        </a>
      </p>

      <h1 className="mt-4 text-2xl font-semibold tracking-tight">{row.label}</h1>
      {/* The kind in words. On the list it is a badge, which is decoration to a
          screen reader; here it is the first thing said about the source. */}
      <p className="text-muted-foreground mt-1 text-sm">
        {[row.kind === "XTREAM" ? t("sourceKindXtream") : t("sourceKindM3u"), originOf(row)]
          .filter(Boolean)
          .join(" · ")}
      </p>

      {limited ? <SyncLimited wait={limited.wait} t={t} /> : null}
      {syncFailed ? (
        <p role="alert" className="border-destructive/40 mt-6 rounded-xl border px-5 py-4 text-sm">
          {t("sourceSyncFailed")}
        </p>
      ) : null}

      <div className="mt-8 space-y-4">
        {running ? (
          <SyncProgress
            step={row.sync_step ?? null}
            kind={row.kind}
            refreshing={condition.browsable}
            t={t}
          />
        ) : row.status === "ERROR" ? (
          <ErrorPanel
            code={row.error_code ?? undefined}
            since={row.last_error_at ?? null}
            id={row.id}
            refreshing={condition.browsable}
            locale={locale as Locale}
            t={t}
            tErrors={tErrors}
            format={format}
          />
        ) : null}

        {condition.browsable ? (
          <CataloguePanel
            row={row}
            condition={condition}
            counts={counts}
            locale={locale as Locale}
            t={t}
            format={format}
          />
        ) : null}
      </div>

      {activeId !== undefined ? (
        <ActivePanel
          row={row}
          active={activeId === row.id}
          browsable={condition.browsable}
          locale={locale as Locale}
          t={t}
        />
      ) : null}

      <ManagePanel
        row={row}
        running={running}
        browsable={condition.browsable}
        confirmingDelete={query.confirm === "delete"}
        deleteFailed={query.delete === "failed"}
        locale={locale as Locale}
        t={t}
      />
    </div>
  );
}

/**
 * "You refreshed too recently", with the server's delay when it gave one.
 *
 * The limit protects the user's own account with their provider — hammering a
 * third-party panel gets it throttled or banned (contract, `429`) — and the
 * sentence says so, because "too many requests" reads as this product being
 * stingy.
 *
 * `wait === null` is a server that named no delay: the message stands, and **no
 * number is made up to fill the gap**.
 */
function SyncLimited({ wait, t }: { wait: DisplayedWait | null; t: Translate }) {
  return (
    <div role="status" className="border-border mt-6 rounded-xl border px-5 py-4">
      <p className="font-medium">{t("sourceSyncLimitedTitle")}</p>
      <p className="text-muted-foreground mt-1 text-sm">{t("sourceSyncLimitedBody")}</p>
      <p className="mt-1 text-sm">
        {wait === null
          ? t("sourceSyncLimitedWaitUnknown")
          : wait.unit === "under-a-minute"
            ? t("sourceSyncLimitedWaitUnderMinute")
            : wait.unit === "minutes"
              ? t("sourceSyncLimitedWaitMinutes", { count: wait.count })
              : t("sourceSyncLimitedWaitHours", { count: wait.count })}
      </p>
    </div>
  );
}

/**
 * The ingestion steps, as a checklist rather than a spinner.
 *
 * A large playlist takes up to a minute, and a minute of silence is where a user
 * concludes it is broken. A named step says two things an indeterminate bar
 * cannot: that it is moving, and how far it got if it fails.
 *
 * Steps and never a percentage: the server reports phases, not a fraction, and a
 * bar filled from a guess is a number this product made up (US-024).
 *
 * `AUTHENTICATED` is skipped for a playlist URL, because a playlist URL
 * authenticates nothing. Showing a step the server never reaches would be a
 * reassuring fiction, and the contract says these are the real phases.
 */
function SyncProgress({
  step,
  kind,
  refreshing,
  t,
}: {
  step: SyncStep | null;
  kind: Source["kind"];
  /** True when a previous catalogue exists: this is a refresh, not an import. */
  refreshing: boolean;
  t: Translate;
}) {
  // PARSING_VOD and PARSING_SERIES are on the Xtream list only, and that is not
  // an omission. A playlist is read once and its films come off the same pass as
  // its channels; and a playlist has no series at all (`adr/0010`), so there is
  // no phase to report. The contract's own rule — these are the server's real
  // phases, and a step the implementation does not distinguish is a reassuring
  // fiction — cuts both ways.
  const steps: SyncStep[] =
    kind === "XTREAM"
      ? [
          "CONNECTING",
          "AUTHENTICATED",
          "PARSING_CHANNELS",
          "PARSING_VOD",
          "PARSING_SERIES",
          "FETCHING_EPG",
        ]
      : ["CONNECTING", "PARSING_CHANNELS", "FETCHING_EPG"];

  // Null while PENDING: claimed, not started. Everything shows as still to come,
  // which is the truth.
  const reached = step ? steps.indexOf(step) : -1;

  return (
    <div role="status" className="border-border rounded-xl border px-5 py-4">
      <p className="font-medium">
        {refreshing ? t("sourceRefreshingTitle") : t("sourceSyncingTitle")}
      </p>
      <p className="text-muted-foreground mt-1 text-sm">{t("sourceSyncingBody")}</p>

      <ol className="mt-4 space-y-2">
        {steps.map((value, index) => (
          <li
            key={value}
            className={
              index <= reached ? "text-foreground text-sm" : "text-muted-foreground text-sm"
            }
          >
            <span aria-hidden="true">{index < reached ? "✓" : index === reached ? "…" : "·"}</span>{" "}
            {t(stepKey(value))}
          </li>
        ))}
      </ol>
    </div>
  );
}

/**
 * What there is to browse: the counts that are known, when the catalogue was
 * last refreshed, what the panel says about the subscription, and the three ways
 * in.
 *
 * Shown for every source that has a catalogue, **whatever its status** (C4): the
 * title says which situation this is, and the line under it says what that means
 * for the catalogue — still available during a refresh, possibly out of date
 * after a failed one. Both sentences are the ones the home page and the
 * catalogue pages use.
 *
 * <h2>Counts: the known ones, and no zero standing in for the others</h2>
 *
 * `counts` comes from `contentCounts`: an entry per number the server actually
 * gave. A count request that failed has no entry, a playlist has no series
 * entry, and an empty list renders no list at all.
 *
 * `last_synced_at` is the last **successful** ingestion (contract), which is
 * exactly what "last synchronised" has to mean under an error.
 */
function CataloguePanel({
  row,
  condition,
  counts,
  locale,
  t,
  format,
}: {
  row: Source;
  condition: SourceCondition;
  counts: ContentCount[];
  locale: Locale;
  t: Translate;
  format: Format;
}) {
  return (
    <div className="border-border rounded-xl border px-5 py-4">
      <p className="font-medium">
        {condition.notice === null ? t("sourceReadyTitle") : t("sourceCatalogueTitle")}
      </p>

      {condition.notice === "syncing" ? (
        <p className="text-muted-foreground mt-1 text-sm">
          {t("sourceNoticeSyncingKeepsCatalogue")}
        </p>
      ) : condition.notice === "error" ? (
        <p className="text-muted-foreground mt-1 text-sm">
          {t("sourceNoticeErrorKeepsCatalogue")}
          {condition.stale ? ` ${t("sourceNoticeStale")}` : null}
        </p>
      ) : null}

      {counts.length > 0 ? (
        <ul
          aria-label={t("sourceContentsLabel")}
          className="text-muted-foreground mt-2 space-y-0.5 text-sm"
        >
          {counts.map((entry) => {
            const message = countMessage(entry);
            return <li key={entry.type}>{t(message.key, message.values)}</li>;
          })}
        </ul>
      ) : null}

      <dl className="text-muted-foreground mt-4 space-y-1 text-sm">
        {row.last_synced_at ? (
          <div className="flex gap-2">
            <dt>{t("sourceLastSynced")}</dt>
            <dd>{format.dateTime(new Date(row.last_synced_at), { dateStyle: "medium", timeStyle: "short" })}</dd>
          </div>
        ) : null}
        {row.expires_at ? (
          <div className="flex gap-2">
            <dt>{t("sourceExpiresOn")}</dt>
            <dd>{format.dateTime(new Date(row.expires_at), { dateStyle: "medium" })}</dd>
          </div>
        ) : null}
        {row.max_connections ? (
          <div className="flex gap-2">
            <dt>{t("sourceMaxConnections")}</dt>
            <dd>{row.max_connections}</dd>
          </div>
        ) : null}
      </dl>

      <p className="mt-4 flex flex-wrap gap-4">
        <a
          href={hrefFor(locale, `/app/sources/${row.id}/channels`)}
          className="text-foreground text-sm underline underline-offset-4"
        >
          {t("sourceOpenCatalogue")}
        </a>
        {/* All three, whatever the source holds. Hiding the films link is what
            made somebody conclude the feature did not exist; each catalogue says
            in its own list when it is empty, which an absent link cannot. */}
        <a
          href={hrefFor(locale, `/app/sources/${row.id}/vod`)}
          className="text-foreground text-sm underline underline-offset-4"
        >
          {t("sourceOpenFilms")}
        </a>
        <a
          href={hrefFor(locale, `/app/sources/${row.id}/series`)}
          className="text-foreground text-sm underline underline-offset-4"
        >
          {t("sourceOpenSeries")}
        </a>
      </p>
    </div>
  );
}

/**
 * Whether this browser browses this source, and the way to make it so (US-024,
 * US-018).
 *
 * <h2>After adding a source, this is the next step — and it differs</h2>
 *
 * - The **first** source became active when it was created. Once its catalogue
 *   is there, the way forward is the home page: "Discover my catalogue". Not
 *   before: a home page with nothing on it is not a destination.
 * - An **additional** source did not take the selection — somebody in the middle
 *   of a series on the first one did not ask to be moved. It offers "Use this
 *   source" instead, and choosing it leaves the user here
 *   (`lib/sources/switch-target.ts`).
 *
 * Both are offered whatever the status: a source still importing can be chosen,
 * and the home page then says it is importing. Nothing is chosen on anybody's
 * behalf.
 */
function ActivePanel({
  row,
  active,
  browsable,
  locale,
  t,
}: {
  row: Source;
  active: boolean;
  browsable: boolean;
  locale: Locale;
  t: Translate;
}) {
  if (active) {
    return (
      <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-2">
        <p className="text-brand-cyan text-sm font-medium">{t("sourceActiveHere")}</p>
        {browsable ? (
          <a
            href={hrefFor(locale, "/app")}
            className="bg-primary text-primary-foreground inline-flex h-9 items-center rounded-full px-4 text-[13px] font-semibold"
          >
            {t("sourceDiscoverCatalogue")}
          </a>
        ) : null}
      </div>
    );
  }

  return (
    <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-2">
      <UseSourceButton
        sourceId={row.id}
        from={`/app/sources/${row.id}`}
        locale={locale}
        label={t("sourceUse")}
      />
      <p className="text-muted-foreground text-sm">{t("sourceSwitcherDeviceNote")}</p>
    </div>
  );
}

/**
 * A named failure and the single action that answers it.
 *
 * The codes are never collapsed into "something went wrong": the user's next
 * move differs completely between a server that does not answer and a password
 * that was refused. `last_error_at` supplies the age, and the age is what makes
 * the message actionable — "credentials refused" does not say whether two hours
 * or two weeks of television have been missed.
 *
 * A source whose **first** import failed is kept, with this panel: "Try again"
 * re-runs the import on the same source, and nobody is sent back through the
 * add form (US-024, "Après ajout"). When a previous catalogue exists, the panel
 * under this one says it is still available.
 */
function ErrorPanel({
  code,
  since,
  id,
  refreshing,
  locale,
  t,
  tErrors,
  format,
}: {
  code?: string;
  since: string | null;
  id: string;
  /** True when a previous catalogue exists: a refresh failed, not the import. */
  refreshing: boolean;
  locale: Locale;
  t: Translate;
  tErrors: Translate;
  format: Format;
}) {
  const credentialFailure = code === "SOURCE_AUTH_FAILED" || code === "SOURCE_EXPIRED";

  return (
    <div role="alert" className="border-destructive/40 rounded-xl border px-5 py-4">
      <p className="font-medium">
        {refreshing ? t("sourceRefreshErrorTitle") : t("sourceErrorTitle")}
      </p>
      <p className="mt-1 text-sm">
        {errorMessage(code, tErrors)}
      </p>

      {since ? (
        <p className="text-muted-foreground mt-1 text-sm">
          {t("sourceErrorSince", {
            when: format.relativeTime(new Date(since), new Date()),
          })}
        </p>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-3">
        {/* Two different buttons, because the two failures need two different
            actions: one is "try the same thing again", the other is "the thing
            you typed is wrong". */}
        {credentialFailure ? (
          <a
            href={hrefFor(locale, `/app/sources/${id}?confirm=none`)}
            className="text-sm underline underline-offset-4"
          >
            {t("sourceFixCredentials")}
          </a>
        ) : null}
        <form action={syncSource}>
          <input type="hidden" name="id" value={id} />
          <Button type="submit" variant="secondary">
            {t("sourceRetry")}
          </Button>
        </form>
      </div>
    </div>
  );
}

/**
 * Rename, automatic re-synchronisation, refresh now, delete.
 *
 * <h2>One refresh at a time</h2>
 *
 * While a synchronisation runs the button reads "Refreshing…" and is disabled
 * (US-024). That is the courtesy; the rule is the server's — a second request
 * answers `409 SOURCE_SYNC_IN_PROGRESS`, which `syncSource` treats as "already
 * happening" — because a disabled attribute stops nobody with two tabs.
 *
 * <h2>The delete confirmation</h2>
 *
 * A query parameter rather than a dialog: it works without JavaScript, it
 * survives a reload, and it has room to say what goes with the source. It
 * **names the source** and lists the consequences one per line — what is
 * removed, what is kept, what it does not do (cancel the subscription), what it
 * cannot undo (adding the source again restores nothing). A confirmation that
 * leaves one of those out is a confirmation the user did not really give.
 *
 * The watch list joins the first line with its own lot (sprint 11).
 *
 * **Cancel comes first**, in the document and therefore for the keyboard: the
 * link that opens the confirmation targets `#delete-confirmation`, the browser
 * moves its sequential focus starting point there, and the first Tab lands on
 * Cancel. No script, and the destructive button is never the default.
 */
function ManagePanel({
  row,
  running,
  browsable,
  confirmingDelete,
  deleteFailed,
  locale,
  t,
}: {
  row: Source;
  running: boolean;
  browsable: boolean;
  confirmingDelete: boolean;
  deleteFailed: boolean;
  locale: Locale;
  t: Translate;
}) {
  return (
    <section className="mt-10 space-y-6">
      <h2 className="text-lg font-semibold tracking-tight">{t("sourceManageTitle")}</h2>

      <form action={updateSource} className="flex flex-wrap items-end gap-3">
        <input type="hidden" name="id" value={row.id} />
        <div className="space-y-1.5">
          <label htmlFor="label" className="text-sm font-medium">
            {t("sourceRenameLabel")}
          </label>
          <Input id="label" name="label" defaultValue={row.label} />
        </div>
        <Button type="submit" variant="secondary">
          {t("sourceRenameSubmit")}
        </Button>
      </form>

      <form action={updateSource} className="space-y-1.5">
        <input type="hidden" name="id" value={row.id} />
        {/* Sent as the opposite of the current value: this is a button that
            flips a setting, not a checkbox that needs a separate save. */}
        <input type="hidden" name="auto_sync" value={row.auto_sync ? "false" : "true"} />
        <p className="text-sm font-medium">
          {row.auto_sync ? t("sourceAutoSyncOn") : t("sourceAutoSyncOff")}
        </p>
        <p className="text-muted-foreground text-sm">{t("sourceAutoSyncHint")}</p>
        <Button type="submit" variant="secondary">
          {row.auto_sync ? t("sourceAutoSyncDisable") : t("sourceAutoSyncEnable")}
        </Button>
      </form>

      <form action={syncSource}>
        <input type="hidden" name="id" value={row.id} />
        <Button type="submit" variant="secondary" disabled={running}>
          {running ? t("sourceResyncRunning") : t("sourceResync")}
        </Button>
        {/* Only where it is true: before a first import there is no catalogue
            to stay available. */}
        {browsable ? (
          <p className="text-muted-foreground mt-1 text-sm">{t("sourceResyncHint")}</p>
        ) : null}
      </form>

      {confirmingDelete ? (
        <section
          id="delete-confirmation"
          aria-labelledby="delete-confirmation-title"
          className="border-destructive/40 scroll-mt-6 space-y-3 rounded-xl border px-5 py-4"
        >
          <h3 id="delete-confirmation-title" className="font-medium">
            {t("sourceDeleteConfirmTitle", { label: row.label })}
          </h3>

          {deleteFailed ? (
            <p role="alert" className="text-destructive text-sm">
              {t("sourceDeleteFailed")}
            </p>
          ) : null}

          <ul className="text-muted-foreground list-disc space-y-1 pl-5 text-sm">
            <li>{t("sourceDeleteConfirmRemoves")}</li>
            <li>{t("sourceDeleteConfirmKeeps")}</li>
            <li>{t("sourceDeleteConfirmProvider")}</li>
            <li>{t("sourceDeleteConfirmNoRestore")}</li>
          </ul>

          <div className="flex flex-wrap items-center gap-3">
            {/* First on purpose. See the documentation above. */}
            <a
              href={hrefFor(locale, `/app/sources/${row.id}`)}
              className="bg-secondary text-secondary-foreground inline-flex h-8 items-center rounded-lg px-2.5 text-sm font-medium"
            >
              {t("sourceDeleteCancel")}
            </a>
            <form action={deleteSource}>
              <input type="hidden" name="id" value={row.id} />
              <Button type="submit" variant="destructive">
                {t("sourceDeleteConfirm")}
              </Button>
            </form>
          </div>
        </section>
      ) : (
        <p>
          <a
            href={`${hrefFor(locale, `/app/sources/${row.id}?confirm=delete`)}#delete-confirmation`}
            className="text-destructive text-sm underline underline-offset-4"
          >
            {t("sourceDelete")}
          </a>
        </p>
      )}
    </section>
  );
}

/**
 * Where the source points, said in one line.
 *
 * The Xtream password is absent from the contract's `Source` and therefore
 * cannot be rendered by accident. The username is returned so an edit form can
 * keep it (US-06), and showing it here is what lets someone tell two
 * subscriptions on one panel apart.
 */
function originOf(row: Source): string {
  if (row.kind === "XTREAM") {
    return [row.host, row.username].filter(Boolean).join(" · ");
  }
  return row.m3u_url ?? "";
}
