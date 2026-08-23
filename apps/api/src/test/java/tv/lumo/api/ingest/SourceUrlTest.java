package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tv.lumo.api.generated.model.IngestionErrorCode;

/**
 * URL parsing for user-supplied sources.
 *
 * <p>This is a security test before it is a validation test. The host produced
 * here is the value ingestion keys its semaphore on and writes to the logs; the
 * URL it came from must never follow it there (AGENTS.md §5), and IPTV
 * playlists routinely carry credentials in their path, their query or their
 * userinfo.
 */
class SourceUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://panel.example.org/playlist.m3u",
            "https://panel.example.org/playlist.m3u",
            "https://panel.example.org:8080/get.php?username=u&password=p&type=m3u",
            "https://user:secret@panel.example.org/playlist.m3u",
            "HTTPS://PANEL.EXAMPLE.ORG/playlist.m3u",
            "  https://panel.example.org/playlist.m3u  ",
    })
    @DisplayName("accepts an absolute http(s) URL and yields only its host")
    void acceptsFetchableUrls(String url) {
        assertThat(SourceUrl.hostOf(SourceUrl.parse(url)))
                // Lower-cased by URI itself; credentials, port, path and query
                // are all absent from what a caller is handed to log.
                .isEqualTo("panel.example.org");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // The case that made this class exist: no scheme, so URI parses it as
            // a relative reference with a null host — and the old code logged the
            // whole thing as the "host".
            "panel.example.org/live/someuser/somepass/1234.ts",
            "//panel.example.org/playlist.m3u",
            "/live/someuser/somepass/1234.ts",
            "playlist.m3u",
            "ftp://panel.example.org/playlist.m3u",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "http://",
            "not a url at all",
    })
    @DisplayName("refuses anything ingestion could not fetch")
    void refusesUnfetchableUrls(String url) {
        assertThatThrownBy(() -> SourceUrl.parse(url))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).code())
                .isEqualTo(IngestionErrorCode.SOURCE_INVALID_FORMAT);
    }

    @Test
    @DisplayName("refuses a missing or blank URL")
    void refusesNothing() {
        assertThatThrownBy(() -> SourceUrl.parse(null)).isInstanceOf(IngestionException.class);
        assertThatThrownBy(() -> SourceUrl.parse("   ")).isInstanceOf(IngestionException.class);
    }

    @Test
    @DisplayName("the rejection never echoes the URL back, not in the message and not in a cause")
    void rejectionCarriesNoUrl() {
        String credentialBearing = "panel.example.org/live/someuser/somepass/1234.ts";

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> SourceUrl.parse(credentialBearing));

        // The message reaches both the logs and the client, as problem+json
        // `detail`.
        assertThat(thrown).hasMessageNotContaining("someuser")
                .hasMessageNotContaining("somepass")
                .hasMessageNotContaining("panel.example.org");

        // And the cause is dropped rather than chained: URI.create quotes the
        // offending URL in its own message, which any log call taking a
        // throwable would print in full.
        assertThat(thrown.getCause()).isNull();
    }

    @Test
    @DisplayName("a URL with credentials in the path still reduces to its host")
    void credentialsInThePathDoNotReachTheHost() {
        String host = SourceUrl.hostOf(
                SourceUrl.parse("http://panel.example.org:8080/live/someuser/somepass/1234.ts"));

        assertThat(host).isEqualTo("panel.example.org");
        assertThat(host).doesNotContain("someuser").doesNotContain("somepass");
    }
}
