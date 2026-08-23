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
        return limiter.onHost(logicalHost, () -> {
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
                    log.debug("Host {} answered HTTP {}", logicalHost, status);
                    throw new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                            "The server answered HTTP " + status);
                }
                return reader.apply(new SizeCappedInputStream(body, properties.ingest().maxPayloadBytes()));
            }
        });
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
