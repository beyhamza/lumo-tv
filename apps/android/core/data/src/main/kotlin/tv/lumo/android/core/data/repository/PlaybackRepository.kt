package tv.lumo.android.core.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.map
import tv.lumo.android.core.data.model.PlaybackTarget
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.model.PlaybackInfo
import tv.lumo.android.network.generated.model.RecordRecentChannelRequest

/**
 * Opening a channel, and remembering it was opened.
 *
 * <h2>One request per playback, and nothing kept</h2>
 *
 * `GET /channels/{id}/playback` is the only operation in the whole contract that
 * emits a stream URL, and that is not an accident of design: on most Xtream
 * panels the URL carries the user's username and password in its path. It is
 * requested at the moment of playing, handed to the player, and dropped.
 *
 * Nothing here writes to Room. Caching a stream URL would put a replayable
 * credential in a database that survives on the device and lands in `adb backup`
 * output, in exchange for saving one request per channel actually watched.
 *
 * <h2>The three refusals a screen must tell apart</h2>
 *
 * All three arrive as `409`, which is exactly why the contract says to branch on
 * the code and never on the status:
 *
 * - `SOURCE_NOT_READY` — the import has not finished. Wait, do not re-register.
 * - `SOURCE_EXPIRED` — the user's subscription with *their* provider ran out.
 * - `SOURCE_MAX_CONNECTIONS` — *their* subscription caps simultaneous streams.
 *
 * The last two are the user's provider saying no. A message that does not make
 * that clear turns their provider's rule into our bug (US-09), which is why
 * [PlaybackTarget.maxConnections] is carried all the way to the player.
 */
@Singleton
class PlaybackRepository @Inject internal constructor(
    private val catalog: CatalogApi,
    private val userdata: UserdataApi,
    private val calls: ApiCaller,
) {

    suspend fun playbackTarget(channelId: String): LumoResult<PlaybackTarget> =
        calls.call { catalog.getChannelPlayback(UUID.fromString(channelId)) }
            .map(PlaybackInfo::asTarget)

    /**
     * Records that a channel was actually watched.
     *
     * **Called when playback starts, never when a channel is focused.** On a
     * television the D-pad crosses twenty channels on its way to one, and a
     * "recently watched" rail built from what the remote passed over is not a
     * convenience — it is the user's own history, made worse.
     *
     * The result is deliberately returned rather than swallowed so a caller can
     * log it, but there is nothing here for a user to act on: this is a side
     * effect of watching television, not a step of it, and a player that
     * interrupted itself to report a failed bookkeeping call would be worse than
     * a rail that misses an entry.
     */
    suspend fun recordWatched(channelId: String): LumoResult<Unit> =
        calls.call {
            userdata.recordRecentChannel(
                RecordRecentChannelRequest(channelId = UUID.fromString(channelId)),
            )
        }.map { }
}

private fun PlaybackInfo.asTarget() = PlaybackTarget(
    channelId = channelId.toString(),
    streamUrl = streamUrl,
    userAgent = userAgent,
    maxConnections = maxConnections,
    // Epoch milliseconds rather than an OffsetDateTime: the player compares it
    // against a clock, and every screen that shows it formats it anyway.
    expiresAtMillis = expiresAt?.toInstant()?.toEpochMilli(),
)

