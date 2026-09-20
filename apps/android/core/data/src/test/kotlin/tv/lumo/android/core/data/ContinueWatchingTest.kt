package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.HOME_RAIL_SIZE
import tv.lumo.android.core.data.model.ResumableFilm
import tv.lumo.android.core.data.model.ResumableSeries
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.model.WatchProgress
import tv.lumo.android.core.data.model.continueWatchingOf
import tv.lumo.android.core.data.model.resumableFilms

/**
 * The home screen's "Continue" rail (US-017, US-019).
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * **The merge.** Films and series arrive as two lists, each most recent first.
 * Concatenating them looks right on any account that only watches films — and puts
 * the episode watched an hour ago behind a film abandoned last month on every
 * other. Only a sort on the server's own timestamp merges them.
 *
 * **The cap.** Cutting each list before merging passes every test written with
 * three cards, and on a real account lets twelve old films push every series out.
 *
 * **The source.** With one source every filter is a no-op, so a rail that ignored
 * the active source would pass every manual test until the day somebody adds a
 * second subscription (US-018).
 *
 * Everything here is fictional: no real title, no real channel (AGENTS.md §1).
 */
class ContinueWatchingTest {

    @Test
    fun `films and series are merged, the most recently watched first`() {
        val rail = continueWatchingOf(
            films = listOf(film("film-new", updatedAt = 400), film("film-old", updatedAt = 100)),
            series = listOf(show("series-mid", updatedAt = 300), show("series-older", updatedAt = 200)),
            sourceId = SOURCE,
        )

        assertThat(rail.map { it.key }).containsExactly(
            "film:film-new",
            "series:series-mid",
            "series:series-older",
            "film:film-old",
        ).inOrder()
    }

    @Test
    fun `a series has one card, and the most recent row decides it`() {
        // `SeriesRepository.resumable` already reduces to one card per series.
        // Said again here because this function is the one the screen trusts.
        val rail = continueWatchingOf(
            films = emptyList(),
            series = listOf(
                show("series-a", updatedAt = 500, episodeId = "episode-3"),
                show("series-a", updatedAt = 200, episodeId = "episode-1"),
            ),
            sourceId = SOURCE,
        )

        assertThat(rail).hasSize(1)
        val card = rail.single() as ContinueItem.Show
        assertThat(card.resume.episode.id).isEqualTo("episode-3")
    }

    @Test
    fun `a finished film is not offered`() {
        val rail = continueWatchingOf(
            films = listOf(
                film("film-done", updatedAt = 900, positionMs = 96, durationMs = 100),
                film("film-going", updatedAt = 100, positionMs = 10, durationMs = 100),
            ),
            series = emptyList(),
            sourceId = SOURCE,
        )

        assertThat(rail.map { it.key }).containsExactly("film:film-going")
    }

    @Test
    fun `a film with no known duration stays, whatever its position`() {
        // The default `WatchProgress.finished` argues for: with no denominator a
        // film is never finished, because vanishing early is the unrecoverable
        // mistake of the two.
        val rail = continueWatchingOf(
            films = listOf(film("film-unknown", updatedAt = 1, positionMs = 9_000_000, durationMs = null)),
            series = emptyList(),
            sourceId = SOURCE,
        )

        assertThat(rail).hasSize(1)
    }

    @Test
    fun `a series offering its next episode from the start is kept`() {
        // Position zero is what `resumable` produces when the episode somebody was
        // watching is over and a successor exists. It is not "nothing watched".
        val rail = continueWatchingOf(
            films = emptyList(),
            series = listOf(show("series-a", updatedAt = 10, positionMs = 0)),
            sourceId = SOURCE,
        )

        assertThat(rail).hasSize(1)
    }

    @Test
    fun `only the active source is shown`() {
        val rail = continueWatchingOf(
            films = listOf(film("film-here", updatedAt = 1), film("film-there", updatedAt = 2, source = OTHER)),
            series = listOf(show("series-there", updatedAt = 3, source = OTHER)),
            sourceId = SOURCE,
        )

        assertThat(rail.map { it.key }).containsExactly("film:film-here")
    }

    @Test
    fun `no active source shows nothing rather than everything`() {
        val rail = continueWatchingOf(
            films = listOf(film("film", updatedAt = 1)),
            series = listOf(show("series", updatedAt = 2)),
            sourceId = null,
        )

        assertThat(rail).isEmpty()
    }

    @Test
    fun `the rail is capped after the merge, not before it`() {
        val films = (1..HOME_RAIL_SIZE).map { film("film-$it", updatedAt = it.toLong()) }
        val recentSeries = show("series-recent", updatedAt = 1_000)

        val rail = continueWatchingOf(films, listOf(recentSeries), SOURCE)

        assertThat(rail).hasSize(HOME_RAIL_SIZE)
        // Capping each list first would have filled the rail with films and
        // dropped the one thing watched last.
        assertThat(rail.first().key).isEqualTo("series:series-recent")
        assertThat(rail.map { it.key }).doesNotContain("film:film-1")
    }

    @Test
    fun `a film and a series sharing an identifier are two cards`() {
        // Two tables, two id spaces. A key built from the bare id would collapse
        // them and crash a `LazyRow` on a duplicate key.
        val rail = continueWatchingOf(
            films = listOf(film("42", updatedAt = 2)),
            series = listOf(show("42", updatedAt = 1)),
            sourceId = SOURCE,
        )

        assertThat(rail.map { it.key }).containsExactly("film:42", "series:42").inOrder()
    }

    // ---- resolving rows into films -------------------------------------------

    @Test
    fun `saved positions are resolved in the order of the rows, not of the cache`() {
        val rows = listOf(row("film-b"), row("film-a"))
        val cache = listOf(vodItem("film-a"), vodItem("film-b"))

        assertThat(resumableFilms(rows, cache).map { it.film.id })
            .containsExactly("film-b", "film-a").inOrder()
    }

    @Test
    fun `a film the cache no longer holds drops out rather than drawing a gap`() {
        val rows = listOf(row("film-gone"), row("film-a"))

        assertThat(resumableFilms(rows, listOf(vodItem("film-a"))).map { it.film.id })
            .containsExactly("film-a")
    }

    @Test
    fun `a finished row is not resolved into a card`() {
        val rows = listOf(row("film-a", positionMs = 99, durationMs = 100))

        assertThat(resumableFilms(rows, listOf(vodItem("film-a")))).isEmpty()
    }

    // ---- fixtures ------------------------------------------------------------

    private fun row(filmId: String, positionMs: Long = 1_000, durationMs: Long? = null) =
        WatchProgress(sourceId = SOURCE, filmId = filmId, positionMs = positionMs, durationMs = durationMs)

    private fun vodItem(id: String, source: String = SOURCE) = VodItem(
        id = id,
        sourceId = source,
        categoryId = null,
        name = "Film $id",
        posterUrl = null,
        year = null,
        durationSeconds = null,
        rating = null,
        plot = null,
        isAdult = false,
    )

    private fun film(
        id: String,
        updatedAt: Long,
        source: String = SOURCE,
        positionMs: Long = 1_000,
        durationMs: Long? = null,
    ) = ResumableFilm(
        film = vodItem(id, source),
        progress = WatchProgress(source, id, positionMs, durationMs, updatedAtMillis = updatedAt),
    )

    private fun show(
        id: String,
        updatedAt: Long,
        source: String = SOURCE,
        episodeId: String = "episode-of-$id",
        positionMs: Long = 1_000,
    ) = ResumableSeries(
        series = Series(
            id = id,
            sourceId = source,
            categoryId = null,
            name = "Series $id",
            posterUrl = null,
            year = null,
            episodeRunTime = null,
            rating = null,
            plot = null,
            isAdult = false,
        ),
        episode = Episode(
            id = episodeId,
            seriesId = id,
            sourceId = source,
            seasonNumber = 1,
            episodeNumber = 1,
            name = null,
            durationSeconds = null,
            plot = null,
        ),
        positionMs = positionMs,
        updatedAtMillis = updatedAt,
    )

    private companion object {
        const val SOURCE = "source-a"
        const val OTHER = "source-b"
    }
}
