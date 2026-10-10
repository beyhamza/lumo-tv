package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * S10B-01: a redirect is a new host, and it goes through the guard like the
 * first one.
 *
 * <p>The panel runs on loopback, which the real guard refuses. So the guard here
 * lets exactly one literal through — {@code 127.0.0.1}, standing in for "a
 * public panel" — and hands every other host to the real
 * {@link PrivateAddressGuard}. {@code localhost} and {@code 169.254.169.254} are
 * then what they are in production: internal, refused.
 */
class IngestionHttpClientRedirectTest {

    private static final String PANEL = "127.0.0.1";

    private HttpServer server;
    private String base;
    private final AtomicInteger internalHits = new AtomicInteger();
    private IngestionHttpClient client;

    @BeforeEach
    void startPanel() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PANEL, 0), 0);
        int port = server.getAddress().getPort();
        base = "http://" + PANEL + ":" + port;

        server.createContext("/playlist.m3u", exchange -> answer(exchange, 200, null, "#EXTM3U\n"));
        server.createContext("/to-same-host", exchange ->
                answer(exchange, 302, "/playlist.m3u", ""));
        server.createContext("/to-localhost", exchange ->
                answer(exchange, 302, "http://localhost:" + port + "/internal", ""));
        server.createContext("/to-metadata", exchange ->
                answer(exchange, 307, "http://169.254.169.254/latest/meta-data/", ""));
        server.createContext("/to-file", exchange ->
                answer(exchange, 302, "file:///etc/passwd", ""));
        server.createContext("/loop", exchange -> answer(exchange, 302, "/loop", ""));
        server.createContext("/internal", exchange -> {
            internalHits.incrementAndGet();
            answer(exchange, 200, null, "secret");
        });
        server.start();

        client = new IngestionHttpClient(new HostConcurrencyLimiter(properties()), properties(), host -> {
            if (!host.equals(PANEL)) {
                PrivateAddressGuard.requirePublic(host, false);
            }
        });
    }

    @AfterEach
    void stopPanel() {
        server.stop(0);
    }

    @Test
    @DisplayName("une redirection vers le même hôte autorisé est suivie")
    void followsAnAllowedRedirect() {
        String body = client.get(PANEL, URI.create(base + "/to-same-host"), IngestionHttpClientRedirectTest::read);

        assertThat(body).isEqualTo("#EXTM3U\n");
    }

    @Test
    @DisplayName("une redirection vers une adresse interne est refusée, sans connexion à la cible")
    void refusesARedirectIntoTheInternalNetwork() {
        assertThatThrownBy(() -> client.get(PANEL, URI.create(base + "/to-localhost"), IngestionHttpClientRedirectTest::read))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).code())
                .isEqualTo(IngestionErrorCode.SOURCE_UNREACHABLE);

        assertThat(internalHits).hasValue(0);
    }

    @Test
    @DisplayName("une redirection vers les métadonnées cloud est refusée")
    void refusesARedirectToCloudMetadata() {
        assertThatThrownBy(() -> client.get(PANEL, URI.create(base + "/to-metadata"), IngestionHttpClientRedirectTest::read))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).code())
                .isEqualTo(IngestionErrorCode.SOURCE_UNREACHABLE);
    }

    @Test
    @DisplayName("une redirection hors http(s) est refusée")
    void refusesANonHttpRedirect() {
        assertThatThrownBy(() -> client.get(PANEL, URI.create(base + "/to-file"), IngestionHttpClientRedirectTest::read))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).code())
                .isEqualTo(IngestionErrorCode.SOURCE_UNREACHABLE);
    }

    @Test
    @DisplayName("une boucle de redirections s'arrête au plafond")
    void stopsARedirectLoop() {
        assertThatThrownBy(() -> client.get(PANEL, URI.create(base + "/loop"), IngestionHttpClientRedirectTest::read))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("more than " + IngestionHttpClient.MAX_REDIRECTS);
    }

    private static void answer(com.sun.net.httpserver.HttpExchange exchange, int status, String location, String body)
            throws IOException {
        if (location != null) {
            exchange.getResponseHeaders().add("Location", location);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String read(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LumoProperties properties() {
        LumoProperties.Ingest ingest =
                new LumoProperties.Ingest(4, 50, Duration.ofSeconds(10), 200, false);
        return new LumoProperties(
                new LumoProperties.Jwt("x".repeat(40), Duration.ofMinutes(15), "https://api.lumo.tv"),
                new LumoProperties.Refresh(Duration.ofDays(30)),
                new LumoProperties.Encryption("dGVzdC1vbmx5LW1hc3Rlci1rZXktMzItYnl0ZXMhISE="),
                new LumoProperties.DeviceCode(Duration.ofMinutes(10), Duration.ofSeconds(5)),
                new LumoProperties.Web("http://localhost:3000"),
                new LumoProperties.Cors(List.of()),
                ingest,
                new LumoProperties.RateLimit(5, 5, Duration.ofMinutes(5), 10, List.of()),
                new LumoProperties.AutoSync(false, Duration.ofHours(1), 12, 25),
                new LumoProperties.Plans(new LumoProperties.Limits(1, 2),
                        new LumoProperties.Limits(null, null)),
                new LumoProperties.Billing("", "", "https://api.stripe.com", 0, "/", "/", "/"));
    }
}
