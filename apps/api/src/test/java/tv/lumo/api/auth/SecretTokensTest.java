package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Token generation and the TV activation alphabet. */
class SecretTokensTest {

    @Test
    @DisplayName("user codes contain no ambiguous glyph")
    void userCodeAlphabetHasNoAmbiguousCharacters() {
        // The code is read off a television at three metres and typed on a phone,
        // so 0/O and 1/I/L are excluded (docs/architecture.md §5).
        String forbidden = "01OIL";

        IntStream.range(0, 2_000).forEach(i -> {
            String code = SecretTokens.generateUserCode();
            assertThat(code).hasSize(8);
            assertThat(code.chars().mapToObj(c -> (char) c))
                    .allSatisfy(c -> assertThat(forbidden.indexOf(c)).isEqualTo(-1));
        });
    }

    @Test
    @DisplayName("a code typed with separators or in lower case still matches")
    void userCodeNormalisationAcceptsHumanInput() {
        // Someone reading K7RM-4XPQ off a screen will type the dash; rejecting
        // them for it is a self-inflicted support ticket.
        assertThat(SecretTokens.normaliseUserCode("k7rm-4xpq")).isEqualTo("K7RM4XPQ");
        assertThat(SecretTokens.normaliseUserCode(" K7RM 4XPQ ")).isEqualTo("K7RM4XPQ");
        assertThat(SecretTokens.normaliseUserCode(null)).isEmpty();
    }

    @Test
    @DisplayName("generated secrets do not repeat")
    void secretsAreUnique() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 5_000).forEach(i -> generated.add(SecretTokens.generate()));

        assertThat(generated).hasSize(5_000);
    }

    @Test
    @DisplayName("hashing is stable and does not echo the plaintext")
    void hashingIsStableAndOpaque() {
        String token = SecretTokens.generate();

        assertThat(SecretTokens.hash(token)).isEqualTo(SecretTokens.hash(token));
        // Only hashes reach the database; the plaintext lives on the device.
        assertThat(SecretTokens.hash(token)).doesNotContain(token).hasSize(64);
    }
}
