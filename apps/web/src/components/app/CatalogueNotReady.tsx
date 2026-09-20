import { getTranslations } from "next-intl/server";
import { SourceNotice } from "@/components/app/SourceNotice";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import type { Source } from "@/lib/api/types";
import { sourceCondition } from "@/lib/sources/source-condition";

/**
 * A catalogue page — channels, films or series — of a source that has **never**
 * finished an import.
 *
 * <h2>What `409 SOURCE_NOT_READY` means now</h2>
 *
 * Since contract lot C4 the listings answer `200` in every status as soon as one
 * ingestion has succeeded. The `409` that brings a page here is therefore no
 * longer "busy": it is "there is no catalogue yet" — a first import waiting,
 * running, or failed.
 *
 * <h2>One component, where there were three copies of a dead end</h2>
 *
 * Each of the three pages drew its own dashed box with a link, and none of them
 * said which of those three situations it was. This says it, in the words every
 * other screen uses (`SourceNotice`): the real step while the import runs, the
 * named failure when it failed. The way out is the source's page in both cases —
 * that is where the progress, "Try again" and the management live.
 *
 * <h2>It reloads by itself while the first import runs, and only then</h2>
 *
 * The same `<meta http-equiv="refresh">` as the source page: no script, and it
 * stops on its own because the tag is absent from the next render once the
 * status has changed. There is nothing on this screen to scroll or to type into,
 * so reloading it costs nobody anything — and the import ending is precisely
 * what the visitor is waiting for. Not rendered for a failed import: reloading
 * an error every five seconds changes nothing.
 *
 * @param source the source when `GET /sources/{id}` answered, `null` when it
 *   did not. The page still renders then, with the plain sentence and the link:
 *   the listing said `SOURCE_NOT_READY`, and that much is known.
 */
export async function CatalogueNotReady({
  sourceId,
  source,
  locale,
}: {
  sourceId: string;
  source: Source | null;
  locale: Locale;
}) {
  const t = await getTranslations("App");
  const condition = source ? sourceCondition(source) : null;

  return (
    <div>
      {condition?.notice === "syncing" ? <meta httpEquiv="refresh" content="5" /> : null}

      <div className="border-border rounded-xl border border-dashed px-5 py-6">
        <p className="font-medium">{t("catalogueNotReady")}</p>
        <p className="text-muted-foreground mt-1 text-sm">{t("catalogueNotReadyBody")}</p>
        {/* The notice carries its own link when there is one to show. Without a
            source to describe — or, improbably, a READY one the listing still
            refused — the link is given here so the box is never a dead end. */}
        {source && condition?.notice ? null : (
          <p className="mt-3">
            <a
              href={hrefFor(locale, `/app/sources/${sourceId}`)}
              className="text-sm underline underline-offset-4"
            >
              {t("catalogueNotReadyLink")}
            </a>
          </p>
        )}
      </div>

      {source && condition ? (
        <SourceNotice source={source} condition={condition} locale={locale} />
      ) : null}
    </div>
  );
}
