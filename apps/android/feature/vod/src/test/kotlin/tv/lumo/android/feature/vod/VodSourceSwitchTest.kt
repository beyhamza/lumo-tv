package tv.lumo.android.feature.vod

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.CatalogueSource
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin

/**
 * The film screen when the active source changes under it (US-018).
 *
 * The search box is what makes this one worth its own test: a query kept across
 * the switch opens the new source on "no results" for a film it never had, which
 * reads as an empty catalogue rather than as a leftover.
 */
class VodSourceSwitchTest {

    private val browsingA = VodState(
        step = VodStep.Browsing,
        sourceId = "source-a",
        categories = listOf(Category("drama", "Catégorie 01", channelCount = null)),
        origin = DataOrigin.Network,
        filter = VodFilter.Resume,
        query = "film 01",
        refreshing = true,
        refreshFailed = true,
    )

    @Test
    fun `another source keeps the section and starts from a blank state`() {
        val next = browsingA.browsing(CatalogueSource.Ready("source-b", isPlaylist = false))

        assertThat(next).isEqualTo(VodState(step = VodStep.Browsing, sourceId = "source-b"))
        // Spelled out, because these are the three the story names.
        assertThat(next.filter).isEqualTo(VodFilter.All)
        assertThat(next.query).isEmpty()
        assertThat(next.continueWatching).isEmpty()
    }

    @Test
    fun `the same source changing status costs nobody the word they were typing`() {
        val importing = browsingA.browsing(CatalogueSource.NotReady("source-a"))

        assertThat(importing.step).isEqualTo(VodStep.NotReadyYet)
        assertThat(importing.query).isEqualTo("film 01")
        assertThat(importing.browsing(CatalogueSource.Ready("source-a", isPlaylist = false)))
            .isEqualTo(browsingA)
    }

    @Test
    fun `every answer of the repository has a step`() {
        val steps = listOf(
            CatalogueSource.Loading to VodStep.Loading,
            CatalogueSource.NoSource to VodStep.NoSource,
            CatalogueSource.NeedsChoice to VodStep.NeedsChoice,
            CatalogueSource.NotReady("source-b") to VodStep.NotReadyYet,
            CatalogueSource.Ready("source-b", isPlaylist = false) to VodStep.Browsing,
        )

        steps.forEach { (source, step) ->
            assertThat(browsingA.browsing(source).step).isEqualTo(step)
        }
        assertThat(browsingA.browsing(CatalogueSource.NeedsChoice).sourceId).isNull()
    }
}
