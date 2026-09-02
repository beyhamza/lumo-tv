package tv.lumo.api.ingest.xtream;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import tv.lumo.api.ingest.ChannelQuality;
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
                    buildStreamUrl(host, username, password, streamId),
                    // The panel's own channel number. Xtream calls it `num`, and
                    // it is not the order the panel happens to return rows in.
                    ChannelQuality.parseNumber(readText(node.path("num"))),
                    // No panel field carries this, so it comes off the name, which
                    // is where panels put it.
                    ChannelQuality.detect(null, name)));
        });
    }

    /** Streams the film categories, one at a time. */
    public void streamVodCategories(String host, String username, String password,
                                    Consumer<XtreamCategory> consumer) {
        streamArray(host, username, password, "get_vod_categories", node -> {
            String id = readText(node.path("category_id"));
            String name = readText(node.path("category_name"));
            if (id != null && name != null) {
                consumer.accept(new XtreamCategory(id, name));
            }
        });
    }

    /**
     * Streams the films, one at a time.
     *
     * <p><b>A film with no container extension is not emitted.</b> Its playback URL
     * cannot be built — that fragment is the only part the panel does not put in a
     * path — so importing it would produce a catalogue entry that opens onto a
     * failure. A catalogue announcing twelve thousand films of which three hundred
     * will never start is worse than one announcing eleven thousand seven hundred.
     *
     * <p>The caller counts what this drops: {@link IngestionService} reports it,
     * because a silent skip is indistinguishable from a panel with fewer films.
     */
    public void streamVodStreams(String host, String username, String password,
                                 Consumer<XtreamVodStream> consumer) {
        streamArray(host, username, password, "get_vod_streams", node -> {
            String streamId = readText(node.path("stream_id"));
            String name = readText(node.path("name"));
            String extension = readText(node.path("container_extension"));
            if (streamId == null || name == null || extension == null) {
                return;
            }
            consumer.accept(new XtreamVodStream(
                    streamId,
                    name,
                    // Panels disagree on the key and often serve both.
                    firstNonBlank(readText(node.path("stream_icon")),
                            readText(node.path("cover"))),
                    readText(node.path("category_id")),
                    "1".equals(readText(node.path("is_adult"))),
                    buildVodStreamUrl(host, username, password, streamId, extension),
                    extension,
                    parseYear(readText(node.path("year"))),
                    parseSeconds(readText(node.path("episode_run_time"))),
                    // Echoed verbatim: `7.4`, `PG-13` and `★★★★` all occur, and
                    // deciding which is meant would be this layer inventing a
                    // meaning the provider did not give.
                    readText(node.path("rating"))));
        });
    }

    /**
     * The synopsis of one film, from {@code get_vod_info}.
     *
     * <p><b>One call, one film, and that is the whole reason this is not part of
     * the listing.</b> A catalogue of thirty thousand films would be thirty
     * thousand requests against the user's own panel at every synchronisation —
     * not slow, bannable. So it is called when somebody opens a film, and the
     * answer is cached.
     *
     * <p>Returns null rather than throwing when the panel answers with something
     * unusable, which they do: an empty array where an object was expected, an
     * `info` object with no `plot`, a `plot` that is the empty string. None of
     * those is a failure worth showing a person — the film still plays.
     *
     * <p>The response is read whole rather than streamed, unlike every other call
     * here. It describes one film and is a few kilobytes; the streaming machinery
     * exists for the catalogue walks, where the array is the size of the panel.
     */
    public String fetchVodPlot(String host, String username, String password, String streamId) {
        URI uri = playerApiWithId(host, username, password, "get_vod_info", "vod_id", streamId);
        return http.get(host, uri, stream -> {
            try {
                JsonNode root = objectMapper.readTree(stream);
                String plot = readText(root.path("info").path("plot"));
                // Panels disagree on the key: `plot` on most, `description` on
                // some, and a few serve both with only one of them filled.
                return plot != null ? plot : readText(root.path("info").path("description"));
            } catch (RuntimeException e) {
                // A malformed answer to an optional field — an array where an
                // object belongs is the common one. The film is unaffected, and a
                // synopsis is not worth failing an open over.
                log.info("Panel returned an unreadable film sheet");
                return null;
            }
        });
    }

    public void streamSeriesCategories(String host, String username, String password,
                                       Consumer<XtreamCategory> consumer) {
        streamArray(host, username, password, "get_series_categories", node -> {
            String id = readText(node.path("category_id"));
            String name = readText(node.path("category_name"));
            if (id != null && name != null) {
                consumer.accept(new XtreamCategory(id, name));
            }
        });
    }

    /**
     * Streams the series list, one at a time.
     *
     * <p><b>Flat, and that is the point.</b> {@code get_series} answers with every
     * series of the panel and no seasons: the tree of one series is a second call,
     * {@code get_series_info}, made when somebody opens it. Walking the tree here
     * would mean one request per series at every synchronisation — eight hundred
     * against the user's own provider, which is not slow but bannable.
     *
     * <p>Unlike a film, a series with no container extension is still emitted:
     * nothing is played at this level, so there is no URL to build and nothing to
     * be missing.
     */
    public void streamSeries(String host, String username, String password,
                             Consumer<XtreamSeries> consumer) {
        streamArray(host, username, password, "get_series", node -> {
            String seriesId = readText(node.path("series_id"));
            String name = readText(node.path("name"));
            if (seriesId == null || name == null) {
                return;
            }
            consumer.accept(new XtreamSeries(
                    seriesId,
                    name,
                    // Panels disagree on the key and often serve both.
                    firstNonBlank(readText(node.path("cover")),
                            readText(node.path("stream_icon"))),
                    readText(node.path("category_id")),
                    // Three keys, and the first that PARSES wins — not the
                    // first that is non-empty. Panels routinely send
                    // `year: "N/A"` beside a usable `releaseDate`, and picking
                    // by emptiness would let the useless one shadow the good
                    // one on half a catalogue.
                    firstYear(readText(node.path("year")),
                            readText(node.path("releaseDate")),
                            readText(node.path("release_date"))),
                    parseMinutes(readText(node.path("episode_run_time"))),
                    // Echoed verbatim, as everywhere else.
                    readText(node.path("rating")),
                    readText(node.path("plot"))));
        });
    }

    /**
     * The whole tree of one series, from {@code get_series_info}.
     *
     * <p><b>One call, one series, and it returns everything.</b> That is what makes
     * it cheap enough to do on demand and far too expensive to do at
     * synchronisation.
     *
     * <p>Read whole rather than streamed, like {@code get_vod_info} and unlike the
     * catalogue walks: it describes one series and is tens of kilobytes.
     *
     * <p><b>The seasons come from the episodes, not from the {@code seasons}
     * array.</b> Panels are inconsistent about that array — absent, empty, or
     * listing seasons that have no episodes — while {@code episodes} is an object
     * keyed by season number and is what actually holds the content. Anything the
     * {@code seasons} array adds on top is merged in for its artwork and its
     * count; a season named there and empty here is kept, because a viewer should
     * see it as empty rather than not at all.
     *
     * @return null when the panel answered with something unusable. The caller
     *     decides what that means, which differs depending on whether a tree is
     *     already cached.
     */
    public List<XtreamSeason> fetchSeriesInfo(String host, String username, String password,
                                              String seriesId) {
        URI uri = playerApiWithId(host, username, password, "get_series_info", "series_id", seriesId);
        return http.get(host, uri, stream -> {
            try {
                return readTree(objectMapper.readTree(stream), host, username, password);
            } catch (RuntimeException e) {
                // A malformed answer. Common enough to be info rather than warn:
                // an array where an object belongs is the usual shape.
                log.info("Panel returned an unreadable series sheet");
                return null;
            }
        });
    }

    /**
     * Turns one {@code get_series_info} answer into seasons.
     *
     * <p>A method of its own rather than inline, so the parsing can be tested
     * against real panel bodies without a socket.
     *
     * <p><b>Returns null when the answer is not a series sheet at all</b>, and that
     * guard is the whole difference between two things a screen must never
     * confuse: a panel that lists a series with no seasons, and a panel that did
     * not answer.
     *
     * <p>Without it, an empty body — or {@code []}, or any unrelated JSON — walks
     * straight through: {@code path("seasons")} and {@code path("episodes")} both
     * yield missing nodes, both loops run zero times, and an <b>empty list comes
     * back as a successful answer</b>. The caller then stores it and stamps
     * {@code tree_fetched_at}, so a moment of transport failure becomes a recorded
     * fact — "this series has no episodes" — cached for six hours.
     *
     * <p>That is exactly what happened: a panel answering 0 bytes to a malformed
     * request left a series with eight seasons showing "no episode listed" on all
     * three clients, and retrying could not help because the emptiness was cached.
     *
     * <p>The test is what the sheet is made of rather than what it contains: an
     * object carrying at least one of {@code info}, {@code seasons} or
     * {@code episodes}. A panel that genuinely lists nothing still sends those keys
     * — that case is real, it has a screen of its own, and this keeps it.
     */
    private List<XtreamSeason> readTree(JsonNode root, String host, String username,
                                        String password) {
        if (root == null || !root.isObject()
                || (!root.has("info") && !root.has("seasons") && !root.has("episodes"))) {
            return null;
        }

        Map<Integer, XtreamSeason> seasons = new LinkedHashMap<>();

        // Declared seasons first, so their artwork and their announced count are
        // in place before the episodes arrive.
        for (JsonNode declared : root.path("seasons")) {
            Integer number = parseInt(readText(declared.path("season_number")));
            if (number == null) {
                continue;
            }
            seasons.put(number, new XtreamSeason(number,
                    parseInt(readText(declared.path("episode_count"))),
                    firstNonBlank(readText(declared.path("cover")),
                            readText(declared.path("cover_big"))),
                    new ArrayList<>()));
        }

        JsonNode episodes = root.path("episodes");
        Iterator<Map.Entry<String, JsonNode>> fields = episodes.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            for (JsonNode episode : entry.getValue()) {
                XtreamEpisode parsed = readEpisode(episode, entry.getKey(), host, username, password);
                if (parsed == null) {
                    continue;
                }
                seasons.computeIfAbsent(parsed.seasonNumber(),
                                n -> new XtreamSeason(n, null, null, new ArrayList<>()))
                        .episodes()
                        .add(parsed);
            }
        }

        List<XtreamSeason> ordered = new ArrayList<>(seasons.values());
        ordered.sort(Comparator.comparingInt(XtreamSeason::seasonNumber));
        for (XtreamSeason season : ordered) {
            season.episodes().sort(Comparator.comparingInt(XtreamEpisode::episodeNumber));
        }
        return ordered;
    }

    /**
     * One episode.
     *
     * <p><b>An episode with no container extension is dropped</b>, for the reason a
     * film without one is: its playback URL cannot be built, and an entry that
     * opens onto a failure is worse than an entry that is not there.
     *
     * <p>The season number is taken from the episode when it carries one and from
     * the key of the {@code episodes} object otherwise. Panels disagree about which
     * of the two they fill, and a few fill both with different values — in which
     * case the episode's own wins, because that is the one the panel puts next to
     * the episode number it also states.
     */
    private XtreamEpisode readEpisode(JsonNode node, String seasonKey, String host,
                                      String username, String password) {
        String episodeId = readText(node.path("id"));
        String extension = readText(node.path("container_extension"));
        Integer number = parseInt(readText(node.path("episode_num")));
        Integer season = parseInt(readText(node.path("season")));
        if (season == null) {
            season = parseInt(seasonKey);
        }
        if (episodeId == null || extension == null || number == null || season == null) {
            return null;
        }

        JsonNode info = node.path("info");
        // The sheet carries an ffprobe dump of ONE audio stream, not of all of
        // them. So this is "the codec of the track the panel describes", which is
        // enough to warn somebody and not enough to offer them a choice — the
        // choice belongs to a player that can read the file itself.
        JsonNode audio = info.path("audio");
        return new XtreamEpisode(
                episodeId,
                season,
                number,
                // Often absent, and far more often than for a film. A client shows
                // "Episode 4" rather than an empty line.
                readText(node.path("title")),
                parseSeconds(readText(info.path("duration_secs"))),
                readText(info.path("plot")),
                buildEpisodeStreamUrl(host, username, password, episodeId, extension),
                extension,
                readText(audio.path("codec_name")),
                parseInt(readText(audio.path("channels"))));
    }

    /**
     * The playback URL of one episode.
     *
     * <p>A film's, with a different path segment. A third method rather than a
     * parameter for the reason the second exists: the segment is the difference,
     * and naming it is cheaper than remembering which flag means which path.
     */
    public static String buildEpisodeStreamUrl(String host, String username, String password,
                                               String episodeId, String containerExtension) {
        return host + "/series/" + encode(username) + "/" + encode(password) + "/"
                + encode(episodeId) + "." + encode(containerExtension);
    }

    /**
     * The first of several candidates that yields a plausible year.
     *
     * <p>Not {@code firstNonBlank} followed by a parse: a panel that sends
     * {@code "N/A"} in {@code year} and a real date in {@code releaseDate} would
     * lose the real one, because the useless value is not blank.
     */
    private static Integer firstYear(String... candidates) {
        for (String candidate : candidates) {
            Integer year = parseYear(candidate);
            if (year != null) {
                return year;
            }
        }
        return null;
    }
    /** A plain integer, or null. Panels send `3`, `` and `N/A` in the same field. */
    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Xtream reports a series run time in minutes, and that is what is stored. */
    private static Integer parseMinutes(String minutes) {
        return parseInt(minutes);
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

    /**
     * The same, for a film.
     *
     * <p>A separate method rather than a boolean on the one above. The two differ
     * in the path segment <em>and</em> in where the extension comes from — a
     * channel's is always {@code .m3u8}, a film's is whatever the panel says — and
     * a flag would have hidden that second difference behind a name that only
     * mentioned the first.
     */
    public static String buildVodStreamUrl(String host, String username, String password,
                                           String streamId, String containerExtension) {
        return host + "/movie/" + encode(username) + "/" + encode(password) + "/"
                + encode(streamId) + "." + encode(containerExtension);
    }

    /** A four-digit year, or null. Panels send `1998`, `1998-03-12`, `` and `N/A`. */
    private static Integer parseYear(String value) {
        if (value == null || value.length() < 4) {
            return null;
        }
        try {
            int year = Integer.parseInt(value.substring(0, 4));
            // A plausibility window rather than a parse check: `N/A` fails above,
            // but a panel echoing a duration into this field would not.
            return year >= 1870 && year <= 2200 ? year : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Xtream reports a run time in minutes. Null when it reports nothing usable. */
    private static Integer parseSeconds(String minutes) {
        if (minutes == null || minutes.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(minutes.trim());
            return value > 0 ? value * 60 : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return (second != null && !second.isBlank()) ? second : null;
    }

    private void streamArray(String host, String username, String password,
                             String action, Consumer<JsonNode> consumer) {
        http.get(host, playerApi(host, username, password, action), stream -> {
            readArray(action, stream, consumer);
            return null;
        });
    }

    /**
     * Walks a top-level JSON array, materialising exactly one element at a time.
     *
     * <p>This is the difference between a sync that works on a 15 000-channel
     * panel and one that does not.
     */
    private void readArray(String action, InputStream stream, Consumer<JsonNode> consumer) {
        try (JsonParser parser = objectMapper.createParser(stream)) {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                // Some panels answer an error object instead of an array.
                throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                        "Expected a JSON array from the panel");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                // `parser.readValueAsTree()`, NOT `objectMapper.readTree(parser)`.
                //
                // The two read like synonyms and are not. The mapper form starts
                // a fresh read from the parser and runs it to end-of-input, so on
                // the first element it swallowed the entire array and then threw
                // "no content to map" — every Xtream panel answered
                // SOURCE_INVALID_FORMAT over a perfectly valid response. The
                // parser form reads the value the parser is standing on, which is
                // what walking an array one element at a time means.
                //
                // Nothing caught this: every ingestion test was an M3U test, and
                // Xtream is the format docs/domain-model.md recommends.
                // XtreamClientTest exists now.
                JsonNode element = parser.readValueAsTree();
                if (element != null) {
                    consumer.accept(element);
                }
            }
        } catch (IngestionException e) {
            throw e;
        } catch (Exception e) {
            // The exception TYPE, and only the type. A Jackson message can quote
            // the source it choked on, and that source is the user catalogue.
            // The class name is enough to tell a truncated stream from malformed
            // JSON, which is the distinction worth having.
            log.info("Reading the {} array failed: {}", action, e.getClass().getSimpleName());
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

    /**
     * The same, for the actions that address one item.
     *
     * <p><b>The parameter name is an argument because it is not the same for the
     * two actions that use this.</b> {@code get_vod_info} reads {@code vod_id};
     * {@code get_series_info} reads {@code series_id}. This helper used to hard-code
     * {@code vod_id} for both — its own comment said "and its kin", which is how the
     * generalisation happened — and it worked against the panels we tried because
     * most of them take whichever identifier is present.
     *
     * <p>A panel that reads only {@code series_id} would have answered nothing for
     * every series, and the phone would have said "episodes unavailable" on all of
     * them — a failure that looks like the provider being down. There is no test
     * that could have caught it either: the stub routed on {@code action} alone.
     */
    private static URI playerApiWithId(String host, String username, String password,
                                       String action, String idParameter, String id) {
        return URI.create(playerApi(host, username, password, action)
                + "&" + idParameter + "=" + encode(id));
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

    /**
     * @param streamUrl sensitive: it carries the user's credentials
     * @param number    the panel's {@code num}, null when it reports none
     * @param quality   read off the name, echoed verbatim, null when absent
     */
    public record XtreamStream(String externalId, String name, String logoUrl, String tvgId,
                               String categoryExternalId, boolean adult, String streamUrl,
                               Integer number, String quality) {
    }

    /**
     * One film, as the panel lists it.
     *
     * <p>No synopsis: it comes from {@code get_vod_info}, which is one HTTP call
     * per film, and this listing walks the whole catalogue.
     *
     * @param streamUrl          sensitive: it carries the user's credentials
     * @param containerExtension never null — a film without one is not emitted at
     *                           all, because its URL could not be built
     * @param year               four digits, or null for the panels that send
     *                           `N/A`, an empty string or a full date
     * @param durationSeconds    converted from the minutes Xtream reports, null
     *                           when it reports nothing usable
     * @param rating             echoed verbatim and never reinterpreted
     */
    /**
     * A series as {@code get_series} lists it: flat, with no season and no episode.
     */
    public record XtreamSeries(String externalId, String name, String posterUrl,
                               String categoryExternalId, Integer year,
                               Integer episodeRunTimeMinutes, String rating, String plot) {
    }

    /**
     * A season and its episodes, as {@code get_series_info} describes them.
     *
     * <p>{@code episodeCount} is what the panel claims. It can disagree with
     * {@link #episodes()}, and when it does the list is what is real — the claim is
     * carried because it is occasionally the only hint that a season is incomplete.
     */
    public record XtreamSeason(int seasonNumber, Integer episodeCount, String posterUrl,
                               List<XtreamEpisode> episodes) {
    }

    /**
     * @param streamUrl sensitive; must not be logged (AGENTS.md §5)
     * @param audioCodec what the panel calls the audio codec — {@code ac3},
     *                   {@code aac}, {@code dts} — echoed and never interpreted.
     *                   Null when the sheet does not say, which is common and
     *                   means "not known" rather than "no sound".
     * @param audioChannels the channel count of that track, when stated
     */
    public record XtreamEpisode(String externalId, int seasonNumber, int episodeNumber,
                                String name, Integer durationSeconds, String plot,
                                String streamUrl, String containerExtension,
                                String audioCodec, Integer audioChannels) {
    }

    public record XtreamVodStream(String externalId, String name, String posterUrl,
                                  String categoryExternalId, boolean adult, String streamUrl,
                                  String containerExtension, Integer year,
                                  Integer durationSeconds, String rating) {
    }
}
