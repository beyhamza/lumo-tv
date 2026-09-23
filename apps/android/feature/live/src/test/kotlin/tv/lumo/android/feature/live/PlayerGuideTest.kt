package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * What the player's bar says about the programme, and when it says nothing
 * (US-16, S7-03 by S9-03).
 *
 * <h2>Why the state and not the ViewModel</h2>
 *
 * The request itself — one per channel, when it opens — is `OnAirTracker`'s,
 * held by `OnAirTrackerTest` in `core:data`; the view model hands it a source
 * and a channel id in one line. What this screen decides is what to draw from
 * what came back, and that is a question of [PlayerUiState] against a clock.
 *
 * <h2>The half of the task that is silence</h2>
 *
 * A channel without `tvg_id`, a source without a guide and a guide that has not
 * loaded all arrive here as an empty list, and the bar must then show the
 * channel's name and nothing else — no "programme unavailable" for somebody
 * whose provider never gives one (S7-03).
 */
class PlayerGuideTest {

    private val now = Instant.parse("2026-09-24T20:30:00Z")

    @Test
    fun `with a guide, the bar has a current programme with its end and a next one`() {
        val state = PlayerUiState(
            programmes = listOf(
                programme("p1", now.minusSeconds(1800), now.plusSeconds(1800)),
                programme("p2", now.plusSeconds(1800), now.plusSeconds(5400)),
            ),
        )

        val onAir = state.onAir(now)

        assertThat(onAir.current?.title).isEqualTo("Programme p1")
        assertThat(onAir.current?.endsAt).isEqualTo(now.plusSeconds(1800))
        assertThat(onAir.next?.title).isEqualTo("Programme p2")
    }

    @Test
    fun `without a guide, the bar has nothing to say and says nothing`() {
        val onAir = PlayerUiState(programmes = emptyList()).onAir(now)

        assertThat(onAir.current).isNull()
        assertThat(onAir.next).isNull()
    }

    @Test
    fun `reopening the bar later recomputes over the same window, no new list needed`() {
        val state = PlayerUiState(
            programmes = listOf(
                programme("p1", now.minusSeconds(1800), now.plusSeconds(600)),
                programme("p2", now.plusSeconds(600), now.plusSeconds(5400)),
            ),
        )

        // Ten minutes later, the same state: the first programme is over.
        val later = state.onAir(now.plusSeconds(601))

        assertThat(later.current?.title).isEqualTo("Programme p2")
        assertThat(later.next).isNull()
    }

    private fun programme(id: String, start: Instant, end: Instant) = EpgProgramme(
        id = id,
        startsAt = start,
        endsAt = end,
        title = "Programme $id",
        description = null,
        category = null,
    )
}
