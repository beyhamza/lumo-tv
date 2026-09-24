import { ChannelLogo } from "@/components/app/ChannelRail";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import type { Channel } from "@/lib/api/types";
import { type DirectView, directViewHref } from "@/lib/direct/view-memory";

/**
 * Direct's two views, and the switch between them (US-16, S9-04-06).
 *
 * <h2>One route, two presentations</h2>
 *
 * Chaînes and Guide are the same catalogue shown twice, so the switch is two
 * plain links that differ only in `?view=channels|guide`. A link and not a
 * button: this zone works without JavaScript (AGENTS.md §3), and the view is a
 * URL like every other state of the page — it survives a reload, it can be
 * shared, and the back button returns to the view that was left.
 *
 * <h2>Switching keeps what the person was looking at</h2>
 *
 * The filter, the search, the page and the player ride along in the link
 * ({@link directViewHref}), because losing the filter on a view change is
 * exactly what GD-01 forbids. The write of the remembered view is the proxy's
 * job: an explicit `?view=` primes the cookie before the page is even rendered.
 *
 * <h2>The Guide view, until S9-05's grid</h2>
 *
 * The Guide is the **« En ce moment »** list: one line per channel of the
 * filtered result, the programme on air and the one after it. It is drawn from
 * the single grouped guide request the page already makes for its channel ids
 * — never one request per row (S9-04-06). A channel the provider gave no guide
 * stays in the list and draws no programme line rather than an invented
 * "unavailable" (S7-03).
 *
 * `Maintenant` sits above the list. Until the time grid of S9-05 exists there
 * is no offset to promise, so it is an anchor that returns to the top — the
 * position a fresh entry in the Guide opens on.
 */
export function DirectViews({
  locale,
  sourceId,
  active,
  query,
  label,
  channelsLabel,
  guideLabel,
}: {
  locale: Locale;
  sourceId: string;
  active: DirectView;
  /** The filter, search, page and player the switch must keep (GD-01). */
  query: {
    categoryId?: string;
    q?: string;
    page?: number;
    group?: string;
    play?: string;
  };
  label: string;
  channelsLabel: string;
  guideLabel: string;
}) {
  const views: { key: DirectView; label: string }[] = [
    { key: "channels", label: channelsLabel },
    { key: "guide", label: guideLabel },
  ];

  return (
    <nav aria-label={label} className="mt-4">
      <ul className="flex gap-2 text-sm">
        {views.map((view) => (
          <li key={view.key}>
            <a
              href={hrefFor(locale, directViewHref(view.key, { sourceId, ...query }))}
              // `aria-current` rather than colour alone: the open view has to be
              // announced, not merely drawn — the same rule as the catalogue
              // tabs above.
              aria-current={view.key === active ? "page" : undefined}
              className={
                view.key === active
                  ? "bg-secondary text-secondary-foreground block rounded-lg px-3 py-1.5 font-medium"
                  : "text-muted-foreground hover:text-foreground block rounded-lg px-3 py-1.5"
              }
            >
              {view.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** One channel of the Guide, with the programmes the page could read for it. */
export type GuideRow = {
  channel: Channel;
  /** Already formatted in the reader's zone. Absent when nothing is on. */
  current?: { time: string; title: string };
  next?: { time: string; title: string };
};

/**
 * The Guide's **« En ce moment »** list.
 *
 * Every row is a link that plays the channel immediately, exactly as a channel
 * row of the Chaînes view does: the Guide of this sprint announces what is on,
 * it does not open a programme sheet — that is S9-06.
 */
export function DirectGuideNow({
  title,
  nowButton,
  nowLabel,
  nextLabel,
  rows,
  playHref,
}: {
  title: string;
  nowButton: string;
  nowLabel: string;
  nextLabel: string;
  rows: readonly GuideRow[];
  playHref: (channelId: string) => string;
}) {
  return (
    // The anchor the `Maintenant` control returns to, and the top of a fresh
    // entry in the Guide.
    <section id="now">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
        <a
          href="#now"
          className="border-border hover:bg-secondary/60 rounded-lg border px-3 py-1.5 text-sm font-medium"
        >
          {nowButton}
        </a>
      </div>

      <ul aria-label={title} className="mt-3 space-y-2">
        {rows.map(({ channel, current, next }) => (
          <li key={channel.id} className="border-border rounded-xl border px-4 py-3">
            <a
              href={playHref(channel.id)}
              className="flex items-center gap-3 underline-offset-4 hover:underline"
            >
              <ChannelLogo channel={channel} />
              <span className="min-w-0 truncate font-medium">{channel.name}</span>
            </a>

            {/* Two independent lines: a gap in the guide has a next programme
                and no current one, and the last programme of the window has the
                reverse. Both absent draws only the channel name, never a
                placeholder (S7-03). */}
            {current ? (
              <ProgrammeLine label={nowLabel} line={current} />
            ) : null}
            {next ? <ProgrammeLine label={nextLabel} line={next} /> : null}
          </li>
        ))}
      </ul>
    </section>
  );
}

function ProgrammeLine({
  label,
  line,
}: {
  label: string;
  line: { time: string; title: string };
}) {
  return (
    <p className="text-muted-foreground mt-1 truncate text-sm">
      <span className="text-foreground font-medium">{label}</span>{" "}
      <span className="tabular-nums">{line.time}</span> {line.title}
    </p>
  );
}
