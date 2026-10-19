package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.shared.crypto.CredentialCipher;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * {@code POST /me/delete} (S16B-02, ADR 0014), over HTTP because what matters is
 * what every device of the account sees afterwards.
 *
 * <p>The sweep does not list tables. It asks the schema for every column named
 * {@code user_id} or {@code source_id} and counts the account's rows in each:
 * a table added next sprint without {@code ON DELETE CASCADE} fails the deletion
 * itself, and one added with a different way of pointing at the account would
 * show up here as a survivor. Rows reached only through another table (seasons,
 * watched intervals) cannot outlive their parent: the foreign key forbids it.
 */
@Import(PostgresContainerInitializer.class)
class AccountDeletionIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct horse battery";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private SessionService sessions;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private PasswordHasher passwords;

    @Autowired
    private CredentialCipher cipher;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;
    private UUID sourceId;
    private AuthSession phone;
    private AuthSession television;
    private AuthSession stranger;

    @BeforeEach
    void fillAnAccount() {
        user = users.insert(UUID.randomUUID(), "delete-" + UUID.randomUUID() + "@test.example",
                passwords.hash(PASSWORD), "Delete Test", "fr");
        phone = sessions.openSession(user, devices.insert(user.id(),
                Platform.ANDROID_MOBILE.getValue(), "Phone", "Model", "0.4.0"));
        television = sessions.openSession(user, devices.insert(user.id(),
                Platform.ANDROID_TV.getValue(), "Living room", "Model", "0.4.0"));

        // An Xtream source, so the encrypted credentials are among what must go.
        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Banc", SourceKind.XTREAM, "https://panel.example",
                "someone", cipher.seal("not-a-real-password"), null, null, null, null);
        UUID channelId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, external_id, name, stream_url, position)
                VALUES (:id, :sourceId, 'fixture:1', 'Chaîne', 'https://stream.example/x.m3u8', 0)
                """).param("id", channelId).param("sourceId", sourceId).update();
        UUID groupId = UUID.randomUUID();
        jdbc.sql("INSERT INTO favorite_group (id, user_id, name) VALUES (:id, :userId, 'Favoris')")
                .param("id", groupId).param("userId", user.id()).update();
        jdbc.sql("""
                INSERT INTO favorite (id, group_id, user_id, source_id, channel_id, position)
                VALUES (:id, :groupId, :userId, :sourceId, :channelId, 0)
                """).param("id", UUID.randomUUID()).param("groupId", groupId).param("userId", user.id())
                .param("sourceId", sourceId).param("channelId", channelId).update();
        jdbc.sql("""
                INSERT INTO playback_progress (id, user_id, source_id, item_type, item_ref, position_ms)
                VALUES (:id, :userId, :sourceId, 'VOD', 'film-1', 60000)
                """).param("id", UUID.randomUUID()).param("userId", user.id())
                .param("sourceId", sourceId).update();
        jdbc.sql("""
                INSERT INTO watchlist_item (id, user_id, source_id, content_type, external_id, title)
                VALUES (:id, :userId, :sourceId, 'VOD', 'film-1', 'Film')
                """).param("id", UUID.randomUUID()).param("userId", user.id())
                .param("sourceId", sourceId).update();

        UserRow other = users.insert(UUID.randomUUID(), "keep-" + UUID.randomUUID() + "@test.example",
                passwords.hash(PASSWORD), "Kept", "en");
        stranger = sessions.openSession(other, devices.insert(other.id(),
                Platform.WEB.getValue(), "lumo.tv", null, "0.4.0"));
    }

    @Test
    @DisplayName("a wrong password is a 403 with a code, and deletes nothing")
    void wrongPasswordDeletesNothing() {
        Response refused = post("/v1/me/delete", phone.getAccessToken(), "{\"password\":\"not it at all\"}");

        // 403 and not 401: a 401 would make every client refresh or sign out.
        assertThat(refused.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.body()).contains("\"code\":\"INVALID_CREDENTIALS\"");
        assertThat(users.findById(user.id())).isPresent();
        assertThat(rowsOfTheAccount()).isPositive();
    }

    @Test
    @DisplayName("deleting the account leaves no row of it in any table")
    void nothingOfTheAccountRemains() {
        assertThat(rowsOfTheAccount()).as("the sweep must see the seeded rows first").isGreaterThan(8);

        Response deleted = post("/v1/me/delete", phone.getAccessToken(), passwordBody());

        assertThat(deleted.status()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(users.findById(user.id())).isEmpty();
        assertThat(rowsOfTheAccount()).isZero();
        // Somebody else's account is not collateral.
        assertThat(get("/v1/me", stranger.getAccessToken()).status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("every device is signed out, the caller's and the others', unexpired tokens included")
    void everyDeviceIsSignedOut() {
        assertThat(post("/v1/me/delete", phone.getAccessToken(), passwordBody()).status())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // The television's access token is still within its fifteen minutes.
        Response tvCall = get("/v1/me/devices", television.getAccessToken());
        assertThat(tvCall.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tvCall.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(get("/v1/me", phone.getAccessToken()).status()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Response refresh = post("/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + television.getRefreshToken() + "\"}");
        assertThat(refresh.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh.body()).contains("\"code\":\"REFRESH_TOKEN_INVALID\"");
    }

    @Test
    @DisplayName("the email is free again: registering it creates a new, empty account")
    void theEmailCanRegisterAgain() {
        post("/v1/me/delete", phone.getAccessToken(), passwordBody());

        assertThat(users.findByEmail(user.email())).isEmpty();
        UserRow again = users.insert(UUID.randomUUID(), user.email(), passwords.hash(PASSWORD), null, "fr");
        assertThat(again.id()).isNotEqualTo(user.id());
    }

    // ---- helpers ------------------------------------------------------------

    private static String passwordBody() {
        return "{\"password\":\"" + PASSWORD + "\"}";
    }

    /** Rows pointing at the account or at one of its sources, in every table that can. */
    private long rowsOfTheAccount() {
        List<Column> columns = jdbc.sql("""
                SELECT table_name, column_name
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND column_name IN ('user_id', 'source_id')
                """)
                .query((rs, n) -> new Column(rs.getString("table_name"), rs.getString("column_name")))
                .list();
        assertThat(columns).extracting(Column::table).contains("source", "device", "refresh_token",
                "favorite", "playback_progress", "watchlist_item", "channel", "entitlement");

        long total = jdbc.sql("SELECT count(*) FROM \"user\" WHERE id = :id")
                .param("id", user.id()).query(Long.class).single();
        for (Column column : columns) {
            // Names come from the catalogue, never from input.
            UUID value = column.name().equals("user_id") ? user.id() : sourceId;
            total += jdbc.sql("SELECT count(*) FROM \"" + column.table() + "\" WHERE "
                            + column.name() + " = :value")
                    .param("value", value).query(Long.class).single();
        }
        return total;
    }

    private record Column(String table, String name) {
    }

    private Response get(String path, String token) {
        return exchange(RestClient.create("http://localhost:" + port).get().uri(path), token);
    }

    private Response post(String path, String token, String json) {
        return exchange(RestClient.create("http://localhost:" + port).post().uri(path)
                .contentType(MediaType.APPLICATION_JSON).body(json), token);
    }

    private Response exchange(RestClient.RequestHeadersSpec<?> spec, String token) {
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        return spec.exchange((request, response) -> new Response(
                        HttpStatus.valueOf(response.getStatusCode().value()),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)),
                false);
    }

    private record Response(HttpStatus status, String body) {
    }
}
