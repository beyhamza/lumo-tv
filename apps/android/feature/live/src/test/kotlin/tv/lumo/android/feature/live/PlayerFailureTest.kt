package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.model.PlaybackTarget
import tv.lumo.android.core.player.PlaybackError
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * What the player says when a channel will not open (US-09).
 *
 * The acceptance criterion is unusually blunt — *"the application does not crash
 * and does not sit on a silent black screen"* — and a silent black screen is
 * exactly what an unhandled player error looks like. So every way this can fail
 * is walked here, from both sides: the contract's refusals before playback
 * starts, and the player's own once it has.
 */
class PlayerFailureTest {

    @Test
    fun `the three refusals that share a 409 are three different sentences`() {
        // The contract collapses them onto one status on purpose and says to
        // branch on the code. They send the user to three different places: wait,
        // stop another device, or renew with the provider.
        assertThat(api(ErrorCode.SOURCE_NOT_READY)).isEqualTo(PlayerFailure.SourceNotReady)
        assertThat(api(ErrorCode.SOURCE_MAX_CONNECTIONS))
            .isEqualTo(PlayerFailure.TooManyStreams(null))
        assertThat(api(ErrorCode.SOURCE_EXPIRED)).isEqualTo(PlayerFailure.SubscriptionExpired)
    }

    @Test
    fun `a channel dropped by the last sync is named as such`() {
        assertThat(api(ErrorCode.CHANNEL_NOT_FOUND)).isEqualTo(PlayerFailure.ChannelGone)
    }

    @Test
    fun `a refused stream carries the user's own ceiling when the panel gave one`() {
        // A panel out of allowed connections answers with an HTTP error rather
        // than a network failure, which is why this is worded as a subscription
        // limit and not as an outage. The number comes from the target the server
        // already handed us, so the sentence can name it.
        assertThat(PlaybackError.REFUSED.asPlayerFailure(target(maxConnections = 2)))
            .isEqualTo(PlayerFailure.TooManyStreams(2))

        // And says it without a figure rather than inventing one when it did not.
        assertThat(PlaybackError.REFUSED.asPlayerFailure(target(maxConnections = null)))
            .isEqualTo(PlayerFailure.TooManyStreams(null))
    }

    @Test
    fun `a stream that cannot be decoded is not offered a retry`() {
        // Nothing about this device changes between two attempts. A retry here
        // costs a wait to relearn what the sentence already said.
        assertThat(PlaybackError.UNPLAYABLE.asPlayerFailure(null))
            .isEqualTo(PlayerFailure.Unplayable)
    }

    @Test
    fun `no network is its own case, and is the one worth retrying unchanged`() {
        assertThat(LumoError.Offline(IOException("no route")).asPlayerFailure())
            .isEqualTo(PlayerFailure.Unreachable())
        assertThat(PlaybackError.UNREACHABLE.asPlayerFailure(null))
            .isEqualTo(PlayerFailure.Unreachable())
    }

    @Test
    fun `a code newer than this build degrades rather than crashing`() {
        // The contract allows new codes within v1 and requires graceful
        // degradation. The `else` branch is deliberate, and this is what says so.
        assertThat(LumoError.UnknownCode("STREAM_GEOBLOCKED").asPlayerFailure())
            .isEqualTo(PlayerFailure.Unexpected)
    }

    private fun api(code: ErrorCode) = LumoError.Api(code, detail = null).asPlayerFailure()

    private fun target(maxConnections: Int?) = PlaybackTarget(
        channelId = "channel",
        streamUrl = "http://panel.example.test/live/user/pass/1.ts",
        userAgent = null,
        maxConnections = maxConnections,
        expiresAtMillis = null,
    )
}
