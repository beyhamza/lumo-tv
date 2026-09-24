package tv.lumo.android.feature.live

/**
 * Where the D-pad lands on the television's channel grid (S9-04-03).
 *
 * <h2>A skeleton is never a target</h2>
 *
 * Paging draws a window it has not loaded yet at the full size of a card, so the
 * grid keeps its shape while the next page arrives. That placeholder has no
 * channel behind it: focusing it would stop the remote on a dead end that
 * appears and disappears as the grid scrolls, and the grid's own focus map says
 * so in as many words (docs/design/tv-focus-map.md, "Une carte de remplacement
 * n'est pas focalisable").
 *
 * The card already refuses the centre key on a null channel. This rule is the
 * other half: the requester that puts the focus there in the first place. It
 * skips what is not loaded, and it never invents an index.
 *
 * <h2>What it must not overwrite</h2>
 *
 * [current] is the place the grid is already on: the first card on arrival, the
 * channel that was being watched on the way back from the player (US-10), or
 * wherever the viewer was before a page loaded in. A loaded card there is left
 * exactly where it is — moving it would yank the focus out from under somebody
 * who is reading the grid — and only a placeholder is replaced by the first
 * card that exists.
 *
 * @param count the number of items Paging reports for the grid, placeholders
 *   included.
 * @param isLoaded whether the item at an index has a channel behind it.
 * @param current the index the grid currently targets.
 */
internal fun arrivalFocusIndex(
    count: Int,
    isLoaded: (Int) -> Boolean,
    current: Int,
): Int {
    if (count <= 0) return current
    if (current in 0 until count && isLoaded(current)) return current
    return (0 until count).firstOrNull(isLoaded) ?: current
}
