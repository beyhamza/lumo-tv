import {
  createFavoriteGroup,
  deleteFavoriteGroup,
  moveFavoriteGroup,
  renameFavoriteGroup,
} from "@/actions/favorites";
import type { FavoriteGroup } from "@/lib/api/types";

/**
 * The favourite groups: which one is open, and what can be done to them.
 *
 * <h2>Two screens, one bar (S6-09)</h2>
 *
 * Written for the channels page in `S4-09` and moved here the moment a second
 * screen needed it, rather than copied. A group bar that behaved differently
 * depending on where somebody found it would be two features wearing one name —
 * and renaming a group in one place and not the other is the kind of divergence
 * nobody notices until they are looking at two different lists.
 *
 * The one thing that had to change to make it shared is [hrefForGroup]: choosing
 * a group means a different URL on each screen, and that is the caller's to know.
 * Nothing else moved.
 *
 * <h2>Links to filter, forms to change</h2>
 *
 * Choosing a group is a link, because it is a view — it belongs in the URL, it
 * survives a reload, the back button undoes it. Creating, renaming and deleting
 * are `<form>`s pointed at Server Actions, because they change something and
 * because the access token is not in the browser (`AGENTS.md` §4). Neither needs
 * JavaScript.
 *
 * <h2>Deleting says what it will do, with the number</h2>
 *
 * The server moves a deleted group's favourites into the default group rather
 * than removing them. "Are you sure?" would tell this person nothing they do not
 * already know; the count and the destination let them predict the state they
 * will be in. Same wording as the phone, same number.
 *
 * <h2>The default group has no delete control</h2>
 *
 * The server refuses it — it is where the others empty into — and a control whose
 * only possible answer is an error teaches somebody that the application is
 * broken.
 *
 * <h2>Nothing here when there is nothing to organise</h2>
 *
 * No groups means no bar: an account that has never starred anything does not
 * need a filter over an empty list. The creation form appears with the first
 * group, which is created by the first star.
 */
export function FavoriteGroups({
  groups,
  activeId,
  hrefForGroup,
  returnTo,
  countInGroup,
  defaultGroupName,
  labels,
  deleteWarning,
  move,
}: {
  groups: FavoriteGroup[];
  activeId?: string;
  /**
   * Where a chip leads. The caller's, because the two screens that show this bar
   * filter two different lists — and it is the only thing that differs between
   * them.
   */
  hrefForGroup: (groupId?: string) => string;
  /** Where the Server Actions send the browser back to, once they have written. */
  returnTo: string;
  countInGroup: (groupId: string) => number;
  defaultGroupName: string;
  labels: {
    all: string;
    create: string;
    name: string;
    rename: string;
    remove: string;
    confirmRemove: string;
  };
  deleteWarning: (count: number) => string;
  /**
   * Labels for "Move earlier" / "Move later" on the open group, each given the
   * group's name. Omitted, the bar does not offer to reorder groups — the
   * channels page files, it does not organise.
   */
  move?: GroupMoveLabels;
}) {
  if (groups.length === 0) return null;

  const active = groups.find((group) => group.id === activeId);

  return (
    <section className="mt-6">
      <nav aria-label={labels.all} className="flex flex-wrap items-center gap-2">
        <GroupLink href={hrefForGroup()} active={activeId === undefined}>
          {labels.all}
        </GroupLink>
        {groups.map((group) => (
          <GroupLink
            key={group.id}
            href={hrefForGroup(group.id)}
            active={group.id === activeId}
          >
            {groupLabel(group, defaultGroupName)}
          </GroupLink>
        ))}
      </nav>

      <div className="mt-3 flex flex-wrap items-end gap-4">
        {/* Renaming and deleting act on the group that is open, so there is one
            of each rather than a control per chip — a bar that carried three
            buttons per group would be unreadable at the width a phone gives it. */}
        {active ? (
          <>
            <form action={renameFavoriteGroup} className="flex items-end gap-2">
              <input type="hidden" name="groupId" value={active.id} />
              <input type="hidden" name="returnTo" value={returnTo} />
              <div className="space-y-1.5">
                <label
                  htmlFor="group-name"
                  className="text-muted-foreground text-xs font-medium"
                >
                  {labels.name}
                </label>
                <input
                  id="group-name"
                  name="name"
                  type="text"
                  required
                  maxLength={100}
                  defaultValue={groupLabel(active, defaultGroupName)}
                  className="border-input bg-background h-9 rounded-lg border px-3 text-sm"
                />
              </div>
              <button
                type="submit"
                className="bg-secondary text-secondary-foreground h-9 rounded-lg px-3 text-sm font-medium"
              >
                {labels.rename}
              </button>
            </form>

            {move ? (
              <GroupMoves
                group={active}
                index={groups.findIndex((group) => group.id === active.id)}
                count={groups.length}
                returnTo={returnTo}
                labels={move}
                name={groupLabel(active, defaultGroupName)}
              />
            ) : null}

            {active.is_default ? null : (
              // Two steps, and the second is where the consequence is read
              // (US-022: "the confirmation explains it"). A native disclosure,
              // so that it needs no JavaScript.
              <details>
                <summary className="border-destructive text-destructive inline-flex h-9 cursor-pointer list-none items-center rounded-lg border px-3 text-sm font-medium [&::-webkit-details-marker]:hidden">
                  {labels.remove}
                </summary>
                <form action={deleteFavoriteGroup} className="mt-2 space-y-1.5">
                  <input type="hidden" name="groupId" value={active.id} />
                  <input type="hidden" name="returnTo" value={returnTo} />
                  <p className="text-muted-foreground max-w-md text-xs">
                    {deleteWarning(countInGroup(active.id))}
                  </p>
                  <button
                    type="submit"
                    className="bg-destructive h-9 rounded-lg px-3 text-sm font-medium text-white"
                  >
                    {labels.confirmRemove}
                  </button>
                </form>
              </details>
            )}
          </>
        ) : null}

        <form action={createFavoriteGroup} className="flex items-end gap-2">
          <input type="hidden" name="returnTo" value={returnTo} />
          <div className="space-y-1.5">
            <label
              htmlFor="new-group-name"
              className="text-muted-foreground text-xs font-medium"
            >
              {labels.create}
            </label>
            <input
              id="new-group-name"
              name="name"
              type="text"
              required
              maxLength={100}
              className="border-input bg-background h-9 rounded-lg border px-3 text-sm"
            />
          </div>
          <button
            type="submit"
            className="bg-secondary text-secondary-foreground h-9 rounded-lg px-3 text-sm font-medium"
          >
            {labels.create}
          </button>
        </form>
      </div>
    </section>
  );
}

export type GroupMoveLabels = {
  earlier: string;
  later: string;
  earlierOf: (group: string) => string;
  laterOf: (group: string) => string;
};

/**
 * Moving the open group one place earlier or later in the account's list
 * (US-022, S11-03). Two forms, so that it works from the keyboard and without
 * JavaScript; the end the group is already at is drawn disabled rather than
 * hidden, so the pair does not jump around as the group moves.
 */
function GroupMoves({
  group,
  index,
  count,
  returnTo,
  labels,
  name,
}: {
  group: FavoriteGroup;
  index: number;
  count: number;
  returnTo: string;
  labels: GroupMoveLabels;
  name: string;
}) {
  const steps = [
    { direction: "up", label: labels.earlier, aria: labels.earlierOf(name), disabled: index <= 0 },
    {
      direction: "down",
      label: labels.later,
      aria: labels.laterOf(name),
      disabled: index < 0 || index >= count - 1,
    },
  ] as const;

  return (
    <div className="flex items-end gap-2">
      {steps.map((step) => (
        <form key={step.direction} action={moveFavoriteGroup}>
          <input type="hidden" name="groupId" value={group.id} />
          <input type="hidden" name="direction" value={step.direction} />
          <input type="hidden" name="returnTo" value={returnTo} />
          <button
            type="submit"
            disabled={step.disabled}
            aria-label={step.aria}
            className="border-border h-9 rounded-lg border px-3 text-sm disabled:opacity-40"
          >
            {step.label}
          </button>
        </form>
      ))}
    </div>
  );
}

function GroupLink({
  href,
  active,
  children,
}: {
  href: string;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <a
      href={href}
      aria-current={active ? "true" : undefined}
      className={`rounded-lg border px-3 py-1.5 text-sm ${
        active
          ? "border-primary bg-primary/10 font-medium"
          : "border-border text-muted-foreground hover:bg-secondary/60"
      }`}
    >
      {children}
    </a>
  );
}

/**
 * What to call a group on screen.
 *
 * The server names the group it creates on the first add, and names it
 * `Favorites`, in English. `is_default` is what lets a client translate it; the
 * second half of the condition is what stops the translation overriding the user
 * once they have renamed it — here, or on their phone.
 */
export function groupLabel(group: FavoriteGroup, translated: string): string {
  return group.is_default && group.name === SERVER_DEFAULT_GROUP_NAME
    ? translated
    : group.name;
}

/** Where a deleted group's channels go, named as the user sees it. */
export function defaultGroupLabel(groups: FavoriteGroup[], translated: string): string {
  const fallback = groups.find((group) => group.is_default);
  return fallback ? groupLabel(fallback, translated) : translated;
}

/** The name the server gives the default group, verbatim. */
const SERVER_DEFAULT_GROUP_NAME = "Favorites";
