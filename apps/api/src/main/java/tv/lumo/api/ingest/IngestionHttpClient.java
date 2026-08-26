package tv.lumo.api.ingest;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * The only way this application talks to a third-party IPTV server.
 *
 * <p>Every call is funnelled through {@link HostConcurrencyLimiter} and every
 * body is handed to the caller as a <b>stream</b>. Nothing is buffered whole: a
 * 15 000-channel M3U or a 200 MB gzipped XMLTV read into memory is what turns a
 * sync into an OutOfMemoryError, and it would defeat the point of parsing on the
 * server in the first place.
 *
 * <p>The stream is size-capped as it is read, so a source that lies about its
 * length — or does not declare one — still cannot exhaust the heap.
 *
 * <p><b>URLs are never logged.</b> An Xtream URL embeds the user's username and
 * password; the host alone is the most that ever reaches a log line
 * (AGENTS.md §5).
 */
@Component
public class IngestionHttpClient {

    private static final Logger log = LoggerFactory.getLogger(IngestionHttpClient.class);

    /** Some panels reject the default Java client outright. */
    private static final String USER_AGENT = "LumoTV/1.0";

    private final HttpClient httpClient;
    private final HostConcurrencyLimiter limiter;
    private final LumoProperties properties;

    public IngestionHttpClient(HostConcurrencyLimiter limiter, LumoProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.ingest().httpTimeout())
                // Panels redirect between http and https constantly. NORMAL
                // follows redirects but not https -> http, which would silently
                // downgrade a credential-bearing request.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetches a URL and hands the decoded, size-capped body to {@code reader}.
     *
     * <p>The stream is closed when {@code reader} returns, so it must not be
     * retained. Blocking style throughout: this thread is virtual, and waiting on
     * a socket is exactly what it is for (ADR 0005).
     *
     * @param logicalHost host used for concurrency accounting and log lines
     */
    public <T> T get(String logicalHost, URI uri, Function<InputStream, T> reader) {
        // Taken from the URI, not from the caller's argument, and that is not a
        // detail. The M3U path passes a bare hostname here; the Xtream path
        // passes the source's stored base URL — "http://panel.example.org" —
        // because that is what a `source` row keeps. So the argument is a
        // hostname only half the time.
        //
        // Two consequences, both fixed by deriving it here. A DNS lookup on
        // "http://panel.example.org" fails, which is how the guard below refused
        // every Xtream source the first time it ran. And the per-host semaphore
        // was keyed on two different strings for one server depending on which
        // path reached it — the exact accounting failure ADR 0005 §1 exists to
        // prevent, and the one SourceUrl.hostOf lower-cases to avoid.
        String host = uri.getHost() == null
                ? logicalHost.toLowerCase(java.util.Locale.ROOT)
                : uri.getHost().toLowerCase(java.util.Locale.ROOT);

        // Before the connection, not after: a host that resolves into our own
        // network is refused rather than fetched (see PrivateAddressGuard).
        PrivateAddressGuard.requirePublic(host, properties.ingest().allowPrivateHosts());

        return limiter.onHost(host, () -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.ingest().httpTimeout())
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream body = decode(response)) {
                int status = response.statusCode();
                if (status == 401 || status == 403) {
                    throw new IngestionException(IngestionErrorCode.SOURCE_AUTH_FAILED,
                            "The server refused the credentials");
                }
                if (status == 404) {
                    throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                            "The server has nothing at that path");
                }
                if (status >= 400) {
                    log.debug("Host {} answered HTTP {}", host, status);
                    throw new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                            "The server answered HTTP " + status);
                }
                try {
                    return reader.apply(
                            new SizeCappedInputStream(body, properties.ingest().maxPayloadBytes()));
                } catch (IngestionException e) {
                    // What the server actually sent, attached to the failure that
                    // could not make sense of it. Without this, a panel answering
                    // an HTML block page and a panel answering a JSON object are
                    // the same line in a log — SOURCE_INVALID_FORMAT, and nothing
                    // to act on. The host and the content type only: never the
                    // URL, never a byte of the body (AGENTS.md §5).
                    throw e.code() == IngestionErrorCode.SOURCE_INVALID_FORMAT
                            ? new IngestionException(e.code(), e.getMessage()
                                    + " [host=" + host
                                    + ", call=" + callOf(uri)
                                    + ", http=" + status
                                    + ", content-type=" + header(response, "Content-Type")
                                    + ", content-encoding=" + header(response, "Content-Encoding") + "]")
                            : e;
                }
            }
        });
    }

    private static String header(HttpResponse<InputStream> response, String name) {
        return response.headers().firstValue(name).orElse("absent");
    }

    /**
     * Which call failed, in one word, and nothing else from the URL.
     *
     * <p>Only the {@code action} parameter, whose values this application writes
     * itself — {@code get_live_categories} and friends. The rest of an Xtream
     * query is the user's username and password, and it does not go anywhere near
     * a log line (AGENTS.md §5). Without this, "the response could not be
     * understood" does not say which of three calls produced it, which is the
     * difference between a diagnosis and a guess.
     */
    private static String callOf(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            return "playlist";
        }
        for (String parameter : query.split("&")) {
            if (parameter.startsWith("action=")) {
                return parameter.substring("action=".length());
            }
        }
        return "authenticate";
    }

    /** Transparently un-gzips, whichever way the server signalled it. */
    private static InputStream decode(HttpResponse<InputStream> response) throws IOException {
        boolean gzipped = response.headers()
                .firstValue("Content-Encoding")
                .map(value -> value.toLowerCase(java.util.Locale.ROOT).contains("gzip"))
                .orElse(false)
                // XMLTV is very often served as a .gz file with no encoding header.
                || response.uri().getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".gz");
        return gzipped ? new GZIPInputStream(response.body()) : response.body();
    }

    /**
     * Fails the read once the cap is crossed.
     *
     * <p>Enforced on the bytes actually read rather than on {@code Content-Length},
     * because a hostile or merely broken server can understate or omit that
     * header, and because a gzipped body expands after the header was written.
     */
    private static final class SizeCappedInputStream extends FilterInputStream {

        private final long maxBytes;
        private long read;

        private SizeCappedInputStream(InputStream delegate, long maxBytes) {
            super(delegate);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) {
            read += n;
            if (read > maxBytes) {
                throw new IngestionException(IngestionErrorCode.SOURCE_TOO_LARGE,
                        "Payload exceeds the " + (maxBytes / (1024 * 1024)) + " MB ingestion limit");
            }
        }
    }
}
