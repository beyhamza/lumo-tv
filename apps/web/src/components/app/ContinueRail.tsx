import { hideFromContinue } from "@/actions/continue";
import { RailFailureLine, type RailFailure } from "./RailFailure";

/**
 * The "Continue" rail of the home page: films and series in progress, side by
 * side (US-017, US-019, S8-E01).
 *
 * <h2>Two actions per card, and they are two links</h2>
 *
 * - **Primary — play now** (S12-05). The poster and the title are one link to
 *   the sheet with its player told to start at the card's position: no
 *   "Resume" button to press in between (US-019).
 * - **Secondary — the detail page.** A second, smaller link under the card: the
 *   same sheet, nothing playing — for a film too, now that the main link no
 *   longer stops there.
 * - **Unavailable** (CW-28): a card whose item left the catalogue has no link,
 *   a line saying so, and the removal below as its only action.
 *
 * - **Remove from Continue watching** (S12-04): a small form under each card,
 *   posted to a Server Action. The card hides on every device and its position
 *   is kept (CW-10); the outcome is shown by the page, above the rail, because a
 *   rail whose last card was removed is not drawn at all.
 *
 * <h2>Presentational, and ignorant of films and series</h2>
 *
 * It takes entries already turned into titles, captions and URLs. What makes a
 * card — one per series, finished ones out, the cap — is decided in
 * `lib/home/continue-watching.ts`, where it is tested.
 *
 * Like every rail of this zone: a `<ul>` that scrolls with CSS, no script, and
 * nothing at all when it is empty — **unless it failed** (S11-05): then its
 * heading and a line with a retry, beside whatever cards did resolve.
 */
export type ContinueRailEntry = {
  key: string;
  title: string;
  /** "S2 E4" for an episode. A film has none. */
  caption?: string;
  posterUrl?: string | null;
  positionMs: number;
  durationMs: number | null;
  /** Plays at once from the card's position (S12-05). Absent on an unavailable card. */
  resume?: { href: string; label: string };
  details?: { href: string; label: string; text: string };
  /**
   * Set on a card whose item is no longer in the catalogue (CW-28): the line
   * said instead of a link. Its only action is the removal below.
   */
  unavailable?: string;
  /**
   * "Remove from Continue watching" (S12-04): the item the server hides, and the
   * words. For a series, the episode holding the card's progress — the server
   * hides the whole series from it.
   */
  hide: {
    sourceId: string;
    itemType: "VOD" | "EPISODE";
    itemRef: string;
    text: string;
    label: string;
  };
};

export function ContinueRail({
  title,
  entries,
  failure,
}: {
  title: string;
  entries: ContinueRailEntry[];
  /** Part of this rail could not be read: said under the heading, with a retry. */
  failure?: RailFailure;
}) {
  if (entries.length === 0 && !failure) return null;

  return (
    <section className="mt-10">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      {failure ? <RailFailureLine failure={failure} /> : null}
      {entries.length === 0 ? null : (
        <ul aria-label={title} className="mt-3 flex gap-4 overflow-x-auto pb-2">
          {entries.map((entry) => (
            <li key={entry.key} className="w-32 shrink-0">
              {entry.resume ? (
                <a
                  href={entry.resume.href}
                  // "Resume <title>", because the visible text is a title and a
                  // title alone does not say what following the link will do.
                  aria-label={entry.resume.label}
                  className="focus-visible:ring-ring block rounded-lg focus-visible:ring-2 focus-visible:outline-none"
                >
                  <CardFace entry={entry} />
                </a>
              ) : (
                // Unavailable (CW-28): the same face, no link — there is nothing
                // left to open — and a line saying why.
                <div className="opacity-70">
                  <CardFace entry={entry} />
                  {entry.unavailable ? (
                    <p className="text-muted-foreground mt-1 text-xs">{entry.unavailable}</p>
                  ) : null}
                </div>
              )}
              {entry.details ? (
                <a
                  href={entry.details.href}
                  aria-label={entry.details.label}
                  className="text-muted-foreground hover:text-foreground mt-1 inline-block text-xs underline underline-offset-4"
                >
                  {entry.details.text}
                </a>
              ) : null}
              {/* A form, not a script: it works without JavaScript and keeps the
                  token on the server. Visible without hovering, and a separate
                  stop for the keyboard after the resume link (S12-E01). */}
              <form action={hideFromContinue} className="mt-1">
                <input type="hidden" name="sourceId" value={entry.hide.sourceId} />
                <input type="hidden" name="itemType" value={entry.hide.itemType} />
                <input type="hidden" name="itemRef" value={entry.hide.itemRef} />
                <button
                  type="submit"
                  aria-label={entry.hide.label}
                  className="text-muted-foreground hover:text-foreground focus-visible:ring-ring rounded text-left text-xs underline underline-offset-4 focus-visible:ring-2 focus-visible:outline-none"
                >
                  {entry.hide.text}
                </button>
              </form>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Poster, position, title and caption: what every card shows, linked or not. */
function CardFace({ entry }: { entry: ContinueRailEntry }) {
  return (
    <>
      <div className="relative">
        <Poster url={entry.posterUrl ?? null} title={entry.title} />
        <PositionBar positionMs={entry.positionMs} durationMs={entry.durationMs} />
      </div>
      <p className="mt-2 truncate text-xs font-medium">{entry.title}</p>
      {entry.caption ? (
        <p className="text-muted-foreground truncate text-xs">{entry.caption}</p>
      ) : null}
    </>
  );
}

/**
 * The poster the user's own source advertises, or the title on a flat card.
 *
 * **No fallback image, ever** (AGENTS.md §1). Not `next/image` either: the host
 * is whatever panel each person subscribes to, and there is no list of them to
 * configure — the reasoning of the films grid, unchanged.
 *
 * `aria-hidden` on the fallback because the title is already in the link, right
 * underneath; read twice it is noise.
 */
function Poster({ url, title }: { url: string | null; title: string }) {
  if (!url) {
    return (
      <div
        aria-hidden="true"
        className="bg-muted text-muted-foreground flex aspect-[2/3] items-center justify-center rounded-lg p-3 text-center text-xs"
      >
        {title}
      </div>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={url}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
      className="bg-muted aspect-[2/3] w-full rounded-lg object-cover"
    />
  );
}

/**
 * How far in, across the foot of the poster.
 *
 * **Nothing at all when the length is unknown**, which is most films on most
 * panels: a bar with no denominator would be a fraction of nothing, and drawing
 * it near-empty would say the viewer had barely started.
 */
function PositionBar({
  positionMs,
  durationMs,
}: {
  positionMs: number;
  durationMs: number | null;
}) {
  if (durationMs == null || durationMs <= 0) return null;
  const percent = Math.min(100, Math.max(0, (positionMs / durationMs) * 100));

  return (
    <div className="bg-muted absolute inset-x-0 bottom-0 h-1 rounded-b-lg">
      <div className="bg-primary h-full rounded-bl-lg" style={{ width: `${percent}%` }} />
    </div>
  );
}
