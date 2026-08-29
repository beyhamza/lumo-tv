package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.repository.SeriesRepository
import tv.lumo.android.core.database.model.EpisodeEntity
import tv.lumo.android.core.database.model.SeasonEntity
import tv.lumo.android.core.database.model.SeriesEntity
import tv.lumo.android.core.database.paging.SeriesPager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * Turning episode positions into one card per series (S6-08).
 *
 * <h2>Why this is the test the task is worth</h2>
 *
 * The whole of S6-08 is one sentence — *progress is recorded on an episode,
 * resuming is thought about in series* — and everything downstream of that
 * sentence is rendering. What can be wrong is the translation, and it can be wrong
 * in four ways that all look plausible on a screen:
 *
 * - three cards for somebody who watched three episodes of one series;
 * - the card offering the episode they just finished rather than the next one;
 * - a finished series that never leaves the rail;
 * - a series whose tree this device does not hold appearing with nothing behind it.
 *
 * None of those throws. All four are noticed by whoever owns the television.
 *
 * <h2>No server here</h2>
 *
 * `resumable` reads the cache and nothing else, on purpose — the rail is built at
 * the moment a catalogue screen opens, and a request per row would be twelve calls
 * to somebody's panel for a strip above a grid. `MockWebServer` is present only
 * because the repository takes an API it never reaches in these cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeSeriesTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi
    private val seriesDao = FakeSeriesDao()
    private val categoryDao = FakeSeriesCategoryDao()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(CatalogApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `an episode under the threshold resumes where it was left`() = runTest {
        given(episodes = listOf(episode("e1", 1, 1), episode("e2", 1, 2)))

        val cards = repository().resumable(
            listOf(progress("e1", positionMs = 300_000L, durationMs = 1_200_000L)),
        )

        assertThat(cards).hasSize(1)
        assertThat(cards.first().episode.id).isEqualTo("e1")
        assertThat(cards.first().positionMs).isEqualTo(300_000L)
    }

    @Test
    fun `an episode past the threshold offers the next one, from the beginning`() = runTest {
        given(episodes = listOf(episode("e1", 1, 1), episode("e2", 1, 2)))

        // 96 % of the way through. What somebody wants next is the following
        // episode, not the credits they have already seen.
        val cards = repository().resumable(
            listOf(progress("e1", positionMs = 1_152_000L, durationMs = 1_200_000L)),
        )

        assertThat(cards.single().episode.id).isEqualTo("e2")
        assertThat(cards.single().positionMs).isEqualTo(0L)
    }

    @Test
    fun `the last episode finished takes the series out of the rail`() = runTest {
        given(episodes = listOf(episode("e1", 1, 1), episode("e2", 1, 2)))

        val cards = repository().resumable(
            listOf(progress("e2", positionMs = 1_152_000L, durationMs = 1_200_000L)),
        )

        // Finished, with nothing after it. Offering to start it over is not an
        // offer.
        assertThat(cards).isEmpty()
    }

    @Test
    fun `the end of a season carries on into the next one`() = runTest {
        given(
            seasons = listOf(season(1), season(2)),
            episodes = listOf(episode("s1e1", 1, 1), episode("s2e1", 2, 1)),
        )

        val cards = repository().resumable(
            listOf(progress("s1e1", positionMs = 1_152_000L, durationMs = 1_200_000L)),
        )

        assertThat(cards.single().episode.id).isEqualTo("s2e1")
    }

    @Test
    fun `three episodes of one series make one card, the most recent`() = runTest {
        given(episodes = listOf(episode("e1", 1, 1), episode("e2", 1, 2), episode("e3", 1, 3)))

        // The server returns most recently touched first, and nothing re-sorts it.
        // A rail that showed three rows here has understood the data and not the
        // use.
        val cards = repository().resumable(
            listOf(
                progress("e3", positionMs = 60_000L, durationMs = 1_200_000L),
                progress("e2", positionMs = 900_000L, durationMs = 1_200_000L),
                progress("e1", positionMs = 500_000L, durationMs = 1_200_000L),
            ),
        )

        assertThat(cards).hasSize(1)
        assertThat(cards.single().episode.id).isEqualTo("e3")
    }

    @Test
    fun `two series make two cards, in the order the server gave them`() = runTest {
        given(episodes = listOf(episode("a1", 1, 1)))
        given(
            seriesId = "other",
            seasons = listOf(season(1, seriesId = "other")),
            episodes = listOf(episode("b1", 1, 1, seriesId = "other")),
        )

        val cards = repository().resumable(
            listOf(
                progress("b1", positionMs = 60_000L, durationMs = 1_200_000L),
                progress("a1", positionMs = 60_000L, durationMs = 1_200_000L),
            ),
        )

        assertThat(cards.map { it.series.id }).containsExactly("other", "series").inOrder()
    }

    @Test
    fun `an episode whose tree this device does not hold is left out`() = runTest {
        given(episodes = listOf(episode("e1", 1, 1)))

        // Started on the phone, never opened here. The card cannot be built — the
        // episode does not resolve to a series — and the rail says nothing rather
        // than showing a row with nothing behind it. Opening the series screen is
        // what fixes it, and that is the normal way in.
        val cards = repository().resumable(
            listOf(progress("elsewhere", positionMs = 60_000L, durationMs = 1_200_000L)),
        )

        assertThat(cards).isEmpty()
    }

    @Test
    fun `an episode with no stated duration is never finished`() = runTest {
        given(episodes = listOf(episode("e1", 1, 1), episode("e2", 1, 2)))

        // Many panels state no running time. A series that vanished from the rail
        // before the end is a loss nothing else records; one that lingers is an
        // annoyance somebody dismisses.
        val cards = repository().resumable(
            listOf(progress("e1", positionMs = 9_999_999L, durationMs = null)),
        )

        assertThat(cards.single().episode.id).isEqualTo("e1")
        assertThat(cards.single().positionMs).isEqualTo(9_999_999L)
    }

    @Test
    fun `no saved position at all is an empty rail, not a failure`() = runTest {
        given(episodes = listOf(episode("e1", 1, 1)))

        assertThat(repository().resumable(emptyList())).isEmpty()
    }

    // ---- fixtures ----------------------------------------------------------

    private fun given(
        seriesId: String = "series",
        seasons: List<SeasonEntity> = listOf(season(1)),
        episodes: List<EpisodeEntity> = emptyList(),
    ) {
        seriesDao.stored += SeriesEntity(
            id = seriesId,
            sourceId = SOURCE,
            categoryId = null,
            externalId = null,
            name = seriesId,
            posterUrl = null,
            year = null,
            episodeRunTime = null,
            rating = null,
            plot = null,
            treeFetchedAt = 1L,
            position = 0,
            isAdult = false,
        )
        seriesDao.seasons += seasons
        seriesDao.episodes += episodes
    }

    private fun season(number: Int, seriesId: String = "series") = SeasonEntity(
        id = "$seriesId-s$number",
        seriesId = seriesId,
        seasonNumber = number,
        episodeCount = null,
        posterUrl = null,
    )

    private fun episode(
        id: String,
        seasonNumber: Int,
        episodeNumber: Int,
        seriesId: String = "series",
    ) = EpisodeEntity(
        id = id,
        seriesId = seriesId,
        seasonId = "$seriesId-s$seasonNumber",
        sourceId = SOURCE,
        externalId = null,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        name = null,
        durationSeconds = null,
        plot = null,
    )

    private fun progress(episodeId: String, positionMs: Long, durationMs: Long?) =
        EpisodeProgress(
            sourceId = SOURCE,
            episodeId = episodeId,
            positionMs = positionMs,
            durationMs = durationMs,
        )

    private fun repository() = SeriesRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        categoryDao = categoryDao,
        seriesDao = seriesDao,
        pager = SeriesPager(seriesDao),
        io = UnconfinedTestDispatcher(),
    )

    private companion object {
        const val SOURCE = "11111111-1111-1111-1111-111111111111"
    }
}
