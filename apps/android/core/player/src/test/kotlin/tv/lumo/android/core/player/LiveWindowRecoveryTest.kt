package tv.lumo.android.core.player

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** S10B-04: a live stream behind its window rejoins the edge instead of failing. */
class LiveWindowRecoveryTest {

    @Test
    fun `a live stream behind its window rejoins the edge`() {
        assertThat(shouldRejoinLiveEdge(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, isLive = true, attempts = 0))
            .isTrue()
    }

    @Test
    fun `the rejoin is bounded, so a broken window ends in an error`() {
        assertThat(
            shouldRejoinLiveEdge(
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                isLive = true,
                attempts = MAX_LIVE_EDGE_REJOINS,
            ),
        ).isFalse()
    }

    @Test
    fun `a film has no live edge to rejoin`() {
        assertThat(shouldRejoinLiveEdge(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, isLive = false, attempts = 0))
            .isFalse()
    }

    @Test
    fun `any other error is not a rejoin`() {
        assertThat(
            shouldRejoinLiveEdge(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, isLive = true, attempts = 0),
        ).isFalse()
    }
}
