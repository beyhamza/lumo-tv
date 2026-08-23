package tv.lumo.api.shared.crypto;

/**
 * Wraps and unwraps data keys. <b>This is the KMS seam.</b>
 *
 * <p>The two methods are deliberately shaped like a KMS {@code Encrypt} /
 * {@code Decrypt} call on a small payload: a data key goes in, an opaque blob
 * comes out. Swapping {@link EnvironmentMasterKeyProvider} for an AWS KMS or
 * Google Cloud KMS implementation is a new bean and nothing else — no change to
 * {@link CredentialCipher}, to the {@code source} domain, or to the stored
 * format.
 *
 * <p>The reason the interface is not "encrypt this password" is that a KMS
 * charges per call and caps payload size. Envelope encryption keeps the KMS on
 * the key path only: one small wrap per credential, never the credential itself.
 *
 * <p>An implementation must never log, return or otherwise expose the master key.
 */
public interface MasterKeyProvider {

    /**
     * Encrypts a freshly generated data key.
     *
     * @param dataKey raw 256-bit key material; the caller zeroes it afterwards
     * @return opaque wrapped form, safe to store next to the ciphertext
     */
    byte[] wrap(byte[] dataKey);

    /**
     * Reverses {@link #wrap}.
     *
     * @throws CryptoException if the blob was not produced by this provider, or
     *                         the master key has changed
     */
    byte[] unwrap(byte[] wrappedDataKey);

    /**
     * Identifies which master key produced a blob, so a future key rotation can
     * tell already-migrated rows from the rest. Stored in the sealed envelope.
     */
    String keyId();
}
