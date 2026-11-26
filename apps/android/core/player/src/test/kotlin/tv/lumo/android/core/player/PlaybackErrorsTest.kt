package tv.lumo.android.core.player

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** S16B-10: a web page served instead of a stream is said as such, not as a decoder problem. */
class PlaybackErrorsTest {

    @Test
    fun `an HLS playlist that turns out to be a web page is not a stream`() {
        assertThat(
            playbackErrorFor(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, "text/html"),
        ).isEqualTo(PlaybackError.NOT_A_STREAM)
    }

    @Test
    fun `a film no extractor recognises, served as a web page, is not a stream`() {
        assertThat(
            playbackErrorFor(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, "text/html; charset=UTF-8"),
        ).isEqualTo(PlaybackError.NOT_A_STREAM)
    }

    @Test
    fun `a malformed container served as media stays unplayable`() {
        assertThat(
            playbackErrorFor(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, "video/mp2t"),
        ).isEqualTo(PlaybackError.UNPLAYABLE)
        assertThat(
            playbackErrorFor(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, null),
        ).isEqualTo(PlaybackError.UNPLAYABLE)
    }

    @Test
    fun `a decoder failure is unplayable whatever the last response was`() {
        assertThat(
            playbackErrorFor(PlaybackException.ERROR_CODE_DECODING_FAILED, "text/html"),
        ).isEqualTo(PlaybackError.UNPLAYABLE)
    }

    @Test
    fun `network and HTTP failures keep their meaning`() {
        assertThat(playbackErrorFor(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null))
            .isEqualTo(PlaybackError.UNREACHABLE)
        assertThat(playbackErrorFor(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "text/html"))
            .isEqualTo(PlaybackError.REFUSED)
        assertThat(playbackErrorFor(PlaybackException.ERROR_CODE_TIMEOUT, null))
            .isEqualTo(PlaybackError.UNKNOWN)
    }

    @Test
    fun `only text html counts as a web page`() {
        assertThat(isWebPage("TEXT/HTML")).isTrue()
        assertThat(isWebPage("text/plain")).isFalse()
        assertThat(isWebPage("application/x-mpegurl")).isFalse()
        assertThat(isWebPage("application/octet-stream")).isFalse()
        assertThat(isWebPage(null)).isFalse()
    }

    @Test
    fun `the last response reads its type whatever the header case`() {
        val last = LastResponse()

        last.record(mapOf("content-type" to listOf("text/html")))
        assertThat(last.contentType).isEqualTo("text/html")

        last.clear()
        assertThat(last.contentType).isNull()
    }
}
