package tv.lumo.android.core.player.cast

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** S16-05: the four refusal signals of the S15-00 report, and what is not a refusal. */
class CastSignalsTest {

    @Test
    fun `signal 1 - a LOAD answered with a failure is a refusal`() {
        assertThat(verdict(CastSignal.LoadAnswered(2100))).isEqualTo(CastVerdict.Refused("load:2100"))
        assertThat(verdict(CastSignal.LoadAnswered(2104))).isEqualTo(CastVerdict.Refused("load:2104"))
    }

    @Test
    fun `a LOAD accepted, cancelled or replaced by another says nothing`() {
        listOf(CastSignals.SUCCESS, CastSignals.CANCELED, CastSignals.REPLACED, CastSignals.INTERRUPTED).forEach {
            assertThat(verdict(CastSignal.LoadAnswered(it))).isEqualTo(CastVerdict.Ignore)
        }
    }

    @Test
    fun `signal 2 - a detailed receiver error is a refusal, its code kept for the log`() {
        assertThat(verdict(CastSignal.MediaError(104, "LOAD_FAILED")))
            .isEqualTo(CastVerdict.Refused("media:LOAD_FAILED:104"))
        assertThat(verdict(CastSignal.MediaError(3016404, "ERROR")))
            .isEqualTo(CastVerdict.Refused("media:ERROR:3016404"))
        assertThat(verdict(CastSignal.MediaError(null, null))).isEqualTo(CastVerdict.Refused("media:?:?"))
    }

    @Test
    fun `904 LOAD_INTERRUPTED is not a refusal`() {
        assertThat(verdict(CastSignal.MediaError(904, "ERROR"))).isEqualTo(CastVerdict.Ignore)
    }

    @Test
    fun `signal 3 - idle on error is a refusal, before or after a start`() {
        assertThat(verdict(CastSignal.WentIdle(RemoteIdleReason.ERROR), started = false))
            .isInstanceOf(CastVerdict.Refused::class.java)
        assertThat(verdict(CastSignal.WentIdle(RemoteIdleReason.ERROR), started = true))
            .isInstanceOf(CastVerdict.Refused::class.java)
    }

    @Test
    fun `idle finished after playing is the end, before playing a refusal`() {
        assertThat(verdict(CastSignal.WentIdle(RemoteIdleReason.FINISHED), started = true)).isEqualTo(CastVerdict.Finished)
        assertThat(verdict(CastSignal.WentIdle(RemoteIdleReason.FINISHED), started = false))
            .isInstanceOf(CastVerdict.Refused::class.java)
    }

    @Test
    fun `idle cancelled is a stop from elsewhere once playing, interrupted is a new LOAD`() {
        assertThat(verdict(CastSignal.WentIdle(RemoteIdleReason.CANCELED), started = true))
            .isEqualTo(CastVerdict.StoppedElsewhere)
        assertThat(verdict(CastSignal.WentIdle(RemoteIdleReason.INTERRUPTED), started = true)).isEqualTo(CastVerdict.Ignore)
        assertThat(verdict(CastSignal.WentIdle(RemoteIdleReason.NONE))).isEqualTo(CastVerdict.Ignore)
    }

    @Test
    fun `signal 4 - silence is a refusal only while nothing has played`() {
        assertThat(verdict(CastSignal.Silence, started = false)).isEqualTo(CastVerdict.Refused("silence"))
        assertThat(verdict(CastSignal.Silence, started = true)).isEqualTo(CastVerdict.Ignore)
        assertThat(CastSignals.SILENCE_TIMEOUT_MS).isEqualTo(15_000L)
    }

    @Test
    fun `the picker closed without a choice is not a refusal`() {
        assertThat(verdict(CastSignal.SessionStartFailed(CastSignals.CANCELED))).isEqualTo(CastVerdict.Ignore)
        assertThat(verdict(CastSignal.SessionStartFailed(15))).isEqualTo(CastVerdict.Ignore)
    }

    private fun verdict(signal: CastSignal, started: Boolean = false) = CastSignals.verdict(signal, started)
}
