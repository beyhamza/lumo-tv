"use client";

import { useTranslations } from "next-intl";
import { playbackRefusal } from "@/lib/playback/refusal";

/**
 * The sentence for a stream the API refused to hand over, and the way out when
 * there is one (contract lot C4 — P2).
 *
 * Shared by the channel player and the film/episode player, which used to carry
 * one copy each of "the codes I have words for". What is said and whether a link
 * follows is decided in `lib/playback/refusal.ts`, pure and tested; this only
 * draws it.
 *
 * The link goes to the source's page in both cases — that is where a refresh is
 * followed and where a source is fixed — and only its wording differs.
 */
export function PlaybackRefusalMessage({
  code,
  sourceHref,
}: {
  code: string;
  /** The source's own page, locale included. Built by the server page. */
  sourceHref: string;
}) {
  const t = useTranslations("App");
  const tErrors = useTranslations("Errors");
  const refusal = playbackRefusal(code);

  return (
    <>
      <p className="mt-1 text-sm">
        {refusal.message.namespace === "Errors"
          ? tErrors(refusal.message.key)
          : t(refusal.message.key)}
      </p>
      {refusal.sourceLink ? (
        <p className="mt-2 text-sm">
          {/* A plain anchor, like every link in this zone: a full navigation is
              the right thing when leaving a player. */}
          <a href={sourceHref} className="underline underline-offset-4">
            {refusal.sourceLink === "fix" ? t("playerFixSource") : t("playerFollowRefresh")}
          </a>
        </p>
      ) : null}
    </>
  );
}
