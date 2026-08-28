package tv.lumo.android.core.data.model

/**
 * A group of favourites, as a screen wants one (US-12).
 *
 * A group belongs to the **account**, not to a source. That is what lets one
 * group hold channels from two subscriptions, and it is why the screen that shows
 * these does not sit under a source the way the catalogue does.
 */
data class FavoriteGroup(
    val id: String,
    val name: String,
    val position: Int,
    /**
     * True for the group an add without a group lands in.
     *
     * A screen renders its own translated wording while this is true **and** the
     * name is still the server's own — the server has to call that group
     * something and calls it `Favorites`, in English. Once the user has renamed
     * it, their name wins. The flag survives a rename; a name never could, which
     * is the whole reason the contract carries it.
     */
    val isDefault: Boolean,
)

/**
 * One channel filed in one group, ready to render.
 *
 * Carries the [Channel] rather than a channel id, because a screen that had to
 * resolve identifiers itself would do it once per list and get the offline case
 * wrong. The resolution is a join in SQLite, done by the repository.
 */
data class FavoriteChannel(
    val favoriteId: String,
    val groupId: String,
    val position: Int,
    val channel: Channel,
)
