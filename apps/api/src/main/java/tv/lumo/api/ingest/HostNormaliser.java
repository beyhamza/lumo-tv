package tv.lumo.api.ingest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import tv.lumo.api.generated.model.IngestionErrorCode;

/**
 * Normalises an Xtream host as a human typed it.
 *
 * <p>US-06 is explicit: tolerate a host with or without a scheme, with or without
 * a port, with or without a trailing slash, and normalise rather than reject.
 * Someone copying a value out of a provider email should not be bounced by a
 * validator for a trailing slash.
 *
 * <p>Result is scheme + host + port, never a path or a query. Everything after
 * the authority is discarded on purpose: {@code player_api.php} paths are built
 * by this application, and accepting a caller-supplied path would let a source
 * point requests wherever it liked.
 */
public final class HostNormaliser {

    private HostNormaliser() {
    }

    public static String normalise(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT, "Host is empty");
        }
        String candidate = rawHost.trim();
        if (!candidate.matches("(?i)^https?://.*")) {
            // Most panels are plain HTTP, and guessing HTTPS for a host that does
            // not serve it turns a working source into SOURCE_UNREACHABLE.
            candidate = "http://" + candidate;
        }
        try {
            URI uri = new URI(candidate);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                        "Host could not be parsed");
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            return port == -1
                    ? scheme + "://" + host.toLowerCase(Locale.ROOT)
                    : scheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
        } catch (URISyntaxException e) {
            throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                    "Host is not a valid URL", e);
        }
    }
}
