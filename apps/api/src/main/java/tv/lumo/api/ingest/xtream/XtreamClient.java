package tv.lumo.api.ingest.xtream;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.ingest.IngestionException;
import tv.lumo.api.ingest.IngestionHttpClient;

/**
 * Client for the Xtream Codes {@code player_api.php} surface.
 *
 * <p>Categories and streams are parsed with a <b>streaming</b> Jackson parser
 * rather than bound to a list: {@code get_live_streams} on a large panel is a
 * single JSON array of tens of thousands of objects, and materialising it would
 * hold the whole catalogue in memory at once.
 *
 * <p>Panels are inconsistent about types — {@code stream_id} comes back as a
 * number from some and a string from others, {@code exp_date} as a unix
 * timestamp, an empty string or {@code null} — so every field is read
 * defensively.
 *
 * <p><b>No URL built here is ever logged.</b> An Xtream URL carries the user's
 * username and password in its path.
 */
@Component
public class XtreamClient {

    private static final Logger log = LoggerFactory.getLogger(XtreamClient.class);

    private final IngestionHttpClient http;
    private final ObjectMapper objectMapper;

    public XtreamClient(IngestionHttpClient http, ObjectMapper objectMapper) {
        this.http = http;
        this.objectMapper = objectMapper;
    }

    /**
     * Authenticates and reads the account information.
     *
     * <p>This is the synchronous validation behind {@code POST /sources}: it is
     * what lets "your credentials were refused by the server" appear while the
     * user is still looking at the form (US-06).
     */
    public XtreamAccount authenticate(String host, String username, String password) {
        JsonNode root = http.get(host, playerApi(host, username, password, null),
                stream -> objectMapper.readTree(stream));

        JsonNode userInfo = root.path("user_info");
        if (userInfo.isMissingNode() || userInfo.isNull()) {
            // A panel that answers 200 with no user_info is not an Xtream panel.
            throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                    "The server did not return an Xtream user_info payload");
        }

        // auth == 0 is the panel's way of saying "wrong credentials"; it does NOT
        // use HTTP 401 for this.
        if (userInfo.path("auth").asInt(0) != 1) {
            throw new IngestionException(IngestionErrorCode.SOURCE_AUTH_FAILED,
                    "The panel rejected the credentials");
        }

        String status = userInfo.path("status").asString("");
        OffsetDateTime expiresAt = readExpiry(userInfo.path("exp_date"));

        if ("Expired".equalsIgnoreCase(status)
                || (expiresAt != null && expiresAt.isBefore(OffsetDateTime.now()))) {
            throw new IngestionException(IngestionErrorCode.SOURCE_EXPIRED,
                    "The subscription with the provider has expired");
        }
        if ("Banned".equalsIgnoreCase(status) || "Disabled".equalsIgnoreCase(status)) {
            throw new IngestionException(IngestionErrorCode.SOURCE_AUTH_FAILED,
                    "The account is not usable: " + status);
        }

        Integer maxConnections = readInt(userInfo.path("max_connections"));
        Integer activeConnections = readInt(userInfo.path("active_cons"));
        if (maxConnections != null && activeConnections != null && activeConnections >= maxConnections) {
            throw new IngestionException(IngestionErrorCode.SOURCE_MAX_CONNECTIONS,
                    "The subscription's simultaneous connection limit is already reached");
        }

        return new XtreamAccount(expiresAt, maxConnections);
    }

    /** Streams the live categories, one at a time. */
    public void streamLiveCategories(String host, String username, String password,
                                     Consumer<XtreamCategory> consumer) {
        streamArray(host, username, password, "get_live_categories", node -> {
            String id = readText(node.path("category_id"));
            String name = readText(node.path("category_name"));
            if (id != null && name != null) {
                consumer.accept(new XtreamCategory(id, name));
            }
        });
    }

    /** Streams the live channels, one at a time. */
    public void streamLiveStreams(String host, String username, String password,
                                  Consumer<XtreamStream> consumer) {
        streamArray(host, username, password, "get_live_streams", node -> {
            String streamId = readText(node.path("stream_id"));
            String name = readText(node.path("name"));
            if (streamId == null || name == null) {
                return;
            }
            consumer.accept(new XtreamStream(
                    streamId,
                    name,
                    readText(node.path("stream_icon")),
                    readText(node.path("epg_channel_id")),
                    readText(node.path("category_id")),
                    // Panels report this as 0/1, "0"/"1" or omit it entirely.
                    "1".equals(readText(node.path("is_adult"))),
                    buildStreamUrl(host, username, password, streamId)));
        });
    }

    /**
     * Builds the playback URL for one channel.
     *
     * <p>Kept next to the client that knows the panel's conventions rather than
     * stored per channel at ingestion time, so credentials can be rotated without
     * rewriting every row.
     */
    public static String buildStreamUrl(String host, String username, String password, String streamId) {
        return host + "/live/" + encode(username) + "/" + encode(password) + "/" + encode(streamId) + ".m3u8";
    }

    private void streamArray(String host, String username, String password,
                             String action, Consumer<JsonNode> consumer) {
        http.get(host, playerApi(host, username, password, action), stream -> {
            readArray(stream, consumer);
            return null;
        });
    }

    /**
     * Walks a top-level JSON array, materialising exactly one element at a time.
     *
     * <p>This is the difference between a sync that works on a 15 000-channel
     * panel and one that does not.
     */
    private void readArray(InputStream stream, Consumer<JsonNode> consumer) {
        try (JsonParser parser = objectMapper.createParser(stream)) {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                // Some panels answer an error object instead of an array.
                throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                        "Expected a JSON array from the panel");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                consumer.accept(objectMapper.readTree(parser));
            }
        } catch (IngestionException e) {
            throw e;
        } catch (Exception e) {
            throw IngestionException.from(e);
        }
    }

    private static URI playerApi(String host, String username, String password, String action) {
        StringBuilder url = new StringBuilder(host)
                .append("/player_api.php?username=").append(encode(username))
                .append("&password=").append(encode(password));
        if (action != null) {
            url.append("&action=").append(action);
        }
        return URI.create(url.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static OffsetDateTime readExpiry(JsonNode node) {
        String raw = readText(node);
        if (raw == null) {
            // A permanent account, or a panel that simply does not report it.
            return null;
        }
        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(raw)), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            log.debug("Unparseable exp_date from panel; treating the account as non-expiring");
            return null;
        }
    }

    private static Integer readInt(JsonNode node) {
        String raw = readText(node);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reads a value that may arrive as a string, a number, null or an empty string. */
    private static String readText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.isTextual() ? node.stringValue() : node.asString("");
        return value.isBlank() ? null : value.trim();
    }

    /** @param expiresAt null when the panel reports no expiry */
    public record XtreamAccount(OffsetDateTime expiresAt, Integer maxConnections) {
    }

    public record XtreamCategory(String externalId, String name) {
    }

    public record XtreamStream(String externalId, String name, String logoUrl, String tvgId,
                               String categoryExternalId, boolean adult, String streamUrl) {
    }
}
