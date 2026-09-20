import type { Channel } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/**
 * One rail: a row of channels to get back to in a click.
 *
 * Lifted out of the channels page in S8-04, when the home page became the third
 * and fourth place to draw one. The markup is that page's, unchanged — the
 * end-to-end suite finds a rail by the accessible name of its list, and that is
 * the part that must not move.
 *
 * <h2>It scrolls with CSS, and with nothing else</h2>
 *
 * `overflow-x: auto` and no JavaScript at all — no carousel, no arrows, no
 * measured widths. A touch screen flicks it, a trackpad swipes it, a keyboard
 * reaches every card because they are links in a list. The zone-wide rule that
 * these screens work without JavaScript is not suspended for decoration.
 *
 * <h2>An empty rail is no rail</h2>
 *
 * Nothing starred yet, or nothing watched yet on this source, renders nothing —
 * no heading, no reserved space (US-017). An empty strip with a heading above it
 * is a promise that something belongs there.
 *
 * <h2>Selecting a card plays</h2>
 *
 * Every card is one link and the caller decides where it goes; on both screens
 * that use this, it is a `?play=` URL, so choosing a channel starts it (US-020)
 * and the back button closes the player.
 */
export function ChannelRail({
  title,
  channels,
  playHref,
  prominent = false,
  more,
}: {
  title: string;
  channels: Channel[];
  /** Builds the link that plays one channel. */
  playHref: (channelId: string) => string;
  /**
   * A section of a page rather than a shortcut above a list. The channels page
   * keeps the small capitals it has always had, because there the rail is a
   * reminder on top of the catalogue somebody came for; on the home page the
   * rails **are** the page.
   */
  prominent?: boolean;
  /**
   * Where the whole of it lives — the library for favourites, the catalogue for
   * recent channels. Beside the heading rather than after the last card: a link
   * at the end of a scroller is a link only the people who scrolled ever see.
   */
  more?: { href: string; label: string };
}) {
  if (channels.length === 0) return null;

  const heading = (
    <h2
      className={
        prominent
          ? "text-lg font-semibold tracking-tight"
          : "text-muted-foreground text-xs font-medium tracking-wide uppercase"
      }
    >
      {title}
    </h2>
  );

  return (
    <section className={prominent ? "mt-10" : "mt-6"}>
      {more ? (
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          {heading}
          <a
            href={more.href}
            className="text-muted-foreground hover:text-foreground text-sm underline underline-offset-4"
          >
            {more.label}
          </a>
        </div>
      ) : (
        heading
      )}
      <ul
        aria-label={title}
        className={cn("flex gap-3 overflow-x-auto pb-2", prominent ? "mt-3" : "mt-2")}
      >
        {channels.map((channel) => (
          <li key={channel.id} className="shrink-0">
            <a
              href={playHref(channel.id)}
              className="border-border hover:bg-secondary/60 flex w-40 items-center gap-2 rounded-xl border px-3 py-2"
            >
              <ChannelLogo channel={channel} />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                {channel.name}
              </span>
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * The logo the user's own playlist advertises, or nothing.
 *
 * <b>Lumo ships no fallback artwork</b> (AGENTS.md §1): a channel with no logo
 * gets its initial, never a bundled image of ours.
 *
 * A plain `<img>`, deliberately, and not `next/image`. The optimiser would fetch
 * every provider logo through our own server, which is the same posture question
 * as relaying a stream (ADR 0007) for a far smaller benefit. The cost of the
 * plain tag is that a logo served over `http` will not load on an `https` page —
 * which is honest: it is the provider's choice, and the initial takes its place.
 */
export function ChannelLogo({ channel }: { channel: Channel }) {
  if (!channel.logo_url) {
    return (
      <span className="bg-secondary text-muted-foreground flex h-8 w-8 shrink-0 items-center justify-center rounded text-xs">
        {channel.name.slice(0, 1).toUpperCase()}
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={channel.logo_url}
      alt=""
      width={32}
      height={32}
      loading="lazy"
      decoding="async"
      // The provider learns nothing about which of their channels is being
      // looked at from which page.
      referrerPolicy="no-referrer"
      className="h-8 w-8 shrink-0 rounded object-contain"
    />
  );
}
