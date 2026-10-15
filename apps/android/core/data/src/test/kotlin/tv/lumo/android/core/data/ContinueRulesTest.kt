package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.PrimaryPlay
import tv.lumo.android.core.data.model.Season
import tv.lumo.android.core.data.model.SheetPlay
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.model.WatchProgress
import tv.lumo.android.core.data.model.resumableFilms
import tv.lumo.android.core.data.model.seriesPickOf
import tv.lumo.android.core.data.model.seriesSheetPlayOf
import tv.lumo.android.core.data.model.sheetPlayOf

/**
 * S12-03/04: the rail follows the server's `in_continue`, a series card follows its
 * flags, and a sheet offers Watch / Resume at… / Watch again (CW cases in names).
 * Fictional content only.
 */
class ContinueRulesTest {

    // ---- the sheet's main action (S12-03) -----------------------------------

    @Test
    fun `nothing saved, or saved at zero, is Watch with no Start over`() {
        assertThat(sheetPlayOf(null, null)).isEqualTo(SheetPlay(PrimaryPlay.Watch, startOver = false))
        assertThat(sheetPlayOf(0L, 6_000_000L)).isEqualTo(SheetPlay(PrimaryPlay.Watch, startOver = false))
    }

    @Test
    fun `started is Resume at the position, with Start over`() {
        assertThat(sheetPlayOf(1_214_000L, 6_000_000L))
            .isEqualTo(SheetPlay(PrimaryPlay.Resume(1_214_000L), startOver = true))
    }

    @Test
    fun `CW-06 exactly 95 percent is not finished yet`() {
        assertThat(sheetPlayOf(5_700_000L, 6_000_000L).primary).isEqualTo(PrimaryPlay.Resume(5_700_000L))
    }

    @Test
    fun `CW-07 past 95 percent is Watch again, which starts from the beginning`() {
        val play = sheetPlayOf(5_700_001L, 6_000_000L)
        assertThat(play).isEqualTo(SheetPlay(PrimaryPlay.WatchAgain, startOver = false))
        assertThat(play.primary.startMs).isEqualTo(0L)
    }

    @Test
    fun `CW-09 unknown duration is never finished`() {
        assertThat(sheetPlayOf(99_000_000L, null).primary).isEqualTo(PrimaryPlay.Resume(99_000_000L))
    }

    // ---- the film rail follows in_continue (S12-04) --------------------------

    @Test
    fun `films are cards only when the server says so - hidden and under 30 s are out`() {
        val rows = listOf(
            film("eligible", inContinue = true),
            film("hidden", inContinue = false, hidden = true),
            film("ten-seconds", inContinue = false),
        )
        val cards = resumableFilms(rows, rows.map { vod(it.filmId) })
        assertThat(cards.map { it.film.id }).containsExactly("eligible")
    }

    // ---- a series card from its rows (S12-04) --------------------------------

    private val order = listOf("e1", "e2", "e3")

    @Test
    fun `CW-01 nothing has reached 30 s - no card`() {
        assertThat(seriesPickOf(listOf(ep("e1", 10_000, inContinue = false)), order)).isNull()
    }

    @Test
    fun `an eligible episode in progress is the card, at its position`() {
        val pick = seriesPickOf(listOf(ep("e2", 600_000, inContinue = true)), order)!!
        assertThat(pick.episodeId).isEqualTo("e2")
        assertThat(pick.positionMs).isEqualTo(600_000)
        assertThat(pick.row?.episodeId).isEqualTo("e2")
    }

    @Test
    fun `a hidden series has no card, even with an older eligible row`() {
        val rows = listOf(
            ep("e2", 600_000, inContinue = false, hidden = true),
            ep("e1", 600_000, inContinue = false, hidden = true),
        )
        assertThat(seriesPickOf(rows, order)).isNull()
    }

    @Test
    fun `CW-18 restarted after the hide - the restarted row is back`() {
        val rows = listOf(
            ep("e2", 5_000, inContinue = true), // restarted: server cleared its hide
            ep("e1", 600_000, inContinue = false, hidden = true),
        )
        assertThat(seriesPickOf(rows, order)?.episodeId).isEqualTo("e2")
    }

    @Test
    fun `CW-19 finished episode with a next never started - the next one from zero`() {
        val pick = seriesPickOf(listOf(ep("e1", FINISHED, inContinue = false)), order)!!
        assertThat(pick.episodeId).isEqualTo("e2")
        assertThat(pick.positionMs).isEqualTo(0L)
        // What a removal names: the row behind the card, not the next episode.
        assertThat(pick.row?.episodeId).isEqualTo("e1")
    }

    @Test
    fun `CW-12 next already started - resumed at its position`() {
        val rows = listOf(ep("e1", FINISHED, inContinue = false), ep("e2", 120_000, inContinue = false))
        val pick = seriesPickOf(rows, order)!!
        assertThat(pick.episodeId).isEqualTo("e2")
        assertThat(pick.positionMs).isEqualTo(120_000)
    }

    @Test
    fun `CW-21 next already finished - offered from the beginning, in order`() {
        val rows = listOf(ep("e1", FINISHED, inContinue = false), ep("e2", FINISHED, inContinue = false))
        val pick = seriesPickOf(rows, order)!!
        assertThat(pick.episodeId).isEqualTo("e2")
        assertThat(pick.positionMs).isEqualTo(0L)
    }

    @Test
    fun `CW-15 last episode finished - the series leaves the rail`() {
        assertThat(seriesPickOf(listOf(ep("e3", FINISHED, inContinue = false)), order)).isNull()
    }

    @Test
    fun `a next episode started under 30 s keeps the series card the finished one earned`() {
        val rows = listOf(ep("e2", 8_000, inContinue = false), ep("e1", FINISHED, inContinue = false))
        val pick = seriesPickOf(rows, order)!!
        assertThat(pick.episodeId).isEqualTo("e2")
        assertThat(pick.positionMs).isEqualTo(8_000)
    }

    // ---- the series sheet (S12-03) ---------------------------------------------

    private val seasons = listOf(
        Season(1, null, null, listOf(episode("e1", 1), episode("e2", 2))),
        Season(2, null, null, listOf(episode("e3", 1))),
    )

    @Test
    fun `series sheet - nothing saved is Watch S1 E1`() {
        val (episode, play) = seriesSheetPlayOf(seasons, emptyMap())!!
        assertThat(episode.id).isEqualTo("e1")
        assertThat(play.primary).isEqualTo(PrimaryPlay.Watch)
    }

    @Test
    fun `series sheet - resumes the most recent episode, hidden or not (CW-10)`() {
        val progress = mapOf(
            "e1" to ep("e1", 600_000, inContinue = false, hidden = true, updatedAt = 1),
            "e2" to ep("e2", 300_000, inContinue = false, hidden = true, updatedAt = 2),
        )
        val (episode, play) = seriesSheetPlayOf(seasons, progress)!!
        assertThat(episode.id).isEqualTo("e2")
        assertThat(play).isEqualTo(SheetPlay(PrimaryPlay.Resume(300_000), startOver = true))
    }

    @Test
    fun `series sheet - finished goes to the next one, across seasons`() {
        val progress = mapOf("e2" to ep("e2", FINISHED, inContinue = false))
        val (episode, play) = seriesSheetPlayOf(seasons, progress)!!
        assertThat(episode.id).isEqualTo("e3")
        assertThat(play.primary).isEqualTo(PrimaryPlay.Watch)
    }

    @Test
    fun `series sheet - all done is Watch again from the first episode`() {
        val progress = mapOf("e3" to ep("e3", FINISHED, inContinue = false))
        val (episode, play) = seriesSheetPlayOf(seasons, progress)!!
        assertThat(episode.id).isEqualTo("e1")
        assertThat(play.primary).isEqualTo(PrimaryPlay.WatchAgain)
    }

    // ---- fixtures ---------------------------------------------------------------

    private fun ep(
        id: String,
        positionMs: Long,
        inContinue: Boolean,
        hidden: Boolean = false,
        updatedAt: Long = 0,
    ) = EpisodeProgress(
        sourceId = SOURCE,
        episodeId = id,
        positionMs = positionMs,
        durationMs = DURATION,
        updatedAtMillis = updatedAt,
        inContinue = inContinue,
        hidden = hidden,
    )

    private fun film(id: String, inContinue: Boolean, hidden: Boolean = false) =
        WatchProgress(SOURCE, id, 600_000, DURATION, inContinue = inContinue, hidden = hidden)

    private fun vod(id: String) = VodItem(
        id = id,
        sourceId = SOURCE,
        categoryId = null,
        name = "Film $id",
        posterUrl = null,
        year = null,
        durationSeconds = null,
        rating = null,
        plot = null,
        isAdult = false,
    )

    private fun episode(id: String, number: Int) = Episode(
        id = id,
        seriesId = "series",
        sourceId = SOURCE,
        seasonNumber = if (id == "e3") 2 else 1,
        episodeNumber = number,
        name = null,
        durationSeconds = DURATION / 1_000,
        plot = null,
    )

    private companion object {
        const val SOURCE = "00000000-0000-0000-0000-000000000001"
        const val DURATION = 2_400_000L
        const val FINISHED = 2_390_000L
    }
}
