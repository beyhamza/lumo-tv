package tv.lumo.android.feature.vod

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.SheetUnavailable
import tv.lumo.android.core.data.WatchlistMembership
import tv.lumo.android.core.data.WatchlistToggleState
import tv.lumo.android.core.designsystem.component.LumoWatchlistNote

/**
 * BUG-S11-06-04: offline, a film sheet with nothing cached greyed "My list…"
 * out with no sentence. The membership could not even be asked — the source
 * comes from the film — and the button has to say why it is disabled (WL-06).
 */
class WatchlistButtonMappingTest {

    @Test
    fun `a sheet that could not load offline disables the button and says why`() {
        val button = WatchlistToggleState().asButtonState(SheetUnavailable.Offline)

        assertThat(button.enabled).isFalse()
        assertThat(button.note).isEqualTo(LumoWatchlistNote.Offline)
        assertThat(button.retryable).isTrue()
    }

    @Test
    fun `a sheet that could not load for another reason says the list could not be checked`() {
        val button = WatchlistToggleState().asButtonState(SheetUnavailable.Failed)

        assertThat(button.enabled).isFalse()
        assertThat(button.note).isEqualTo(LumoWatchlistNote.CheckFailed)
    }

    @Test
    fun `a membership read in flight is not explained away`() {
        val button = WatchlistToggleState(busy = true).asButtonState(SheetUnavailable.Offline)

        assertThat(button.note).isNull()
    }

    @Test
    fun `a loaded sheet keeps the toggle's own sentences`() {
        val known = WatchlistToggleState(membership = WatchlistMembership.Out).asButtonState(null)
        assertThat(known.enabled).isTrue()
        assertThat(known.note).isNull()

        val offline = WatchlistToggleState(membership = WatchlistMembership.Out, offline = true).asButtonState()
        assertThat(offline.note).isEqualTo(LumoWatchlistNote.Offline)
    }
}
