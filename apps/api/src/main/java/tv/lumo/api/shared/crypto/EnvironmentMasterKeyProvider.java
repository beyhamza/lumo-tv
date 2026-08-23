package tv.lumo.api.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * Master key read from the environment. Development and self-hosted deployments.
 *
 * <p>In production this bean is replaced by a KMS-backed {@link MasterKeyProvider}
 * — see that interface. The stored envelope format does not change, only who
 * holds the key.
 *
 * <p>The key is validated at start-up rather than on first use. An API that boots
 * cleanly and then fails to save the first source anyone registers is far worse
 * than one that refuses to boot.
 */
@Component
public class EnvironmentMasterKeyProvider implements MasterKeyProvider {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec masterKey;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public EnvironmentMasterKeyProvider(LumoProperties properties) {
        byte[] raw = decode(properties.encryption().masterKey());
        if (raw.length != KEY_BYTES) {
            Arrays.fill(raw, (byte) 0);
            throw new IllegalStateException(
                    "LUMO_ENCRYPTION_MASTER_KEY must decode to exactly 32 bytes (AES-256). "
                            + "Generate one with: openssl rand -base64 32");
        }
        this.masterKey = new SecretKeySpec(raw, ALGORITHM);
        // Fingerprint, not the key: it identifies which key sealed a row without
        // being of any use to whoever reads it.
        this.keyId = fingerprint(raw);
        Arrays.fill(raw, (byte) 0);
    }

    @Override
    public byte[] wrap(byte[] dataKey) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] wrapped = cipher.doFinal(dataKey);

            byte[] out = new byte[NONCE_BYTES + wrapped.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(wrapped, 0, out, NONCE_BYTES, wrapped.length);
            return out;
        } catch (Exception e) {
            throw new CryptoException("Failed to wrap a data key", e);
        }
    }

    @Override
    public byte[] unwrap(byte[] wrappedDataKey) {
        if (wrappedDataKey == null || wrappedDataKey.length <= NONCE_BYTES) {
            throw new CryptoException("Wrapped data key is truncated");
        }
        try {
            GCMParameterSpec spec =
                    new GCMParameterSpec(TAG_BITS, wrappedDataKey, 0, NONCE_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec);
            return cipher.doFinal(wrappedDataKey, NONCE_BYTES, wrappedDataKey.length - NONCE_BYTES);
        } catch (Exception e) {
            // A GCM tag mismatch here means the master key changed or the row was
            // tampered with. Both are operational incidents, not user errors.
            throw new CryptoException("Failed to unwrap a data key: wrong master key or corrupt envelope", e);
        }
    }

    @Override
    public String keyId() {
        return keyId;
    }

    private static byte[] decode(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "LUMO_ENCRYPTION_MASTER_KEY is not set. The API will not start without it: "
                            + "running on a default key would silently expose every stored IPTV credential.");
        }
        try {
            return Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LUMO_ENCRYPTION_MASTER_KEY is not valid base64", e);
        }
    }

    private static String fingerprint(byte[] key) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("lumo-master-key-id".getBytes(StandardCharsets.UTF_8)));
            byte[] mixed = java.security.MessageDigest.getInstance("SHA-256").digest(concat(digest, key));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(mixed, 8));
        } catch (Exception e) {
            throw new CryptoException("Failed to fingerprint the master key", e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
