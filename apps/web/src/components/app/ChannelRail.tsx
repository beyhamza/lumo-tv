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
 * <h2>An empty rail is no rail — unless it carries doors</h2>
 *
 * Nothing starred yet, or nothing watched yet on this source, renders nothing —
 * no heading, no reserved space (US-017). An empty strip with a heading above it
 * is a promise that something belongs there.
 *
 * The Live rail is the exception, and it is deliberate: its two entries
 * *Toutes les chaînes* and *Guide TV* are the home page's explicit ways into
 * the catalogue and the guide (S9-04-07), and they must be there **before** any
 * history exists. A cold account has no recent channel, so the rail draws its
 * heading and its two doors and no card and no scroller. The Favourites rail
 * does not opt in: "Voir tous les favoris" over an empty list is a cul-de-sac,
 * where the Live doors open on real content.
 *
 * <h2>Selecting a card plays</h2>
 *
 * Every card is one link and the caller decides where it goes; on both screens
 * that use this, it is a `?play=` URL, so choosing a channel starts it (US-020)
 * and the back button closes the player.
 *
 * <h2>What is on, under the name — or nothing (S9-03)</h2>
 *
 * A card may carry the programme on air and when it ends. It is the caller's
 * to load — in **one** request for the whole rail, never one per card — and
 * to format; this component only draws two lines when it is handed them.
 *
 * A channel with nothing on shows the name, **as before**, and nothing else:
 * no "programme unavailable", no reserved space. Somebody whose provider ships
 * no guide would read that sentence under every card, forever. An absent
 * information hides nothing (S7-03), which is the opposite of the rule for a
 * tab — a tab is a door.
 */
export function ChannelRail({
  title,
  channels,
  playHref,
  prominent = false,
  more,
  onAir,
  keepEntriesWhenEmpty = false,
}: {
  title: string;
  channels: Channel[];
  /** Builds the link that plays one channel. */
  playHref: (channelId: string) => string;
  /**
   * By channel id, the programme on air and the sentence saying when it ends,
   * already in the reader's language and zone. A channel absent from the map
   * has nothing on, and its card says nothing about it.
   */
  onAir?: ReadonlyMap<string, OnAirLine>;
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
   *
   * A list since S9-04-07: the Live rail closes on two entries — *Toutes les
   * chaînes* and *Guide TV* — while the Favourites rail still has its one. The
   * order is the reading order, left to right.
   */
  more?: readonly { href: string; label: string }[];
  /**
   * Whether the entries above stay when there is no card. Only the Live rail
   * says yes (S9-04-07): the two doors must exist on a cold account, before any
   * card can. An opt-in rather than a rule, because the same `more` on the
   * Favourites rail is exactly the dead end US-017 keeps off the page.
   */
  keepEntriesWhenEmpty?: boolean;
}) {
  const parts = railParts(channels.length, more?.length ?? 0, keepEntriesWhenEmpty === true);
  if (parts === null) return null;

  // Whether any card of this rail has a programme to show. Decided once for
  // the rail rather than per card, so that the cards keep one width.
  const guided = channels.some((channel) => onAir?.has(channel.id));

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
      {parts.entries && more ? (
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          {heading}
          <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
            {more.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="text-muted-foreground hover:text-foreground text-sm underline underline-offset-4"
              >
                {link.label}
              </a>
            ))}
          </div>
        </div>
      ) : (
        heading
      )}
      {parts.cards ? (
        <ul
          aria-label={title}
          className={cn("flex gap-3 overflow-x-auto pb-2", prominent ? "mt-3" : "mt-2")}
        >
          {channels.map((channel) => {
            const airing = onAir?.get(channel.id);
            return (
              <li key={channel.id} className="shrink-0">
                <a
                  href={playHref(channel.id)}
                  className={cn(
                    "border-border hover:bg-secondary/60 flex h-full items-center gap-2 rounded-xl border px-3 py-2",
                    // Wider once the rail carries programme titles: under a logo
                    // and a gap, ten rems leave six for text, which cuts most
                    // titles at their third word. A rail with no guide keeps the
                    // width it has always had.
                    guided ? "w-52" : "w-40",
                  )}
                >
                  <ChannelLogo channel={channel} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{channel.name}</span>
                    {airing ? (
                      <>
                        <span className="text-muted-foreground block truncate text-xs">
                          {airing.title}
                        </span>
                        <span className="text-muted-foreground/80 block truncate text-[11px] tabular-nums">
                          {airing.until}
                        </span>
                      </>
                    ) : null}
                  </span>
                </a>
              </li>
            );
          })}
        </ul>
      ) : null}
    </section>
  );
}

/**
 * Which parts of a rail are drawn, and whether it is drawn at all.
 *
 * Null is "no rail": no heading, no space, the US-017 rule. A rail with cards
 * always draws them; its entries ride along when there are any. A rail with no
 * card draws only when its caller kept the entries on purpose and there are
 * entries to keep — the Live rail's two doors on a cold account (S9-04-07).
 *
 * Extracted from the render so the rule can be tested without a DOM: the web
 * unit suite runs in Node with no React rendering (vitest.config.mts), and the
 * one thing worth pinning here is precisely which rail survives being empty.
 */
export function railParts(
  channelCount: number,
  entryCount: number,
  keepEntriesWhenEmpty: boolean,
): { cards: boolean; entries: boolean } | null {
  if (channelCount > 0) return { cards: true, entries: entryCount > 0 };
  if (keepEntriesWhenEmpty && entryCount > 0) return { cards: false, entries: true };
  return null;
}

/** The two lines a card draws under a channel's name when something is on. */
export type OnAirLine = {
  title: string;
  /** "Until 21:00", already translated and in the reader's zone. */
  until: string;
};

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
