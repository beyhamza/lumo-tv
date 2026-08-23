package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Host normalisation (US-06): tolerate what a human pastes out of a provider
 * email, normalise rather than reject.
 */
class HostNormaliserTest {

    @ParameterizedTest
    @DisplayName("accepts a host with or without scheme, port or trailing slash")
    @ValueSource(strings = {
            "panel.example.org:8080",
            "http://panel.example.org:8080",
            "http://panel.example.org:8080/",
            "  http://panel.example.org:8080/  ",
            "http://PANEL.EXAMPLE.ORG:8080",
            "http://panel.example.org:8080/player_api.php"
    })
    void normalisesToTheSameAuthority(String input) {
        assertThat(HostNormaliser.normalise(input)).isEqualTo("http://panel.example.org:8080");
    }

    @Test
    @DisplayName("defaults to http rather than https")
    void defaultsToHttp() {
        // Most panels are plain HTTP. Guessing https for a host that does not
        // serve it turns a working source into SOURCE_UNREACHABLE.
        assertThat(HostNormaliser.normalise("panel.example.org")).isEqualTo("http://panel.example.org");
    }

    @Test
    @DisplayName("keeps an explicit https scheme")
    void preservesHttps() {
        assertThat(HostNormaliser.normalise("https://panel.example.org"))
                .isEqualTo("https://panel.example.org");
    }

    @Test
    @DisplayName("discards any path, query or fragment the caller supplied")
    void discardsPath() {
        // player_api.php paths are built by this application. Accepting a
        // caller-supplied path would let a source aim requests wherever it liked.
        assertThat(HostNormaliser.normalise("http://panel.example.org/a/b?c=d#e"))
                .isEqualTo("http://panel.example.org");
    }

    @Test
    @DisplayName("rejects an empty or unparseable host")
    void rejectsGarbage() {
        assertThatThrownBy(() -> HostNormaliser.normalise("")).isInstanceOf(IngestionException.class);
        assertThatThrownBy(() -> HostNormaliser.normalise(null)).isInstanceOf(IngestionException.class);
        assertThatThrownBy(() -> HostNormaliser.normalise("http://")).isInstanceOf(IngestionException.class);
    }
}
