package tv.lumo.android.core.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * What a Media3 error means for the viewer.
 *
 * [lastContentType] is the `Content-Type` of the last HTTP response the player
 * opened, or null. It is what tells a stream this device cannot read apart from
 * a server that sent no stream at all (S16B-10): a panel blocked by the viewer's
 * internet provider, or one that answers with its own error page, serves HTML —
 * redirects included — and the extractors then report it as a malformed or
 * unknown container. Worded as a decoder problem, that sends somebody looking at
 * their television when the answer is on the network.
 */
internal fun playbackErrorFor(errorCode: Int, lastContentType: String?): PlaybackError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> PlaybackError.UNREACHABLE

    // A panel that has run out of allowed simultaneous connections answers with
    // an HTTP error, not with a network failure — which is why US-09 wants this
    // case worded as a subscription limit rather than as an outage.
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
    -> PlaybackError.REFUSED

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    -> if (isWebPage(lastContentType)) PlaybackError.NOT_A_STREAM else PlaybackError.UNPLAYABLE

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    -> PlaybackError.UNPLAYABLE

    else -> PlaybackError.UNKNOWN
}

/**
 * Whether a response was a web page.
 *
 * `text/html` only. Panels label their playlists `text/plain`,
 * `application/octet-stream` or nothing at all, and none of those says the
 * answer was not media; a page is the one label that does.
 */
internal fun isWebPage(contentType: String?): Boolean =
    contentType?.substringBefore(';')?.trim()?.equals("text/html", ignoreCase = true) == true

/**
 * The `Content-Type` of the last HTTP response the player opened.
 *
 * Read only once playback has failed, and only to word the failure — never to
 * decide what to play. Volatile because Media3 opens sources on its loader
 * threads and reports errors on its playback thread.
 */
internal class LastResponse {
    @Volatile
    var contentType: String? = null
        private set

    fun record(headers: Map<String, List<String>>) {
        contentType = headers.entries
            .firstOrNull { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.lastOrNull()
    }

    fun clear() {
        contentType = null
    }
}

/** Every source made by [upstream], each noting its response in [last] once opened. */
@OptIn(UnstableApi::class)
internal class RecordingDataSourceFactory(
    private val upstream: DataSource.Factory,
    private val last: LastResponse,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RecordingDataSource(upstream.createDataSource(), last)
}

@OptIn(UnstableApi::class)
private class RecordingDataSource(
    private val delegate: DataSource,
    private val last: LastResponse,
) : DataSource {

    override fun open(dataSpec: DataSpec): Long {
        val length = delegate.open(dataSpec)
        last.record(delegate.responseHeaders)
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    override fun addTransferListener(transferListener: TransferListener) =
        delegate.addTransferListener(transferListener)

    override fun getUri() = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() = delegate.close()
}
