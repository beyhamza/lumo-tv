/**
 * Which entry of the rail is the current page — **one, or none, never two**
 * (US-017, S8-E03).
 *
 * <h2>Why this is a function with tests and not a `startsWith` in the layout</h2>
 *
 * The rail's entries overlap by construction. The active source's catalogues
 * live at `/app/sources/{id}/vod`, which is *under* `/app/sources`; and the home
 * page lives at `/app`, which is a prefix of everything. A per-entry prefix test
 * lights up "Films" and "Sources" together, and a screen reader then announces
 * two current pages. S8-03 patched the first overlap with a special case; the
 * home entry would have needed a second one.
 *
 * <h2>The rule: the most specific entry wins</h2>
 *
 * Among the entries the path is at or under, the one with the longest `href` is
 * current. "Films" beats "Sources" on the active source's films; "Sources" wins
 * on another source's films, reached from My sources, because no catalogue entry
 * matches there — exactly what the special case did, without being one.
 *
 * An `exact` entry matches its own path only. That is the home page: `/app/devices`
 * is not "under home" in any sense a person would recognise.
 *
 * Matching is on whole segments: `/app/sources-archive` is not under
 * `/app/sources`.
 *
 * @param pathname the requested path, locale included, as the proxy reported it.
 * @param entries hrefs in the same form — already localised.
 * @returns the index of the current entry, or -1.
 */
export function currentEntryIndex(
  pathname: string,
  entries: readonly { href: string; exact?: boolean }[],
): number {
  let best = -1;

  entries.forEach((entry, index) => {
    const matches =
      pathname === entry.href ||
      (!entry.exact && pathname.startsWith(`${entry.href}/`));
    if (!matches) return;
    if (best === -1 || entry.href.length > entries[best].href.length) best = index;
  });

  return best;
}
