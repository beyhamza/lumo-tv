package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel

/**
 * What a heart shows, and when (US-12).
 *
 * <h2>Why the state and not the ViewModel</h2>
 *
 * Everything worth getting wrong here is a question the screen asks of
 * [LiveState] while it draws a row: is this channel starred, which groups is it
 * in, and what should it show while a change is still in flight. The calls to the
 * repository around them are three lines and a `launch`; the decisions are these.
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * A heart that takes a round trip to fill is a heart somebody taps twice, and the
 * second tap is a second request against a state that has already changed. So the
 * optimistic entry has to win over the cache — and, just as importantly, has to
 * stop winning once it settles, or it outlives the change it stood in for and a
 * heart stays filled after another device removed the favourite.
 *
 * And the one that is genuinely easy to get wrong: taking a channel out of one
 * group does not unstar it. It may well be in another, and emptying the heart is a
 * lie that the next Room emission corrects a moment later — a flicker on a list
 * somebody is looking at.
 */
class LiveFavoritesTest {

    private val channel = channel("channel-1")
    private val other = channel("channel-2")

    @Test
    fun `an optimistic entry wins over what the cache says, in both directions`() {
        val filling = LiveState(
            favoritedChannelIds = emptySet(),
            pendingFavorites = mapOf(channel.id to true),
        )
        val emptying = LiveState(
            favoritedChannelIds = setOf(channel.id),
            pendingFavorites = mapOf(channel.id to false),
        )

        assertThat(filling.isFavorited(channel.id)).isTrue()
        assertThat(emptying.isFavorited(channel.id)).isFalse()
    }

    @Test
    fun `a settled heart falls back to the cache, which is why the entry is dropped`() {
        val state = LiveState(favoritedChannelIds = setOf(channel.id))

        // No pending entry: the answer is Room's. Keeping the entry after a
        // successful write would leave a heart filled when another device removes
        // the favourite, because nothing would ever look at the cache again.
        assertThat(state.isFavorited(channel.id)).isTrue()
        assertThat(state.isFavorited(other.id)).isFalse()
    }

    @Test
    fun `the groups a channel is in are its own, not the list's`() {
        val state = LiveState(
            favorites = listOf(
                favorite("f1", "documentaire", channel),
                favorite("f2", "cine", channel),
                favorite("f3", "sport", other),
            ),
        )

        assertThat(state.groupsOf(channel.id)).containsExactly("documentaire", "cine")
        assertThat(state.groupsOf(other.id)).containsExactly("sport")
    }

    @Test
    fun `taking a channel out of one group leaves it starred when it is in another`() {
        val state = LiveState(
            favorites = listOf(
                favorite("f1", "documentaire", channel),
                favorite("f2", "cine", channel),
            ),
        )

        // Still in "Ciné", so the heart stays filled while the removal is in
        // flight. This is the case a `count(...) > 1` on the wrong channel gets
        // wrong, and it only shows on an account that uses more than one group.
        assertThat(state.stillFavoritedWithout(channel.id, "documentaire")).isTrue()
    }

    @Test
    fun `taking a channel out of its only group empties the heart`() {
        val state = LiveState(favorites = listOf(favorite("f1", "documentaire", channel)))

        assertThat(state.stillFavoritedWithout(channel.id, "documentaire")).isFalse()
        // And another channel's favourites do not keep it alive.
        assertThat(
            LiveState(favorites = listOf(favorite("f3", "sport", other)))
                .stillFavoritedWithout(channel.id, "documentaire"),
        ).isFalse()
    }

    // ---- helpers -----------------------------------------------------------

    private fun channel(id: String) = Channel(
        id = id,
        sourceId = "source",
        categoryId = null,
        name = "Chaîne $id",
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun favorite(id: String, groupId: String, channel: Channel) = FavoriteChannel(
        favoriteId = id,
        groupId = groupId,
        position = 0,
        channel = channel,
    )
}
