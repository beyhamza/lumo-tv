package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.CatalogueSource
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup

/**
 * The channel screen when the active source changes under it (US-018).
 *
 * "Keep the section open, reset the filters that belonged to the old source."
 * Invisible with one source, and wrong in a way that looks like a broken
 * catalogue with two: a category id carried into another source filters its grid
 * onto nothing, with no chip lit to say why.
 */
class LiveSourceSwitchTest {

    private val channel = Channel(
        id = "c1",
        sourceId = "source-a",
        categoryId = "news",
        name = "Chaîne 01",
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private val browsingA = LiveState(
        step = LiveStep.Browsing,
        sourceId = "source-a",
        categories = listOf(Category("news", "Catégorie 01", channelCount = 12)),
        origin = DataOrigin.Network,
        filter = CatalogueFilter.Category("news"),
        refreshFailed = true,
        groups = listOf(FavoriteGroup("g1", "Groupe 01", position = 0, isDefault = true)),
        favorites = listOf(FavoriteChannel("f1", "g1", position = 0, channel = channel)),
        favoritedChannelIds = setOf("c1"),
        recent = listOf(channel),
        pendingFavorites = mapOf("c9" to true),
        sheetChannel = channel,
        favoriteError = LumoError.UnknownCode("SOMETHING"),
    )

    @Test
    fun `another source keeps the section and drops what belonged to the old one`() {
        val next = browsingA.browsing(CatalogueSource.Ready("source-b", isPlaylist = true))

        assertThat(next.step).isEqualTo(LiveStep.Browsing)
        assertThat(next.sourceId).isEqualTo("source-b")

        // The old source's: filter, categories, refresh outcome, its favourites
        // and recent channels, and a sheet open on one of its channels.
        assertThat(next.filter).isEqualTo(CatalogueFilter.All)
        assertThat(next.categories).isEmpty()
        assertThat(next.refreshFailed).isFalse()
        assertThat(next.favorites).isEmpty()
        assertThat(next.recent).isEmpty()
        assertThat(next.sheetChannel).isNull()
    }

    @Test
    fun `what belongs to the account survives the switch`() {
        val next = browsingA.browsing(CatalogueSource.Ready("source-b", isPlaylist = true))

        // Groups are the account's. The hearts are Room's, keyed by channel. A
        // write in flight still has to settle, and its failure still to be said.
        assertThat(next.groups).isEqualTo(browsingA.groups)
        assertThat(next.favoritedChannelIds).isEqualTo(browsingA.favoritedChannelIds)
        assertThat(next.pendingFavorites).isEqualTo(browsingA.pendingFavorites)
        assertThat(next.favoriteError).isEqualTo(browsingA.favoriteError)
    }

    @Test
    fun `the same source changing status costs nobody their category`() {
        // Re-read while on screen — a refresh of the list, a sync that starts.
        // The category somebody picked is still a category of this source.
        val importing = browsingA.browsing(firstImport("source-a"))

        assertThat(importing.step).isEqualTo(LiveStep.Importing)
        assertThat(importing.filter).isEqualTo(CatalogueFilter.Category("news"))

        val back = importing.browsing(CatalogueSource.Ready("source-a", isPlaylist = true))
        assertThat(back).isEqualTo(browsingA)
    }

    @Test
    fun `every answer of the repository has a step, and none of them is a crash`() {
        val steps = listOf(
            CatalogueSource.Loading to LiveStep.Loading,
            CatalogueSource.NoSource to LiveStep.NoSource,
            CatalogueSource.NeedsChoice to LiveStep.NeedsChoice,
            // A first import, and the two sentences it used to share.
            firstImport("source-b") to LiveStep.Importing,
            firstImport("source-b", failed = true) to LiveStep.ImportFailed,
            CatalogueSource.Ready("source-b", isPlaylist = false) to LiveStep.Browsing,
        )

        steps.forEach { (source, step) ->
            assertThat(browsingA.browsing(source).step).isEqualTo(step)
        }
        // Asked to choose, the grid reads no source at all — not the old one.
        assertThat(browsingA.browsing(CatalogueSource.NeedsChoice).sourceId).isNull()
    }

    @Test
    fun `channels this device holds are shown, whatever the source is doing`() {
        // Lot C4. The visibility rule is `CatalogueSource.face`, pinned in
        // core:data; what is held here is that this screen applies it.
        val importing = firstImport("source-b")
        val failed = firstImport("source-b", failed = true)

        assertThat(browsingA.browsing(importing, cachedItems = 0).step).isEqualTo(LiveStep.Importing)
        assertThat(browsingA.browsing(importing, cachedItems = 40).step).isEqualTo(LiveStep.Browsing)
        assertThat(browsingA.browsing(failed, cachedItems = 0).step).isEqualTo(LiveStep.ImportFailed)
        assertThat(browsingA.browsing(failed, cachedItems = 40).step).isEqualTo(LiveStep.Browsing)
    }

    @Test
    fun `the notice belongs to the active source and survives a switch`() {
        // Written by its own collector, which may run before or after this one:
        // either way a switch must not blank what the new source has to say.
        val refreshing = SourceNotice.Refreshing(step = null, hasCatalogue = true)
        val noticed = browsingA.copy(notice = refreshing)

        assertThat(noticed.browsing(CatalogueSource.Ready("source-b", isPlaylist = false)).notice)
            .isEqualTo(refreshing)
        assertThat(noticed.browsing(CatalogueSource.Ready("source-a", isPlaylist = true)).notice)
            .isEqualTo(refreshing)
    }

    private fun firstImport(sourceId: String, failed: Boolean = false) =
        CatalogueSource.FirstImport(sourceId, isPlaylist = false, failed = failed)
}
