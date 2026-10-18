package tv.lumo.android.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** PS-07: a change is pending until the player confirms it, and fails honestly otherwise. */
class TrackChangeTest {

    private val before = TrackOptions(
        known = true,
        audio = listOf(audio("0:0", selected = true), audio("0:1", selected = false)),
        subtitles = listOf(text("2:0", selected = false)),
        qualities = listOf(quality("1:0", applied = true), quality("1:1", applied = true)),
    )

    @Test
    fun `a choice waits, showing what plays, until the player confirms it`() {
        val pending = TrackChangeRules.start(TrackChange.Idle, TrackKind.AUDIO, "0:1", before, nowMs = 1_000)
        assertThat(pending).isEqualTo(TrackChange.Pending(TrackKind.AUDIO, "0:1", "0:0", 1_000))

        // The player has not switched yet: still pending.
        assertThat(pending.onOptions(before)).isEqualTo(pending)

        val after = before.copy(audio = listOf(audio("0:0", selected = false), audio("0:1", selected = true)))
        assertThat(pending.onOptions(after)).isEqualTo(TrackChange.Idle)
    }

    @Test
    fun `no answer within the timeout fails, not a moment before`() {
        val pending = TrackChangeRules.start(TrackChange.Idle, TrackKind.SUBTITLES, "2:0", before, nowMs = 0)

        assertThat(pending.onTick(nowMs = TrackChangeRules.TIMEOUT_MS - 1)).isEqualTo(pending)
        assertThat(pending.onTick(nowMs = TrackChangeRules.TIMEOUT_MS))
            .isEqualTo(TrackChange.Failed(TrackKind.SUBTITLES, target = "2:0", previous = null))
    }

    @Test
    fun `a player error during the change fails it, and only then`() {
        val pending = TrackChangeRules.start(TrackChange.Idle, TrackKind.AUDIO, "0:1", before, nowMs = 0)
        assertThat(pending.onFailure()).isEqualTo(TrackChange.Failed(TrackKind.AUDIO, "0:1", "0:0"))
        assertThat(TrackChange.Idle.onFailure()).isEqualTo(TrackChange.Idle)
    }

    @Test
    fun `a failure stays until the viewer retries, once per press`() {
        val failed = TrackChange.Failed(TrackKind.AUDIO, "0:1", "0:0")

        // Time passing and the same options do nothing: no loop.
        assertThat(failed.onTick(nowMs = 1_000_000)).isEqualTo(failed)
        assertThat(failed.onOptions(before)).isEqualTo(failed)

        val retried = failed.retried(nowMs = 50_000)
        assertThat(retried).isEqualTo(TrackChange.Pending(TrackKind.AUDIO, "0:1", "0:0", 50_000))
        assertThat(retried.onTick(nowMs = 50_000 + TrackChangeRules.TIMEOUT_MS)).isEqualTo(failed)
    }

    @Test
    fun `a second choice of the same kind keeps what was playing before the first`() {
        val first = TrackChangeRules.start(TrackChange.Idle, TrackKind.AUDIO, "0:1", before, nowMs = 0)
        val second = TrackChangeRules.start(first, TrackKind.AUDIO, "0:2", before, nowMs = 500)
        assertThat(second).isEqualTo(TrackChange.Pending(TrackKind.AUDIO, "0:2", "0:0", 500))

        // Going back to what plays waits for nothing.
        assertThat(TrackChangeRules.start(second, TrackKind.AUDIO, "0:0", before, nowMs = 600))
            .isEqualTo(TrackChange.Idle)
    }

    @Test
    fun `a manual quality is confirmed only once it is the one variant playing`() {
        val asked = before.copy(qualityAuto = false)
        val pending = TrackChangeRules.start(TrackChange.Idle, TrackKind.QUALITY, "1:1", before, nowMs = 0)
        assertThat((pending as TrackChange.Pending).previous).isNull()

        // Requested but Media3 still adapts across both: not yet.
        assertThat(pending.onOptions(asked)).isEqualTo(pending)
        val pinned = asked.copy(qualities = listOf(quality("1:0", applied = false), quality("1:1", applied = true)))
        assertThat(pending.onOptions(pinned)).isEqualTo(TrackChange.Idle)
    }

    @Test
    fun `a new stream drops whatever was pending or failed`() {
        val pending = TrackChangeRules.start(TrackChange.Idle, TrackKind.AUDIO, "0:1", before, nowMs = 0)
        assertThat(pending.onOptions(TrackOptions())).isEqualTo(TrackChange.Idle)
        assertThat(pending.onFailure().onOptions(TrackOptions())).isEqualTo(TrackChange.Idle)
    }

    private fun audio(id: String, selected: Boolean) = AudioTrack(
        id = id, language = null, label = null, mimeType = null, channelCount = null,
        selected = selected, playable = true,
    )

    private fun text(id: String, selected: Boolean) = SubtitleTrack(
        id = id, language = null, label = null, mimeType = null, selected = selected, playable = true,
    )

    private fun quality(id: String, applied: Boolean) = VideoQuality(
        id = id, width = null, height = 720, bitrate = null, selected = false, playable = true, applied = applied,
    )
}
