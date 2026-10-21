package tv.lumo.android.core.player.cast

/**
 * The container a Cast receiver is told to expect (S16-01, sprint 16 decision 3).
 *
 * The contract carries no format hint in 0.4.0, so the sender guesses from the
 * extension of the URL's **path**: the query is ignored, because Xtream panels put
 * a token after the extension and a `?token=…m3u8` must not be read as HLS.
 * MKV and raw MPEG-TS are *attempted* — the Default Media Receiver documents
 * neither outside HLS (S15-00 rows 8 and 9) — and a refusal is then named like any
 * other (S16-05).
 */
enum class CastContainer(val mimeType: String) {
    HLS("application/x-mpegurl"),
    MP4("video/mp4"),
    MATROSKA("video/x-matroska"),
    MPEG_TS("video/mp2t"),
}

object CastRules {

    /**
     * Decision 3: `.m3u8` → HLS, `.mp4`/`.m4v` → MP4, `.mkv` → Matroska, `.ts` →
     * MPEG-TS; anything else is HLS for a channel and MP4 for a film or an episode.
     */
    fun containerOf(streamUrl: String, isLive: Boolean): CastContainer {
        val path = streamUrl
            .substringBefore('#')
            .substringBefore('?')
            .substringAfter("://", missingDelimiterValue = streamUrl)
            .substringAfter('/', missingDelimiterValue = "")
        val extension = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "")
        return when (extension.lowercase()) {
            "m3u8", "m3u" -> CastContainer.HLS
            "mp4", "m4v" -> CastContainer.MP4
            "mkv" -> CastContainer.MATROSKA
            "ts" -> CastContainer.MPEG_TS
            else -> if (isLive) CastContainer.HLS else CastContainer.MP4
        }
    }

    /**
     * Whether the player draws a Cast button (S16-01).
     *
     * - no Google Play services, or a television: never;
     * - nothing on the network and no session: never — the button appears when a
     *   device is detected, and only then;
     * - a stream that needs its own User-Agent: never, the receiver sends its own
     *   and cannot be told otherwise (decision 6);
     * - before the stream is known: not yet, or a source with a User-Agent would
     *   show the button for the second it takes to ask for its address.
     */
    fun showButton(
        castSupported: Boolean,
        devicesAvailable: Boolean,
        sessionConnected: Boolean,
        streamKnown: Boolean,
        needsUserAgent: Boolean,
    ): Boolean = castSupported &&
        (devicesAvailable || sessionConnected) &&
        streamKnown &&
        !needsUserAgent
}
