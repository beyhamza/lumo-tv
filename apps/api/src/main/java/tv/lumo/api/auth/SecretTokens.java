package tv.lumo.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of opaque secrets: refresh tokens, device codes, and
 * email verification / password reset tokens.
 *
 * <p><b>Only hashes are stored.</b> The plaintext exists on the device or in the
 * email and nowhere else, so a database dump yields nothing that can be
 * presented back to the API.
 *
 * <p>SHA-256 rather than Argon2 here on purpose. These are 256-bit random
 * values, not passwords: they have no guessable structure, so a slow hash buys
 * nothing and would put an Argon2 computation on the refresh path, which every
 * client hits every fifteen minutes.
 */
public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    /**
     * Alphabet for the TV activation user_code: no {@code 0}/{@code O} and no
     * {@code 1}/{@code I}/{@code L}, because the code is read off a television
     * at three metres and typed on a phone (docs/architecture.md §5).
     */
    private static final char[] USER_CODE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int USER_CODE_LENGTH = 8;

    private SecretTokens() {
    }

    /** A 256-bit URL-safe secret. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** An 8-character code from the unambiguous alphabet. */
    public static String generateUserCode() {
        StringBuilder code = new StringBuilder(USER_CODE_LENGTH);
        for (int i = 0; i < USER_CODE_LENGTH; i++) {
            code.append(USER_CODE_ALPHABET[RANDOM.nextInt(USER_CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * Normalises a user code as typed by a human: upper-cased, with spaces and
     * separators removed. Someone reading {@code K7RM-4XPQ} off a screen will
     * type the dash, and rejecting them for it would be a self-inflicted support
     * ticket.
     */
    public static String normaliseUserCode(String typed) {
        if (typed == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(USER_CODE_LENGTH);
        for (char c : typed.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    public static String hash(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required and always present", e);
        }
    }
}
