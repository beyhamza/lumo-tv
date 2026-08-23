package tv.lumo.api.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM envelope encryption for Xtream credentials.
 *
 * <p>Every credential gets its own random data key. The data key encrypts the
 * password; the {@link MasterKeyProvider} wraps the data key. Only the wrapped
 * form is stored, so <b>a database dump on its own decrypts nothing</b> — the
 * master key lives outside the database, in the environment today and in a KMS
 * in production (docs/domain-model.md §2).
 *
 * <p>Per-credential data keys also bound the blast radius of a nonce collision
 * to one row, and make re-encrypting a single credential possible without
 * touching any other.
 *
 * <h2>Envelope layout</h2>
 * <pre>
 *   magic     2 bytes   0x4C 0x55  ("LU") — rejects a blob that is not ours
 *   version   1 byte    format version, so a future layout can be told apart
 *   keyIdLen  1 byte
 *   keyId     n bytes   which master key wrapped this, for rotation
 *   wrapLen   2 bytes   big-endian
 *   wrapped   n bytes   data key, wrapped by the MasterKeyProvider
 *   nonce    12 bytes   GCM nonce for the payload
 *   payload   n bytes   ciphertext + 16-byte GCM tag
 * </pre>
 *
 * <p>The key id and version are inside the envelope rather than in a database
 * column on purpose: a row can then be moved, backed up or restored without
 * carrying separate metadata that could drift out of step with it.
 */
@Component
public class AesGcmCredentialCipher implements CredentialCipher {

    private static final byte[] MAGIC = {'L', 'U'};
    private static final byte VERSION = 1;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final MasterKeyProvider masterKeys;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCredentialCipher(MasterKeyProvider masterKeys) {
        this.masterKeys = masterKeys;
    }

    @Override
    public byte[] seal(String plaintext) {
        if (plaintext == null) {
            throw new CryptoException("Refusing to seal a null credential");
        }
        byte[] dataKey = new byte[KEY_BYTES];
        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            random.nextBytes(dataKey);

            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, ALGORITHM),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] payload = cipher.doFinal(plainBytes);

            byte[] wrapped = masterKeys.wrap(dataKey);
            byte[] keyId = masterKeys.keyId().getBytes(StandardCharsets.UTF_8);
            if (keyId.length > 255) {
                throw new CryptoException("Master key id is too long for the envelope format");
            }

            ByteBuffer buffer = ByteBuffer.allocate(
                    MAGIC.length + 1 + 1 + keyId.length + 2 + wrapped.length + NONCE_BYTES + payload.length);
            buffer.put(MAGIC);
            buffer.put(VERSION);
            buffer.put((byte) keyId.length);
            buffer.put(keyId);
            buffer.putShort((short) wrapped.length);
            buffer.put(wrapped);
            buffer.put(nonce);
            buffer.put(payload);
            return buffer.array();
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to seal a credential", e);
        } finally {
            // Best effort: the JVM may have copied these, but leaving key material
            // in a live heap for the GC to schedule is worse than not trying.
            Arrays.fill(dataKey, (byte) 0);
            Arrays.fill(plainBytes, (byte) 0);
        }
    }

    @Override
    public String open(byte[] sealed) {
        if (sealed == null || sealed.length < MAGIC.length + 4 + NONCE_BYTES) {
            throw new CryptoException("Sealed credential is truncated");
        }
        byte[] dataKey = null;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(sealed);

            byte[] magic = new byte[MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new CryptoException("Sealed credential does not carry the Lumo envelope magic");
            }
            byte version = buffer.get();
            if (version != VERSION) {
                throw new CryptoException("Unsupported credential envelope version: " + version);
            }

            int keyIdLength = Byte.toUnsignedInt(buffer.get());
            byte[] keyId = new byte[keyIdLength];
            buffer.get(keyId);

            int wrappedLength = Short.toUnsignedInt(buffer.getShort());
            byte[] wrapped = new byte[wrappedLength];
            buffer.get(wrapped);

            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);

            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            dataKey = masterKeys.unwrap(wrapped);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, ALGORITHM),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plain = cipher.doFinal(payload);
            try {
                return new String(plain, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to open a sealed credential", e);
        } finally {
            if (dataKey != null) {
                Arrays.fill(dataKey, (byte) 0);
            }
        }
    }
}
