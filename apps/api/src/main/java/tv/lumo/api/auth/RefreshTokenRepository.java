package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Access to {@code refresh_token}.
 *
 * <p>Tokens are stored hashed; the plaintext is never written here. Rotation and
 * reuse detection live in {@link SessionService} — this class only reads and
 * writes rows.
 */
@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbc;

    public RefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks the row for the duration of the transaction.
     *
     * <p>{@code FOR UPDATE} is what makes rotation safe under the concurrent
     * refresh clients actually produce. Two parallel calls with the same token
     * would otherwise both read it as valid, both rotate, and the second would
     * revoke the first — signing the user out. The lock serialises them so the
     * loser sees a revoked row and is correctly reported as reuse.
     */
    public Optional<RefreshTokenRow> lockByHash(String tokenHash) {
        return jdbc.sql("""
                SELECT id, user_id, device_id, token_hash, expires_at, revoked_at, replaced_by
                  FROM refresh_token
                 WHERE token_hash = :hash
                   FOR UPDATE
                """)
                .param("hash", tokenHash)
                .query(RefreshTokenRepository::map)
                .optional();
    }

    public void insert(UUID id, UUID userId, UUID deviceId, String tokenHash, OffsetDateTime expiresAt) {
        jdbc.sql("""
                INSERT INTO refresh_token (id, user_id, device_id, token_hash, expires_at)
                VALUES (:id, :userId, :deviceId, :hash, :expiresAt)
                """)
                .param("id", id)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("hash", tokenHash)
                .param("expiresAt", expiresAt)
                .update();
    }

    public void markRotated(UUID oldTokenId, UUID newTokenId) {
        jdbc.sql("""
                UPDATE refresh_token
                   SET revoked_at = now(), replaced_by = :newId
                 WHERE id = :oldId
                """)
                .param("newId", newTokenId)
                .param("oldId", oldTokenId)
                .update();
    }

    /**
     * Revokes every live token of one device.
     *
     * <p>Called on sign-out, on device removal, and — the important case — when a
     * consumed refresh token is presented again. That is theft detection: the
     * whole chain goes, and the device must sign in from scratch
     * (docs/domain-model.md §2).
     */
    public int revokeDeviceChain(UUID deviceId) {
        return jdbc.sql("""
                UPDATE refresh_token
                   SET revoked_at = now()
                 WHERE device_id = :deviceId
                   AND revoked_at IS NULL
                """)
                .param("deviceId", deviceId)
                .update();
    }

    /** Revokes every live token of an account, on every device. Used after a password reset. */
    public int revokeAllForUser(UUID userId) {
        return jdbc.sql("""
                UPDATE refresh_token
                   SET revoked_at = now()
                 WHERE user_id = :userId
                   AND revoked_at IS NULL
                """)
                .param("userId", userId)
                .update();
    }

    /**
     * Row of {@code refresh_token}.
     *
     * @param revokedAt non-null means consumed or revoked; presenting it again is reuse
     */
    public record RefreshTokenRow(
            UUID id,
            UUID userId,
            UUID deviceId,
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime revokedAt,
            UUID replacedBy
    ) {
        public boolean isRevoked() {
            return revokedAt != null;
        }

        public boolean isExpired(OffsetDateTime now) {
            return expiresAt.isBefore(now);
        }
    }

    static RefreshTokenRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RefreshTokenRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("device_id", UUID.class),
                rs.getString("token_hash"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("revoked_at", OffsetDateTime.class),
                rs.getObject("replaced_by", UUID.class));
    }
}
