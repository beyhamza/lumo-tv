package tv.lumo.android.feature.home

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.data.model.HOME_RAIL_SIZE

/**
 * What the home screen asks the guide for, and what it drops (US-16, S9-03).
 *
 * The request is one per rail, through `OnAirTracker` (held in `core:data`);
 * what this screen owns is *which* ids go into it — the Live rail's, capped and
 * scoped to the active source like the rail itself — and that a change of
 * source does not leave the old source's programmes under the new cards.
 */
class HomeGuideTest {

    @Test
    fun `the guide is asked for the Live rail's channels, in rail order and capped`() {
        val watched = (1..HOME_RAIL_SIZE + 3).map { channel("recent-$it") } + channel("other", source = "source-b")
        val state = browsing(recent = watched)

        assertThat(state.liveChannelIds).hasSize(HOME_RAIL_SIZE)
        assertThat(state.liveChannelIds.first()).isEqualTo("recent-1")
        assertThat(state.liveChannelIds).doesNotContain("other")
    }

    @Test
    fun `no Live rail, nothing to ask for`() {
        assertThat(browsing(recent = emptyList()).liveChannelIds).isEmpty()
    }

    @Test
    fun `another source drops what was on air`() {
        val state = browsing(recent = listOf(channel("c1"))).copy(
            onAir = mapOf("c1" to programme("p1")),
        )

        val switched = state.showing(HomeSource(HomeStep.Browsing, sourceId = "source-b"))
        val same = state.showing(HomeSource(HomeStep.Browsing, sourceId = "source-a"))

        assertThat(switched.onAir).isEmpty()
        assertThat(same.onAir).containsKey("c1")
    }

    private fun browsing(recent: List<Channel>) = HomeState(
        step = HomeStep.Browsing,
        sourceId = "source-a",
        recent = recent,
        continueLoaded = true,
        favoritesLoaded = true,
        recentLoaded = true,
    )

    private fun channel(id: String, source: String = "source-a") = Channel(
        id = id,
        sourceId = source,
        categoryId = null,
        name = "Chaîne $id",
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun programme(id: String) = EpgProgramme(
        id = id,
        startsAt = Instant.EPOCH,
        endsAt = Instant.EPOCH.plusSeconds(3600),
        title = "Programme $id",
        description = null,
        category = null,
    )
}
