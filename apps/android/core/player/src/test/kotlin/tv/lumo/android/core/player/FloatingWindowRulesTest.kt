package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * S15-01/02: when leaving opens a floating window, what the window offers, and
 * how its end is read.
 */
class FloatingWindowRulesTest {

    @Test
    fun `leaving during a playback opens the window when the setting is on and the system allows it`() {
        assertThat(FloatingWindowRules.enterOnLeave(settingOn = true, available = true, playing = true)).isTrue()
    }

    @Test
    fun `setting off, window refused or nothing playing - S10B-03 applies instead`() {
        assertThat(FloatingWindowRules.enterOnLeave(settingOn = false, available = true, playing = true)).isFalse()
        assertThat(FloatingWindowRules.enterOnLeave(settingOn = true, available = false, playing = true)).isFalse()
        assertThat(FloatingWindowRules.enterOnLeave(settingOn = true, available = true, playing = false)).isFalse()
    }

    @Test
    fun `playing or loading counts as playing, a pause, a failure or the end do not`() {
        assertThat(FloatingWindowRules.isPlaying(PlaybackState.Playing(title = null))).isTrue()
        assertThat(FloatingWindowRules.isPlaying(PlaybackState.Buffering)).isTrue()
        assertThat(FloatingWindowRules.isPlaying(PlaybackState.Paused)).isFalse()
        assertThat(FloatingWindowRules.isPlaying(PlaybackState.Idle)).isFalse()
        assertThat(FloatingWindowRules.isPlaying(PlaybackState.Ended)).isFalse()
        assertThat(FloatingWindowRules.isPlaying(PlaybackState.Failed(PlaybackError.UNKNOWN))).isFalse()
    }

    @Test
    fun `a film or an episode offers one play-pause toggle`() {
        assertThat(FloatingWindowRules.actions(live = false, playing = true))
            .containsExactly(FloatingWindowAction.PAUSE)
        assertThat(FloatingWindowRules.actions(live = false, playing = false))
            .containsExactly(FloatingWindowAction.PLAY)
    }

    @Test
    fun `a channel adds Direct after the toggle`() {
        assertThat(FloatingWindowRules.actions(live = true, playing = true))
            .containsExactly(FloatingWindowAction.PAUSE, FloatingWindowAction.LIVE).inOrder()
        assertThat(FloatingWindowRules.actions(live = true, playing = false))
            .containsExactly(FloatingWindowAction.PLAY, FloatingWindowAction.LIVE).inOrder()
    }

    @Test
    fun `a window ending on a started activity was expanded, on a stopped one closed`() {
        assertThat(FloatingWindowRules.exitOf(activityStarted = true)).isEqualTo(FloatingWindowExit.EXPANDED)
        assertThat(FloatingWindowRules.exitOf(activityStarted = false)).isEqualTo(FloatingWindowExit.CLOSED)
    }

    @Test
    fun `the window's shape is the video's, clamped to what Android accepts`() {
        assertThat(FloatingWindowRules.aspectRatio(1920, 1080)).isEqualTo(1920 to 1080)
        assertThat(FloatingWindowRules.aspectRatio(0, 0)).isEqualTo(16 to 9)
        assertThat(FloatingWindowRules.aspectRatio(3000, 1000)).isEqualTo(239 to 100)
        assertThat(FloatingWindowRules.aspectRatio(100, 1000)).isEqualTo(100 to 239)
    }
}
