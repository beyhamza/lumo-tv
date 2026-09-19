package tv.lumo.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
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
 * Reading and playing a catalogue whose source is not {@code READY} (lot C4).
 *
 * <p>Through HTTP rather than against the repositories, because the rules under
 * test are the controller's two guards and nothing else — the rows are in the
 * database in every one of these cases, which is the whole point. Before C4 a
 * source being refreshed answered 409 to every listing while its catalogue sat
 * there intact, and one whose nightly refresh had failed refused to play
 * anything until the next one succeeded.
 *
 * <p>The two permissions are asserted side by side on purpose. They used to be
 * the same test — {@code status == READY} — and the way this regresses is
 * somebody making them the same test again.
 */
@Import(PostgresContainerInitializer.class)
class PreviousCatalogueIntegrationTest extends PostgresIntegrationTest {

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

    private String token;
    private UUID sourceId;
    private UUID channelId;
    private UUID filmId;

    @BeforeEach
    void aSourceWithACatalogue() {
        UserRow user = users.insert(UUID.randomUUID(),
                "previous-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Test", "fr");
        UUID deviceId = devices.insert(user.id(), Platform.WEB.getValue(), "lumo.tv", null, "0.2.0");
        token = sessions.openSession(user, deviceId).getAccessToken();

        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Banc", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/one.m3u", null, null, null);

        channelId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, external_id, name, stream_url, position)
                VALUES (:id, :sourceId, :externalId, 'Chaîne 01', 'https://stream.example/x.m3u8', 0)
                """)
                .param("id", channelId).param("sourceId", sourceId)
                .param("externalId", "test:" + channelId).update();

        filmId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO vod_item (id, source_id, external_id, name, stream_url,
                                      container_extension, position)
                VALUES (:id, :sourceId, :externalId, 'Le Voyage',
                        'https://stream.example/movie.mkv', 'mkv', 0)
                """)
                .param("id", filmId).param("sourceId", sourceId)
                .param("externalId", "test:" + filmId).update();
    }

    // ---- reading ------------------------------------------------------------

    @Test
    @DisplayName("pendant une actualisation, le catalogue précédent se consulte")
    void listingsAnswerWhileSyncing() {
        given("SYNCING", true, null);

        for (String listing : new String[] {"channels", "categories", "vod", "series"}) {
            Response response = get("/v1/sources/" + sourceId + "/" + listing);
            assertThat(response.status()).as(listing).isEqualTo(HttpStatus.OK);
        }
        assertThat(get("/v1/sources/" + sourceId + "/channels").body()).contains("Chaîne 01");
    }

    @Test
    @DisplayName("après un échec, et après une correction d'identifiants, aussi")
    void listingsAnswerAfterAFailureAndWhilePending() {
        given("ERROR", true, "SOURCE_UNREACHABLE");
        assertThat(get("/v1/sources/" + sourceId + "/channels").status()).isEqualTo(HttpStatus.OK);

        // What a PATCH of the credentials leaves behind: PENDING, and the date of
        // the last success still there.
        given("PENDING", true, null);
        assertThat(get("/v1/sources/" + sourceId + "/vod").status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("avant la première ingestion réussie, 409 quel que soit le statut")
    void listingsRefuseUntilTheFirstSuccess() {
        for (String status : new String[] {"PENDING", "SYNCING", "ERROR"}) {
            given(status, false, "ERROR".equals(status) ? "SOURCE_UNREACHABLE" : null);

            Response response = get("/v1/sources/" + sourceId + "/channels");

            // An empty page here would read as "this source has no channels" and
            // the client would stop polling.
            assertThat(response.status()).as(status).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.body()).contains("\"code\":\"SOURCE_NOT_READY\"");
        }
    }

    @Test
    @DisplayName("les compteurs restent affichés pendant une actualisation")
    void countsSurviveAResync() {
        given("SYNCING", true, null);
        assertThat(get("/v1/sources/" + sourceId).body()).contains("\"channel_count\":1");

        given("ERROR", true, "SOURCE_UNREACHABLE");
        assertThat(get("/v1/sources/" + sourceId).body()).contains("\"channel_count\":1");

        // Unknown is not zero: before the first success there is no number to show.
        given("SYNCING", false, null);
        assertThat(get("/v1/sources/" + sourceId).body()).doesNotContain("\"channel_count\":1");
    }

    // ---- playing ------------------------------------------------------------

    @Test
    @DisplayName("consulter n'est pas lire : la lecture reste fermée pendant une ingestion")
    void playbackStaysClosedWhileAnIngestionRuns() {
        for (String status : new String[] {"SYNCING", "PENDING"}) {
            given(status, true, null);

            Response channel = get("/v1/channels/" + channelId + "/playback");
            Response film = get("/v1/vod/" + filmId + "/playback");

            assertThat(channel.status()).as(status).isEqualTo(HttpStatus.CONFLICT);
            assertThat(channel.body()).contains("\"code\":\"SOURCE_NOT_READY\"");
            assertThat(film.status()).as(status).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    @DisplayName("une panne passagère du fournisseur à l'heure de l'actualisation ne bloque plus la lecture")
    void playbackIsOpenAfterATransientFailure() {
        given("ERROR", true, "SOURCE_UNREACHABLE");

        assertThat(get("/v1/channels/" + channelId + "/playback").status()).isEqualTo(HttpStatus.OK);
        assertThat(get("/v1/vod/" + filmId + "/playback").status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("identifiants refusés ou abonnement expiré : la lecture reste fermée, avec ce code-là")
    void playbackStaysClosedWhenTheAccountIsTheProblem() {
        for (String code : new String[] {"SOURCE_AUTH_FAILED", "SOURCE_EXPIRED"}) {
            given("ERROR", true, code);

            Response response = get("/v1/channels/" + channelId + "/playback");

            assertThat(response.status()).as(code).isEqualTo(HttpStatus.CONFLICT);
            // Its own code, not SOURCE_NOT_READY: "your credentials were refused"
            // is the message that tells the user what to do.
            assertThat(response.body()).contains("\"code\":\"" + code + "\"");
        }
    }

    @Test
    @DisplayName("un échec sans catalogue précédent ne donne rien à lire")
    void playbackStaysClosedAfterAFailedFirstIngestion() {
        given("ERROR", false, "SOURCE_UNREACHABLE");

        Response response = get("/v1/channels/" + channelId + "/playback");

        assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.body()).contains("\"code\":\"SOURCE_NOT_READY\"");
    }

    // ---- support ------------------------------------------------------------

    /**
     * Puts the source in a state directly, the way ingestion would have left it.
     *
     * @param hasCatalogue whether an ingestion succeeded before — {@code last_synced_at}
     */
    private void given(String status, boolean hasCatalogue, String errorCode) {
        jdbc.sql("""
                UPDATE source
                   SET status = CAST(:status AS text),
                       sync_step = CASE WHEN CAST(:status AS text) = 'SYNCING' THEN 'PARSING_CHANNELS' END,
                       error_code = CAST(:errorCode AS text),
                       last_error_at = CASE WHEN CAST(:errorCode AS text) IS NULL THEN NULL ELSE now() END,
                       last_synced_at = CASE WHEN :hasCatalogue THEN now() - interval '1 day' END
                 WHERE id = :id
                """)
                .param("status", status)
                .param("errorCode", errorCode)
                .param("hasCatalogue", hasCatalogue)
                .param("id", sourceId)
                .update();
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
