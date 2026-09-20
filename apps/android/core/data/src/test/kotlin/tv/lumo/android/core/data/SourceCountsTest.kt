package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.repository.SeriesRepository
import tv.lumo.android.core.data.repository.toCountOrNull
import tv.lumo.android.core.database.paging.SeriesPager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * The series counter of "My sources" (US-024).
 *
 * `Source` carries no such number, so it is `total_elements` of a one-row
 * listing. The film twin is pinned in [VodRepositoryTest]; what is worth a second
 * look here is the rule both share — **an unknown number is null and never
 * zero** — and the conversion that guards it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceCountsTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi

    private val seriesDao = FakeSeriesDao()
    private val sourceId = UUID.randomUUID().toString()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(CatalogApi::class.java)
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `the series count is the listing's total, asked with one row`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"items":[],"page":0,"size":1,"total_elements":312,"total_pages":312}"""),
        )

        assertThat(repository().seriesCount(sourceId)).isEqualTo(312)
        assertThat(server.takeRequest().requestUrl?.queryParameter("size")).isEqualTo("1")
    }

    @Test
    fun `a server error leaves the series count unknown rather than zero`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody("""{"type":"x","title":"x","status":500,"code":"INTERNAL_ERROR"}"""),
        )

        assertThat(repository().seriesCount(sourceId)).isNull()
    }

    @Test
    fun `a total that is not a count is unknown`() {
        assertThat(0L.toCountOrNull()).isEqualTo(0)
        assertThat(1_248L.toCountOrNull()).isEqualTo(1_248)
        assertThat((-1L).toCountOrNull()).isNull()
        assertThat((Int.MAX_VALUE.toLong() + 1).toCountOrNull()).isNull()
    }

    private fun repository() = SeriesRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        categoryDao = FakeSeriesCategoryDao(),
        seriesDao = seriesDao,
        pager = SeriesPager(seriesDao),
        io = UnconfinedTestDispatcher(),
    )
}
