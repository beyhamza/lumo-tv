"use client";

import { useCallback, useEffect, useRef } from "react";
import { ChannelLogo } from "@/components/app/ChannelRail";
import type { Channel, EpgProgramme } from "@/lib/api/types";
import { clockTime } from "@/lib/epg/format";
import { useProgrammeClock } from "@/lib/epg/live-clock";
import { momentOf, watchAllowed, watchAvailable } from "@/lib/epg/programme";

/**
 * The programme sheet of the web (US-16, S9-06-04, GD-07/08/10/11).
 *
 * <h2>A side panel over the grid, not a screen</h2>
 *
 * Selecting a programme **in the Guide** opens this panel and leaves the grid
 * drawn underneath, exactly as the television's side sheet does
 * (`docs/design/0.2.0/direct-guide.md`, "Fiche de programme"). It shows the
 * programme captured when it was opened — its title never changes underneath the
 * viewer — and offers « Regarder en direct » only while that programme is
 * current. The Chaînes view keeps its immediate playback: only the Guide goes
 * through information first.
 *
 * <h2>Why this is a Client Component</h2>
 *
 * GD-07 wants the action to disappear at the end **without a reload**, and
 * GD-08 wants it to appear when a future programme starts, again without one.
 * Both need a clock in the browser. The server rendered with `epgNow()`
 * (`LUMO_NOW` when a session pinned it) and passed that instant in; the hook
 * advances it with elapsed time, so the panel and the server never disagree
 * (`live-clock.ts`). The temporal decision itself is the pure module
 * `lib/epg/programme.ts` — the panel calls it, it does not restate it.
 *
 * <h2>What still works without JavaScript</h2>
 *
 * The panel is server-rendered too, so it appears with scripting off, the
 * « Fermer » link closes it, and the action is a real `<a href>` to the channel
 * player. What scripting adds is only the time: a no-JS page keeps the moment it
 * was rendered with, and the activation guard below has nothing to re-read.
 *
 * <h2>Keyboard (QA-06-04-12, GD-14)</h2>
 *
 * Escape closes the panel before anything under it. On arrival the focus goes
 * to the action when there is one and to Fermer otherwise; when the action
 * disappears while it held the focus, the focus joins **Fermer** rather than
 * falling to the document (GD-07).
 */

export type ProgrammeSheetLabels = {
  /** Accessible name of the dialog. */
  sheet: string;
  /** The action, « Regarder en direct ». */
  watch: string;
  /** « Fermer ». */
  close: string;
};

export function ProgrammeSheet({
  programme,
  channel,
  nowIso,
  timeZone,
  locale,
  playHref,
  closeHref,
  labels,
}: {
  programme: EpgProgramme;
  /** The channel the programme is on; its logo, or a neutral initial. */
  channel?: Channel;
  /** The instant the server rendered with, from `epgNow()` (I-3). */
  nowIso: string;
  timeZone: string;
  locale: string;
  /** Where « Regarder en direct » goes: the channel player URL. */
  playHref: string;
  /** The same URL without `?programme=`, so closing returns where it opened. */
  closeHref: string;
  labels: ProgrammeSheetLabels;
}) {
  // `nowIso` comes from the server's `epgNow()`; an unreadable value is kept as
  // `NaN`, which `momentOf` reports as past — no action, no crash. Never
  // `Date.now()` here: that is an impure call during render (React lint) and it
  // would defeat the pinned clock anyway.
  const serverNowMs = Date.parse(nowIso);

  const { now, instant } = useProgrammeClock(programme, serverNowMs);
  const current = watchAvailable(momentOf(programme, now));

  const watchRef = useRef<HTMLAnchorElement>(null);
  const closeRef = useRef<HTMLAnchorElement>(null);
  // Which of the two the viewer last focused. Only a node *gaining* focus
  // writes it, so the action leaving the composition cannot clear it by
  // blurring — that is what lets GD-07 see that the focus has to move.
  const focused = useRef<"watch" | "close" | null>(null);
  const previousCurrent = useRef(current);

  const close = useCallback(() => {
    // The sheet is a URL (`?programme=`), like every other state of this page,
    // so closing is a navigation and the back button does the same thing.
    window.location.assign(closeHref);
  }, [closeHref]);

  // Arrival (GD-08): the action when the programme is current, Fermer
  // otherwise. Read on the server's instant, never a fresh `Date.now()`.
  useEffect(() => {
    const arrival = watchAvailable(momentOf(programme, serverNowMs));
    (arrival ? watchRef.current : closeRef.current)?.focus();
    // Deliberately once per open programme.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [programme.id]);

  // GD-07: the single place the focus moves when the action disappears. The
  // rule is not restated here — `current` is `watchAvailable(momentOf(...))`.
  useEffect(() => {
    if (previousCurrent.current && !current && focused.current === "watch") {
      closeRef.current?.focus();
    }
    previousCurrent.current = current;
  }, [current]);

  // Escape closes the panel first (QA-06-04-12).
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        close();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [close]);

  const range = [clockTime(programme.starts_at, locale, timeZone), clockTime(programme.ends_at, locale, timeZone)]
    .filter((part): part is string => part !== undefined)
    .join(" – ");

  return (
    <div
      role="presentation"
      onClick={close}
      className="bg-foreground/50 fixed inset-0 z-50 flex justify-end"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={labels.sheet}
        // A click inside the panel must not reach the backdrop's close handler.
        onClick={(event) => event.stopPropagation()}
        className="bg-background border-border flex h-full w-full max-w-sm flex-col gap-4 overflow-y-auto border-l p-5 shadow-xl"
      >
        {channel ? (
          <div className="flex items-center gap-3">
            {/* The channel's own logo, or a neutral initial — Lumo ships no
                artwork of its own and invents no brand (GD-11). */}
            <ChannelLogo channel={channel} />
            <span className="text-muted-foreground min-w-0 truncate text-sm font-medium">
              {channel.name}
            </span>
          </div>
        ) : null}

        <h2 className="text-lg font-semibold tracking-tight">{programme.title}</h2>

        {range ? (
          <p className="text-muted-foreground text-sm tabular-nums">{range}</p>
        ) : null}

        {/* An absent description is no zone at all (GD-11, S7-03). */}
        {programme.description ? (
          <p className="text-sm">{programme.description}</p>
        ) : null}

        <div className="mt-auto flex flex-col gap-2">
          {current ? (
            <a
              ref={watchRef}
              href={playHref}
              onFocus={() => {
                focused.current = "watch";
              }}
              onClick={(event) => {
                // GD-08: read the instant again at activation — a sheet left
                // open across the end does not play, even if the panel has not
                // re-rendered yet.
                if (!watchAllowed(programme, instant())) event.preventDefault();
              }}
              className="bg-primary text-primary-foreground rounded-lg px-4 py-2 text-center text-sm font-medium"
            >
              {labels.watch}
            </a>
          ) : null}

          <a
            ref={closeRef}
            href={closeHref}
            onFocus={() => {
              focused.current = "close";
            }}
            className="border-border hover:bg-secondary/60 rounded-lg border px-4 py-2 text-center text-sm font-medium"
          >
            {labels.close}
          </a>
        </div>
      </div>
    </div>
  );
}
