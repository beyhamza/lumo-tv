package tv.lumo.android.feature.home

/**
 * Where the television's home screen puts the focus **by itself**, or null to
 * leave the remote alone.
 *
 * A pure function, because this is the part of a television screen that has a
 * wrong answer and that no screenshot shows: a screen that never takes the focus
 * leaves `BACK` as the only key that does anything, and one that takes it a second
 * too late snatches it out of somebody's hand.
 *
 * <h2>The rules, in the order they are tried</h2>
 *
 * 1. **Never, once the viewer has moved.** [placedKey] is the card this screen last
 *    focused on its own; if the focus is anywhere else — another card, or outside
 *    the content altogether, which is what a null [focusedKey] means — the remote
 *    is being used and is left alone, whatever arrives afterwards.
 * 2. **The card that launched a player**, when there is one and it is still on
 *    screen. `BACK` from a player returns to where the viewer was, not to the head
 *    of the page (US-10's rule, here as on the grids).
 * 3. **The first card of the first rail.** That is the first "Continue" card when
 *    there is one — the rails are in the validated order — and otherwise the first
 *    thing focusable in whatever rail comes first (design S8-E01: *"première
 *    reprise à l'accueil"*).
 *
 * <h2>Why rule 1 lets the focus move once more</h2>
 *
 * The rails do not arrive together: favourites come out of Room in a frame, the
 * progress list is a request. On a cold start the focus first lands on a
 * favourite, and "Continue" appears above it a moment later. While the viewer has
 * not touched the remote — the focus is still exactly where this screen put it —
 * following the new first card is a correction, not a theft. One key press ends
 * that.
 *
 * @param keys every focusable card, in reading order: rail by rail, left to right.
 * @param returnKey the card that last opened a player or a detail screen.
 * @param placedKey the card this screen last focused on its own, or null if it
 * has not placed the focus since it came into view.
 * @param focusedKey the card that holds the focus now, or null when none does.
 */
internal fun homeFocusTarget(
    keys: List<String>,
    returnKey: String?,
    placedKey: String?,
    focusedKey: String?,
): String? {
    if (keys.isEmpty()) return null

    val viewerMoved = placedKey != null && focusedKey != placedKey
    if (viewerMoved) return null

    return returnKey?.takeIf { it in keys } ?: keys.first()
}

/** The keys of every card of [sections], in reading order. See [homeFocusTarget]. */
internal fun focusKeysOf(sections: List<HomeSection>): List<String> = sections.flatMap { section ->
    when (section) {
        is HomeSection.Continue -> section.items.map { it.key }
        is HomeSection.Favorites -> section.channels.map { favoriteKey(it.channel.id) }
        is HomeSection.Live -> section.channels.map { recentKey(it.id) }
    }
}

/**
 * A channel can sit in both rails at once — a favourite watched this morning — so
 * a bare channel id would name two cards, and the focus would come back to the
 * wrong one of them.
 */
internal fun favoriteKey(channelId: String): String = "favorite:$channelId"

internal fun recentKey(channelId: String): String = "recent:$channelId"
