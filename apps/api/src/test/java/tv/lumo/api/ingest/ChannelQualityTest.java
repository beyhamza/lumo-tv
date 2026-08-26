package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the definition badge is read from, and — more importantly — what it is not
 * read from.
 *
 * <p>The contract types {@code Channel.quality} as a free string precisely so the
 * server never has to file the unrecognised under some value. These tests pin
 * both halves of that: recognised tokens come back exactly as written, and
 * everything else comes back null rather than guessed.
 */
class ChannelQualityTest {

    @ParameterizedTest
    @CsvSource({
            "TF1 FHD,FHD",
            "Canal+ Sport 4K,4K",
            "Discovery HD,HD",
            "France 2 SD,SD",
            "Sky Sports UHD,UHD",
            "Cinema HEVC,HEVC",
    })
    @DisplayName("reads the token out of the name")
    void readsTheTokenOutOfTheName(String name, String expected) {
        assertThat(ChannelQuality.detect(null, name)).isEqualTo(expected);
    }

    @Test
    @DisplayName("echoes the token exactly as the source wrote it")
    void echoesVerbatim() {
        // Not upper-cased, not normalised. An enumeration would have to pick one
        // spelling; a free string does not, and the contract chose the free string.
        assertThat(ChannelQuality.detect(null, "Arte fhd")).isEqualTo("fhd");
    }

    @Test
    @DisplayName("the last token wins, because qualifiers trail")
    void theLastTokenWins() {
        // "HD Sport" in 4K is a 4K feed. Reading the leftmost match would
        // downgrade it to HD.
        assertThat(ChannelQuality.detect(null, "HD Sport 4K")).isEqualTo("4K");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Chaîne 01", "SHDTV", "Nickelodeon", "France 3 Nord"})
    @DisplayName("says nothing when the source says nothing")
    void saysNothingWhenTheSourceSaysNothing(String name) {
        // SHDTV is the one that matters: the token has to be bounded, or every
        // name containing the letters would sprout a badge.
        assertThat(ChannelQuality.detect(null, name)).isNull();
    }

    @Test
    @DisplayName("an explicit attribute beats the name")
    void explicitAttributeWins() {
        assertThat(ChannelQuality.detect("UHD", "Something HD")).isEqualTo("UHD");
    }

    @Test
    @DisplayName("an unrecognised explicit attribute is echoed, not dropped")
    void unrecognisedExplicitAttributeIsEchoed() {
        // The whole point of a free string: the server does not get to decide
        // that "MPEG4" is not a quality.
        assertThat(ChannelQuality.detect("MPEG4", "Whatever")).isEqualTo("MPEG4");
    }

    @Test
    @DisplayName("an over-long attribute is truncated to the contract's cap")
    void overLongAttributeIsTruncated() {
        assertThat(ChannelQuality.detect("Q".repeat(40), "Whatever")).hasSize(20);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "42,42", "007,7", "99999,99999"})
    @DisplayName("reads a channel number")
    void readsAChannelNumber(String raw, int expected) {
        assertThat(ChannelQuality.parseNumber(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "N/A", "1.5", "-3", "0", "12 34", "1234567"})
    @DisplayName("anything that is not a channel number is simply absent")
    void unreadableNumbersAreAbsent(String raw) {
        // Optional field: unreadable is not an error, it is nothing. A playlist
        // that writes "N/A" has said it has no number.
        assertThat(ChannelQuality.parseNumber(raw)).isNull();
    }
}
