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
 * **The source line.** It earns its place only when the account has more than one
 * subscription. Under a single source it is the same sentence on every row, which
 * is noise; and offline the names are simply not there, which is a supported state
 * and not a blank.
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
    fun `the source is named only when there is more than one to tell apart`() {
        val favorite = favorite("f1", "documentaire", channel("c1", "Arte", sourceId = "s1"))

        val single = FavoritesState(sourceNames = mapOf("s1" to "Ma playlist"))
        val two = FavoritesState(sourceNames = mapOf("s1" to "Ma playlist", "s2" to "Mon panel"))
        val offline = FavoritesState(sourceNames = emptyMap())

        assertThat(single.sourceLabel(favorite)).isNull()
        assertThat(two.sourceLabel(favorite)).isEqualTo("Ma playlist")
        // Offline the names were never fetched. No line, no blank, no placeholder.
        assertThat(offline.sourceLabel(favorite)).isNull()
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
