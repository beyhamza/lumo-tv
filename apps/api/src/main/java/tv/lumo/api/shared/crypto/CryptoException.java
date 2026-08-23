package tv.lumo.api.shared.crypto;

/**
 * A cryptographic operation failed.
 *
 * <p>The message never carries key material, ciphertext or plaintext: this
 * exception is logged, and a credential that reaches a log line has already
 * failed the requirement it was encrypted to satisfy.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        // The cause is kept for the stack trace but its message is not
        // propagated: JCE exceptions can echo buffer contents.
        super(message, cause);
    }
}
