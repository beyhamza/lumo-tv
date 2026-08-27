package tv.lumo.android.core.data.model

import tv.lumo.android.core.common.log.Redact

/**
 * What the player needs to open one channel, and nothing that outlives it.
 *
 * <h2>This type exists for one reason: the generated model prints the URL</h2>
 *
 * The contract types `stream_url` as `format: password` and says so in as many
 * words — *"`password` makes every generator mask this property in
 * `toString()`"*. That is true of the Java generator, which emits
 * `streamUrl: *`. It is **not** true of the Kotlin one: `PlaybackInfo` comes out
 * as a plain `data class`, and a `data class` prints every property it has.
 *
 * A stream URL carries the user's panel credentials in its path on most Xtream
 * providers, and AGENTS.md §5 forbids it from reaching a log line at any level.
 * A data class ends up in a crash report sooner or later — the same argument
 * that gave `SessionTokens` its own `toString`, and the same answer here.
 *
 * So the generated model never leaves this module, and what a player receives is
 * this: the same fields, with a [toString] that cannot leak.
 *
 * The gap itself is worth closing upstream rather than only working around; it
 * is written up in `docs/design/api-gaps.md`.
 *
 * <h2>Not cached, ever</h2>
 *
 * Issued per playback by `GET /channels/{id}/playback` and dropped afterwards.
 * Nothing here is written to Room — a replayable credential in a database that
 * survives on the device and lands in `adb backup` output would be a poor trade
 * for saving one request per channel actually watched.
 */
data class PlaybackTarget(
    val channelId: String,
    /** **Sensitive.** Opened by the player, never logged, never persisted. */
    val streamUrl: String,
    /** The User-Agent the source requires, or null for the player's default. */
    val userAgent: String?,
    /**
     * Simultaneous streams the *user's own* subscription allows.
     *
     * Carried so the player can say which subscription refused a stream: the
     * limit is their provider's, not Lumo's, and a message that does not make
     * that clear turns their provider's rule into our bug (US-09).
     */
    val maxConnections: Int?,
    /** Epoch milliseconds, or null when the panel issues no expiry. */
    val expiresAtMillis: Long?,
) {
    override fun toString(): String =
        "PlaybackTarget(channelId=$channelId, streamUrl=${Redact.url(streamUrl)}, " +
            "userAgent=$userAgent, maxConnections=$maxConnections, " +
            "expiresAtMillis=$expiresAtMillis)"
}
