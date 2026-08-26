package tv.lumo.api.ingest.xtream;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tv.lumo.api.ingest.HostConcurrencyLimiter;
import tv.lumo.api.ingest.IngestionHttpClient;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * The Xtream ingestion path, against a panel that behaves like a real one.
 *
 * <h2>Why this test did not exist, and what that cost</h2>
 *
 * Every ingestion test in this project was an M3U test. The Xtream path —
 * <b>the preferred format</b>, the one docs/domain-model.md recommends because
 * it carries categories, the guide and account information — had no coverage
 * beyond the parts a unit test of the parser reaches.
 *
 * <p>It was broken. {@code readArray} walked to the first {@code START_OBJECT}
 * and then asked the mapper to read a tree from the parser, which in this
 * Jackson version consumes to end-of-input instead of reading the current value:
 * every real panel answered {@code SOURCE_INVALID_FORMAT}, whatever it sent.
 * Nothing caught it until a user pointed a real subscription at it.
 *
 * <h2>The fixture is invented, and gzipped</h2>
 *
 * No real channel, no real bouquet, no real logo (AGENTS.md §1). Gzipped because
 * that is what panels actually send — the one under test sends
 * {@code Content-Encoding: gzip} on every call, including the small ones.
 */
class XtreamClientTest {

    private HttpServer server;
    private XtreamClient client;
    private String host;

    @BeforeEach
    void startPanel() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/player_api.php", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = query != null && query.contains("action=get_live_categories")
                    ? CATEGORIES
                    : query != null && query.contains("action=get_live_streams")
                            ? STREAMS
                            : ACCOUNT;
            respondGzipped(exchange, body);
        });
        server.start();

        host = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new XtreamClient(httpClient(), JsonMapper.builder().build());
    }

    @AfterEach
    void stopPanel() {
        server.stop(0);
    }

    @Test
    @DisplayName("les catégories d'un panel réel sont lues, une par une")
    void readsCategories() {
        List<XtreamClient.XtreamCategory> categories = new ArrayList<>();

        client.streamLiveCategories(host, "user", "secret", categories::add);

        // The regression in one assertion: this used to be zero, with the
        // ingestion reporting SOURCE_INVALID_FORMAT over a perfectly valid array.
        assertThat(categories).hasSize(3);
        assertThat(categories.getFirst().externalId()).isEqualTo("1");
        assertThat(categories.getFirst().name()).isEqualTo("Généralistes");
    }

    @Test
    @DisplayName("les chaînes sont lues avec leur numéro et leur qualité")
    void readsStreams() {
        List<XtreamClient.XtreamStream> streams = new ArrayList<>();

        client.streamLiveStreams(host, "user", "secret", streams::add);

        assertThat(streams).hasSize(2);

        XtreamClient.XtreamStream first = streams.getFirst();
        assertThat(first.name()).isEqualTo("Chaîne 01 FHD");
        // The panel's own number, not the order it happened to return rows in.
        assertThat(first.number()).isEqualTo(1);
        // Read off the name, echoed exactly as written.
        assertThat(first.quality()).isEqualTo("FHD");
        assertThat(first.adult()).isFalse();

        // A panel that reports neither reports neither: no zero, no invented badge.
        assertThat(streams.get(1).number()).isNull();
        assertThat(streams.get(1).quality()).isNull();
        assertThat(streams.get(1).adult()).isTrue();
    }

    @Test
    @DisplayName("l'authentification lit l'expiration et le nombre de connexions")
    void authenticates() {
        XtreamClient.XtreamAccount account = client.authenticate(host, "user", "secret");

        assertThat(account.maxConnections()).isEqualTo(2);
        assertThat(account.expiresAt()).isNotNull();
    }

    // ---- fixture ------------------------------------------------------------

    private static final String ACCOUNT = """
            {"user_info":{"username":"user","auth":1,"status":"Active",
             "exp_date":"1900000000","max_connections":"2","active_cons":"0"},
             "server_info":{"url":"127.0.0.1","port":"80"}}
            """;

    private static final String CATEGORIES = """
            [{"category_id":"1","category_name":"Généralistes","parent_id":0},
             {"category_id":"2","category_name":"Sport","parent_id":0},
             {"category_id":"3","category_name":"Découverte","parent_id":0}]
            """;

    /** `stream_id` as a number on one and a string on the other: panels do both. */
    private static final String STREAMS = """
            [{"num":1,"name":"Chaîne 01 FHD","stream_id":101,"stream_icon":"",
              "epg_channel_id":"c1","category_id":"1","is_adult":"0"},
             {"num":"","name":"Chaîne 02","stream_id":"102","stream_icon":null,
              "epg_channel_id":null,"category_id":"2","is_adult":"1"}]
            """;

    private static void respondGzipped(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = compressed.toByteArray();

        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Content-Encoding", "gzip");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * A client pointed at loopback, which {@code PrivateAddressGuard} refuses by
     * default and for good reason — so the test opens the one property that
     * exists to let it, and nothing else does.
     */
    private static IngestionHttpClient httpClient() {
        LumoProperties.Ingest ingest =
                new LumoProperties.Ingest(4, 50, Duration.ofSeconds(10), 200, true);
        LumoProperties properties = new LumoProperties(
                new LumoProperties.Jwt("x".repeat(40), Duration.ofMinutes(15), "https://api.lumo.tv"),
                new LumoProperties.Refresh(Duration.ofDays(30)),
                new LumoProperties.Encryption("dGVzdC1vbmx5LW1hc3Rlci1rZXktMzItYnl0ZXMhISE="),
                new LumoProperties.DeviceCode(Duration.ofMinutes(10), Duration.ofSeconds(5)),
                new LumoProperties.Web("http://localhost:3000"),
                new LumoProperties.Cors(List.of()),
                ingest,
                new LumoProperties.RateLimit(5, 5),
                new LumoProperties.AutoSync(false, Duration.ofHours(1), 12, 25),
                new LumoProperties.Plans(new LumoProperties.Limits(1, 2),
                        new LumoProperties.Limits(null, null)),
                new LumoProperties.Billing("", "", "https://api.stripe.com", 0, "/", "/", "/"));

        return new IngestionHttpClient(new HostConcurrencyLimiter(properties), properties);
    }
}
