"use client";

import { useTranslations } from "next-intl";

/**
 * What a page of the account zone shows when it throws instead of answering.
 *
 * The pages are meant to catch their own failures — `fetched()` turns a request
 * that got no answer into "unavailable" — and this is the net for the ones that
 * do not. Without it, a refused connection to the API surfaced as Next's
 * generic error page, in English on `/fr`, with nothing to do but reload
 * (BUG-S11-06-03). An outage reads as an outage, in the user's language, with
 * a way back.
 *
 * Client component, because Next requires it of an error boundary; its three
 * strings come from the `AppError` namespace the zone layout provides.
 */
export default function AppError({ reset }: { error: Error; reset: () => void }) {
  const t = useTranslations("AppError");

  return (
    <div role="status" className="border-border rounded-xl border border-dashed px-5 py-6">
      <p className="font-medium">{t("title")}</p>
      <p className="text-muted-foreground mt-1 text-sm">{t("body")}</p>
      <p className="mt-3">
        <button
          type="button"
          onClick={reset}
          className="text-sm underline underline-offset-4"
        >
          {t("retry")}
        </button>
      </p>
    </div>
  );
}
