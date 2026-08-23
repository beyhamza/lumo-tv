package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Access to {@code user_token}: single-use email-verification and
 * password-reset tokens, stored hashed.
 */
@Repository
public class UserTokenRepository {

    public enum Purpose {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }

    private final JdbcClient jdbc;

    public UserTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID userId, Purpose purpose, String tokenHash, OffsetDateTime expiresAt) {
        jdbc.sql("""
                INSERT INTO user_token (id, user_id, purpose, token_hash, expires_at)
                VALUES (:id, :userId, :purpose, :hash, :expiresAt)
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("purpose", purpose.name())
                .param("hash", tokenHash)
                .param("expiresAt", expiresAt)
                .update();
    }

    /** Locks the row so a token cannot be consumed twice by concurrent calls. */
    public Optional<UserTokenRow> lockByHash(String tokenHash, Purpose purpose) {
        return jdbc.sql("""
                SELECT id, user_id, purpose, expires_at, consumed_at
                  FROM user_token
                 WHERE token_hash = :hash AND purpose = :purpose
                   FOR UPDATE
                """)
                .param("hash", tokenHash)
                .param("purpose", purpose.name())
                .query((rs, n) -> new UserTokenRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        Purpose.valueOf(rs.getString("purpose")),
                        rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("consumed_at", OffsetDateTime.class)))
                .optional();
    }

    public void consume(UUID tokenId) {
        jdbc.sql("UPDATE user_token SET consumed_at = now() WHERE id = :id")
                .param("id", tokenId)
                .update();
    }

    /** Invalidates outstanding tokens of one purpose, so issuing a new one retires the old. */
    public void consumeAllFor(UUID userId, Purpose purpose) {
        jdbc.sql("""
                UPDATE user_token SET consumed_at = now()
                 WHERE user_id = :userId AND purpose = :purpose AND consumed_at IS NULL
                """)
                .param("userId", userId)
                .param("purpose", purpose.name())
                .update();
    }

    public record UserTokenRow(UUID id, UUID userId, Purpose purpose,
                               OffsetDateTime expiresAt, OffsetDateTime consumedAt) {

        public boolean isUsable(OffsetDateTime now) {
            return consumedAt == null && expiresAt.isAfter(now);
        }
    }
}
