package tv.lumo.api.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * {@code POST /sources/{id}/sync}: what it refuses, and in which order (lot C4).
 *
 * <p>The contract had {@code 429 SOURCE_SYNC_RATE_LIMITED} and {@code Retry-After}
 * for months and nothing sent them. The screens of sprint 8 show "again in 3 min"
 * from that header, so this test is about the header as much as the status.
 *
 * <p>The playlist is served by a loopback server this class starts, which is why
 * the private-address guard is lifted here and nowhere else — the same exception,
 * for the same reason, as {@code IngestionServiceIntegrationTest}.
 */
@Import(PostgresContainerInitializer.class)
@TestPropertySource(properties = "lumo.ingest.allow-private-hosts=true")
class ManualSyncPaceIntegrationTest extends PostgresIntegrationTest {

    private static final String PLAYLIST = """
            #EXTM3U
            #EXTINF:-1 tvg-id="chaine01.test" group-title="Groupe 01",Chaîne 01
            http://127.0.0.1:9/stream/01.m3u8
            #EXTINF:-1 tvg-id="chaine02.test" group-title="Groupe 01",Chaîne 02
            http://127.0.0.1:9/stream/02.m3u8
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private JdbcClient jdbc;

    private HttpServer playlistServer;
    private String playlistUrl;
    private String token;
    private UUID sourceId;

    @BeforeEach
    void servePlaylistAndRegisterSource() throws IOException {
        playlistServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        playlistServer.createContext("/", exchange -> {
            byte[] body = PLAYLIST.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "audio/x-mpegurl");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        playlistServer.start();
        playlistUrl = "http://127.0.0.1:" + playlistServer.getAddress().getPort();

        UserRow user = users.insert(UUID.randomUUID(),
                "pace-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Test", "fr");
        UUID deviceId = devices.insert(user.id(), Platform.WEB.getValue(), "lumo.tv", null, "0.2.0");
        token = sessions.openSession(user, deviceId).getAccessToken();

        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Banc", SourceKind.M3U_URL, null, null, null,
                playlistUrl + "/playlist.m3u", null, null, null);
    }

    @AfterEach
    void stopPlaylistServer() {
        playlistServer.stop(0);
    }

    @Test
    @DisplayName("la seconde demande reçoit 429 et le délai qu'il reste")
    void theSecondRequestIsToldToWait() {
        assertThat(sync().status()).isEqualTo(HttpStatus.ACCEPTED);
        awaitTerminalStatus();

        Response second = sync();

        assertThat(second.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.body()).contains("\"code\":\"SOURCE_SYNC_RATE_LIMITED\"");
        // The shipped interval is five minutes and a few seconds have passed.
        assertThat(Long.parseLong(second.retryAfter())).isBetween(200L, 300L);
    }

    @Test
    @DisplayName("une synchronisation déjà en cours répond 409, et ne coûte pas l'intervalle")
    void inProgressComesBeforeThePace() {
        jdbc.sql("UPDATE source SET status = 'SYNCING', sync_step = 'CONNECTING' WHERE id = :id")
                .param("id", sourceId).update();

        Response busy = sync();
        assertThat(busy.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(busy.body()).contains("\"code\":\"SOURCE_SYNC_IN_PROGRESS\"");

        // "Already refreshing" started nothing, so the next real request is not
        // told to wait for a synchronisation that was never its own.
        jdbc.sql("UPDATE source SET status = 'READY', sync_step = NULL WHERE id = :id")
                .param("id", sourceId).update();
        assertThat(sync().status()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("corriger l'adresse de la source ne compte pas dans la limite")
    void aCredentialChangeDoesNotCount() {
        // A PATCH that changes what gets fetched starts an ingestion by itself.
        Response patched = send(HttpMethod.PATCH, "/v1/sources/" + sourceId,
                "{\"m3u_url\":\"" + playlistUrl + "/corrected.m3u\"}");
        assertThat(patched.status()).isEqualTo(HttpStatus.OK);
        awaitTerminalStatus();

        // Correcting a source must never make anyone wait: the first refresh the
        // user asks for afterwards goes through.
        assertThat(sync().status()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("la source d'un autre compte répond 404, jamais 429")
    void notFoundComesFirst() {
        Response response = send(HttpMethod.POST, "/v1/sources/" + UUID.randomUUID() + "/sync", null);

        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.body()).contains("\"code\":\"SOURCE_NOT_FOUND\"");
    }

    // ---- support ------------------------------------------------------------

    private Response sync() {
        return send(HttpMethod.POST, "/v1/sources/" + sourceId + "/sync", null);
    }

    private void awaitTerminalStatus() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            String status = jdbc.sql("SELECT status FROM source WHERE id = :id")
                    .param("id", sourceId).query(String.class).single();
            if ("READY".equals(status) || "ERROR".equals(status)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("The source never left SYNCING");
    }

    private Response send(HttpMethod method, String path, String json) {
        RestClient.RequestBodySpec request = RestClient.create("http://localhost:" + port)
                .method(method)
                .uri(path)
                .header("Authorization", "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return request.exchange((req, response) -> new Response(
                HttpStatus.valueOf(response.getStatusCode().value()),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                response.getHeaders().getFirst("Retry-After")), false);
    }

    private record Response(HttpStatus status, String body, String retryAfter) {
    }
}
