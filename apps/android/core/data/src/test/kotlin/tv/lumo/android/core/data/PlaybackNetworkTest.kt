package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * S13-05: the Wi-Fi only gate — Q7's table, and what a player screen does when
 * the network turns during playback (PS-08 to PS-12).
 */
class PlaybackNetworkTest {

    private val wifi = NetworkFacts(wifi = true, validated = true)
    private val cellular = NetworkFacts(cellular = true, validated = true)

    @Test
    fun `mobile data allowed lets every launch through`() {
        assertThat(playbackVerdict(true, cellular)).isEqualTo(NetworkVerdict.ALLOWED)
        assertThat(playbackVerdict(true, null)).isEqualTo(NetworkVerdict.ALLOWED)
    }

    @Test
    fun `Q7 with mobile data off`() {
        // Wi-Fi, metered or not: metering is not read.
        assertThat(playbackVerdict(false, wifi)).isEqualTo(NetworkVerdict.ALLOWED)
        // A VPN over Wi-Fi carries both transports.
        assertThat(playbackVerdict(false, wifi.copy(vpn = true))).isEqualTo(NetworkVerdict.ALLOWED)
        assertThat(playbackVerdict(false, cellular)).isEqualTo(NetworkVerdict.WIFI_REQUIRED)
        assertThat(playbackVerdict(false, NetworkFacts(ethernet = true, validated = true)))
            .isEqualTo(NetworkVerdict.WIFI_REQUIRED)
        assertThat(playbackVerdict(false, cellular.copy(vpn = true))).isEqualTo(NetworkVerdict.WIFI_UNCONFIRMED)
        assertThat(playbackVerdict(false, NetworkFacts(vpn = true, validated = true)))
            .isEqualTo(NetworkVerdict.WIFI_UNCONFIRMED)
        assertThat(playbackVerdict(false, NetworkFacts(validated = true))).isEqualTo(NetworkVerdict.WIFI_UNCONFIRMED)
        // Captive portal: Wi-Fi, not validated. No silent switch to mobile (PS-10).
        assertThat(playbackVerdict(false, NetworkFacts(wifi = true))).isEqualTo(NetworkVerdict.NO_CONNECTION)
        assertThat(playbackVerdict(false, null)).isEqualTo(NetworkVerdict.NO_CONNECTION)
    }

    @Test
    fun `a blocked launch asks for nothing and says why`() = runTest {
        val gate = FakeGate(NetworkVerdict.WIFI_REQUIRED)
        val guard = PlaybackNetworkGuard(gate)

        assertThat(guard.admit()).isFalse()
        assertThat(guard.block.value).isEqualTo(NetworkBlock(NetworkVerdict.WIFI_REQUIRED, interrupted = false))
    }

    @Test
    fun `Wi-Fi lost while playing stops the stream once, then Wi-Fi back offers Resume without restarting`() = runTest {
        val gate = FakeGate(NetworkVerdict.ALLOWED)
        val guard = PlaybackNetworkGuard(gate)
        var interruptions = 0
        guard.watch(backgroundScope) { interruptions++ }

        assertThat(guard.admit()).isTrue()
        runCurrent()
        assertThat(interruptions).isEqualTo(0)

        gate.current.value = NetworkVerdict.WIFI_REQUIRED
        runCurrent()
        assertThat(interruptions).isEqualTo(1)
        assertThat(guard.block.value?.interrupted).isTrue()

        gate.current.value = NetworkVerdict.ALLOWED
        runCurrent()
        // Back, but nothing restarted by itself (Q7): the screen offers Resume.
        assertThat(interruptions).isEqualTo(1)
        assertThat(guard.block.value?.networkBack).isTrue()

        // Lost again before Resume: the offer is withdrawn.
        gate.current.value = NetworkVerdict.WIFI_REQUIRED
        runCurrent()
        assertThat(guard.block.value?.networkBack).isFalse()

        // Resume checks again, and only an allowed network lifts the block.
        gate.current.value = NetworkVerdict.ALLOWED
        runCurrent()
        assertThat(guard.admit()).isTrue()
        assertThat(guard.block.value).isNull()
    }

    @Test
    fun `turning mobile data off during a mobile playback interrupts it at once (PS-11)`() = runTest {
        // Mobile data allowed, on cellular: playing.
        val gate = FakeGate(NetworkVerdict.ALLOWED)
        val guard = PlaybackNetworkGuard(gate)
        var interruptions = 0
        guard.watch(backgroundScope) { interruptions++ }
        guard.admit()
        runCurrent()

        // The preference flips: the gate's verdict changes with it.
        gate.current.value = NetworkVerdict.WIFI_REQUIRED
        runCurrent()

        assertThat(interruptions).isEqualTo(1)
    }

    @Test
    fun `nothing playing, nothing to interrupt`() = runTest {
        val gate = FakeGate(NetworkVerdict.ALLOWED)
        val guard = PlaybackNetworkGuard(gate)
        var interruptions = 0
        guard.watch(backgroundScope) { interruptions++ }
        guard.admit()
        guard.idle()

        gate.current.value = NetworkVerdict.WIFI_REQUIRED
        runCurrent()

        assertThat(interruptions).isEqualTo(0)
        assertThat(guard.block.value).isNull()
    }

    @Test
    fun `the panel's words follow the block`() {
        assertThat(NetworkBlock(NetworkVerdict.WIFI_REQUIRED, false).titleRes)
            .isEqualTo(R.string.core_data_network_wifi_required)
        assertThat(NetworkBlock(NetworkVerdict.WIFI_UNCONFIRMED, false).bodyRes)
            .isEqualTo(R.string.core_data_network_unconfirmed_body)
        assertThat(NetworkBlock(NetworkVerdict.NO_CONNECTION, true).titleRes)
            .isEqualTo(R.string.core_data_network_no_connection)
        val back = NetworkBlock(NetworkVerdict.WIFI_REQUIRED, true, networkBack = true)
        assertThat(back.titleRes).isEqualTo(R.string.core_data_network_back)
        assertThat(back.bodyRes).isNull()
    }

    private class FakeGate(initial: NetworkVerdict) : PlaybackNetworkGate {
        val current = MutableStateFlow(initial)
        override suspend fun verdict(): NetworkVerdict = current.value
        override val verdicts = current
    }
}
