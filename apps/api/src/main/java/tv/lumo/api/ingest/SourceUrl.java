package tv.lumo.api.ingest;

import java.net.URI;
import java.util.Locale;
import tv.lumo.api.generated.model.IngestionErrorCode;

/**
 * The one way a user-supplied source URL becomes a {@link URI}.
 *
 * <p><b>Why this exists.</b> Ingestion needs a host to key its per-host
 * semaphore on, and that host reaches the logs (AGENTS.md §5 forbids a stream
 * URL in a log at any level, so the host is the most that may). The previous
 * shape of that code fell back to the whole URL when {@code URI.getHost()}
 * returned null:
 *
 * <pre>{@code String host = uri.getHost() == null ? source.m3uUrl() : uri.getHost();}</pre>
 *
 * <p>A scheme-less URL — which is exactly what people paste — parses as a
 * relative reference with a null host, so that fallback put the full URL, path
 * and query included, into a WARN line. IPTV panels routinely carry credentials
 * in both.
 *
 * <p>So the rule is enforced at the only place it can be: parsing. Anything that
 * is not an absolute http(s) URL with a host is refused outright, and what comes
 * back is a URI whose {@code getHost()} is guaranteed non-null.
 *
 * <p>Userinfo (<code>https://user:pass@host/…</code>) is deliberately accepted:
 * real playlists use it, and it never reaches a log because callers key on
 * {@code getHost()}, which excludes it.
 */
public final class SourceUrl {

    private SourceUrl() {
    }

    /**
     * @throws IngestionException {@code SOURCE_INVALID_FORMAT} for anything that
     *                            is not an absolute http(s) URL with a host
     */
    public static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw invalid();
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            // The cause is deliberately dropped rather than chained:
            // URI.create's own message quotes the offending URL in full, and a
            // chained cause is printed by any log call that takes a throwable.
            throw invalid();
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw invalid();
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid();
        }
        return uri;
    }

    /**
     * The host, lower-cased, and never more than the host. Safe to log.
     *
     * <p>The lower-casing is not cosmetic. This value is the key of the per-host
     * semaphore, and {@code URI} preserves the case it was given: without it,
     * {@code PANEL.EXAMPLE.ORG} and {@code panel.example.org} get two separate
     * semaphores and the same server takes twice the concurrency it was
     * budgeted — which is the failure ADR 0005 §1 exists to prevent. DNS is
     * case-insensitive, so they are one host.
     */
    public static String hostOf(URI uri) {
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static IngestionException invalid() {
        // No URL in the message either: this string travels to the client as a
        // problem+json `detail` and into the logs of whoever runs this.
        return new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                "The URL must be an absolute http or https URL");
    }
}
