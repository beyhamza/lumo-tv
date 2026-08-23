package tv.lumo.api.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id password hashing (AGENTS.md §5).
 *
 * <p>Also provides the constant-work path used when an email does not exist.
 * Without it, "no such account" returns in microseconds while "wrong password"
 * takes the full Argon2 cost, and the difference is measurable from outside —
 * which is exactly the account enumeration US-01 forbids.
 */
@Component
public class PasswordHasher {

    /**
     * A valid Argon2id hash of a value nobody knows, verified against whenever
     * the account does not exist or has no password. The verification is real
     * work with a real result that is always false.
     */
    private final String dummyHash;
    private final PasswordEncoder encoder;

    public PasswordHasher() {
        this.encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        this.dummyHash = encoder.encode("::lumo-absent-account::");
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null) {
            // SSO-only account, or no such account. Burn the same CPU anyway.
            encoder.matches(rawPassword, dummyHash);
            return false;
        }
        return encoder.matches(rawPassword, storedHash);
    }

    /** Spends the cost of a verification for an account that does not exist. */
    public void burn(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }
}
