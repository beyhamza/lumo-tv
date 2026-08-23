package tv.lumo.api.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Access to {@code oauth_identity}. */
@Repository
public class OAuthIdentityRepository {

    private final JdbcClient jdbc;

    public OAuthIdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findUserId(String provider, String providerUserId) {
        return jdbc.sql("""
                SELECT user_id FROM oauth_identity
                 WHERE provider = :provider AND provider_user_id = :providerUserId
                """)
                .param("provider", provider)
                .param("providerUserId", providerUserId)
                .query(UUID.class)
                .optional();
    }

    /**
     * Attaches a provider identity to an existing account.
     *
     * <p>ON CONFLICT DO NOTHING because two concurrent first-time sign-ins with
     * the same Google account would otherwise race on the unique index. Either
     * one winning is correct; what matters is that a SECOND ACCOUNT is never
     * created for an email that already exists (US-03).
     */
    public void link(UUID userId, String provider, String providerUserId) {
        jdbc.sql("""
                INSERT INTO oauth_identity (id, user_id, provider, provider_user_id)
                VALUES (:id, :userId, :provider, :providerUserId)
                ON CONFLICT (provider, provider_user_id) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("provider", provider)
                .param("providerUserId", providerUserId)
                .update();
    }
}
