package tv.lumo.api.catalog;

import java.net.URI;

/**
 * The address of a stream at the https port its panel announces (ADR 0007).
 *
 * <p>Only the scheme and the port change. The stream still goes directly from the
 * user's panel to the player, and the result carries the same credentials as its
 * input: <b>never log either</b> (AGENTS.md §5).
 */
final class SecureStreamUrl {

    private static final int DEFAULT_HTTPS_PORT = 443;

    private SecureStreamUrl() {
    }

    /** @return null when there is nothing to offer: no port, or a URL that is not plain http */
    static String of(String streamUrl, Integer httpsPort) {
        if (httpsPort == null) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(streamUrl);
        } catch (IllegalArgumentException e) {
            // Deliberately dropped: the exception's message quotes the URL.
            return null;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return null;
        }
        String port = httpsPort == DEFAULT_HTTPS_PORT ? "" : ":" + httpsPort;
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return "https://" + uri.getHost() + port + uri.getRawPath() + query;
    }
}
