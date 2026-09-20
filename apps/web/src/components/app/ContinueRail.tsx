/**
 * The "Continue" rail of the home page: films and series in progress, side by
 * side (US-017, US-019, S8-E01).
 *
 * <h2>Two actions per card, and they are two links</h2>
 *
 * - **Primary — resume.** The poster and the title are one link to the URL that
 *   resumes: the same one the films and series rails have always used, so a card
 *   here and a card there cannot disagree about where "resume" goes.
 * - **Secondary — the detail page.** A second, smaller link under the card, only
 *   when it leads somewhere else. A film's resume URL **is** its detail page —
 *   the player lives there — so a film card has one link: two links to one
 *   destination are two stops for a keyboard and two announcements for a screen
 *   reader, for nothing.
 *
 * **No "remove from Continue".** That is sprint 12 and needs a contract lot of
 * its own; a control drawn before it works is worse than its absence.
 *
 * <h2>Presentational, and ignorant of films and series</h2>
 *
 * It takes entries already turned into titles, captions and URLs. What makes a
 * card — one per series, finished ones out, the cap — is decided in
 * `lib/home/continue-watching.ts`, where it is tested.
 *
 * Like every rail of this zone: a `<ul>` that scrolls with CSS, no script, and
 * nothing at all when it is empty.
 */
export type ContinueRailEntry = {
  key: string;
  title: string;
  /** "S2 E4" for an episode. A film has none. */
  caption?: string;
  posterUrl?: string | null;
  positionMs: number;
  durationMs: number | null;
  resume: { href: string; label: string };
  details?: { href: string; label: string; text: string };
};

export function ContinueRail({
  title,
  entries,
}: {
  title: string;
  entries: ContinueRailEntry[];
}) {
  if (entries.length === 0) return null;

  return (
    <section className="mt-10">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      <ul aria-label={title} className="mt-3 flex gap-4 overflow-x-auto pb-2">
        {entries.map((entry) => (
          <li key={entry.key} className="w-32 shrink-0">
            <a
              href={entry.resume.href}
              // "Resume <title>", because the visible text is a title and a
              // title alone does not say what following the link will do.
              aria-label={entry.resume.label}
              className="focus-visible:ring-ring block rounded-lg focus-visible:ring-2 focus-visible:outline-none"
            >
              <div className="relative">
                <Poster url={entry.posterUrl ?? null} title={entry.title} />
                <PositionBar positionMs={entry.positionMs} durationMs={entry.durationMs} />
              </div>
              <p className="mt-2 truncate text-xs font-medium">{entry.title}</p>
              {entry.caption ? (
                <p className="text-muted-foreground truncate text-xs">{entry.caption}</p>
              ) : null}
            </a>
            {entry.details ? (
              <a
                href={entry.details.href}
                aria-label={entry.details.label}
                className="text-muted-foreground hover:text-foreground mt-1 inline-block text-xs underline underline-offset-4"
              >
                {entry.details.text}
              </a>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
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
