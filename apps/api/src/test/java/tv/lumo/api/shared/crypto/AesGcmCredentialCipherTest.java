package tv.lumo.api.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tv.lumo.api.shared.config.LumoProperties;

/** Credential encryption. Domain logic, so it comes with its tests (AGENTS.md §5). */
class AesGcmCredentialCipherTest {

    private static final String KEY_A = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String KEY_B = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

    private static AesGcmCredentialCipher cipherWith(String masterKey) {
        return new AesGcmCredentialCipher(new EnvironmentMasterKeyProvider(properties(masterKey)));
    }

    @Test
    @DisplayName("a sealed credential opens back to the original")
    void roundTrips() {
        AesGcmCredentialCipher cipher = cipherWith(KEY_A);
        String password = "sup3r-s3cret-xtream-pw";

        assertThat(cipher.open(cipher.seal(password))).isEqualTo(password);
    }

    @Test
    @DisplayName("the ciphertext never contains the plaintext")
    void ciphertextDoesNotLeakPlaintext() {
        String password = "recognisable-marker-value";
        byte[] sealed = cipherWith(KEY_A).seal(password);

        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain(password);
    }

    @Test
    @DisplayName("sealing the same value twice produces different ciphertexts")
    void sealingIsNonDeterministic() {
        AesGcmCredentialCipher cipher = cipherWith(KEY_A);

        // Fresh data key and fresh nonce every time. Identical output would let
        // anyone with database access tell which users share a password.
        assertThat(cipher.seal("same-password")).isNotEqualTo(cipher.seal("same-password"));
    }

    @Test
    @DisplayName("a credential sealed under one master key cannot be opened with another")
    void wrongMasterKeyFails() {
        byte[] sealed = cipherWith(KEY_A).seal("password");

        assertThatThrownBy(() -> cipherWith(KEY_B).open(sealed))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("a tampered envelope fails the GCM tag check rather than decrypting")
    void tamperingIsDetected() {
        AesGcmCredentialCipher cipher = cipherWith(KEY_A);
        byte[] sealed = cipher.seal("password");
        // GCM is authenticated: flipping any byte must fail, not yield garbage.
        sealed[sealed.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.open(sealed)).isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("a blob that is not ours is rejected on the magic bytes")
    void foreignBlobIsRejected() {
        // Long enough to get past the truncation guard and actually reach the
        // magic-byte check.
        byte[] foreign = "this is definitely not a Lumo credential envelope"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> cipherWith(KEY_A).open(foreign))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("magic");
    }

    @Test
    @DisplayName("a truncated envelope is rejected before any decryption is attempted")
    void truncatedBlobIsRejected() {
        assertThatThrownBy(() -> cipherWith(KEY_A).open("short".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    @DisplayName("a master key of the wrong length is refused at construction")
    void shortMasterKeyIsRefused() {
        String tooShort = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));

        // Fails at start-up rather than when the first source is registered.
        assertThatThrownBy(() -> new EnvironmentMasterKeyProvider(properties(tooShort)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static LumoProperties properties(String masterKey) {
        return new LumoProperties(
                new LumoProperties.Jwt("x".repeat(40), java.time.Duration.ofMinutes(15), "https://api.lumo.tv"),
                new LumoProperties.Refresh(java.time.Duration.ofDays(30)),
                new LumoProperties.Encryption(masterKey),
                new LumoProperties.DeviceCode(java.time.Duration.ofMinutes(10), java.time.Duration.ofSeconds(5)),
                new LumoProperties.Web("http://localhost:3000"),
                new LumoProperties.Cors(java.util.List.of()),
                new LumoProperties.Ingest(4, 50, java.time.Duration.ofSeconds(10), 200),
                new LumoProperties.RateLimit(5, 5),
                new LumoProperties.AutoSync(false, java.time.Duration.ofHours(1), 12, 25),
                new LumoProperties.Plans(new LumoProperties.Limits(1, 2),
                        new LumoProperties.Limits(null, null)),
                // Unconfigured, which is what this test's subject cares about:
                // nothing here touches billing.
                new LumoProperties.Billing("", "", "https://api.stripe.com", 0,
                        "/app/subscription", "/app/subscription", "/app/subscription"));
    }
}
