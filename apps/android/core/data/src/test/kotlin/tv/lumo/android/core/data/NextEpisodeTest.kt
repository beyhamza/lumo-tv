package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.Season
import tv.lumo.android.core.data.model.episodeAfter

/**
 * What "next episode" means (S6-06).
 *
 * <h2>Why this rule and not the countdown</h2>
 *
 * S6-06 delivers a card, a ten-second countdown, a focus target and a key that
 * cancels it. Only one of those has a right and a wrong answer, and it is this
 * one: which episode comes next. The rest is Compose, and Compose is not what gets
 * this wrong — a panel with a gap in its numbering is.
 *
 * It also has to give the **same** answer on the television and on the phone. A
 * rule written twice is a rule that will differ, and "the television skipped an
 * episode" is a bug nobody reports precisely because it looks like a decision.
 *
 * <h2>Every case here is a panel that exists</h2>
 *
 * The gaps are not hypothetical. Real catalogues number specials as episode 0,
 * skip a number when a file failed to upload, and carry season 1 and season 3
 * because season 2 was never ingested. Every one of those would end a series early
 * under an implementation that added one to a number.
 */
class NextEpisodeTest {

    @Test
    fun `inside a season, the next episode is the next one listed`() {
        val tree = listOf(season(1, episodes = listOf(ep("a", 1, 1), ep("b", 1, 2))))

        assertThat(tree.episodeAfter("a")?.id).isEqualTo("b")
    }

    @Test
    fun `a gap in the numbering does not end the season`() {
        // Episode 2 was never uploaded. Adding one to the number would look for an
        // episode that is not there and stop a season three episodes early.
        val tree = listOf(season(1, episodes = listOf(ep("a", 1, 1), ep("c", 1, 3))))

        assertThat(tree.episodeAfter("a")?.id).isEqualTo("c")
    }

    @Test
    fun `the last episode of a season goes on to the next season`() {
        val tree = listOf(
            season(1, episodes = listOf(ep("s1e1", 1, 1), ep("s1e2", 1, 2))),
            season(2, episodes = listOf(ep("s2e1", 2, 1))),
        )

        assertThat(tree.episodeAfter("s1e2")?.id).isEqualTo("s2e1")
    }

    @Test
    fun `a missing season number does not end the series`() {
        // Seasons 1 and 3, because season 2 was never ingested. The next season
        // present is the answer; `seasonNumber + 1` would hide half a series.
        val tree = listOf(
            season(1, episodes = listOf(ep("s1e1", 1, 1))),
            season(3, episodes = listOf(ep("s3e1", 3, 1))),
        )

        assertThat(tree.episodeAfter("s1e1")?.id).isEqualTo("s3e1")
    }

    @Test
    fun `an empty season is stepped over rather than stopped at`() {
        // A season the panel declares and lists nothing under. Stopping here would
        // end a series on a season that has no episode to play.
        val tree = listOf(
            season(1, episodes = listOf(ep("s1e1", 1, 1))),
            season(2, episodes = emptyList()),
            season(3, episodes = listOf(ep("s3e1", 3, 1))),
        )

        assertThat(tree.episodeAfter("s1e1")?.id).isEqualTo("s3e1")
    }

    @Test
    fun `the last episode of the last season offers nothing`() {
        val tree = listOf(season(1, episodes = listOf(ep("a", 1, 1), ep("b", 1, 2))))

        // Null, and the screen goes back to the series rather than offering a card
        // with nothing behind it.
        assertThat(tree.episodeAfter("b")).isNull()
    }

    @Test
    fun `an episode this tree does not hold offers nothing rather than throwing`() {
        // The tree can be refetched under a player that is still running, and a
        // provider that dropped an episode is not a crash.
        val tree = listOf(season(1, episodes = listOf(ep("a", 1, 1))))

        assertThat(tree.episodeAfter("gone")).isNull()
    }

    @Test
    fun `an episode numbered zero is an episode like any other`() {
        // Specials are numbered 0 by several panels. Nothing about this rule reads
        // the number, which is exactly why it survives them.
        val tree = listOf(season(1, episodes = listOf(ep("special", 1, 0), ep("a", 1, 1))))

        assertThat(tree.episodeAfter("special")?.id).isEqualTo("a")
    }

    @Test
    fun `a tree with no season at all offers nothing`() {
        assertThat(emptyList<Season>().episodeAfter("a")).isNull()
    }
}

private fun season(number: Int, episodes: List<Episode>) = Season(
    seasonNumber = number,
    episodeCount = episodes.size,
    posterUrl = null,
    episodes = episodes,
)

private fun ep(id: String, season: Int, number: Int) = Episode(
    id = id,
    seriesId = "series",
    sourceId = "source",
    seasonNumber = season,
    episodeNumber = number,
    name = null,
    durationSeconds = null,
    plot = null,
)
