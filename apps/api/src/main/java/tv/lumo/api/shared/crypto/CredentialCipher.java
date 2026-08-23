package tv.lumo.api.shared.crypto;

/**
 * Seals and opens the Xtream password of a {@code source}.
 *
 * <p>The plaintext never leaves the {@code source} layer, is never returned by
 * the API even to its owner, and never appears in a log
 * (docs/domain-model.md §2).
 */
public interface CredentialCipher {

    /** @return the sealed envelope, ready to store in {@code source.password_encrypted} */
    byte[] seal(String plaintext);

    /** @throws CryptoException if the envelope is corrupt, truncated or was sealed under another key */
    String open(byte[] sealed);
}
