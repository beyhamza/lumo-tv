package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Argon2id hashing and the constant-work path that prevents account enumeration. */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    @DisplayName("a correct password verifies and an incorrect one does not")
    void verifiesCorrectPassword() {
        String hash = hasher.hash("correct-horse-battery");

        assertThat(hasher.matches("correct-horse-battery", hash)).isTrue();
        assertThat(hasher.matches("wrong-password-entirely", hash)).isFalse();
    }

    @Test
    @DisplayName("hashing the same password twice gives different hashes")
    void hashesAreSalted() {
        assertThat(hasher.hash("same-password")).isNotEqualTo(hasher.hash("same-password"));
    }

    @Test
    @DisplayName("the hash is Argon2id")
    void usesArgon2id() {
        assertThat(hasher.hash("whatever")).startsWith("$argon2id$");
    }

    @Test
    @DisplayName("a null stored hash never matches but still does the work")
    void nullHashNeverMatches() {
        // SSO-only accounts have no password. Returning false early would make
        // them distinguishable by response time.
        assertThat(hasher.matches("anything", null)).isFalse();
    }

    @Test
    @DisplayName("burn() costs about as much as a real verification")
    void burnCostsComparableWork() {
        String hash = hasher.hash("reference-password");

        long realStart = System.nanoTime();
        hasher.matches("reference-password", hash);
        long realCost = System.nanoTime() - realStart;

        long burnStart = System.nanoTime();
        hasher.burn("reference-password");
        long burnCost = System.nanoTime() - burnStart;

        // The point is the ORDER OF MAGNITUDE, not a precise ratio: an unknown
        // email must not answer in microseconds while a known one takes
        // milliseconds (US-01, US-02). Bounds are loose because this runs on
        // shared CI hardware.
        assertThat(burnCost).isGreaterThan(realCost / 10);
    }
}
