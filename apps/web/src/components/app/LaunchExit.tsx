/**
 * The way out of a sheet opened from a Continue card that could not even reach
 * its player (S12-05): the film or the series did not load, or the episode the
 * card named is not in the series any more.
 *
 * Links, not buttons: the sheet is a Server Component, and retrying is reading
 * the same URL again (the rule of the home rails, S11-05). The progress line is
 * said because it is the worry — nothing was saved, the card is still there
 * (CW-25).
 */
export function LaunchExit({
  retry,
  back,
  kept,
}: {
  /** Absent when retrying cannot help: the item is gone. */
  retry?: { href: string; label: string };
  back: { href: string; label: string };
  kept: string;
}) {
  return (
    <>
      <p className="text-muted-foreground mt-2 text-sm">{kept}</p>
      <div className="mt-3 flex flex-wrap gap-3">
        {retry ? (
          <a
            href={retry.href}
            className="bg-primary text-primary-foreground focus-visible:ring-ring inline-flex h-10 items-center rounded-lg px-5 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
          >
            {retry.label}
          </a>
        ) : null}
        <a
          href={back.href}
          className="bg-secondary text-secondary-foreground focus-visible:ring-ring inline-flex h-10 items-center rounded-lg px-5 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
        >
          {back.label}
        </a>
      </div>
    </>
  );
}
