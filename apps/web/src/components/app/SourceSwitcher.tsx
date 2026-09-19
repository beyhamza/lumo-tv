import { getTranslations } from "next-intl/server";
import { selectSource } from "@/actions/active-source";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import type { SourceStatus } from "@/lib/api/types";
import type { ActiveSourceView } from "@/lib/sources/active-source-store";
import { cn } from "@/lib/utils";

/**
 * The source this browser is browsing, and the way to change it (US-018,
 * S8-E04).
 *
 * <h2>Five things it can be, and none of them is a guess</h2>
 *
 * - **unavailable** — `GET /sources` did not answer. It says so and offers
 *   nothing: an outage is not a deletion, so this is neither "no source" nor a
 *   question;
 * - **none** — the way to add one;
 * - **one source** — its name, as plain text. A disclosure that opens on a list
 *   of one is a control that does nothing;
 * - **several** — the active one's name, and a list to switch;
 * - **several, none chosen** — the same list, already open, under an explicit
 *   prompt, with **no row marked**. Marking one would be this component picking
 *   a catalogue on the user's behalf, which is the thing the rule forbids.
 *
 * <h2>A `<details>` and one form per row: no script, no client component</h2>
 *
 * The rail has no client boundary (see the layout) and this does not add one.
 * The browser opens and closes a `<details>` by itself, with the keyboard as
 * well as the mouse — `<summary>` is focusable and answers Enter and Space — and
 * every row is a submit button in its own `<form>`, posting to a Server Action.
 * So the switch works with JavaScript disabled, and the choice, which is a
 * cookie, is written in the only kind of place that can write one.
 *
 * Choosing applies at once. There is nothing to confirm: no favourite and no
 * progress is lost by looking at another source.
 *
 * <h2>The active row is announced, not only drawn</h2>
 *
 * `aria-current` carries it; the check mark is decoration and hidden from
 * assistive technology, which would otherwise read "check mark" and leave the
 * listener to work out what was checked.
 */
export async function SourceSwitcher({
  view,
  locale,
  pathname,
}: {
  view: ActiveSourceView;
  locale: Locale;
  /**
   * The page this is rendered on, locale included, as the proxy reported it.
   * It travels in the form so the action can keep the open section — and is
   * validated there, not here: a hidden field is the browser's to rewrite.
   */
  pathname: string;
}) {
  const t = await getTranslations("App");

  if (view.state === "unavailable") {
    return (
      <p role="status" className="text-muted-foreground/80 px-3.5 pb-4 text-xs">
        {t("sourceSwitcherUnavailable")}
      </p>
    );
  }

  if (view.state === "none") {
    return (
      <div className="px-3.5 pb-4">
        <Caption>{t("sourceSwitcherLabel")}</Caption>
        <p className="text-muted-foreground mt-1 text-sm">{t("sourceSwitcherNone")}</p>
        <a
          href={hrefFor(locale, "/app/sources/new")}
          className="text-brand-cyan mt-1.5 inline-block text-[13px] font-medium"
        >
          {t("sourceSwitcherAdd")}
        </a>
      </div>
    );
  }

  const active = view.state === "selected" ? view.source : null;

  if (active && view.sources.length === 1) {
    return (
      <div className="px-3.5 pb-4">
        <Caption>{t("sourceSwitcherLabel")}</Caption>
        <p className="mt-1 truncate text-sm font-medium">{active.label}</p>
      </div>
    );
  }

  return (
    <details
      // Remounted when the active source changes. With JavaScript, React keeps
      // the DOM node across the action's redirect and the menu would stay open
      // over the page the user just asked for; without it the page reloads and
      // this key is irrelevant.
      key={active?.id ?? "needs-choice"}
      // Open when a choice is owed: the question is on screen, not behind a
      // click somebody has no reason to make.
      open={active === null}
      className="group mb-3"
    >
      <summary className="hover:bg-card flex cursor-pointer list-none items-center gap-2 rounded-xl px-3.5 py-2 [&::-webkit-details-marker]:hidden">
        <span className="min-w-0 flex-1">
          <Caption>{t("sourceSwitcherLabel")}</Caption>
          <span
            className={cn(
              "mt-1 block truncate text-sm font-medium",
              active === null && "text-brand-cyan",
            )}
          >
            {active ? active.label : t("sourceSwitcherChoose")}
          </span>
        </span>
        <span
          aria-hidden="true"
          className="text-muted-foreground/80 text-xs transition-transform group-open:rotate-180"
        >
          ▾
        </span>
      </summary>

      <div className="bg-card mt-1.5 rounded-xl p-1.5">
        {active === null ? (
          <p className="text-muted-foreground px-2 pt-1.5 pb-2 text-xs">
            {t("sourceSwitcherChooseHint")}
          </p>
        ) : null}

        <ul aria-label={t("sourceSwitcherListLabel")} className="flex flex-col gap-0.5">
          {view.sources.map((source) => {
            const current = source.id === active?.id;
            return (
              <li key={source.id}>
                <form action={selectSource}>
                  <input type="hidden" name="sourceId" value={source.id} />
                  <input type="hidden" name="from" value={pathname} />
                  <button
                    type="submit"
                    aria-current={current ? "true" : undefined}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm",
                      current
                        ? "bg-secondary text-foreground font-medium"
                        : "text-muted-foreground hover:bg-secondary/60 hover:text-foreground",
                    )}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{source.label}</span>
                      <span
                        className={cn(
                          "block text-[11px] font-normal",
                          source.status === "ERROR"
                            ? "text-destructive"
                            : "text-muted-foreground/80",
                        )}
                      >
                        {t(statusKey(source.status))}
                      </span>
                    </span>
                    {current ? (
                      <span aria-hidden="true" className="text-brand-cyan flex-none">
                        ✓
                      </span>
                    ) : null}
                  </button>
                </form>
              </li>
            );
          })}
        </ul>

        {/* Said where the switch is made, because it is the surprising half of
            the rule: somebody who changes source here and finds the television
            unchanged should have been told, not left to file a bug. */}
        <p className="text-muted-foreground/80 px-2 pt-2 text-[11px]">
          {t("sourceSwitcherDeviceNote")}
        </p>
        <a
          href={hrefFor(locale, "/app/sources")}
          className="text-brand-cyan block px-2 pt-1.5 pb-1.5 text-[13px] font-medium"
        >
          {t("sourceSwitcherManage")}
        </a>
      </div>
    </details>
  );
}

function Caption({ children }: { children: React.ReactNode }) {
  return (
    <span className="text-muted-foreground/80 block text-[11px] font-semibold tracking-[0.1em] uppercase">
      {children}
    </span>
  );
}

type StatusKey =
  | "sourceStatusPending"
  | "sourceStatusSyncing"
  | "sourceStatusReady"
  | "sourceStatusError";

/**
 * The same four words as the pills on My sources, so a source is not "Ready" in
 * one place and something else in the other.
 *
 * One case per value and no `default`, like `stepKey` on the source page: a
 * status added to the contract fails the build here instead of showing up under
 * the wrong word.
 */
function statusKey(status: SourceStatus): StatusKey {
  switch (status) {
    case "PENDING":
      return "sourceStatusPending";
    case "SYNCING":
      return "sourceStatusSyncing";
    case "READY":
      return "sourceStatusReady";
    case "ERROR":
      return "sourceStatusError";
  }
}
