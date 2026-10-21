package tv.lumo.android.core.player.cast

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** S16-01, sprint 16 decisions 3 and 6. Fixture URLs only: no real stream (AGENTS.md §1). */
class CastRulesTest {

    @Test
    fun `the extension of the path decides the container`() {
        assertThat(CastRules.containerOf("https://bench.test/stream/index.m3u8", isLive = true)).isEqualTo(CastContainer.HLS)
        assertThat(CastRules.containerOf("https://bench.test/film/a.mp4", isLive = false)).isEqualTo(CastContainer.MP4)
        assertThat(CastRules.containerOf("https://bench.test/film/a.M4V", isLive = false)).isEqualTo(CastContainer.MP4)
        assertThat(CastRules.containerOf("https://bench.test/film/a.mkv", isLive = false)).isEqualTo(CastContainer.MATROSKA)
        assertThat(CastRules.containerOf("http://bench.test/live/u/p/1.ts", isLive = true)).isEqualTo(CastContainer.MPEG_TS)
    }

    @Test
    fun `the query is ignored, a token after the extension included`() {
        assertThat(CastRules.containerOf("https://bench.test/movie/1.mkv?token=abc.m3u8", isLive = false))
            .isEqualTo(CastContainer.MATROSKA)
        assertThat(CastRules.containerOf("https://bench.test/live/1?format=x.ts", isLive = true))
            .isEqualTo(CastContainer.HLS)
        assertThat(CastRules.containerOf("https://bench.test/film/a.mp4#t=10", isLive = false))
            .isEqualTo(CastContainer.MP4)
    }

    @Test
    fun `no extension is HLS for a channel and MP4 for a film or an episode`() {
        assertThat(CastRules.containerOf("http://bench.test/live/u/p/1", isLive = true)).isEqualTo(CastContainer.HLS)
        assertThat(CastRules.containerOf("http://bench.test/movie/u/p/1", isLive = false)).isEqualTo(CastContainer.MP4)
        // A dot in the host is not an extension.
        assertThat(CastRules.containerOf("http://bench.test", isLive = false)).isEqualTo(CastContainer.MP4)
        assertThat(CastRules.containerOf("http://bench.test/", isLive = true)).isEqualTo(CastContainer.HLS)
    }

    @Test
    fun `mime types are the ones the Default Media Receiver documents`() {
        assertThat(CastContainer.HLS.mimeType).isEqualTo("application/x-mpegurl")
        assertThat(CastContainer.MP4.mimeType).isEqualTo("video/mp4")
        assertThat(CastContainer.MATROSKA.mimeType).isEqualTo("video/x-matroska")
        assertThat(CastContainer.MPEG_TS.mimeType).isEqualTo("video/mp2t")
    }

    @Test
    fun `the button appears with a device and a stream that may be sent`() {
        assertThat(show()).isTrue()
        assertThat(show(devices = false, connected = true)).isTrue()
    }

    @Test
    fun `no button without Play services, without a device, or before the stream is known`() {
        assertThat(show(supported = false)).isFalse()
        assertThat(show(devices = false)).isFalse()
        assertThat(show(streamKnown = false)).isFalse()
    }

    @Test
    fun `no button for a source that needs its own User-Agent`() {
        assertThat(show(needsUserAgent = true)).isFalse()
        assertThat(show(connected = true, needsUserAgent = true)).isFalse()
    }

    private fun show(
        supported: Boolean = true,
        devices: Boolean = true,
        connected: Boolean = false,
        streamKnown: Boolean = true,
        needsUserAgent: Boolean = false,
    ) = CastRules.showButton(supported, devices, connected, streamKnown, needsUserAgent)
}
