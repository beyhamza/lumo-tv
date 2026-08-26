package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Access to {@code "user"}. The table name is a reserved word, hence the quoting. */
@Repository
public class UserRepository {

    private static final String COLUMNS = """
            id, email, password_hash, display_name, locale,
            email_verified_at, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM \"user\" WHERE email = :email")
                .param("email", email)
                .query(UserRepository::map)
                .optional();
    }

    public Optional<UserRow> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM \"user\" WHERE id = :id")
                .param("id", id)
                .query(UserRepository::map)
                .optional();
    }

    public UserRow insert(UUID id, String email, String passwordHash, String displayName, String locale) {
        jdbc.sql("""
                INSERT INTO "user" (id, email, password_hash, display_name, locale)
                VALUES (:id, :email, :passwordHash, :displayName, :locale)
                """)
                .param("id", id)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .param("displayName", displayName)
                .param("locale", locale)
                .update();
        return findById(id).orElseThrow();
    }

    /** Sets a password on an account that had none, or replaces an existing one. */
    public void updatePasswordHash(UUID userId, String passwordHash) {
        jdbc.sql("""
                UPDATE "user" SET password_hash = :hash, updated_at = now() WHERE id = :id
                """)
                .param("hash", passwordHash)
                .param("id", userId)
                .update();
    }

    /**
     * Updates the two mutable properties of a profile.
     *
     * <p>Null means unchanged, for both. The contract says an explicit
     * {@code null} clears {@code display_name}, and the generated
     * {@code UpdateUserRequest} cannot express the difference between "sent as
     * null" and "not sent" — it holds a plain {@code String}. Rather than guess,
     * this treats absence and null alike; the alternative reading would clear a
     * user's display name every time a client patched their locale.
     *
     * <p>Email is not here and will not be: changing it requires re-verification,
     * which is not in v1.
     */
    public void updateProfile(UUID userId, String displayName, String locale) {
        jdbc.sql("""
                UPDATE "user"
                   SET display_name = COALESCE(:displayName, display_name),
                       locale       = COALESCE(:locale, locale),
                       updated_at   = now()
                 WHERE id = :id
                """)
                .param("displayName", displayName)
                .param("locale", locale)
                .param("id", userId)
                .update();
    }

    public void markEmailVerified(UUID userId) {
        jdbc.sql("""
                UPDATE "user"
                   SET email_verified_at = COALESCE(email_verified_at, now()), updated_at = now()
                 WHERE id = :id
                """)
                .param("id", userId)
                .update();
    }

    static UserRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UserRow(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("locale"),
                rs.getObject("email_verified_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
