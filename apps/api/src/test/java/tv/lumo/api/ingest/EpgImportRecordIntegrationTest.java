package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.catalog.CatalogReadRepository;
import tv.lumo.api.catalog.EpgReadService;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.EpgBenchFixtures;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * What the ingestion writes about its own import of the guide (US-16, lot C1,
 * D3; acceptance cases C1-07, C1-08, C1-10), and the read-side snapshot that
 * lets a client trust it.
 *
 * <p>Built on the bench's fixtures ({@code EpgBenchFixtures}) and its loopback
 * server, because the cases here are the bench's cases with one more question
 * asked of each: not only "is the source READY and how many rows are there",
 * but "what does the row say happened to the guide". The bench proved that
 * {@code last_synced_at} answers the wrong question — a guide that broke after
 * several batches still ended READY with that date set — and these are the
 * tests of the column that answers the right one.
 */
@Import(PostgresContainerInitializer.class)
// Lifted here for the same reason as in the bench: the guide is served by a
// loopback server this class started itself. Every other test keeps the guard.
@TestPropertySource(properties = "lumo.ingest.allow-private-hosts=true")
class EpgImportRecordIntegrationTest extends PostgresIntegrationTest {

    @Autowired private IngestionService ingestion;
    @Autowired private SourceRepository sources;
    @Autowired private CatalogReadRepository catalog;
    @Autowired private UserRepository users;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactions;

    private HttpServer server;
    private String host;
    private UUID owner;
    private UUID source;
    private OffsetDateTime anchor;
    private volatile byte[] guide;

    @BeforeEach
    void start() throws Exception {
        anchor = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).withSecond(0);
        guide = EpgBenchFixtures.xml(anchor, 3, 30, 20, false, false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/playlist.m3u", exchange -> {
            byte[] body = EpgBenchFixtures.playlist(3).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/epg.xml", exchange -> {
            byte[] body = guide;
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        host = "http://127.0.0.1:" + server.getAddress().getPort();
        owner = users.insert(UUID.randomUUID(), "record-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Import record", "fr").id();
        source = UUID.randomUUID();
        sources.insert(source, owner, "Guide", SourceKind.M3U_URL, null,
                null, null, host + "/playlist.m3u", host + "/epg.xml", null, null);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    // ---- C1-08: nothing invented ---------------------------------------------------

    @Test
    @DisplayName("C1-08 — une source qui précède le lot est UNKNOWN sans date, et le reste sans URL de guide")
    void aSourceStartsUnknownAndStaysSoWithoutAGuide() {
        // What the migration leaves on every existing row: the column defaults,
        // which `insert` does not name. Not `last_synced_at`, not anything.
        Record before = record();
        assertThat(before.status()).isEqualTo("UNKNOWN");
        assertThat(before.lastSuccessAt()).isNull();
        assertThat(before.startedAt()).isNull();
        assertThat(before.finishedAt()).isNull();
        assertThat(before.attemptId()).isNull();

        jdbc.sql("UPDATE source SET epg_url = NULL WHERE id = :id AND user_id = :owner")
                .param("id", source).param("owner", owner).update();
        sync();

        // The catalogue came in; no attempt was made, so nothing was recorded.
        assertThat(channels()).isEqualTo(3);
        assertThat(record()).isEqualTo(before);
        assertThat(sources.findEpgImport(source, owner).orElseThrow().configured()).isFalse();
    }

    @Test
    @DisplayName("C1-08 — un guide vide est un import réussi, pas une preuve de couverture")
    void anEmptyGuideIsASuccessfulImport() {
        guide = "<tv/>".getBytes(StandardCharsets.UTF_8);
        sync();

        Record record = record();
        assertThat(record.status()).isEqualTo("SUCCEEDED");
        assertThat(record.lastSuccessAt()).isNotNull();
        assertThat(record.startedAt()).isNotNull();
        assertThat(record.finishedAt()).isNotNull().isAfterOrEqualTo(record.startedAt());
        assertThat(record.lastSuccessAt()).isEqualTo(record.finishedAt());
        assertThat(record.attemptId()).isNotNull();
        assertThat(programmes()).isZero();
    }

    // ---- C1-07: failure keeps the last success honest --------------------------------

    @Test
    @DisplayName("C1-07 — un guide cassé après des lots : source READY, lots conservés, FAILED, date de succès intacte")
    void aGuideBrokenAfterBatchesIsRecordedAsFailedWithoutMovingTheSuccessDate() {
        sync();
        Record first = record();
        assertThat(first.status()).isEqualTo("SUCCEEDED");
        long stored = programmes();
        assertThat(stored).isGreaterThan(0);

        // The bench's fixture: one channel, one-minute programmes, cut off
        // after more than a batch has been written.
        guide = EpgBenchFixtures.xml(anchor, 1, 1, 20, true, false);
        sync();

        Record second = record();
        // The bench's observation, now with the column that says so.
        assertThat(status()).isEqualTo("READY");
        assertThat(programmes()).isGreaterThan(500);
        assertThat(second.status()).isEqualTo("FAILED");
        assertThat(second.lastSuccessAt()).isEqualTo(first.lastSuccessAt());
        assertThat(second.startedAt()).isAfterOrEqualTo(first.finishedAt());
        assertThat(second.finishedAt()).isNotNull().isAfterOrEqualTo(second.startedAt());
        assertThat(second.attemptId()).isNotEqualTo(first.attemptId());
    }

    @Test
    @DisplayName("C1-07 — un guide cassé avant le premier lot : FAILED, aucune date de succès inventée")
    void aGuideBrokenBeforeAnyBatchIsFailedWithNoSuccessDate() {
        guide = "<tv><broken".getBytes(StandardCharsets.UTF_8);
        sync();

        Record record = record();
        assertThat(status()).isEqualTo("READY");
        assertThat(programmes()).isZero();
        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.lastSuccessAt()).isNull();
        assertThat(record.startedAt()).isNotNull();
        assertThat(record.finishedAt()).isNotNull();
    }

    // ---- C1-10: a superseded attempt cannot publish ---------------------------------------

    @Test
    @DisplayName("C1-10 — changer l'URL du guide pendant une tentative : la tentative ne publie pas, le relevé est UNKNOWN")
    void aConfigurationChangeDuringAnAttemptStopsItFromPublishing() {
        sync();
        assertThat(record().status()).isEqualTo("SUCCEEDED");

        // An attempt in flight, as the worker opens one: claimed, RUNNING, dated.
        assertThat(sources.markSyncing(source)).isTrue();
        UUID attempt = sources.beginEpgAttempt(source);
        assertThat(record().status()).isEqualTo("RUNNING");
        assertThat(record().attemptId()).isEqualTo(attempt);

        // A label-only PATCH leaves the record alone: nothing about the guide moved.
        sources.update(source, owner, "Renamed", null, null, null, null, null, null, false);
        assertThat(record().status()).isEqualTo("RUNNING");

        // The PATCH that changes the guide — the statement SourceService.update
        // issues with resetToPending=true when epg_url is in the request.
        sources.update(source, owner, null, null, null, null, null,
                host + "/epg.xml?v=2", null, true);

        Record reset = record();
        assertThat(reset.status()).isEqualTo("UNKNOWN");
        assertThat(reset.lastSuccessAt()).isNull();
        assertThat(reset.startedAt()).isNull();
        assertThat(reset.finishedAt()).isNull();
        assertThat(reset.attemptId()).isNull();

        // The attempt comes back to publish, and finds no row to publish on.
        assertThat(sources.markEpgSucceeded(source, attempt)).isFalse();
        assertThat(sources.markEpgFailed(source, attempt)).isFalse();
        assertThat(record()).isEqualTo(reset);
        // The old programmes are kept, reported as unverified rather than deleted.
        assertThat(programmes()).isGreaterThan(0);
    }

    // ---- INTERRUPTED: the housekeeping sweep ----------------------------------------------------

    @Test
    @DisplayName("libérer une source bloquée en SYNCING passe sa tentative RUNNING à INTERRUPTED, et ne touche pas une tentative finie")
    void releasingAStuckSyncInterruptsARunningAttemptOnly() {
        sync();
        Record finished = record();
        assertThat(finished.status()).isEqualTo("SUCCEEDED");

        // A worker that died mid-guide: RUNNING, and the row untouched for
        // longer than the sweep tolerates.
        sources.markSyncing(source);
        sources.beginEpgAttempt(source);
        age(source);
        ingestion.housekeeping();

        Record interrupted = record();
        assertThat(status()).isEqualTo("ERROR");
        assertThat(interrupted.status()).isEqualTo("INTERRUPTED");
        assertThat(interrupted.finishedAt()).isNotNull();
        assertThat(interrupted.lastSuccessAt()).isEqualTo(finished.lastSuccessAt());

        // A source stuck for another reason, its guide attempt long finished:
        // released, and the guide's record says what it said.
        UUID other = UUID.randomUUID();
        sources.insert(other, owner, "Autre", SourceKind.M3U_URL, null, null, null,
                host + "/playlist.m3u", host + "/epg.xml", null, null);
        sources.markSyncing(other);
        UUID attempt = sources.beginEpgAttempt(other);
        assertThat(sources.markEpgSucceeded(other, attempt)).isTrue();
        sources.markSyncing(other);
        age(other);
        ingestion.housekeeping();

        assertThat(jdbc.sql("SELECT status FROM source WHERE id = :id").param("id", other)
                .query(String.class).single()).isEqualTo("ERROR");
        assertThat(jdbc.sql("SELECT epg_attempt_status FROM source WHERE id = :id").param("id", other)
                .query(String.class).single()).isEqualTo("SUCCEEDED");
    }

    // ---- the read-side snapshot ------------------------------------------------------------------

    @Test
    @DisplayName("les deux lectures de l'EPG sont déclarées en lecture seule et REPEATABLE READ")
    void bothReadsRunInARepeatableReadTransaction() throws Exception {
        for (Method method : List.of(
                EpgReadService.class.getMethod("readGrid", UUID.class, UUID.class, List.class,
                        EpgReadService.Window.class),
                EpgReadService.class.getMethod("readChannel", UUID.class, UUID.class,
                        EpgReadService.Window.class))) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional).as(method.getName()).isNotNull();
            assertThat(transactional.readOnly()).as(method.getName()).isTrue();
            assertThat(transactional.isolation()).as(method.getName()).isEqualTo(Isolation.REPEATABLE_READ);
        }
    }

    @Test
    @DisplayName("dans une transaction REPEATABLE READ, relevé et programmes viennent du même instantané")
    void theRecordAndTheProgrammesComeFromOneSnapshot() throws Exception {
        UUID channel = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, external_id, name, tvg_id, stream_url, position)
                VALUES (:id, :source, :ext, 'Chaîne test', 'snapshot.test', 'https://stream.example/x.m3u8', 0)
                """).param("id", channel).param("source", source).param("ext", "test:" + channel).update();

        TransactionTemplate readOnlyRepeatableRead = new TransactionTemplate(transactions);
        readOnlyRepeatableRead.setReadOnly(true);
        readOnlyRepeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        readOnlyRepeatableRead.executeWithoutResult(status -> {
            // The first statement takes the snapshot: nothing recorded, no programme.
            assertThat(sources.findEpgImport(source, owner).orElseThrow().attemptStatus()).isEqualTo("UNKNOWN");
            assertThat(catalog.findProgrammesByTvgIds(source, owner, List.of("snapshot.test"),
                    anchor, anchor.plusHours(1), 5_001)).isEmpty();

            // Meanwhile, on another connection, an attempt runs to completion and
            // commits: a programme and a SUCCEEDED record.
            Thread writer = Thread.ofVirtual().start(() -> {
                sources.markSyncing(source);
                UUID attempt = sources.beginEpgAttempt(source);
                jdbc.sql("""
                        INSERT INTO epg_programme (id, source_id, tvg_id, starts_at, ends_at, title)
                        VALUES (:id, :source, 'snapshot.test', :from, :to, 'Written meanwhile')
                        """).param("id", UUID.randomUUID()).param("source", source)
                        .param("from", anchor).param("to", anchor.plusMinutes(30)).update();
                sources.markEpgSucceeded(source, attempt);
            });
            join(writer);

            // Same transaction: neither the record nor the programme is visible.
            // A READ COMMITTED read here would answer SUCCEEDED beside a row
            // list that may or may not include what it vouches for.
            assertThat(sources.findEpgImport(source, owner).orElseThrow().attemptStatus()).isEqualTo("UNKNOWN");
            assertThat(catalog.findProgrammesByTvgIds(source, owner, List.of("snapshot.test"),
                    anchor, anchor.plusHours(1), 5_001)).isEmpty();
        });

        // Outside it, both are there — together.
        assertThat(sources.findEpgImport(source, owner).orElseThrow().attemptStatus()).isEqualTo("SUCCEEDED");
        assertThat(catalog.findProgrammesByTvgIds(source, owner, List.of("snapshot.test"),
                anchor, anchor.plusHours(1), 5_001)).hasSize(1);
    }

    // ---- support ----------------------------------------------------------------------------------

    private record Record(String status, OffsetDateTime lastSuccessAt, OffsetDateTime startedAt,
                          OffsetDateTime finishedAt, UUID attemptId) {
    }

    private Record record() {
        return jdbc.sql("""
                SELECT epg_attempt_status, epg_last_success_at, epg_attempt_started_at,
                       epg_attempt_finished_at, epg_attempt_id
                  FROM source WHERE id = :id
                """)
                .param("id", source)
                .query((rs, n) -> new Record(
                        rs.getString("epg_attempt_status"),
                        rs.getObject("epg_last_success_at", OffsetDateTime.class),
                        rs.getObject("epg_attempt_started_at", OffsetDateTime.class),
                        rs.getObject("epg_attempt_finished_at", OffsetDateTime.class),
                        rs.getObject("epg_attempt_id", UUID.class)))
                .single();
    }

    private void sync() {
        assertThat(ingestion.schedule(source)).isTrue();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        String status = "PENDING";
        while (System.nanoTime() < deadline) {
            status = status();
            if (!List.of("PENDING", "SYNCING").contains(status)) break;
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(status).isEqualTo("READY");
    }

    private static void join(Thread thread) {
        try {
            thread.join(Duration.ofSeconds(30).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        assertThat(thread.isAlive()).as("the writer finished").isFalse();
    }

    private void age(UUID id) {
        jdbc.sql("UPDATE source SET updated_at = now() - interval '31 minutes' WHERE id = :id")
                .param("id", id).update();
    }

    private String status() {
        return jdbc.sql("SELECT status FROM source WHERE id = :id").param("id", source)
                .query(String.class).single();
    }

    private long programmes() {
        return jdbc.sql("SELECT count(*) FROM epg_programme WHERE source_id = :id")
                .param("id", source).query(Long.class).single();
    }

    private long channels() {
        return jdbc.sql("SELECT count(*) FROM channel WHERE source_id = :id")
                .param("id", source).query(Long.class).single();
    }
}
