package tv.lumo.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
 * {@code GET /sources/{id}/epg} through HTTP, against the contract (US-16, lot
 * C1, acceptance cases C1-01 to C1-06 and the C4 rule).
 *
 * <p>Through HTTP rather than against the service, because most of what is
 * under test is the boundary: how a batch binds, which code a refusal carries,
 * and that a {@code 422} body holds no programme. The rows are seeded directly
 * in SQL — the ingestion that would normally write them is exercised in
 * {@code EpgImportRecordIntegrationTest} — so each case controls exactly which
 * programmes exist, down to the minute.
 *
 * <p>Two sources on the same account and a third on another, on purpose: the
 * refusals this operation promises are about <em>this</em> source, not about
 * the account, and the case that regresses quietly is a channel of the user's
 * other source being answered for (C1-04, C1-05).
 *
 * <p>No channel name, logo or stream URL here is real; the tvg_ids are
 * synthetic identifiers.
 */
@Import(PostgresContainerInitializer.class)
class GroupedEpgIntegrationTest extends PostgresIntegrationTest {

    private static final OffsetDateTime ANCHOR =
            OffsetDateTime.now(ZoneOffset.UTC).withNano(0).withSecond(0);

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

    @Autowired
    private ObjectMapper json;

    private String token;
    private UUID userId;
    /** The source under test: has a guide URL. */
    private UUID sourceId;
    /** The same account's other source. */
    private UUID otherSourceId;
    /** Somebody else's source. */
    private UUID foreignSourceId;

    @BeforeEach
    void twoSourcesOnThisAccountAndOneOnAnother() {
        UserRow user = users.insert(UUID.randomUUID(),
                "grid-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Grid Test", "fr");
        userId = user.id();
        UUID deviceId = devices.insert(userId, Platform.WEB.getValue(), "lumo.tv", null, "0.2.0");
        token = sessions.openSession(user, deviceId).getAccessToken();

        sourceId = UUID.randomUUID();
        sources.insert(sourceId, userId, "Guide", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/one.m3u", "https://guide.example/one.xml", null, null);
        otherSourceId = UUID.randomUUID();
        sources.insert(otherSourceId, userId, "Autre", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/two.m3u", "https://guide.example/two.xml", null, null);

        UserRow stranger = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Stranger", "en");
        foreignSourceId = UUID.randomUUID();
        sources.insert(foreignSourceId, stranger.id(), "Étrangère", SourceKind.M3U_URL, null, null,
                null, "https://playlist.example/three.m3u", null, null, null);
    }

    // ---- C1-01: the batch, whole and in order ----------------------------------

    @Test
    @DisplayName("C1-01 — cent chaînes rendent cent entrées, dans l'ordre demandé, listes vides comprises")
    void aHundredChannelsAnswerAHundredEntriesInRequestOrder() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            // Every third channel has no tvg_id; every other mapped one has a
            // programme. The rest is mapped and empty — a normal answer.
            String tvgId = i % 3 == 0 ? null : "grid-" + i + ".test";
            UUID id = channel(sourceId, tvgId);
            if (tvgId != null && i % 2 == 0) {
                programme(sourceId, tvgId, ANCHOR, ANCHOR.plusHours(1), "Programme " + i);
            }
            ids.add(id);
        }
        Collections.shuffle(ids, new Random(16));

        Response response = grid(sourceId, ids, ANCHOR, ANCHOR.plusHours(3));

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        JsonNode channels = response.json().get("channels");
        assertThat(channels).hasSize(100);
        for (int i = 0; i < 100; i++) {
            JsonNode entry = channels.get(i);
            assertThat(entry.get("channel_id").stringValue())
                    .as("entry %d keeps the requested order", i)
                    .isEqualTo(ids.get(i).toString());
            assertThat(entry.get("programmes").isArray()).isTrue();
            assertThat(entry.get("mapping_status").stringValue()).isIn("MAPPED", "NO_TVG_ID");
        }
        // Empty lists are entries, not holes.
        long empty = 0;
        long noTvgId = 0;
        for (JsonNode entry : channels) {
            if (entry.get("programmes").isEmpty()) empty++;
            if ("NO_TVG_ID".equals(entry.get("mapping_status").stringValue())) noTvgId++;
        }
        assertThat(noTvgId).isEqualTo(34);
        assertThat(empty).isGreaterThan(noTvgId);
        assertThat(response.json().get("source_id").stringValue()).isEqualTo(sourceId.toString());
    }

    @Test
    @DisplayName("C1-01 — cent une chaînes : 400, rien n'est lu")
    void aHundredAndOneIsRefused() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ids.add(channel(sourceId, "many-" + i + ".test"));
        }

        Response response = grid(sourceId, ids, ANCHOR, ANCHOR.plusHours(1));

        assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    // ---- C1-02: parameters --------------------------------------------------

    @Test
    @DisplayName("C1-02 — liste vide, doublon, UUID invalide : 400 VALIDATION_FAILED")
    void malformedBatchesAreRefused() {
        UUID a = channel(sourceId, "a.test");

        assertValidationFailed(get("/v1/sources/" + sourceId + "/epg"), "no channelIds at all");
        assertValidationFailed(get("/v1/sources/" + sourceId + "/epg?channelIds="), "empty channelIds");
        assertValidationFailed(get("/v1/sources/" + sourceId + "/epg?channelIds=" + a + "&channelIds=" + a),
                "a duplicate");
        assertValidationFailed(get("/v1/sources/" + sourceId + "/epg?channelIds=not-a-uuid"),
                "a malformed identifier");
    }

    @Test
    @DisplayName("C1-02 — durée nulle, négative ou de plus de 96 h : 400 VALIDATION_FAILED")
    void invalidWindowsAreRefused() {
        List<UUID> ids = List.of(channel(sourceId, "w.test"));

        assertValidationFailed(grid(sourceId, ids, ANCHOR, ANCHOR), "to == from");
        assertValidationFailed(grid(sourceId, ids, ANCHOR, ANCHOR.minusMinutes(1)), "to before from");
        assertValidationFailed(grid(sourceId, ids, ANCHOR, ANCHOR.plusHours(96).plusSeconds(1)),
                "wider than 96 h");
        // The bound itself is allowed: the rule is <=, and four days is what the
        // single-channel operation has always accepted.
        assertThat(grid(sourceId, ids, ANCHOR, ANCHOR.plusHours(96)).status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("les bornes par défaut sont renvoyées : `from` = maintenant capturé une fois, `to` = from + 24 h")
    void defaultsAreEchoed() {
        List<UUID> ids = List.of(channel(sourceId, "d.test"));

        Response response = grid(sourceId, ids, null, null);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        OffsetDateTime from = OffsetDateTime.parse(response.json().get("from").stringValue());
        OffsetDateTime to = OffsetDateTime.parse(response.json().get("to").stringValue());
        OffsetDateTime generatedAt = OffsetDateTime.parse(response.json().get("generated_at").stringValue());
        assertThat(Duration.between(from, to)).isEqualTo(Duration.ofHours(24));
        // One clock reading serves both: the default `from` IS `generated_at`.
        assertThat(from).isEqualTo(generatedAt);
    }

    // ---- C1-03: boundaries ----------------------------------------------------

    @Test
    @DisplayName("C1-03 — finir à `from` ou commencer à `to` exclut ; chevaucher une borne inclut, horaires complets")
    void boundariesAreHalfOpen() {
        UUID id = channel(sourceId, "edge.test");
        OffsetDateTime from = ANCHOR;
        OffsetDateTime to = ANCHOR.plusHours(2);
        programme(sourceId, "edge.test", from.minusHours(1), from, "Ends at from");
        programme(sourceId, "edge.test", to, to.plusHours(1), "Starts at to");
        programme(sourceId, "edge.test", from.minusMinutes(30), from.plusMinutes(30), "Straddles from");
        programme(sourceId, "edge.test", to.minusMinutes(30), to.plusMinutes(30), "Straddles to");

        Response response = grid(sourceId, List.of(id), from, to);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        JsonNode programmes = response.json().get("channels").get(0).get("programmes");
        assertThat(programmes).hasSize(2);
        assertThat(programmes.get(0).get("title").stringValue()).isEqualTo("Straddles from");
        assertThat(programmes.get(1).get("title").stringValue()).isEqualTo("Straddles to");
        // Full times, not clipped to the window: "started 30 minutes ago" is the
        // information a grid draws.
        assertThat(OffsetDateTime.parse(programmes.get(0).get("starts_at").stringValue()))
                .isEqualTo(from.minusMinutes(30));
        assertThat(OffsetDateTime.parse(programmes.get(1).get("ends_at").stringValue()))
                .isEqualTo(to.plusMinutes(30));
    }

    // ---- C1-04: ownership -----------------------------------------------------

    @Test
    @DisplayName("C1-04 — source d'un autre compte : 404 SOURCE_NOT_FOUND, sans données")
    void foreignSourceIsNotFound() {
        UUID theirs = channel(foreignSourceId, "theirs.test");
        programme(foreignSourceId, "theirs.test", ANCHOR, ANCHOR.plusHours(1), "Their programme");

        Response response = grid(foreignSourceId, List.of(theirs), ANCHOR, ANCHOR.plusHours(1));

        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.body())
                .contains("\"code\":\"SOURCE_NOT_FOUND\"")
                .doesNotContain("Their programme")
                .doesNotContain(theirs.toString());
    }

    @Test
    @DisplayName("C1-04 — une chaîne de l'autre source du même compte dans le lot : 404 CHANNEL_NOT_FOUND, lot entier refusé")
    void aChannelOfAnotherSourceOfTheSameAccountRefusesTheWholeBatch() {
        UUID mine = channel(sourceId, "mine.test");
        programme(sourceId, "mine.test", ANCHOR, ANCHOR.plusHours(1), "My programme");
        UUID other = channel(otherSourceId, "other.test");

        Response response = grid(sourceId, List.of(mine, other), ANCHOR, ANCHOR.plusHours(1));

        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND);
        // The whole batch, and nothing about which identifier or what the valid
        // half would have held.
        assertThat(response.body())
                .contains("\"code\":\"CHANNEL_NOT_FOUND\"")
                .doesNotContain("My programme")
                .doesNotContain(mine.toString())
                .doesNotContain(other.toString())
                .doesNotContain("channels");
    }

    @Test
    @DisplayName("C1-04 — un identifiant inconnu refuse aussi le lot")
    void anUnknownChannelRefusesTheBatch() {
        UUID mine = channel(sourceId, "known.test");

        Response response = grid(sourceId, List.of(mine, UUID.randomUUID()), ANCHOR, ANCHOR.plusHours(1));

        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.body()).contains("\"code\":\"CHANNEL_NOT_FOUND\"");
    }

    // ---- C1-05: shared tvg_ids ---------------------------------------------------

    @Test
    @DisplayName("C1-05 — deux chaînes partageant un tvg_id reçoivent chacune leur entrée ; un autre source avec le même texte n'est jamais mélangée")
    void sharedTvgIdsGetOneEntryEachAndNeverCrossSources() {
        UUID first = channel(sourceId, "shared.test");
        UUID second = channel(sourceId, "shared.test");
        UUID programmeId = programme(sourceId, "shared.test", ANCHOR, ANCHOR.plusHours(1), "Shared");
        // Same tvg_id text on the same account's other source, with its own guide.
        UUID elsewhere = channel(otherSourceId, "shared.test");
        programme(otherSourceId, "shared.test", ANCHOR, ANCHOR.plusHours(1), "Not yours");

        Response response = grid(sourceId, List.of(first, second), ANCHOR, ANCHOR.plusHours(1));

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        JsonNode channels = response.json().get("channels");
        assertThat(channels).hasSize(2);
        for (JsonNode entry : channels) {
            assertThat(entry.get("programmes")).hasSize(1);
            // The same programme id under both: a client must not collapse a row
            // of the grid in the name of de-duplicating ids.
            assertThat(entry.get("programmes").get(0).get("id").stringValue())
                    .isEqualTo(programmeId.toString());
        }
        assertThat(response.body()).doesNotContain("Not yours");

        // And from the other side: its own guide, and only its own.
        Response theirs = grid(otherSourceId, List.of(elsewhere), ANCHOR, ANCHOR.plusHours(1));
        assertThat(theirs.status()).isEqualTo(HttpStatus.OK);
        assertThat(theirs.body()).contains("Not yours").doesNotContain("\"Shared\"");
    }

    // ---- C1-06: the two ceilings ----------------------------------------------------

    @Test
    @DisplayName("C1-06 — 5 000 occurrences passent entières ; 5 001 lignes : 422 sans aucun programme")
    void theOccurrenceCeilingIsExactOnDistinctRows() {
        UUID id = channel(sourceId, "bulk.test");
        // 5 000 one-minute programmes fit in 96 h with room to spare.
        oneMinuteProgrammes(sourceId, "bulk.test", ANCHOR, 5_000);

        Response under = grid(sourceId, List.of(id), ANCHOR, ANCHOR.plusHours(96));
        assertThat(under.status()).isEqualTo(HttpStatus.OK);
        assertThat(under.json().get("channels").get(0).get("programmes")).hasSize(5_000);

        programme(sourceId, "bulk.test", ANCHOR.plusMinutes(5_000), ANCHOR.plusMinutes(5_001), "One too many");

        Response over = grid(sourceId, List.of(id), ANCHOR, ANCHOR.plusHours(96));
        // By number: Spring 7 spells 422 UNPROCESSABLE_CONTENT and keeps the old
        // constant as a distinct, deprecated value.
        assertThat(over.status().value()).isEqualTo(422);
        assertThat(over.body())
                .contains("\"code\":\"EPG_WINDOW_TOO_LARGE\"")
                .doesNotContain("\"programmes\"")
                .doesNotContain("\"channels\"");
    }

    @Test
    @DisplayName("C1-06 — un programme partagé compte une fois par chaîne : 100 × 50 = 5 000 passe, une de plus refuse")
    void theOccurrenceCeilingCountsSharedProgrammesPerChannel() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(channel(sourceId, "hub.test"));
        }
        oneMinuteProgrammes(sourceId, "hub.test", ANCHOR, 50);

        Response under = grid(sourceId, ids, ANCHOR, ANCHOR.plusHours(1));
        assertThat(under.status()).isEqualTo(HttpStatus.OK);
        int occurrences = 0;
        for (JsonNode entry : under.json().get("channels")) {
            occurrences += entry.get("programmes").size();
        }
        // Nothing trimmed to fit: exactly 5 000 came back.
        assertThat(occurrences).isEqualTo(5_000);

        // Fifty rows became fifty-one: 100 × 51 occurrences, over a query that
        // returned nowhere near 5 001 rows. This is the count the LIMIT alone
        // cannot make.
        programme(sourceId, "hub.test", ANCHOR.plusMinutes(50), ANCHOR.plusMinutes(51), "Fifty-first");

        Response over = grid(sourceId, ids, ANCHOR, ANCHOR.plusHours(1));
        // By number: Spring 7 spells 422 UNPROCESSABLE_CONTENT and keeps the old
        // constant as a distinct, deprecated value.
        assertThat(over.status().value()).isEqualTo(422);
        assertThat(over.body()).contains("\"code\":\"EPG_WINDOW_TOO_LARGE\"").doesNotContain("\"channels\"");
    }

    @Test
    @DisplayName("C1-06 — des descriptions longues franchissent 4 Mio bien avant 5 000 occurrences : 422, rien de tronqué")
    void theByteCeilingIsIndependentOfTheOccurrenceCeiling() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(channel(sourceId, "long.test"));
        }
        // The bench's case: 600 occurrences of an 8 192-character description
        // measured 5 075 314 bytes before the envelope.
        String description = "x".repeat(8_192);
        for (int i = 0; i < 6; i++) {
            programme(sourceId, "long.test", ANCHOR.plusMinutes(30L * i), ANCHOR.plusMinutes(30L * (i + 1)),
                    "Long " + i, description);
        }

        Response over = grid(sourceId, ids, ANCHOR, ANCHOR.plusHours(3));
        // By number: Spring 7 spells 422 UNPROCESSABLE_CONTENT and keeps the old
        // constant as a distinct, deprecated value.
        assertThat(over.status().value()).isEqualTo(422);
        assertThat(over.body())
                .contains("\"code\":\"EPG_WINDOW_TOO_LARGE\"")
                .doesNotContain("\"channels\"")
                .doesNotContain(description.substring(0, 64));

        // Half the channels is under the ceiling, and comes back whole: every
        // description at its full length, none summarised to fit.
        Response under = grid(sourceId, ids.subList(0, 40), ANCHOR, ANCHOR.plusHours(3));
        assertThat(under.status()).isEqualTo(HttpStatus.OK);
        assertThat(under.body().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(4 * 1024 * 1024);
        for (JsonNode entry : under.json().get("channels")) {
            assertThat(entry.get("programmes")).hasSize(6);
            for (JsonNode programme : entry.get("programmes")) {
                assertThat(programme.get("description").stringValue()).hasSize(8_192);
            }
        }
    }

    // ---- the import record on the wire ------------------------------------------------

    @Test
    @DisplayName("`epg` décrit l'import et ne date jamais le contenu ; sans URL de guide, configured=false")
    void theImportRecordIsEchoedAsStored() {
        UUID id = channel(sourceId, "meta.test");
        OffsetDateTime success = ANCHOR.minusHours(30);
        jdbc.sql("""
                UPDATE source
                   SET epg_last_success_at = :success, epg_attempt_started_at = :started,
                       epg_attempt_finished_at = :finished, epg_attempt_status = 'FAILED',
                       epg_attempt_id = :attempt
                 WHERE id = :id
                """)
                .param("success", success).param("started", ANCHOR.minusHours(2))
                .param("finished", ANCHOR.minusHours(1)).param("attempt", UUID.randomUUID())
                .param("id", sourceId).update();

        JsonNode epg = grid(sourceId, List.of(id), ANCHOR, ANCHOR.plusHours(1)).json().get("epg");

        assertThat(epg.get("configured").booleanValue()).isTrue();
        assertThat(OffsetDateTime.parse(epg.get("last_successful_import_at").stringValue())).isEqualTo(success);
        assertThat(epg.get("last_attempt_status").stringValue()).isEqualTo("FAILED");
        assertThat(epg.get("last_attempt_started_at").isNull()).isFalse();
        assertThat(epg.get("last_attempt_finished_at").isNull()).isFalse();
        // No URL, no secret, anywhere in the answer.
        assertThat(grid(sourceId, List.of(id), ANCHOR, ANCHOR.plusHours(1)).body())
                .doesNotContain("guide.example").doesNotContain("epg_url");

        // A source of the caller's with no guide URL at all.
        UUID unconfiguredSourceId = UUID.randomUUID();
        sources.insert(unconfiguredSourceId, userId, "Sans guide", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/four.m3u", null, null, null);
        UUID unconfigured = channel(unconfiguredSourceId, "x.test");
        JsonNode none = grid(unconfiguredSourceId, List.of(unconfigured), ANCHOR, ANCHOR.plusHours(1))
                .json().get("epg");
        assertThat(none.get("configured").booleanValue()).isFalse();
        assertThat(none.get("last_attempt_status").stringValue()).isEqualTo("UNKNOWN");
        assertThat(none.get("last_successful_import_at").isNull()).isTrue();
    }

    @Test
    @DisplayName("GET /channels/{id}/epg porte le même `epg`, et refuse une durée nulle")
    void theSingleChannelOperationCarriesTheRecordToo() {
        UUID id = channel(sourceId, "single.test");
        programme(sourceId, "single.test", ANCHOR, ANCHOR.plusHours(1), "Single");

        Response response = get("/v1/channels/" + id + "/epg?from=" + ANCHOR + "&to=" + ANCHOR.plusHours(2));

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.json().get("items")).hasSize(1);
        JsonNode epg = response.json().get("epg");
        assertThat(epg.get("configured").booleanValue()).isTrue();
        assertThat(epg.get("last_attempt_status").stringValue()).isEqualTo("UNKNOWN");

        assertValidationFailed(get("/v1/channels/" + id + "/epg?from=" + ANCHOR + "&to=" + ANCHOR),
                "to == from on the single-channel operation");
    }

    // ---- C4: not gated on READY ---------------------------------------------------------

    @Test
    @DisplayName("C4 — la grille répond pendant une synchronisation, après un échec, et avant le premier succès")
    void answersInEveryStatus() {
        UUID id = channel(sourceId, "status.test");
        programme(sourceId, "status.test", ANCHOR, ANCHOR.plusHours(1), "Still here");

        given("SYNCING", true, null);
        assertThat(grid(sourceId, List.of(id), ANCHOR, ANCHOR.plusHours(1)).body()).contains("Still here");

        given("ERROR", true, "SOURCE_UNREACHABLE");
        assertThat(grid(sourceId, List.of(id), ANCHOR, ANCHOR.plusHours(1)).body()).contains("Still here");

        // No 409 here, unlike the listings: the contract lists none, and a first
        // ingestion that has already written rows has a guide to show.
        given("SYNCING", false, null);
        assertThat(grid(sourceId, List.of(id), ANCHOR, ANCHOR.plusHours(1)).status()).isEqualTo(HttpStatus.OK);
    }

    // ---- seeding --------------------------------------------------------------------------

    private UUID channel(UUID source, String tvgId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, external_id, name, tvg_id, stream_url, position)
                VALUES (:id, :sourceId, :externalId, 'Chaîne test', :tvgId,
                        'https://stream.example/x.m3u8', 0)
                """)
                .param("id", id).param("sourceId", source)
                .param("externalId", "test:" + id).param("tvgId", tvgId).update();
        return id;
    }

    private UUID programme(UUID source, String tvgId, OffsetDateTime from, OffsetDateTime to, String title) {
        return programme(source, tvgId, from, to, title, null);
    }

    private UUID programme(UUID source, String tvgId, OffsetDateTime from, OffsetDateTime to,
                           String title, String description) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO epg_programme (id, source_id, tvg_id, starts_at, ends_at, title, description)
                VALUES (:id, :sourceId, :tvgId, :from, :to, :title, :description)
                """)
                .param("id", id).param("sourceId", source).param("tvgId", tvgId)
                .param("from", from).param("to", to).param("title", title)
                .param("description", description).update();
        return id;
    }

    /** {@code count} consecutive one-minute programmes from {@code from}, in one statement. */
    private void oneMinuteProgrammes(UUID source, String tvgId, OffsetDateTime from, int count) {
        jdbc.sql("""
                INSERT INTO epg_programme (id, source_id, tvg_id, starts_at, ends_at, title)
                SELECT gen_random_uuid(), :sourceId, :tvgId,
                       :from + make_interval(mins => g), :from + make_interval(mins => g + 1),
                       'Minute ' || g
                  FROM generate_series(0, :count - 1) AS g
                """)
                .param("sourceId", source).param("tvgId", tvgId)
                .param("from", from).param("count", count).update();
    }

    /** The source's state as ingestion would have left it, as in {@code PreviousCatalogueIntegrationTest}. */
    private void given(String status, boolean hasCatalogue, String errorCode) {
        jdbc.sql("""
                UPDATE source
                   SET status = CAST(:status AS text),
                       sync_step = CASE WHEN CAST(:status AS text) = 'SYNCING' THEN 'FETCHING_EPG' END,
                       error_code = CAST(:errorCode AS text),
                       last_error_at = CASE WHEN CAST(:errorCode AS text) IS NULL THEN NULL ELSE now() END,
                       last_synced_at = CASE WHEN :hasCatalogue THEN now() - interval '1 day' END
                 WHERE id = :id
                """)
                .param("status", status).param("errorCode", errorCode)
                .param("hasCatalogue", hasCatalogue).param("id", sourceId).update();
    }

    // ---- HTTP ------------------------------------------------------------------------------

    private Response grid(UUID source, List<UUID> channelIds, OffsetDateTime from, OffsetDateTime to) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/v1/sources/" + source + "/epg");
        for (UUID id : channelIds) {
            uri.queryParam("channelIds", id);
        }
        if (from != null) uri.queryParam("from", from.toString());
        if (to != null) uri.queryParam("to", to.toString());
        return get(uri.build().toUriString());
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

    private static void assertValidationFailed(Response response, String what) {
        assertThat(response.status()).as(what).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.body()).as(what).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    private final class Response {
        private final HttpStatus status;
        private final String body;

        Response(HttpStatus status, String body) {
            this.status = status;
            this.body = body;
        }

        HttpStatus status() {
            return status;
        }

        String body() {
            return body;
        }

        JsonNode json() {
            return GroupedEpgIntegrationTest.this.json.readTree(body);
        }
    }
}
