package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.catalog.CatalogReadRepository;
import tv.lumo.api.catalog.CatalogWriteRepository;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.EpgBenchFixtures;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/** S9-00: real ingestion and stored-guide measurements, not a grouped API implementation. */
@Import(PostgresContainerInitializer.class)
@TestPropertySource(properties = "lumo.ingest.allow-private-hosts=true")
class EpgBenchIntegrationTest extends PostgresIntegrationTest {
    @Autowired private IngestionService ingestion;
    @Autowired private SourceRepository sources;
    @Autowired private UserRepository users;
    @Autowired private JdbcClient jdbc;
    @Autowired private CatalogReadRepository catalog;
    @Autowired private CatalogWriteRepository writes;
    @Autowired private ObjectMapper mapper;
    private HttpServer server;
    private UUID owner;
    private UUID source;
    private OffsetDateTime anchor;
    private volatile byte[] guide;
    private volatile boolean gzip;
    private String host;

    @BeforeEach
    void start() throws Exception {
        anchor = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).withSecond(0);
        guide = EpgBenchFixtures.xml(anchor, 100, 30, 100, false, false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/playlist.m3u", exchange -> {
            byte[] body = EpgBenchFixtures.playlist(100).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/epg.xml", exchange -> {
            byte[] body = gzip ? compress(guide) : guide;
            if (gzip && !exchange.getRequestURI().getPath().endsWith(".gz")) {
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            }
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        host = "http://127.0.0.1:" + server.getAddress().getPort();
        owner = users.insert(UUID.randomUUID(), "epg-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "EPG bench", "fr").id();
        source = UUID.randomUUID();
        sources.insert(source, owner, "EPG bench", SourceKind.M3U_URL, null,
                null, null, host + "/playlist.m3u", host + "/epg.xml", null, null);
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void importsGzipAndKeepsProgrammeIdentityAcrossRepeatedImports() throws Exception {
        gzip = true;
        sync();
        long count = count();
        assertThat(count).isGreaterThan(18000);
        UUID first = jdbc.sql("SELECT id FROM epg_programme WHERE source_id=:id ORDER BY id LIMIT 1")
                .param("id", source).query(UUID.class).single();
        // Exercise suffix-only gzip as well as Content-Encoding, without changing identity.
        jdbc.sql("UPDATE source SET epg_url=:url WHERE id=:id AND user_id=:owner")
                .param("url", host + "/epg.xml.gz").param("id", source).param("owner", owner).update();
        sync();
        assertThat(count()).isEqualTo(count);
        assertThat(jdbc.sql("SELECT count(*) FROM epg_programme WHERE source_id=:id AND id=:first")
                .param("id", source).param("first", first).query(Long.class).single()).isEqualTo(1);
        Path dir = Path.of("build", "epg-bench-fixtures");
        Files.createDirectories(dir);
        Files.write(dir.resolve("epg.xml"), guide);
        Files.write(dir.resolve("epg.xml.gz"), compress(guide));
        Files.writeString(dir.resolve("epg-empty.xml"), "<tv/>");
        Files.write(dir.resolve("epg-broken-after-batch.xml"),
                EpgBenchFixtures.xml(anchor, 1, 1, 20, true, false));
        Files.write(dir.resolve("epg-old.xml"),
                EpgBenchFixtures.xml(anchor.minusDays(10), 1, 30, 20, false, false));
    }

    @Test
    void emptyAndOutOfRetentionGuidesDoNotFailTheCatalogue() throws Exception {
        guide = "<tv/>".getBytes(StandardCharsets.UTF_8);
        sync();
        assertThat(count()).isZero();
        guide = EpgBenchFixtures.xml(anchor.minusDays(10), 1, 30, 20, false, false);
        sync();
        assertThat(count()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM channel WHERE source_id=:id")
                .param("id", source).query(Long.class).single()).isEqualTo(100);
    }

    @Test
    void readsVariableDurationsAndEmptyMappingsWithoutCrossingOwnership() throws Exception {
        guide = EpgBenchFixtures.xml(anchor, 1, 30, 100, false, true);
        sync();
        UUID channel = jdbc.sql("SELECT id FROM channel WHERE source_id=:id AND tvg_id='epg-bench-1'")
                .param("id", source).query(UUID.class).single();
        var programmes = catalog.findProgrammes(channel, owner, anchor, anchor.plusHours(1));
        assertThat(programmes).hasSize(2);
        assertThat(programmes.getFirst().getEndsAt()).isEqualTo(anchor.plusMinutes(15));
        assertThat(programmes.getLast().getEndsAt()).isEqualTo(anchor.plusHours(1));
        assertThat(catalog.findProgrammes(channel, UUID.randomUUID(), anchor, anchor.plusHours(1))).isEmpty();
        assertThat(catalog.findProgrammes(channel, owner, anchor.plusMinutes(15), anchor.plusHours(1)))
                .hasSize(1);
        jdbc.sql("UPDATE channel SET tvg_id=NULL WHERE id=:id AND source_id=:source")
                .param("id", channel).param("source", source).update();
        assertThat(catalog.findProgrammes(channel, owner, anchor, anchor.plusHours(1))).isEmpty();
    }

    @Test
    void noGuideConfigurationStillImportsChannels() throws Exception {
        jdbc.sql("UPDATE source SET epg_url=NULL WHERE id=:id AND user_id=:owner")
                .param("id", source).param("owner", owner).update();
        sync();
        assertThat(count()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM channel WHERE source_id=:id")
                .param("id", source).query(Long.class).single()).isEqualTo(100);
    }

    @Test
    void purgesExpiredStoredProgrammesWithoutRemovingCurrentOnes() throws Exception {
        guide = EpgBenchFixtures.xml(anchor, 1, 30, 20, false, false);
        sync();
        long current = count();
        jdbc.sql("""
                INSERT INTO epg_programme(id, source_id, tvg_id, starts_at, ends_at, title)
                VALUES (:id, :source, 'epg-bench-1', :start, :end, 'Expired test programme')
                """).param("id", UUID.randomUUID()).param("source", source)
                .param("start", anchor.minusDays(3)).param("end", anchor.minusDays(2)).update();
        assertThat(count()).isEqualTo(current + 1);
        assertThat(writes.purgeExpiredProgrammes()).isGreaterThanOrEqualTo(1);
        assertThat(count()).isEqualTo(current);
    }

    @Test
    void sharedMappingsAndLongDescriptionsExceedByteCapBeforeProgrammeCap() throws Exception {
        guide = EpgBenchFixtures.xml(anchor, 1, 30, 8192, false, false);
        sync();
        jdbc.sql("UPDATE channel SET tvg_id='epg-bench-1' WHERE source_id=:id")
                .param("id", source).update();
        var ids = jdbc.sql("SELECT id FROM channel WHERE source_id=:id ORDER BY id")
                .param("id", source).query(UUID.class).list();
        var entries = new ArrayList<Map<String, Object>>();
        for (UUID channel : ids) {
            var items = catalog.findProgrammes(channel, owner, anchor, anchor.plusHours(3));
            assertThat(items).hasSize(6);
            entries.add(Map.of("channel_id", channel, "mapping_status", "MAPPED", "programmes", items));
        }
        byte[] json = mapper.writeValueAsBytes(Map.of("channels", entries));
        assertThat(json.length).isGreaterThan(4 * 1024 * 1024);
        var result = Map.of("channels", 100, "hours", 3, "description_characters", 8192,
                "stored_programmes", count(), "response_programmes", 600,
                "json_bytes_without_metadata", json.length, "gzip_bytes", compress(json).length);
        writeReport("epg-bench-long-descriptions.json", result);
    }

    @Test
    void malformedGuideCanLeaveCommittedBatchesWhileSourceBecomesReady() throws Exception {
        guide = "<tv><broken".getBytes(StandardCharsets.UTF_8);
        sync();
        assertThat(count()).isZero();
        guide = EpgBenchFixtures.xml(anchor, 1, 1, 20, true, false);
        sync();
        assertThat(count()).isGreaterThan(500);
        assertThat(jdbc.sql("SELECT last_synced_at IS NOT NULL FROM source WHERE id=:id")
                .param("id", source).query(Boolean.class).single()).isTrue();
    }

    @Test
    void measuresStoredWindowsAndCandidatePayloadWithoutClaimingApiLatency() throws Exception {
        sync();
        List<UUID> ids = jdbc.sql("SELECT id FROM channel WHERE source_id=:id ORDER BY external_id")
                .param("id", source).query(UUID.class).list();
        List<Map<String, Object>> measurements = new ArrayList<>();
        for (int channels : new int[]{1, 50, 100}) {
            for (int hours : new int[]{3, 24, 96}) {
                OffsetDateTime from = hours == 96 ? anchor.minusDays(1) : anchor;
                OffsetDateTime to = from.plusHours(hours);
                ManagementFactory.getMemoryPoolMXBeans().stream()
                        .filter(pool -> pool.getType() == MemoryType.HEAP)
                        .forEach(pool -> pool.resetPeakUsage());
                long started = System.nanoTime();
                List<Map<String, Object>> entries = new ArrayList<>();
                int programmes = 0;
                for (UUID channel : ids.subList(0, channels)) {
                    var items = catalog.findProgrammes(channel, owner, from, to);
                    programmes += items.size();
                    entries.add(Map.of("channel_id", channel, "mapping_status", "MAPPED", "programmes", items));
                }
                double readMs = (System.nanoTime() - started) / 1_000_000.0;
                // A candidate envelope for size measurement only; no client/server type or route is added.
                var epg = new LinkedHashMap<String, Object>();
                epg.put("configured", true);
                epg.put("last_successful_import_at", anchor);
                epg.put("last_attempt_started_at", anchor);
                epg.put("last_attempt_finished_at", anchor);
                epg.put("last_attempt_status", "SUCCEEDED");
                long serializationStart = System.nanoTime();
                byte[] json = mapper.writeValueAsBytes(Map.of("source_id", source, "from", from,
                        "to", to, "generated_at", anchor, "epg", epg, "channels", entries));
                double serializationMs = (System.nanoTime() - serializationStart) / 1_000_000.0;
                var row = new LinkedHashMap<String, Object>();
                row.put("channels", channels); row.put("hours", hours);
                row.put("programmes", programmes); row.put("json_bytes", json.length);
                row.put("gzip_bytes", compress(json).length);
                row.put("existing_repository_reads", channels); row.put("repository_read_ms", readMs);
                row.put("serialization_ms", serializationMs);
                row.put("jvm_heap_pool_peaks_sum_bytes", ManagementFactory.getMemoryPoolMXBeans().stream()
                        .filter(pool -> pool.getType() == MemoryType.HEAP)
                        .mapToLong(pool -> pool.getPeakUsage().getUsed()).sum());
                measurements.add(row);
                assertThat(entries).hasSize(channels);
                assertThat(programmes).isEqualTo(channels * hours * 2);
            }
        }
        writeReport("epg-bench.json", measurements);
    }

    private void writeReport(String name, Object value) throws Exception {
        Path output = Path.of("build", "reports", name);
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    private void sync() throws InterruptedException {
        assertThat(ingestion.schedule(source)).isTrue();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        String status = "PENDING";
        while (System.nanoTime() < deadline) {
            status = jdbc.sql("SELECT status FROM source WHERE id=:id")
                    .param("id", source).query(String.class).single();
            if (!List.of("PENDING", "SYNCING").contains(status)) break;
            Thread.sleep(25);
        }
        assertThat(status).isEqualTo("READY");
    }

    private long count() {
        return jdbc.sql("SELECT count(*) FROM epg_programme WHERE source_id=:id")
                .param("id", source).query(Long.class).single();
    }

    private static byte[] compress(byte[] bytes) throws java.io.IOException {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) { gzip.write(bytes); }
        return out.toByteArray();
    }
}
