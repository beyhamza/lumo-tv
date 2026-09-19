package tv.lumo.android.feature.series

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.CatalogueSource
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.DataOrigin

/**
 * The series screen when the active source changes under it (US-018).
 *
 * The film screen's rule, plus the one fact that is this screen's own: whether
 * the source is a playlist. It picks the sentence of an empty grid, and carried
 * over from the previous source it would tell the owner of an Xtream panel that
 * their format cannot carry series — the worse of the two mistakes.
 */
class SeriesSourceSwitchTest {

    private val browsingPlaylist = SeriesState(
        step = SeriesStep.Browsing,
        sourceId = "source-a",
        categories = listOf(Category("drama", "Catégorie 01", channelCount = null)),
        origin = DataOrigin.Network,
        filter = SeriesFilter.Category("drama"),
        query = "série 01",
        refreshFailed = true,
        isPlaylist = true,
    )

    @Test
    fun `another source starts from a blank state, and says what kind it is`() {
        val next = browsingPlaylist.browsing(CatalogueSource.Ready("source-b", isPlaylist = false))

        assertThat(next).isEqualTo(
            SeriesState(step = SeriesStep.Browsing, sourceId = "source-b", isPlaylist = false),
        )
    }

    @Test
    fun `the same source changing status keeps the filters`() {
        val importing = browsingPlaylist.browsing(CatalogueSource.NotReady("source-a"))

        assertThat(importing.step).isEqualTo(SeriesStep.NotReadyYet)
        assertThat(importing.filter).isEqualTo(SeriesFilter.Category("drama"))
        assertThat(importing.query).isEqualTo("série 01")
    }

    @Test
    fun `every answer of the repository has a step`() {
        val steps = listOf(
            CatalogueSource.Loading to SeriesStep.Loading,
            CatalogueSource.NoSource to SeriesStep.NoSource,
            CatalogueSource.NeedsChoice to SeriesStep.NeedsChoice,
            CatalogueSource.NotReady("source-b") to SeriesStep.NotReadyYet,
            CatalogueSource.Ready("source-b", isPlaylist = true) to SeriesStep.Browsing,
        )

        steps.forEach { (source, step) ->
            assertThat(browsingPlaylist.browsing(source).step).isEqualTo(step)
        }
        assertThat(browsingPlaylist.browsing(CatalogueSource.NeedsChoice).sourceId).isNull()
    }
}
