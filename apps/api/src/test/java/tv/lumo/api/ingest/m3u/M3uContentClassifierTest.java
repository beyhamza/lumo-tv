package tv.lumo.api.ingest.m3u;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tv.lumo.api.generated.model.ContentType;

/**
 * Telling a film from a channel in a playlist (ADR 0009).
 *
 * <h2>This test says how the rule is wrong, not that it is right</h2>
 *
 * Rule 3 of the ADR asks for exactly that, and it is not modesty. The rule is a
 * heuristic over a format that declares nothing; it <em>will</em> misfile things.
 * A test listing only the cases it gets right would be a test that pretends
 * otherwise, and the next person to touch it would have no idea which way the
 * errors were meant to fall.
 *
 * <p>So the two sections below are the two directions of error, each with the
 * reasoning that made it acceptable.
 */
class M3uContentClassifierTest {

    @ParameterizedTest
    @DisplayName("a file extension or a /movie/ segment makes it a film")
    @CsvSource({
        "http://panel.example:8080/movie/user/pass/501.mkv",
        "http://panel.example:8080/movie/user/pass/501.m3u8",
        "http://cdn.example/vod/le-voyage.mp4",
        "http://cdn.example/vod/le-voyage.AVI",
        "http://cdn.example/films/x.m4v?token=abc123",
        "http://cdn.example/films/x.mov#t=0",
    })
    void structuralSignalsMakeAFilm(String url) {
        assertThat(M3uContentClassifier.classify(url)).isEqualTo(ContentType.VOD);
    }

    @ParameterizedTest
    @DisplayName("everything else is a channel, extension or not")
    @CsvSource({
        "http://panel.example:8080/live/user/pass/101.m3u8",
        "http://panel.example:8080/user/pass/101.ts",
        "http://cdn.example/hls/channel-01",
        "http://cdn.example/hls/channel-01?token=abc.mp4x",
        // A dot in the host, none in the path: `vod.example` is a hostname.
        "http://vod.example/live/1",
    })
    void everythingElseIsAChannel(String url) {
        assertThat(M3uContentClassifier.classify(url)).isEqualTo(ContentType.LIVE);
    }

    // ---- how it is wrong, on purpose ----------------------------------------

    @Test
    @DisplayName("a live channel served as a progressive .mp4 is filed as a film")
    void aProgressiveLiveChannelIsMisfiled() {
        // The residual risk ADR 0009 accepts rather than hides. It is rare, and the
        // way out is the per-category override the ADR gives a shape and a price
        // to — not a cleverer rule here.
        assertThat(M3uContentClassifier.classify("http://cdn.example/live/news.mp4"))
                .isEqualTo(ContentType.VOD);
    }

    @Test
    @DisplayName("a film in a group called Films, served as .m3u8, stays a channel")
    void aFilmInAFilmGroupStaysAChannel() {
        // Doubt falls towards LIVE, and this is what that costs: the film is in the
        // wrong list. It still plays and search still finds it. The other direction
        // would have put it in a poster grid with no poster, next to a "resume at
        // 20 min" that means nothing.
        //
        // Note what is *not* passed to this method: the group title. It classifies
        // nothing, because `CINE+`, `Film4` and `VOD Sports News` are all names of
        // live channels.
        assertThat(M3uContentClassifier.classify("http://cdn.example/vod/le-voyage.m3u8"))
                .isEqualTo(ContentType.LIVE);
    }

    @Test
    @DisplayName("an entry with neither extension nor path segment is a channel")
    void nothingToGoOnMeansChannel() {
        assertThat(M3uContentClassifier.classify("http://cdn.example/stream/9182"))
                .isEqualTo(ContentType.LIVE);
        // And so is nothing at all, rather than an exception on an ingestion that
        // has fifteen thousand more entries to read.
        assertThat(M3uContentClassifier.classify(null)).isEqualTo(ContentType.LIVE);
        assertThat(M3uContentClassifier.classify("")).isEqualTo(ContentType.LIVE);
    }

    @Test
    @DisplayName("a query string is not part of the path, so its dots do not count")
    void queryStringsAreCutOff() {
        // Real playlists carry tokens and session ids, and a token can end in
        // anything at all. Reading the extension off the whole URL would classify
        // on somebody's session id.
        assertThat(M3uContentClassifier.classify("http://cdn.example/hls/1?session=x.mkv"))
                .isEqualTo(ContentType.LIVE);
    }
}
