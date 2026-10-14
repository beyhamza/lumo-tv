package tv.lumo.api.userdata;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.MultiSourceAccountsFixture;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * The watchlist over HTTP (S11-01, ADR 0011): identity, order, disappearance,
 * cascade, isolation, and above all "a retry is not a new intention".
 *
 * <p>Over HTTP rather than against the service because half of what is tested
 * lives in the seam: the {@code Idempotency-Key} header, {@code 201} against
 * {@code 200}, a replayed {@code 204}, a missing header answering {@code 400}.
 *
 * <p>Catalogue rows are inserted directly, as in {@link MultiSourceAccountsFixture}:
 * ingestion would mean an outbound request. A film "disappearing" is its row
 * being deleted, which is exactly what a completed synchronisation does to a
 * film it no longer saw ({@code CatalogWriteRepository.deleteVodNotIn}).
 */
@Import(PostgresContainerInitializer.class)
class WatchlistIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository users;
    @Autowired private SourceRepository sources;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionService sessions;
    @Autowired private UserdataService userdata;
    @Autowired private JdbcClient jdbc;
    @Autowired private ObjectMapper objectMapper;

    private MultiSourceAccountsFixture.Dataset data;
    private String a;
    private String b;

    @BeforeEach
    void seed() {
        data = new MultiSourceAccountsFixture(users, sources, devices, sessions, userdata, jdbc).seed();
        a = data.a().accessToken();
        b = data.b().accessToken();
    }

    // ---- add, list, order ---------------------------------------------------

    @Test
    @DisplayName("an add answers 201 with the item, available, and the list shows it")
    void addThenList() {
        UUID film = film(data.sourceA1(), "ext-1", "Film neutre 1");

        Response added = add(a, UUID.randomUUID(), "VOD", film);

        assertThat(added.status()).isEqualTo(201);
        JsonNode item = added.json();
        assertThat(item.get("available").asBoolean()).isTrue();
        assertThat(item.get("content_id").asString()).isEqualTo(film.toString());
        assertThat(item.get("source_id").asString()).isEqualTo(data.sourceA1().toString());
        assertThat(item.get("title").asString()).isEqualTo("Film neutre 1");
        assertThat(ids(list(a, ""))).containsExactly(item.get("id").asString());
    }

    @Test
    @DisplayName("most recent add first; a series is one item")
    void mostRecentFirst() {
        String first = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f1", "Film 1")).id();
        String second = add(a, UUID.randomUUID(), "SERIES", series(data.sourceA1(), "s1", "Série 1")).id();
        String third = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f2", "Film 2")).id();

        assertThat(ids(list(a, ""))).containsExactly(third, second, first);
    }

    @Test
    @DisplayName("filters: by source (WL-14), by type, and content_types ignores the type filter (WL-17)")
    void filters() {
        String filmA1 = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f1", "Film A1")).id();
        String seriesA1 = add(a, UUID.randomUUID(), "SERIES", series(data.sourceA1(), "s1", "Série A1")).id();
        String filmA2 = add(a, UUID.randomUUID(), "VOD", film(data.sourceA2(), "f1", "Film A2")).id();

        JsonNode a1 = list(a, "?sourceId=" + data.sourceA1());
        assertThat(ids(a1)).containsExactly(seriesA1, filmA1);
        assertThat(a1.get("content_types").toString()).isEqualTo("[\"VOD\",\"SERIES\"]");

        JsonNode a1Films = list(a, "?sourceId=" + data.sourceA1() + "&contentType=VOD");
        assertThat(ids(a1Films)).containsExactly(filmA1);
        assertThat(a1Films.get("total_elements").asLong()).isEqualTo(1);
        assertThat(a1Films.get("content_types").toString()).isEqualTo("[\"VOD\",\"SERIES\"]");

        JsonNode a2 = list(a, "?sourceId=" + data.sourceA2());
        assertThat(ids(a2)).containsExactly(filmA2);
        assertThat(a2.get("content_types").toString()).isEqualTo("[\"VOD\"]");
    }

    // ---- a retry is not a new intention (WL-10, WL-11, WL-12) ---------------

    @Test
    @DisplayName("WL-11: replaying an add returns the recorded answer and changes nothing")
    void replayedAddChangesNothing() {
        UUID film = film(data.sourceA1(), "ext-1", "Film 1");
        UUID key = UUID.randomUUID();

        Response first = add(a, key, "VOD", film);
        Response replay = add(a, key, "VOD", film);

        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(list(a, "").get("total_elements").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a second add under a new key answers 200, and the item keeps its added_at and rank")
    void secondAddKeepsRank() {
        UUID film = film(data.sourceA1(), "f1", "Film 1");
        Response first = add(a, UUID.randomUUID(), "VOD", film);
        String later = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f2", "Film 2")).id();

        Response again = add(a, UUID.randomUUID(), "VOD", film);

        assertThat(again.status()).isEqualTo(200);
        assertThat(again.json().get("added_at").asString())
                .isEqualTo(first.json().get("added_at").asString());
        assertThat(ids(list(a, ""))).containsExactly(later, first.id());
    }

    @Test
    @DisplayName("WL-12: a delayed retry of an add does not undo a removal accepted since")
    void delayedRetryDoesNotUndoANewerRemoval() {
        UUID film = film(data.sourceA1(), "f1", "Film 1");
        UUID addKey = UUID.randomUUID();
        String itemId = add(a, addKey, "VOD", film).id();
        // The phone's response was lost; meanwhile the television removes the item.
        assertThat(remove(a, UUID.randomUUID(), itemId).status()).isEqualTo(204);

        Response retry = add(a, addKey, "VOD", film);

        assertThat(retry.status()).isEqualTo(201);
        assertThat(list(a, "").get("total_elements").asLong()).isZero();
    }

    @Test
    @DisplayName("WL-10: a replayed removal answers 204 again; a new removal of the same item answers 404")
    void replayedRemovalIsNotSomebodyElsesRemoval() {
        String itemId = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f1", "Film 1")).id();
        UUID key = UUID.randomUUID();

        assertThat(remove(a, key, itemId).status()).isEqualTo(204);
        assertThat(remove(a, key, itemId).status()).isEqualTo(204);

        Response fresh = remove(a, UUID.randomUUID(), itemId);
        assertThat(fresh.status()).isEqualTo(404);
        assertThat(fresh.body()).contains("WATCHLIST_ITEM_NOT_FOUND");
    }

    @Test
    @DisplayName("one key for two different requests is refused (409 IDEMPOTENCY_KEY_REUSED)")
    void keyReusedForAnotherRequest() {
        UUID key = UUID.randomUUID();
        add(a, key, "VOD", film(data.sourceA1(), "f1", "Film 1"));

        Response other = add(a, key, "VOD", film(data.sourceA1(), "f2", "Film 2"));

        assertThat(other.status()).isEqualTo(409);
        assertThat(other.body()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(list(a, "").get("total_elements").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a request that failed was not accepted: its key is not recorded")
    void failedRequestRecordsNothing() {
        UUID key = UUID.randomUUID();
        assertThat(add(a, key, "VOD", UUID.randomUUID()).status()).isEqualTo(404);

        assertThat(add(a, key, "VOD", film(data.sourceA1(), "f1", "Film 1")).status()).isEqualTo(201);
    }

    @Test
    @DisplayName("a key past its 24 hours is forgotten: the same key is a new request")
    void expiredKeyIsForgotten() {
        String itemId = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f1", "Film 1")).id();
        UUID key = UUID.randomUUID();
        assertThat(remove(a, key, itemId).status()).isEqualTo(204);
        jdbc.sql("UPDATE watchlist_intention SET accepted_at = now() - interval '25 hours' "
                        + "WHERE idempotency_key = :key")
                .param("key", key)
                .update();

        assertThat(remove(a, key, itemId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("a missing or malformed Idempotency-Key answers 400, not 500")
    void keyIsRequired() {
        UUID film = film(data.sourceA1(), "f1", "Film 1");
        String body = "{\"content_type\":\"VOD\",\"content_id\":\"" + film + "\"}";

        Response missing = send(HttpMethod.POST, "/v1/me/watchlist", a, null, body);
        Response malformed = send(HttpMethod.POST, "/v1/me/watchlist", a, "not-a-uuid", body);

        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body()).contains("VALIDATION_FAILED");
        assertThat(malformed.status()).isEqualTo(400);
        assertThat(list(a, "").get("total_elements").asLong()).isZero();
    }

    // ---- disappearance, identity, cascade (WL-01..05, WL-15, WL-16) ---------

    @Test
    @DisplayName("WL-01/03: a film gone after a sync stays, unavailable, with its title, and can be removed")
    void disappearedFilmStaysUnavailable() {
        UUID film = film(data.sourceA1(), "f1", "Film disparu");
        String itemId = add(a, UUID.randomUUID(), "VOD", film).id();

        jdbc.sql("DELETE FROM vod_item WHERE id = :id").param("id", film).update();

        JsonNode item = list(a, "").get("items").get(0);
        assertThat(item.get("available").asBoolean()).isFalse();
        assertThat(item.get("content_id").isNull()).isTrue();
        assertThat(item.get("title").asString()).isEqualTo("Film disparu");
        assertThat(remove(a, UUID.randomUUID(), itemId).status()).isEqualTo(204);
        assertThat(list(a, "").get("total_elements").asLong()).isZero();
    }

    @Test
    @DisplayName("WL-02: a series gone after a sync is one unavailable card")
    void disappearedSeriesIsOneCard() {
        UUID series = series(data.sourceA1(), "s1", "Série disparue");
        add(a, UUID.randomUUID(), "SERIES", series);

        jdbc.sql("DELETE FROM series WHERE id = :id").param("id", series).update();

        JsonNode page = list(a, "");
        assertThat(page.get("total_elements").asLong()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("available").asBoolean()).isFalse();
        assertThat(page.get("content_types").toString()).isEqualTo("[\"SERIES\"]");
    }

    @Test
    @DisplayName("WL-15: the same content coming back is the same item, at its place, with its new id")
    void returningContentResumesItsCard() {
        UUID film = film(data.sourceA1(), "f1", "Film 1");
        String first = add(a, UUID.randomUUID(), "VOD", film).id();
        String later = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f2", "Film 2")).id();
        jdbc.sql("DELETE FROM vod_item WHERE id = :id").param("id", film).update();

        UUID back = film(data.sourceA1(), "f1", "Film 1");

        JsonNode page = list(a, "");
        assertThat(ids(page)).containsExactly(later, first);
        assertThat(page.get("items").get(1).get("available").asBoolean()).isTrue();
        assertThat(page.get("items").get(1).get("content_id").asString()).isEqualTo(back.toString());
    }

    @Test
    @DisplayName("WL-16: a different content with the same title is not merged")
    void homonymIsNotMerged() {
        UUID film = film(data.sourceA1(), "f1", "Même titre");
        add(a, UUID.randomUUID(), "VOD", film);
        jdbc.sql("DELETE FROM vod_item WHERE id = :id").param("id", film).update();

        film(data.sourceA1(), "f9", "Même titre");

        assertThat(list(a, "").get("items").get(0).get("available").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("WL-05: deleting the source deletes its items, they do not linger as unavailable")
    void sourceDeletionCascades() {
        add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f1", "Film A1"));
        String keep = add(a, UUID.randomUUID(), "VOD", film(data.sourceA2(), "f1", "Film A2")).id();

        assertThat(send(HttpMethod.DELETE, "/v1/sources/" + data.sourceA1(), a, null, null).status())
                .isEqualTo(204);

        assertThat(ids(list(a, ""))).containsExactly(keep);
    }

    @Test
    @DisplayName("WL-13 / G5: saving progress neither removes the item nor moves it")
    void progressDoesNotTouchTheList() {
        UUID film = film(data.sourceA1(), "f1", "Film 1");
        String first = add(a, UUID.randomUUID(), "VOD", film).id();
        String second = add(a, UUID.randomUUID(), "VOD", film(data.sourceA1(), "f2", "Film 2")).id();

        Response saved = send(HttpMethod.PUT, "/v1/me/progress", a, null,
                "{\"source_id\":\"" + data.sourceA1() + "\",\"item_type\":\"VOD\",\"item_ref\":\""
                        + film + "\",\"position_ms\":600000,\"duration_ms\":600000}");

        assertThat(saved.status()).isEqualTo(200);
        assertThat(ids(list(a, ""))).containsExactly(second, first);
    }

    // ---- isolation ----------------------------------------------------------

    @Test
    @DisplayName("isolation: B can neither list, add nor remove anything of A's (404, never 403)")
    void isolation() {
        UUID filmOfA = film(data.sourceA1(), "f1", "Film de A");
        String itemOfA = add(a, UUID.randomUUID(), "VOD", filmOfA).id();

        assertThat(list(b, "").get("total_elements").asLong()).isZero();
        assertThat(list(b, "?sourceId=" + data.sourceA1()).get("total_elements").asLong()).isZero();

        Response addOfA = add(b, UUID.randomUUID(), "VOD", filmOfA);
        assertThat(addOfA.status()).isEqualTo(404);
        assertThat(addOfA.body()).contains("VOD_ITEM_NOT_FOUND");
        assertThat(remove(b, UUID.randomUUID(), itemOfA).status()).isEqualTo(404);

        assertThat(ids(list(a, ""))).containsExactly(itemOfA);
    }

    @Test
    @DisplayName("isolation: the same key used by two accounts is two keys")
    void keysAreScopedToTheAccount() {
        UUID key = UUID.randomUUID();
        assertThat(add(a, key, "VOD", film(data.sourceA1(), "f1", "Film de A")).status()).isEqualTo(201);

        UUID filmOfB = film(data.sourceB1(), "f1", "Film de B");
        assertThat(add(b, key, "VOD", filmOfB).status()).isEqualTo(201);
    }

    // ---- helpers ------------------------------------------------------------

    private UUID film(UUID sourceId, String externalId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO vod_item (id, source_id, external_id, name, stream_url)
                VALUES (:id, :sourceId, :externalId, :name, 'https://stream.example/film.mp4')
                """)
                .param("id", id)
                .param("sourceId", sourceId)
                .param("externalId", externalId)
                .param("name", name)
                .update();
        return id;
    }

    private UUID series(UUID sourceId, String externalId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO series (id, source_id, external_id, name)
                VALUES (:id, :sourceId, :externalId, :name)
                """)
                .param("id", id)
                .param("sourceId", sourceId)
                .param("externalId", externalId)
                .param("name", name)
                .update();
        return id;
    }

    private Response add(String token, UUID key, String type, UUID contentId) {
        return send(HttpMethod.POST, "/v1/me/watchlist", token, key.toString(),
                "{\"content_type\":\"" + type + "\",\"content_id\":\"" + contentId + "\"}");
    }

    private Response remove(String token, UUID key, String itemId) {
        return send(HttpMethod.DELETE, "/v1/me/watchlist/" + itemId, token, key.toString(), null);
    }

    private JsonNode list(String token, String query) {
        Response response = send(HttpMethod.GET, "/v1/me/watchlist" + query, token, null, null);
        assertThat(response.status()).isEqualTo(200);
        return response.json();
    }

    private static List<String> ids(JsonNode page) {
        return page.get("items").valueStream().map(item -> item.get("id").asString()).toList();
    }

    private Response send(HttpMethod method, String path, String token, String key, String json) {
        RestClient.RequestBodySpec spec = RestClient.create("http://localhost:" + port)
                .method(method).uri(path)
                .header("Authorization", "Bearer " + token);
        if (key != null) {
            spec.header("Idempotency-Key", key);
        }
        if (json != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return spec.exchange((request, response) -> new Response(
                response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                objectMapper));
    }

    private record Response(int status, String body, ObjectMapper mapper) {
        JsonNode json() {
            return mapper.readTree(body);
        }

        String id() {
            return json().get("id").asString();
        }
    }
}
