/**
 * The line a home rail draws when part of it could not be read (S11-05).
 *
 * Under the rail's heading, beside the cards that did load — never instead of
 * the page, and never as an empty rail that would read as "nothing here". The
 * retry is a plain link to the same page: a fresh server render re-reads
 * everything, and it works without JavaScript.
 */
export type RailFailure = {
  /** What could not be loaded, already in the reader's language. */
  text: string;
  retryHref: string;
  retryLabel: string;
};

export function RailFailureLine({ failure }: { failure: RailFailure }) {
  return (
    <p role="status" className="text-muted-foreground mt-2 text-sm">
      {failure.text}{" "}
      <a href={failure.retryHref} className="text-foreground underline underline-offset-4">
        {failure.retryLabel}
      </a>
    </p>
  );
}
