package tv.lumo.androidtv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.feature.live.LiveDestination
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.source.SourceDestination
import tv.lumo.android.feature.vod.VodDestination

/**
 * What the rail offers (US-13).
 *
 * The phone has the same test and the same reason — a negative requirement is the
 * kind that gets silently undone — but it weighs more here. **A rail entry is a
 * mandatory stop on the way down.** A door onto an empty room costs every viewer a
 * `DOWN` press, on every journey, for a room most M3U playlists do not have; on a
 * phone the equivalent tab is simply ignored.
 *
 * The order is asserted, and on a television that is not cosmetic either: the rail
 * is where the D-pad lands first, and a list that reorders itself between two
 * launches moves every destination the viewer has learnt to reach by counting
 * presses.
 */
class TvDestinationsTest {

    @Test
    fun `a source with no films offers no films entry`() {
        val routes = tvDestinations(hasFilms = false).map { it.route }

        assertThat(routes).doesNotContain(VodDestination.route)
        assertThat(routes).containsExactly(
            LiveDestination.route,
            SourceDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `a source with films offers it second, right after the channels`() {
        val routes = tvDestinations(hasFilms = true).map { it.route }

        // Second, so the two catalogue destinations sit together at the top of the
        // rail: they are what the television is for, and everything below them is
        // something one goes to occasionally.
        assertThat(routes).containsExactly(
            LiveDestination.route,
            VodDestination.route,
            SourceDestination.route,
            SettingsDestination.route,
        ).inOrder()
    }

    @Test
    fun `channels stay first either way`() {
        // The shortest journey from the rail, whatever else is in it. That is the
        // rule the rail was built on, and the films entry must not shift it.
        assertThat(tvDestinations(hasFilms = false).first().route)
            .isEqualTo(LiveDestination.route)
        assertThat(tvDestinations(hasFilms = true).first().route)
            .isEqualTo(LiveDestination.route)
    }
}
