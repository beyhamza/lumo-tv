import { getTranslations } from "next-intl/server";
import { addToWatchlist, removeFromWatchlist } from "@/actions/watchlist";
import type { WatchlistContentType } from "@/lib/api/types";
import type { Membership } from "@/lib/watchlist/membership";
import { INTENTION_KEY_FIELD, newIntentionKey } from "@/lib/watchlist/write";

/**
 * The "My list" controls: the sheet's button, the list's removal and the
 * notice of an unconfirmed change (US-022, S11-04).
 *
 * Server Components rendering plain forms. Each form is **one intention**: its
 * `Idempotency-Key` is minted here, while rendering, and travels in a hidden
 * field (`lib/watchlist/write.ts`). Rendering the form twice is two intentions;
 * posting the same form twice is one.
 */

const BUTTON =
  "focus-visible:ring-ring rounded-lg border px-3 py-1.5 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none";

/**
 * "Add to my list" or "In my list" on a film or series sheet (S11-E06).
 *
 * Pressing "In my list" removes the item — progress is a separate resource and
 * is not touched. When membership could not be read, no button at all: an
 * "Add" offered on a guess could be a second add of something already listed,
 * which is harmless, or hide a removal the user cannot see, which is not.
 */
export async function WatchlistToggle({
  membership,
  contentType,
  contentId,
  returnTo,
  retryHref,
}: {
  membership: Membership;
  contentType: WatchlistContentType;
  contentId: string;
  /** The sheet's own path and query, without the notice. */
  returnTo: string;
  /** The sheet, reloaded: a new read of the list. */
  retryHref: string;
}) {
  const t = await getTranslations("App");

  if (membership.state === "unknown") {
    return (
      <p role="status" className="text-muted-foreground mt-4 text-sm">
        {t("watchlistStateUnknown")}{" "}
        <a href={retryHref} className="underline underline-offset-4">
          {t("watchlistRetry")}
        </a>
      </p>
    );
  }

  if (membership.state === "in") {
    return (
      <form action={removeFromWatchlist} className="mt-4">
        <input type="hidden" name={INTENTION_KEY_FIELD} value={newIntentionKey()} />
        <input type="hidden" name="itemId" value={membership.itemId} />
        <input type="hidden" name="returnTo" value={returnTo} />
        <button
          type="submit"
          aria-label={t("watchlistInListAction")}
          className={`${BUTTON} bg-secondary text-secondary-foreground border-transparent`}
        >
          {t("watchlistInList")}
        </button>
      </form>
    );
  }

  return (
    <form action={addToWatchlist} className="mt-4">
      <input type="hidden" name={INTENTION_KEY_FIELD} value={newIntentionKey()} />
      <input type="hidden" name="contentType" value={contentType} />
      <input type="hidden" name="contentId" value={contentId} />
      <input type="hidden" name="returnTo" value={returnTo} />
      <button type="submit" className={`${BUTTON} border-border`}>
        {t("watchlistAdd")}
      </button>
    </form>
  );
}

/** "Remove from my list", addressed by the item's own id (WL-03). */
export async function RemoveFromWatchlist({
  itemId,
  title,
  returnTo,
}: {
  itemId: string;
  title: string;
  returnTo: string;
}) {
  const t = await getTranslations("App");

  return (
    <form action={removeFromWatchlist}>
      <input type="hidden" name={INTENTION_KEY_FIELD} value={newIntentionKey()} />
      <input type="hidden" name="itemId" value={itemId} />
      <input type="hidden" name="returnTo" value={returnTo} />
      <button
        type="submit"
        aria-label={t("watchlistRemoveOf", { title })}
        className={`${BUTTON} border-border text-muted-foreground hover:text-foreground w-full font-normal`}
      >
        {t("watchlistRemove")}
      </button>
    </form>
  );
}

/**
 * "Change not confirmed" — the answer to a write whose outcome is unknown.
 *
 * Shown on the page the action came back to, which has re-read the list, so
 * what is on screen below it is the current state, and the buttons on it are
 * new intentions. Never "done", never "failed": neither is known.
 */
export async function WatchlistUnconfirmed({ show }: { show: boolean }) {
  if (!show) return null;
  const t = await getTranslations("App");

  return (
    <div role="status" className="border-border mt-4 rounded-xl border px-4 py-3">
      <p className="font-medium">{t("watchlistUnconfirmed")}</p>
      <p className="text-muted-foreground mt-1 text-sm">{t("watchlistUnconfirmedHint")}</p>
    </div>
  );
}
