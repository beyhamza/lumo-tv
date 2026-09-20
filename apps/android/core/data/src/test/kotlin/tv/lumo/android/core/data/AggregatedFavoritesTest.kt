package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup

/**
 * Every favourite of the active source, each channel once (US-020).
 *
 * <h2>The rule is one sentence, and every clause of it can be got wrong quietly</h2>
 *
 * *Walk the groups in their order, then the channels in their order inside each
 * group; the first occurrence of a channel decides its place.* An implementation
 * that sorted by favourite position alone would interleave the groups. One that
 * deduplicated by name would merge two channels the user filed separately. One
 * that deduplicated by favourite id would deduplicate nothing at all — a channel
 * in two groups is two favourites. Each of those looks right on an account with
 * one group and no repeated channel, which is every account used for a demo.
 *
 * All names are fictional (AGENTS.md §1).
 */
class AggregatedFavoritesTest {

    private val first = group("group-first", position = 0)
    private val second = group("group-second", position = 1)

    @Test
    fun `groups are walked in their order, then channels in theirs`() {
        val result = aggregatedFavorites(
            groups = listOf(first, second),
            favorites = listOf(
                favorite("f1", second, "channel-c", position = 0),
                favorite("f2", first, "channel-b", position = 1),
                favorite("f3", first, "channel-a", position = 0),
            ),
            sourceId = SOURCE,
        )

        assertThat(result.map { it.channel.id })
            .containsExactly("channel-a", "channel-b", "channel-c").inOrder()
    }

    @Test
    fun `a channel in two groups appears once, where it is met first`() {
        val result = aggregatedFavorites(
            groups = listOf(first, second),
            favorites = listOf(
                favorite("f1", first, "channel-a", position = 0),
                favorite("f2", first, "channel-shared", position = 1),
                favorite("f3", second, "channel-shared", position = 0),
                favorite("f4", second, "channel-z", position = 1),
            ),
            sourceId = SOURCE,
        )

        assertThat(result.map { it.channel.id })
            .containsExactly("channel-a", "channel-shared", "channel-z").inOrder()
        // The membership that is drawn is the first one met; the other is still
        // there in the account, and this function removed nothing.
        assertThat(result.single { it.channel.id == "channel-shared" }.groupId).isEqualTo(first.id)
    }

    @Test
    fun `moving a group moves the channels it is the first to hold`() {
        val favorites = listOf(
            favorite("f1", first, "channel-a", position = 0),
            favorite("f2", second, "channel-shared", position = 0),
            favorite("f3", first, "channel-shared", position = 1),
        )

        val swapped = aggregatedFavorites(
            groups = listOf(first.copy(position = 1), second.copy(position = 0)),
            favorites = favorites,
            sourceId = SOURCE,
        )

        // No separate ranking is kept for the home screen: reordering the groups
        // in the library is what reorders the rail.
        assertThat(swapped.map { it.channel.id }).containsExactly("channel-shared", "channel-a").inOrder()
        assertThat(swapped.first().groupId).isEqualTo(second.id)
    }

    @Test
    fun `the order of the lists handed in does not matter, only the positions`() {
        val result = aggregatedFavorites(
            // Handed over backwards on purpose.
            groups = listOf(second, first),
            favorites = listOf(
                favorite("f2", second, "channel-b", position = 0),
                favorite("f1", first, "channel-a", position = 0),
            ),
            sourceId = SOURCE,
        )

        assertThat(result.map { it.channel.id }).containsExactly("channel-a", "channel-b").inOrder()
    }

    @Test
    fun `two channels with the same name are two channels`() {
        val result = aggregatedFavorites(
            groups = listOf(first),
            favorites = listOf(
                favorite("f1", first, "channel-1", position = 0, name = "Test channel"),
                favorite("f2", first, "channel-2", position = 1, name = "Test channel"),
            ),
            sourceId = SOURCE,
        )

        assertThat(result).hasSize(2)
    }

    @Test
    fun `only the active source is shown`() {
        val result = aggregatedFavorites(
            groups = listOf(first, second),
            favorites = listOf(
                favorite("f1", first, "channel-other", position = 0, source = OTHER),
                favorite("f2", second, "channel-mine", position = 0),
            ),
            sourceId = SOURCE,
        )

        assertThat(result.map { it.channel.id }).containsExactly("channel-mine")
    }

    @Test
    fun `a channel removed from its first group takes the place of its next membership`() {
        // The partial-removal case of 19 September: the channel stays in the
        // aggregation while one membership is left, and that one decides its place.
        val result = aggregatedFavorites(
            groups = listOf(first, second),
            favorites = listOf(
                favorite("f1", first, "channel-a", position = 0),
                favorite("f3", second, "channel-shared", position = 0),
            ),
            sourceId = SOURCE,
        )

        assertThat(result.map { it.channel.id }).containsExactly("channel-a", "channel-shared").inOrder()
    }

    @Test
    fun `no active source shows nothing rather than everything`() {
        val result = aggregatedFavorites(
            groups = listOf(first),
            favorites = listOf(favorite("f1", first, "channel-a", position = 0)),
            sourceId = null,
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `a favourite whose group has not reached the cache yet is kept, last`() {
        val result = aggregatedFavorites(
            groups = listOf(first),
            favorites = listOf(
                favorite("f1", group("group-late", position = 5), "channel-late", position = 0),
                favorite("f2", first, "channel-a", position = 0),
            ),
            sourceId = SOURCE,
        )

        assertThat(result.map { it.channel.id }).containsExactly("channel-a", "channel-late").inOrder()
    }

    // ---- fixtures ------------------------------------------------------------

    private fun group(id: String, position: Int) =
        FavoriteGroup(id = id, name = id, position = position, isDefault = false)

    private fun favorite(
        id: String,
        group: FavoriteGroup,
        channelId: String,
        position: Int,
        source: String = SOURCE,
        name: String = "Channel $channelId",
    ) = FavoriteChannel(
        favoriteId = id,
        groupId = group.id,
        position = position,
        channel = Channel(
            id = channelId,
            sourceId = source,
            categoryId = null,
            name = name,
            logoUrl = null,
            number = null,
            quality = null,
            isAdult = false,
        ),
    )

    private companion object {
        const val SOURCE = "source-a"
        const val OTHER = "source-b"
    }
}
