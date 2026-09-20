import { getTranslations } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { errorMessage } from "@/lib/api/error-message";
import type { Source } from "@/lib/api/types";
import type { SourceCondition } from "@/lib/sources/source-condition";
import { stepKey } from "@/lib/sources/sync-step";

/**
 * What a source is going through, above whatever can still be shown.
 *
 * Written for the home page (S8-04) and lifted here, unchanged in what it says,
 * when the three catalogue pages needed the same sentences (S8-05): since
 * contract lot C4 a catalogue is served in every status once one ingestion has
 * succeeded, so "refreshing" and "the last refresh failed" are things a catalogue
 * page has to be able to say **over a catalogue**, not instead of one. A third
 * wording of the same two facts would be a third place for them to drift.
 *
 * <h2>The step is the server's, in the source page's own words</h2>
 *
 * `sync_step` is the real phase and the message keys are the ones the source
 * page's checklist uses, so "Reading the channels" here is "Reading the
 * channels" there. `PENDING` has no step yet — claimed, not started — and says
 * so with the status word rather than inventing a phase. Never a percentage:
 * the server reports none (US-024, "Actualisation").
 *
 * <h2>An error names itself and points at the fix, it does not carry it</h2>
 *
 * The message comes from `error_code` through `errorMessage`, the mapping every
 * other screen uses. Retrying and correcting credentials are forms that live on
 * the source's page, with its confirmation and its progress; a second copy here
 * would be a second place for them to drift. One link.
 *
 * <h2>"May be out of date" is said only when it is true</h2>
 *
 * `condition.stale`: the last attempt failed and a previous catalogue is what is
 * on screen (US-024, "Indisponibilité"). It promises nothing about the streams —
 * a catalogue that lists a channel does not make the provider serve it.
 *
 * `role="status"` for a synchronisation and `role="alert"` for a failure, as on
 * the source page: one is news, the other needs somebody.
 *
 * @returns nothing at all for a source with nothing to announce, so a caller can
 *   render it unconditionally.
 */
export async function SourceNotice({
  source,
  condition,
  locale,
}: {
  source: Source;
  condition: SourceCondition;
  locale: Locale;
}) {
  if (condition.notice === null) return null;

  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");
  const sourceHref = hrefFor(locale, `/app/sources/${source.id}`);

  if (condition.notice === "syncing") {
    return (
      <div role="status" className="border-border mt-6 rounded-xl border px-5 py-4">
        <p className="font-medium">{t("sourceNoticeSyncingTitle", { source: source.label })}</p>
        <p className="mt-1 text-sm">
          {source.sync_step ? t(stepKey(source.sync_step)) : t("sourceStatusPending")}
        </p>
        <p className="text-muted-foreground mt-1 text-sm">
          {condition.browsable
            ? t("sourceNoticeSyncingKeepsCatalogue")
            : t("sourceNoticeSyncingFirst")}
        </p>
        <p className="mt-3">
          <a href={sourceHref} className="text-sm underline underline-offset-4">
            {t("sourceNoticeSyncingLink")}
          </a>
        </p>
      </div>
    );
  }

  return (
    <div role="alert" className="border-destructive/40 mt-6 rounded-xl border px-5 py-4">
      <p className="font-medium">{t("sourceNoticeErrorTitle", { source: source.label })}</p>
      <p className="mt-1 text-sm">{errorMessage(source.error_code ?? undefined, tErrors)}</p>
      {condition.browsable ? (
        <p className="text-muted-foreground mt-1 text-sm">
          {t("sourceNoticeErrorKeepsCatalogue")}
          {condition.stale ? ` ${t("sourceNoticeStale")}` : null}
        </p>
      ) : null}
      <p className="mt-3">
        <a href={sourceHref} className="text-sm underline underline-offset-4">
          {t("sourceNoticeErrorLink")}
        </a>
      </p>
    </div>
  );
}
