"use client";

import { useTranslations } from "next-intl";

/**
 * What a player becomes once the server has confirmed its source was deleted
 * (US-024, "Suppression").
 *
 * <h2>One sentence and one action, by decision</h2>
 *
 * "This source has been removed from your account.", and **Continue**. Nothing
 * else: no other channel is started, no other source is opened, no countdown
 * runs. Whoever deleted the source — the same person on their phone, most likely
 * — is not asking this browser to pick something else to watch.
 *
 * <h2>Continue goes to the home page, and the rest follows by itself</h2>
 *
 * The cookie of this browser may still name the deleted source. It does not need
 * repairing: the home page resolves the active source from `GET /sources` on
 * every render, and a stored id absent from that list resolves exactly like no
 * choice at all — the only remaining source, a question when several remain, or
 * "add a source" when none does (US-018, `lib/sources/active-source.ts`).
 *
 * A plain anchor: a full page load is wanted here, because everything the layout
 * drew — the rail, the switcher — was drawn from a list that is now wrong.
 *
 * `role="alert"`: playback stopped under somebody who did nothing, and assistive
 * technology has to say why without being asked.
 */
export function SourceDeleted({ continueHref }: { continueHref: string }) {
  const t = useTranslations("App");

  return (
    <section
      role="alert"
      className="border-destructive/40 mb-8 rounded-xl border px-5 py-5"
    >
      <p className="font-medium">{t("playerSourceDeleted")}</p>
      <p className="mt-4">
        <a
          href={continueHref}
          className="bg-primary text-primary-foreground inline-flex h-10 items-center rounded-lg px-5 text-sm font-medium"
        >
          {t("playerSourceDeletedContinue")}
        </a>
      </p>
    </section>
  );
}
