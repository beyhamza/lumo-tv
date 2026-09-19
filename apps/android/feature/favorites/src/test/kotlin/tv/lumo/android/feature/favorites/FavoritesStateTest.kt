package tv.lumo.android.feature.favorites

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup

/**
 * What the favourites screen shows, and in what order (US-12).
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * **The order.** Favourites are sorted by the user's own `position`, which is the
 * thing S4-05 lets them change. Sorting by channel name instead would look
 * perfectly reasonable on any screenshot and would make that whole task
 * invisible — the reorder would work, and nothing would move.
 *
 * **The two empty states.** An account with nothing starred and a group that
 * happens to be empty are different situations wanting different words: one names
 * the gesture that starts, the other says this shelf is empty. Collapsing them
 * gives somebody with forty favourites a lesson on how to press a heart.
 *
 * **The source.** The list is the account's and the screen shows the active
 * source's share of it (US-018). With one source the two are the same list, so a
 * filter that did nothing — or one that deleted instead of hiding — would pass
 * every manual test and only show on the day somebody adds a second subscription.
 */
class FavoritesStateTest {

    private val documentaire = group("documentaire", "Documentaire")
    private val cine = group("cine", "Ciné")

    @Test
    fun `the open tab lists its own favourites, in the user's order`() {
        val state = FavoritesState(
            loading = false,
            groups = listOf(documentaire, cine),
            selectedGroupId = "documentaire",
            activeSourceId = "source",
            favorites = listOf(
                favorite("f1", "documentaire", channel("c1", "Zèbre"), position = 2),
                favorite("f2", "documentaire", channel("c2", "Arte"), position = 0),
                favorite("f3", "cine", channel("c3", "Ciné+"), position = 0),
            ),
        )

        // By position, not by name: sorting alphabetically here would look right
        // and would make the reorder of S4-05 do nothing visible.
        assertThat(state.visible.map { it.channel.name }).containsExactly("Arte", "Zèbre").inOrder()
    }

    @Test
    fun `an account with nothing starred is not the same as an empty group`() {
        val nothing = FavoritesState(loading = false, favorites = emptyList())
        val emptyTab = FavoritesState(
            loading = false,
            groups = listOf(documentaire, cine),
            selectedGroupId = "cine",
            activeSourceId = "source",
            favorites = listOf(favorite("f1", "documentaire", channel("c1", "Arte"))),
        )

        assertThat(nothing.nothingAtAll).isTrue()
        // Forty favourites and an empty shelf: this person does not need the
        // gesture explained to them again.
        assertThat(emptyTab.nothingAtAll).isFalse()
        assertThat(emptyTab.visible).isEmpty()
    }

    @Test
    fun `loading is not emptiness`() {
        // Before the first Room emission there is nothing to show and nothing to
        // conclude. Saying "no favourites yet" here would put an empty state in
        // front of somebody who has forty of them.
        assertThat(FavoritesState(loading = true, favorites = emptyList()).nothingAtAll).isFalse()
    }

    @Test
    fun `only the active source's favourites are shown, and switching back finds the others`() {
        val state = FavoritesState(
            loading = false,
            groups = listOf(documentaire),
            selectedGroupId = "documentaire",
            activeSourceId = "source-a",
            favorites = listOf(
                favorite("f1", "documentaire", channel("c1", "Chaîne 01", sourceId = "source-a")),
                favorite("f2", "documentaire", channel("c2", "Chaîne 02", sourceId = "source-b")),
            ),
        )

        assertThat(state.visible.map { it.channel.name }).containsExactly("Chaîne 01")

        // Nothing was removed to get there: the same list, read for the other
        // source, shows what was hidden a moment ago.
        val switched = state.copy(activeSourceId = "source-b")
        assertThat(switched.visible.map { it.channel.name }).containsExactly("Chaîne 02")
        assertThat(switched.favorites).hasSize(2)
    }

    @Test
    fun `favourites held only in another source read as none here`() {
        val state = FavoritesState(
            loading = false,
            activeSourceId = "source-a",
            favorites = listOf(
                favorite("f1", "documentaire", channel("c1", "Chaîne 01", sourceId = "source-b")),
            ),
        )

        // The gesture is what helps somebody who has starred nothing in the source
        // they are looking at, whatever they did in another one.
        assertThat(state.nothingAtAll).isTrue()
    }

    @Test
    fun `no active source shows nothing rather than everything`() {
        // Several sources and no choice yet: there is no catalogue on screen for
        // a favourite to belong to.
        val state = FavoritesState(
            loading = false,
            selectedGroupId = "documentaire",
            activeSourceId = null,
            favorites = listOf(favorite("f1", "documentaire", channel("c1", "Chaîne 01"))),
        )

        assertThat(state.visible).isEmpty()
    }

    @Test
    fun `the deletion count spans every source, because the deletion does`() {
        val state = FavoritesState(
            loading = false,
            groups = listOf(documentaire),
            activeSourceId = "source-a",
            favorites = listOf(
                favorite("f1", "documentaire", channel("c1", "Chaîne 01", sourceId = "source-a")),
                favorite("f2", "documentaire", channel("c2", "Chaîne 02", sourceId = "source-b")),
            ),
        )

        // The server moves both. A count of one would be a promise the deletion
        // does not keep.
        assertThat(state.countIn(documentaire)).isEqualTo(2)
    }

    // ---- organising (S4-05) ------------------------------------------------

    @Test
    fun `a move is offered only where there is somewhere to go`() {
        val sport = group("sport", "Sport")
        val state = FavoritesState(
            loading = false,
            groups = listOf(documentaire, cine, sport),
        )

        // The ends offer one direction, not two. An entry that does nothing is an
        // entry somebody presses once to find out that it does nothing.
        assertThat(state.canMoveGroup(documentaire, -1)).isFalse()
        assertThat(state.canMoveGroup(documentaire, 1)).isTrue()
        assertThat(state.canMoveGroup(sport, 1)).isFalse()
        assertThat(state.canMoveGroup(cine, -1)).isTrue()
    }

    @Test
    fun `moving a favourite is bounded by its own tab, not by the whole list`() {
        val only = favorite("f1", "cine", channel("c1", "Ciné+"))
        val state = FavoritesState(
            loading = false,
            groups = listOf(documentaire, cine),
            selectedGroupId = "cine",
            activeSourceId = "source",
            favorites = listOf(
                only,
                // Three more favourites, in the other group. They must not make
                // this one look movable: the visible list is one tab.
                favorite("f2", "documentaire", channel("c2", "Arte"), position = 0),
                favorite("f3", "documentaire", channel("c3", "RMC"), position = 1),
            ),
        )

        assertThat(state.canMoveFavorite(only, -1)).isFalse()
        assertThat(state.canMoveFavorite(only, 1)).isFalse()
    }

    @Test
    fun `the deletion count is the group's own, and the targets exclude it`() {
        val favorite = favorite("f1", "documentaire", channel("c1", "Arte"))
        val state = FavoritesState(
            loading = false,
            groups = listOf(documentaire, cine),
            favorites = listOf(
                favorite,
                favorite("f2", "documentaire", channel("c2", "RMC")),
                favorite("f3", "cine", channel("c3", "Ciné+")),
            ),
        )

        // The number is the whole point of the confirmation: it lets somebody
        // predict the state they will be in.
        assertThat(state.countIn(documentaire)).isEqualTo(2)
        assertThat(state.countIn(cine)).isEqualTo(1)
        // A favourite cannot be moved into the group it is already in — the entry
        // is absent rather than disabled.
        assertThat(state.moveTargets(favorite).map { it.id }).containsExactly("cine")
    }

    // ---- helpers -----------------------------------------------------------

    private fun group(id: String, name: String) = FavoriteGroup(
        id = id,
        name = name,
        position = 0,
        isDefault = false,
    )

    private fun channel(id: String, name: String, sourceId: String = "source") = Channel(
        id = id,
        sourceId = sourceId,
        categoryId = null,
        name = name,
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun favorite(
        id: String,
        groupId: String,
        channel: Channel,
        position: Int = 0,
    ) = FavoriteChannel(
        favoriteId = id,
        groupId = groupId,
        position = position,
        channel = channel,
    )
}
