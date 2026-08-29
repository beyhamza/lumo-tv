import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { deleteSource, syncSource, updateSource } from "@/actions/sources";
import { Unavailable } from "@/components/app/Unavailable";
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
 * One source: what it is doing, why it failed, and what can be done to it.
 *
 * Three screens in one route, because they are three states of one thing and the
 * user arrives here from a `202` without knowing which one they will get:
 *
 * - `PENDING` / `SYNCING` — the wait, with the step the server is on (M1);
 * - `READY` — how many channels and categories were found (US-06, US-07);
 * - `ERROR` — which failure, how old it is, and the one action that helps.
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
  const { confirm } = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");
  const format = await getFormatter();

  const source = await fetched(() =>
    api(session.accessToken).GET("/sources/{id}", { params: { path: { id } } }),
  );

  // Whether this source offers films at all (US-13). One request, against a
  // list counted in tens, and it decides one link — a source that carries only
  // channels, which is most M3U playlists, must not be offered a door onto an
  // empty grid. A failure here is `false`: the link is absent rather than
  // promising something nothing has confirmed.
  const filmCategories = await api(session.accessToken).GET(
    "/sources/{id}/categories",
    { params: { path: { id }, query: { contentType: "VOD" } } },
  );
  const hasFilms = (filmCategories.data?.items.length ?? 0) > 0;

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
  const running = row.status === "PENDING" || row.status === "SYNCING";

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
      <p className="text-muted-foreground mt-1 text-sm">{originOf(row)}</p>

      <div className="mt-8">
        {running ? (
          <SyncProgress step={row.sync_step ?? null} kind={row.kind} t={t} />
        ) : row.status === "ERROR" ? (
          <ErrorPanel
            code={row.error_code ?? undefined}
            since={row.last_error_at ?? null}
            id={row.id}
            locale={locale as Locale}
            t={t}
            tErrors={tErrors}
            format={format}
          />
        ) : (
          <ReadyPanel
            row={row}
            hasFilms={hasFilms}
            locale={locale as Locale}
            t={t}
            format={format}
          />
        )}
      </div>

      <ManagePanel
        row={row}
        confirmingDelete={confirm === "delete"}
        locale={locale as Locale}
        t={t}
      />
    </div>
  );
}

/**
 * The four ingestion steps, as a checklist rather than a spinner.
 *
 * A large playlist takes up to a minute, and a minute of silence is where a user
 * concludes it is broken. A named step says two things an indeterminate bar
 * cannot: that it is moving, and how far it got if it fails.
 *
 * `AUTHENTICATED` is skipped for a playlist URL, because a playlist URL
 * authenticates nothing. Showing a step the server never reaches would be a
 * reassuring fiction, and the contract says these are the real phases.
 */
function SyncProgress({
  step,
  kind,
  t,
}: {
  step: SyncStep | null;
  kind: Source["kind"];
  t: Translate;
}) {
  // PARSING_VOD is on the Xtream list only, and that is not an omission: a
  // playlist is read once and its films come off the same pass as its channels,
  // so an M3U source never reports that phase. The contract's own rule — these
  // are the server's real phases, and a step the implementation does not
  // distinguish is a reassuring fiction — cuts both ways.
  const steps: SyncStep[] =
    kind === "XTREAM"
      ? ["CONNECTING", "AUTHENTICATED", "PARSING_CHANNELS", "PARSING_VOD", "FETCHING_EPG"]
      : ["CONNECTING", "PARSING_CHANNELS", "FETCHING_EPG"];

  // Null while PENDING: claimed, not started. Everything shows as still to come,
  // which is the truth.
  const reached = step ? steps.indexOf(step) : -1;

  return (
    <div role="status" className="border-border rounded-xl border px-5 py-4">
      <p className="font-medium">{t("sourceSyncingTitle")}</p>
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

type SyncStepKey =
  | "syncStepConnecting"
  | "syncStepAuthenticated"
  | "syncStepParsingChannels"
  | "syncStepParsingVod"
  | "syncStepFetchingEpg";

/**
 * One case per value, and no `default`.
 *
 * The previous version fell through to "fetching the guide", which was harmless
 * until the contract grew a fifth phase — and then it labelled the film catalogue
 * as the EPG. Exhaustive, `PARSING_VOD` was a type error the moment it was
 * generated rather than a wrong word on somebody's screen.
 */
function stepKey(step: SyncStep): SyncStepKey {
  switch (step) {
    case "CONNECTING":
      return "syncStepConnecting";
    case "AUTHENTICATED":
      return "syncStepAuthenticated";
    case "PARSING_CHANNELS":
      return "syncStepParsingChannels";
    case "PARSING_VOD":
      return "syncStepParsingVod";
    case "FETCHING_EPG":
      return "syncStepFetchingEpg";
  }
}

/**
 * What was found, in one line, plus what the panel says about the subscription.
 *
 * Both counts or neither: "1 248 chaînes · 96 catégories" is one sentence, and a
 * screen that could render half of it would render half a sentence.
 */
function ReadyPanel({
  row,
  hasFilms,
  locale,
  t,
  format,
}: {
  row: Source;
  hasFilms: boolean;
  locale: Locale;
  t: Translate;
  format: Format;
}) {
  return (
    <div className="border-border rounded-xl border px-5 py-4">
      <p className="font-medium">{t("sourceReadyTitle")}</p>

      <p className="text-muted-foreground mt-1 text-sm">
        {t("sourceCounts", {
          channels: row.channel_count ?? 0,
          categories: row.category_count ?? 0,
        })}
      </p>

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
        {/* Only when the source has films. An empty promise is worse than an
            absence: somebody offered a door goes looking for the room. */}
        {hasFilms ? (
          <a
            href={hrefFor(locale, `/app/sources/${row.id}/vod`)}
            className="text-foreground text-sm underline underline-offset-4"
          >
            {t("sourceOpenFilms")}
          </a>
        ) : null}
      </p>
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
 */
function ErrorPanel({
  code,
  since,
  id,
  locale,
  t,
  tErrors,
  format,
}: {
  code?: string;
  since: string | null;
  id: string;
  locale: Locale;
  t: Translate;
  tErrors: Translate;
  format: Format;
}) {
  const credentialFailure = code === "SOURCE_AUTH_FAILED" || code === "SOURCE_EXPIRED";

  return (
    <div role="alert" className="border-destructive/40 rounded-xl border px-5 py-4">
      <p className="font-medium">{t("sourceErrorTitle")}</p>
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
 * Rename, automatic re-synchronisation, sync now, delete.
 *
 * The delete confirmation is a query parameter rather than a dialog: it works
 * without JavaScript, it survives a reload, and it names what goes with the
 * source. Favourites go too, by cascade, and a confirmation that does not say so
 * is a confirmation the user did not really give.
 */
function ManagePanel({
  row,
  confirmingDelete,
  locale,
  t,
}: {
  row: Source;
  confirmingDelete: boolean;
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
        <Button type="submit" variant="secondary">
          {t("sourceResync")}
        </Button>
        <p className="text-muted-foreground mt-1 text-sm">{t("sourceResyncHint")}</p>
      </form>

      {confirmingDelete ? (
        <div className="border-destructive/40 space-y-3 rounded-xl border px-5 py-4">
          <p className="font-medium">{t("sourceDeleteConfirmTitle")}</p>
          <p className="text-muted-foreground text-sm">{t("sourceDeleteConfirmBody")}</p>
          <div className="flex gap-3">
            <form action={deleteSource}>
              <input type="hidden" name="id" value={row.id} />
              <Button type="submit" variant="destructive">
                {t("sourceDeleteConfirm")}
              </Button>
            </form>
            <a
              href={hrefFor(locale, `/app/sources/${row.id}`)}
              className="text-muted-foreground self-center text-sm underline underline-offset-4"
            >
              {t("sourceDeleteCancel")}
            </a>
          </div>
        </div>
      ) : (
        <p>
          <a
            href={hrefFor(locale, `/app/sources/${row.id}?confirm=delete`)}
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

