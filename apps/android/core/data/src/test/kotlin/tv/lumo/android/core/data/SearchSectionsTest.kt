package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.data.model.CataloguePresence
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.data.model.SearchPage
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.repository.cataloguePresenceOf
import tv.lumo.android.core.data.repository.orLocalSearchPage
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

    private val failure: LumoResult<SearchPage<Channel>> =
        LumoResult.Failure(LumoError.Offline(IOException("no network")))
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

    // ---- S10-02: the presence rule ------------------------------------------

    @Test
    fun `an absent type is not asked for, even in the grouped view`() = runTest {
        val asked = mutableListOf<String>()

        val result = searchSections(
            filter = SearchFilter.All,
            present = CataloguePresence(channels = true, films = false, series = false),
            channels = { asked += "channels"; channels },
            films = { error("an absent film catalogue must not be asked for") },
            series = { error("an absent series catalogue must not be asked for") },
        )

        assertThat(asked).containsExactly("channels")
        // Null is still "not asked for", and it is what keeps the section Idle on
        // the screen rather than an empty result the source did not give.
        assertThat(result.films).isNull()
        assertThat(result.series).isNull()
    }

    @Test
    fun `a present type with no match is an empty success, not a hidden one`() = runTest {
        val empty = LumoResult.Success(SearchPage(emptyList<Channel>(), totalElements = 0L))

        val result = searchSections(
            filter = SearchFilter.Channels,
            present = CataloguePresence(channels = true, films = false, series = false),
            channels = { empty },
            films = { error("never") },
            series = { error("never") },
        )

        // Present and empty: a success carrying zero, which the screen keeps under
        // its filter. Distinct from the type being absent above.
        assertThat(result.channels).isEqualTo(empty)
    }

    @Test
    fun `a probe that did not answer leaves its type present`() = runTest {
        val offline = LumoResult.Failure(LumoError.Offline(IOException("no network")))

        val presence = cataloguePresenceOf(
            channels = { LumoResult.Success(true) },
            films = { LumoResult.Success(false) },
            series = { offline },
        )

        assertThat(presence.channels).isTrue()
        assertThat(presence.films).isFalse()
        // Fail-open: an outage never hides a filter (SR-11).
        assertThat(presence.series).isTrue()
    }

    // ---- S10-04: the offline fallback, and only it --------------------------

    @Test
    fun `offline with a cache answers from it, marked as such`() = runTest {
        val cached = SearchPage(listOf(channel("c1")), totalElements = 1L, fromCache = true)

        val result = failure.orLocalSearchPage(hasCachedCatalogue = true) { cached }

        assertThat(result).isEqualTo(LumoResult.Success(cached))
        assertThat((result as LumoResult.Success).value.fromCache).isTrue()
    }

    @Test
    fun `offline without a cache keeps the failure`() = runTest {
        val result = failure.orLocalSearchPage(hasCachedCatalogue = false) {
            error("an empty cache has no page to give")
        }

        // No local answer: the failure stands rather than becoming a false
        // "no result" over an empty cache (SR-14).
        assertThat(result).isEqualTo(failure)
    }

    @Test
    fun `a server error is not papered over by the cache`() = runTest {
        val serverError: LumoResult<SearchPage<Channel>> =
            LumoResult.Failure(LumoError.UnknownCode("INTERNAL_ERROR"))

        val result = serverError.orLocalSearchPage(hasCachedCatalogue = true) {
            error("the server answered; the cache must not replace it")
        }

        assertThat(result).isEqualTo(serverError)
    }

    @Test
    fun `a successful online page is left untouched`() = runTest {
        val online = SearchPage(listOf(channel("c1")), totalElements = 1L)

        val result = LumoResult.Success(online).orLocalSearchPage(hasCachedCatalogue = true) {
            error("nothing failed, so the cache is not read")
        }

        assertThat(result).isEqualTo(LumoResult.Success(online))
    }

    private fun channel(id: String) = Channel(
        id = id,
        sourceId = "source-1",
        categoryId = null,
        name = "Falaise",
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )
}
