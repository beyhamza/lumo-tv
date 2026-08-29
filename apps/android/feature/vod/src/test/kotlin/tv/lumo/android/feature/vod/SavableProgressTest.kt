package tv.lumo.android.feature.vod

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.SeekAvailability

/**
 * What is worth writing to `PUT /me/progress` (S5-11).
 *
 * <h2>The live guard, which the sprint asks each client to test</h2>
 *
 * `ProgressItemType` has no `LIVE` value, so a progress row for a channel cannot
 * be *expressed* — that much is a compile error rather than a check. What a type
 * system does not prevent is the sequence that actually happens: **one
 * `LumoPlayer` is shared by the channel player and the film player**, and a view
 * model that writes whatever the player reports will happily write a position for
 * a continuous stream. The sprint names that risk in as many words, and this is
 * the guard it asked for.
 *
 * <h2>The zero guard, which is not thrift</h2>
 *
 * A save at position zero **overwrites a real position with the beginning of the
 * film**. The player reports zero for every frame before the first one decodes,
 * so without this guard opening a film and leaving immediately would lose where
 * somebody was — the exact scenario resume exists for.
 */
class SavableProgressTest {

    @Test
    fun `a live stream is never saved, whatever its position says`() {
        val live = PlaybackProgress(
            positionMs = 90_000,
            durationMs = null,
            seek = SeekAvailability.LIVE,
        )

        assertThat(live.savable()).isFalse()
    }

    @Test
    fun `nothing is saved before anything has loaded`() {
        // UNKNOWN means no stream is ready. Whatever the position holds is not a
        // position, and writing it would be writing a guess.
        val loading = PlaybackProgress(positionMs = 0, seek = SeekAvailability.UNKNOWN)

        assertThat(loading.savable()).isFalse()
    }

    @Test
    fun `position zero is never saved`() {
        val atTheStart = PlaybackProgress(positionMs = 0, seek = SeekAvailability.AVAILABLE)

        // Not thrift: this would overwrite a real position with the beginning.
        assertThat(atTheStart.savable()).isFalse()
    }

    @Test
    fun `a film that is playing is saved`() {
        val playing = PlaybackProgress(
            positionMs = 1_214_000,
            durationMs = 5_400_000,
            seek = SeekAvailability.AVAILABLE,
        )

        assertThat(playing.savable()).isTrue()
    }

    @Test
    fun `a film the server will not seek is still saved`() {
        // The position is real even when the server refuses to move to it. What
        // is lost is the ability to resume *there*, which is the player's problem
        // and not a reason to forget where somebody was.
        val refused = PlaybackProgress(
            positionMs = 600_000,
            durationMs = 5_400_000,
            seek = SeekAvailability.REFUSED,
        )

        assertThat(refused.savable()).isTrue()
    }
}
