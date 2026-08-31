package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.shared.crypto.CredentialCipher;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Synchronising a whole Xtream source, end to end (debt n° 5).
 *
 * <h2>Why this file exists, and what its absence cost</h2>
 *
 * <p>{@code IngestionService} orchestrates everything a source does — connect,
 * authenticate, channels, films, series, EPG, and the partial failures in
 * between — and until now <b>nothing exercised it</b>. Two hundred and fifty
 * tests, and none of them walked this path.
 *
 * <p>That is not an abstract gap. The {@code source_sync_step_check} constraint
 * enumerated the valid values of {@code sync_step} and never learnt
 * {@code PARSING_VOD} (sprint 5) or {@code PARSING_SERIES} (sprint 6), so
 * {@code markSyncStep} threw a {@code DataIntegrityViolationException} — not an
 * {@code IngestionException} — and slipped past the handler whose whole job is to
 * stop a catalogue's failure from failing its source. <b>Every Xtream source
 * synchronised for two sprints ended in {@code ERROR} and lost its channels with
 * its films</b>, blaming the user's provider. It was found by using the product.
 *
 * <p>{@code SyncStepConstraintTest} closed that class of bug by iterating
 * {@code SyncStep.values()}. This closes the hole underneath it: the path itself.
 *
 * <h2>It runs against the bench's own fixtures, on purpose</h2>
 *
 * <p>The JSON served here is read from {@code apps/web/e2e/bench/fixtures/xtream}
 * — the committed files, not a copy. So the fixtures are load-bearing: a fixture
 * that drifts from what {@code XtreamClient} expects fails this test rather than a
 * qualification session three weeks later.
 *
 * <p>The routing is nginx's, in twenty lines: an action selects a file, and the
 * two single-item actions read <b>two different parameter names</b> and fall back
 * to {@code wrong-id.json} otherwise. Duplicating that much of the bench is
 * honest — it is two rules — and it is what lets this run without Docker for
 * anything but Postgres.
 */
@Import(PostgresContainerInitializer.class)
// The one place the private-address guard is lifted, and it is lifted HERE rather
// than in application-test.yml on purpose. `PrivateAddressGuard` refuses a source
// pointing inside our own network — in production that is how somebody maps it —
// and it must keep refusing for every other test. This class is the exception
// because its panel is a loopback server it started itself.
@TestPropertySource(properties = "lumo.ingest.allow-private-hosts=true")
class IngestionServiceIntegrationTest extends PostgresIntegrationTest {

    /**
     * The bench's fixtures, from the repository rather than from a copy.
     *
     * <p>Relative to {@code apps/api}, which is where Gradle runs this module.
     */
    private static final Path FIXTURES =
            Path.of("..", "web", "e2e", "bench", "fixtures", "xtream");

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private UserRepository users;

    @Autowired
    private CredentialCipher cipher;

    @Autowired
    private JdbcClient jdbc;

    private HttpServer panel;
    private String host;
    private UUID sourceId;

    @BeforeEach
    void startPanelAndRegisterSource() throws IOException {
        panel = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        panel.createContext("/player_api.php", exchange -> {
            byte[] body = fixtureFor(exchange.getRequestURI().getQuery());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        panel.start();
        host = "http://127.0.0.1:" + panel.getAddress().getPort();

        UserRow owner = users.insert(UUID.randomUUID(),
                "ingest-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Ingestion Test", "fr");

        sourceId = UUID.randomUUID();
        // A real sealed password, through the real cipher: `openPassword` unseals
        // it, and a hand-made blob would fail before the first request went out.
        sources.insert(sourceId, owner.id(), "Banc", SourceKind.XTREAM, host,
                "bench", cipher.seal("bench"), null, null, null, null);
    }

    @AfterEach
    void stopPanel() {
        panel.stop(0);
    }

    @Test
    @DisplayName("une source Xtream se synchronise entière et finit READY")
    void synchronisesAWholeSource() {
        assertThat(ingestion.schedule(sourceId)).isTrue();
        awaitTerminalStatus();

        // The case the two-sprint bug would have failed on, and the reason this
        // assertion is first: the source reached the end rather than ERROR.
        assertThat(status()).isEqualTo("READY");
        assertThat(errorCode()).isNull();
        // Nothing is mid-flight any more. A step left behind is what a progress
        // screen would show for ever.
        assertThat(syncStep()).isNull();
    }

    @Test
    @DisplayName("les chaînes, les films et les séries sont tous là après une seule passe")
    void importsAllThreeCatalogues() {
        ingestion.schedule(sourceId);
        awaitTerminalStatus();

        assertThat(count("channel")).isEqualTo(3);
        // Three films are offered and one has no `container_extension`: ADR 0009
        // cannot build a URL for it, so it is dropped rather than imported
        // unplayable. Two is the right answer and three would be the defect.
        assertThat(count("vod_item")).isEqualTo(2);
        assertThat(count("series")).isEqualTo(2);

        // LIVE, VOD and SERIES categories share one table. A synchronisation that
        // replaced by source alone would empty two of the three, which is a bug
        // this project has already had once.
        assertThat(categoryCount("LIVE")).isEqualTo(2);
        assertThat(categoryCount("VOD")).isEqualTo(1);
        assertThat(categoryCount("SERIES")).isEqualTo(1);
    }

    @Test
    @DisplayName("aucun arbre n'est chargé à la synchronisation")
    void loadsNoTreeAtSyncTime() {
        ingestion.schedule(sourceId);
        awaitTerminalStatus();

        // S6-03's ruling, and the one that keeps a panel of fifty thousand series
        // from being fifty thousand requests at every synchronisation. The tree is
        // fetched when somebody opens a series, and `tree_fetched_at` is what says
        // whether that has happened.
        Long stamped = jdbc.sql("SELECT count(*) FROM series WHERE tree_fetched_at IS NOT NULL")
                .query(Long.class).single();
        assertThat(stamped).isZero();
        // `season` hangs off its series rather than off the source — it is the
        // only one of the three that carries no `source_id`, because a season has
        // no meaning apart from the series it belongs to.
        Long seasons = jdbc.sql("""
                SELECT count(*) FROM season s
                  JOIN series ON series.id = s.series_id
                 WHERE series.source_id = :id
                """).param("id", sourceId).query(Long.class).single();
        assertThat(seasons).isZero();
        assertThat(count("episode")).isZero();
    }

    @Test
    @DisplayName("une seconde passe ne duplique rien")
    void isIdempotent() {
        ingestion.schedule(sourceId);
        awaitTerminalStatus();
        long channels = count("channel");
        long films = count("vod_item");
        long series = count("series");

        // `markSyncing` refuses a source that is not idle, so the second pass has
        // to start from a settled one. That is also how a real re-sync happens.
        assertThat(ingestion.schedule(sourceId)).isTrue();
        awaitTerminalStatus();

        assertThat(status()).isEqualTo("READY");
        // The upserts key on (source_id, external_id). A second pass that doubled
        // the catalogue would be the re-synchronisation bug every IPTV client
        // eventually has.
        assertThat(count("channel")).isEqualTo(channels);
        assertThat(count("vod_item")).isEqualTo(films);
        assertThat(count("series")).isEqualTo(series);
    }

    @Test
    @DisplayName("un panel injoignable met la source en ERROR sans la laisser en SYNCING")
    void leavesNoSourceStuckWhenThePanelDies() {
        panel.stop(0);

        ingestion.schedule(sourceId);
        awaitTerminalStatus();

        assertThat(status()).isEqualTo("ERROR");
        // The half that matters more than the code: a source left in SYNCING can
        // never be retried, because `markSyncing` refuses to reclaim it.
        assertThat(syncStep()).isNull();
    }

    // ---- the bench's routing, in twenty lines --------------------------------

    /**
     * nginx's {@code map} plus its {@code try_files} fallback.
     *
     * <p>The single-item actions are the interesting half: {@code get_vod_info}
     * reads {@code vod_id} and {@code get_series_info} reads {@code series_id}. A
     * request that sends the other names a file that does not exist and lands on
     * {@code wrong-id.json} — an empty array with a 200, which is what a strict
     * panel answers for an id it did not receive, and what caught the client
     * addressing a series by {@code vod_id}.
     */
    private static byte[] fixtureFor(String query) {
        Map<String, String> args = parse(query);
        String action = args.getOrDefault("action", "");

        String file = switch (action) {
            case "get_live_categories" -> "live-categories.json";
            case "get_live_streams" -> "live-streams.json";
            case "get_vod_categories" -> "vod-categories.json";
            case "get_vod_streams" -> "vod-streams.json";
            case "get_series_categories" -> "series-categories.json";
            case "get_series" -> "series.json";
            case "get_vod_info" -> "vod-info-" + args.getOrDefault("vod_id", "") + ".json";
            case "get_series_info" -> "series-info-" + args.getOrDefault("series_id", "") + ".json";
            // No action at all is the authentication call.
            default -> "account.json";
        };

        Path candidate = FIXTURES.resolve(file);
        Path served = Files.isRegularFile(candidate) ? candidate : FIXTURES.resolve("wrong-id.json");
        try {
            return Files.readAllBytes(served);
        } catch (IOException e) {
            throw new IllegalStateException("The bench fixture is missing: " + served, e);
        }
    }

    private static Map<String, String> parse(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        return List.of(query.split("&")).stream()
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(java.util.stream.Collectors.toMap(
                        pair -> pair[0],
                        pair -> java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8),
                        (first, second) -> first));
    }

    // ---- waiting, without a new dependency -----------------------------------

    /**
     * Polls until the source stops being {@code SYNCING}.
     *
     * <p>A loop rather than Awaitility, which is not on this classpath: the whole
     * need is one predicate and adding a library for it would be the larger change.
     * The ingestion runs on {@code IngestionService}'s own executor, so the test
     * thread has to wait for something it did not start.
     */
    private void awaitTerminalStatus() {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            String status = status();
            if (status != null && !"SYNCING".equals(status) && !"PENDING".equals(status)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the ingestion", e);
            }
        }
        throw new AssertionError("The source was still " + status() + " after 30 s");
    }

    // ---- reads ---------------------------------------------------------------

    private String status() {
        return one("SELECT status FROM source WHERE id = :id");
    }

    private String syncStep() {
        return one("SELECT sync_step FROM source WHERE id = :id");
    }

    private String errorCode() {
        return one("SELECT error_code FROM source WHERE id = :id");
    }

    private String one(String sql) {
        return jdbc.sql(sql).param("id", sourceId)
                .query(String.class).optional().orElse(null);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE source_id = :id")
                .param("id", sourceId).query(Long.class).single();
    }

    private long categoryCount(String contentType) {
        return jdbc.sql("""
                SELECT count(*) FROM category
                 WHERE source_id = :id AND content_type = :type
                """)
                .param("id", sourceId).param("type", contentType)
                .query(Long.class).single();
    }

}
