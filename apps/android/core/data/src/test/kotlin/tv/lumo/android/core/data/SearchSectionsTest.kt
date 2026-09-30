package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.data.model.SearchPage
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.repository.searchSections

/**
 * The composition rule of a unified search (S10-01).
 *
 * "A section that fails does not fail the search" is Q9's sentence, and it is a
 * property of this assembly rather than of any repository. Pinned here without a
 * session, a server or a dispatcher: the three repositories are lambdas, and a
 * test decides what each of them answers.
 */
class SearchSectionsTest {

    private val failure = LumoResult.Failure(LumoError.Offline(IOException("no network")))
    private val channels = LumoResult.Success(SearchPage(emptyList<Channel>(), totalElements = 0L))
    private val films = LumoResult.Success(SearchPage(emptyList<VodItem>(), totalElements = 0L))
    private val series = LumoResult.Success(SearchPage(emptyList<Series>(), totalElements = 0L))

    @Test
    fun `one section failing leaves the other two intact`() = runTest {
        val result = searchSections(
            filter = SearchFilter.All,
            channels = { failure },
            films = { films },
            series = { series },
        )

        // The failure is a failure, never an empty success — Q9, "une erreur
        // n'est jamais un ensemble vide".
        assertThat(result.channels).isInstanceOf(LumoResult.Failure::class.java)
        assertThat(result.films).isInstanceOf(LumoResult.Success::class.java)
        assertThat(result.series).isInstanceOf(LumoResult.Success::class.java)
    }

    @Test
    fun `the grouped view asks for all three types`() = runTest {
        val asked = mutableListOf<String>()

        searchSections(
            filter = SearchFilter.All,
            channels = { asked += "channels"; channels },
            films = { asked += "films"; films },
            series = { asked += "series"; series },
        )

        assertThat(asked).containsExactly("channels", "films", "series")
    }

    @Test
    fun `a single type asks for itself and leaves the others unrequested`() = runTest {
        val result = searchSections(
            filter = SearchFilter.Films,
            channels = { error("a films search must not read channels") },
            films = { films },
            series = { error("a films search must not read series") },
        )

        // Null is "not asked for", distinct from a failure: a screen shows the one
        // section it has and no error for the ones this filter does not carry.
        assertThat(result.channels).isNull()
        assertThat(result.films).isInstanceOf(LumoResult.Success::class.java)
        assertThat(result.series).isNull()
    }

    @Test
    fun `each series section asks for itself`() = runTest {
        val onlySeries = searchSections(
            filter = SearchFilter.Series,
            channels = { error("never") },
            films = { error("never") },
            series = { series },
        )
        assertThat(onlySeries.series).isInstanceOf(LumoResult.Success::class.java)

        val onlyChannels = searchSections(
            filter = SearchFilter.Channels,
            channels = { channels },
            films = { error("never") },
            series = { error("never") },
        )
        assertThat(onlyChannels.channels).isInstanceOf(LumoResult.Success::class.java)
    }
}
