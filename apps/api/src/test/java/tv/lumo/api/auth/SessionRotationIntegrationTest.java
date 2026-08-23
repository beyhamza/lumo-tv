package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.TokenPair;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Refresh-token rotation and reuse detection (US-04).
 *
 * <p>AGENTS.md §5 names token rotation as business logic that ships with its
 * tests, and this is the security-critical path: getting it wrong either signs
 * every user out at random or lets a stolen token live forever.
 *
 * <p>Runs against a real PostgreSQL because the behaviour under test is partly
 * the database's: the {@code FOR UPDATE} lock and the revoked/replaced_by chain.
 */
@Import(PostgresContainerInitializer.class)
class SessionRotationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;
    private UUID deviceId;

    @BeforeEach
    void createAccount() {
        user = users.insert(UUID.randomUUID(),
                "rotation-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Rotation Test", "en");
        deviceId = devices.insert(user.id(), Platform.ANDROID_TV.getValue(),
                "Test TV", "Test Model", "1.0.0");
    }

    @Test
    @DisplayName("rotating issues a new pair and the old refresh token stops working")
    void rotationIssuesANewPairAndRetiresTheOld() {
        AuthSession session = sessions.openSession(user, deviceId);
        String original = session.getRefreshToken();

        TokenPair rotated = sessions.rotate(original);

        assertThat(rotated.getRefreshToken()).isNotEqualTo(original);
        assertThat(rotated.getAccessToken()).isNotBlank();
        // Single use: the presented token is spent.
        assertThat(revokedAt(original)).isNotNull();
        assertThat(revokedAt(rotated.getRefreshToken())).isNull();
    }

    @Test
    @DisplayName("the rotation chain records which token replaced which")
    void rotationRecordsTheChain() {
        AuthSession session = sessions.openSession(user, deviceId);
        TokenPair rotated = sessions.rotate(session.getRefreshToken());

        UUID replacedBy = jdbc.sql("SELECT replaced_by FROM refresh_token WHERE token_hash = :hash")
                .param("hash", SecretTokens.hash(session.getRefreshToken()))
                .query(UUID.class)
                .single();
        UUID newId = jdbc.sql("SELECT id FROM refresh_token WHERE token_hash = :hash")
                .param("hash", SecretTokens.hash(rotated.getRefreshToken()))
                .query(UUID.class)
                .single();

        assertThat(replacedBy).isEqualTo(newId);
    }

    @Test
    @DisplayName("presenting a consumed token revokes the ENTIRE device chain")
    void reuseRevokesTheWholeChain() {
        AuthSession session = sessions.openSession(user, deviceId);
        String first = session.getRefreshToken();
        TokenPair second = sessions.rotate(first);
        TokenPair third = sessions.rotate(second.getRefreshToken());

        // `third` is live and legitimate at this point.
        assertThat(revokedAt(third.getRefreshToken())).isNull();

        // Someone replays the already-spent first token. We cannot tell a replay
        // by the real device from a leak, so we assume the worse case.
        assertThatThrownBy(() -> sessions.rotate(first))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED);

        // The thief and the legitimate device both lose access; the account owner
        // signs in again (docs/domain-model.md §2).
        assertThat(revokedAt(third.getRefreshToken())).isNotNull();
        assertThat(liveTokenCount(deviceId)).isZero();
    }

    @Test
    @DisplayName("reuse detection is scoped to one device and spares the others")
    void reuseDoesNotAffectOtherDevices() {
        UUID otherDeviceId = devices.insert(user.id(), Platform.ANDROID_MOBILE.getValue(),
                "Phone", "Test Model", "1.0.0");
        AuthSession phone = sessions.openSession(user, otherDeviceId);

        AuthSession tv = sessions.openSession(user, deviceId);
        sessions.rotate(tv.getRefreshToken());
        assertThatThrownBy(() -> sessions.rotate(tv.getRefreshToken()))
                .isInstanceOf(ApiException.class);

        // A compromised television must not sign the user out of their phone.
        assertThat(revokedAt(phone.getRefreshToken())).isNull();
        assertThat(liveTokenCount(otherDeviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown refresh token is INVALID, not REUSED")
    void unknownTokenIsInvalid() {
        assertThatThrownBy(() -> sessions.rotate(SecretTokens.generate()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                // REUSED would tell a guesser that their token existed once.
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("only the plaintext hash is stored, never the token itself")
    void onlyHashesAreStored() {
        AuthSession session = sessions.openSession(user, deviceId);

        Integer matches = jdbc.sql("SELECT count(*) FROM refresh_token WHERE token_hash = :raw")
                .param("raw", session.getRefreshToken())
                .query(Integer.class)
                .single();

        assertThat(matches).isZero();
        assertThat(revokedAt(session.getRefreshToken())).isNull();
    }

    @Test
    @DisplayName("signing out revokes the device chain and is idempotent")
    void signOutRevokesAndIsIdempotent() {
        AuthSession session = sessions.openSession(user, deviceId);

        sessions.signOut(session.getRefreshToken(), user.id());
        assertThat(liveTokenCount(deviceId)).isZero();

        // A token that is already gone still yields success: telling a caller
        // which tokens exist is an oracle we do not need.
        sessions.signOut(session.getRefreshToken(), user.id());
    }

    @Test
    @DisplayName("a caller cannot sign out a device belonging to someone else")
    void signOutRejectsAForeignToken() {
        AuthSession session = sessions.openSession(user, deviceId);
        UUID strangerId = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example", null, null, "en").id();

        assertThatThrownBy(() -> sessions.signOut(session.getRefreshToken(), strangerId))
                .isInstanceOf(ApiException.class);

        assertThat(liveTokenCount(deviceId)).isEqualTo(1);
    }

    // ---- helpers ------------------------------------------------------------

    private java.time.OffsetDateTime revokedAt(String plaintextToken) {
        return jdbc.sql("SELECT revoked_at FROM refresh_token WHERE token_hash = :hash")
                .param("hash", SecretTokens.hash(plaintextToken))
                .query(java.time.OffsetDateTime.class)
                .optional()
                .orElse(null);
    }

    private int liveTokenCount(UUID device) {
        return jdbc.sql("""
                SELECT count(*) FROM refresh_token
                 WHERE device_id = :deviceId AND revoked_at IS NULL
                """)
                .param("deviceId", device)
                .query(Integer.class)
                .single();
    }
}
