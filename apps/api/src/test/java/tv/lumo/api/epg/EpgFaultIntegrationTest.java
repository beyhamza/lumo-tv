package tv.lumo.api.epg;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * {@code LUMO_EPG_FAULT=503} through HTTP (I-5, S9-07-03).
 *
 * <p>Proves the wiring, not the gate: with the environment variable set, the
 * two guide reads answer the injected status as an RFC 7807 body, and an
 * unrelated read on the same account is untouched. That last assertion is the
 * point of the injection — "the guide is refused while the rest of the screen
 * is fine" is the case GD-10 has to be played against, and a fault that took
 * the whole API down would not reproduce it.
 *
 * <p>The gate's own rules (unset, {@code 0}, a non-5xx value) are
 * {@code EpgFaultInjectionTest}'s; a second Spring context for them would cost
 * more than it proves.
 */
@Import(PostgresContainerInitializer.class)
@TestPropertySource(properties = "LUMO_EPG_FAULT=503")
class EpgFaultIntegrationTest extends PostgresIntegrationTest {

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

    private String token;
    private UUID sourceId;

    @BeforeEach
    void oneAccountWithOneSource() {
        UserRow user = users.insert(UUID.randomUUID(),
                "fault-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Fault Test", "fr");
        UUID deviceId = devices.insert(user.id(), Platform.WEB.getValue(), "lumo.tv", null, "0.2.0");
        token = sessions.openSession(user, deviceId).getAccessToken();

        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Guide", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/one.m3u", "https://guide.example/one.xml", null, null);
    }

    @Test
    @DisplayName("I-5 — armé : les deux lectures du guide refusent en 503, le reste répond")
    void theGuideReadsFailWhileTheAccountStaysReadable() {
        UUID channelId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC);
        String window = "?channelIds=" + channelId
                + "&from=" + from + "&to=" + from.plusHours(1);

        Response grid = get("/v1/sources/" + sourceId + "/epg" + window);
        assertThat(grid.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(grid.body())
                .contains("\"code\":\"INTERNAL_ERROR\"")
                .contains("LUMO_EPG_FAULT=503");

        Response day = get("/v1/channels/" + channelId + "/epg");
        assertThat(day.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(day.body()).contains("\"code\":\"INTERNAL_ERROR\"");

        // The fault is scoped to the guide: the account's own source list still
        // answers, which is what the web's distinct-error screen rests on.
        Response sources = get("/v1/sources");
        assertThat(sources.status()).isEqualTo(HttpStatus.OK);
    }

    private Response get(String path) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(path)
                .header("Authorization", "Bearer " + token)
                .exchange((request, response) -> new Response(
                        HttpStatus.valueOf(response.getStatusCode().value()),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)),
                        false);
    }

    private record Response(HttpStatus status, String body) {
    }
}
