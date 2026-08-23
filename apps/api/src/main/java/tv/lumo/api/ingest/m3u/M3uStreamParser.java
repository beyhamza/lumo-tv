package tv.lumo.api.ingest.m3u;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.ingest.IngestionException;

/**
 * Streaming M3U parser.
 *
 * <p><b>Line by line, never the whole file.</b> Each channel is handed to the
 * consumer as soon as its two lines have been read, so peak memory is one entry
 * regardless of whether the playlist holds fifty channels or fifty thousand.
 *
 * <p>The format in practice:
 * <pre>
 * #EXTM3U
 * #EXTINF:-1 tvg-id="x" tvg-name="y" tvg-logo="z" group-title="g",Display Name
 * &lt;url&gt;
 * </pre>
 *
 * <p>Real playlists are messier than the format suggests: blank lines between
 * entries, {@code #EXTGRP} on its own line, unquoted attribute values, CRLF, a
 * BOM, and entries whose URL never arrives. All of that is tolerated. An entry
 * that cannot be made sense of is skipped rather than failing the import — one
 * malformed line out of fifteen thousand should not cost the user their whole
 * catalogue.
 */
@Component
public class M3uStreamParser {

    private static final Pattern ATTRIBUTE =
            Pattern.compile("([A-Za-z0-9-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
    /**
     * The group an entry with no {@code group-title} falls into (US-07).
     *
     * <p>It is a <b>fallback label</b>, not a translated one — the server has no
     * business inventing user-facing copy in one language. What clients key on
     * is the stable {@code external_id} the ingestion layer gives this group
     * ({@code m3u:__unclassified__}); this string is only what a client that
     * does not recognise the sentinel would show.
     */
    public static final String UNCLASSIFIED = "Unclassified";

    /**
     * @return how many channels were emitted
     * @throws IngestionException {@code SOURCE_INVALID_FORMAT} if this is not an
     *                            M3U at all, {@code SOURCE_EMPTY} if it parses but
     *                            holds no channel
     */
    public int parse(InputStream input, Consumer<ParsedChannel> consumer) {
        int emitted = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8), 64 * 1024)) {

            String first = nextMeaningfulLine(reader);
            if (first == null || !stripBom(first).startsWith("#EXTM3U")) {
                // Without this check an HTML error page parses as a playlist with
                // zero channels, and the user is told their playlist is empty when
                // the real problem is that the URL is wrong.
                throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                        "The response does not start with #EXTM3U");
            }

            ExtInf pending = null;
            String group = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#EXTINF")) {
                    pending = parseExtInf(line);
                } else if (line.startsWith("#EXTGRP")) {
                    // Legacy alternative to group-title, still emitted by some
                    // generators; applies to the entry that follows.
                    int colon = line.indexOf(':');
                    group = colon >= 0 ? line.substring(colon + 1).trim() : null;
                } else if (line.startsWith("#")) {
                    continue;
                } else if (pending != null) {
                    consumer.accept(new ParsedChannel(
                            pending.tvgId(),
                            pending.name(),
                            pending.logo(),
                            firstNonBlank(pending.group(), group, UNCLASSIFIED),
                            line));
                    emitted++;
                    pending = null;
                    group = null;
                }
                // A URL with no preceding #EXTINF has no name and no group; it is
                // skipped rather than imported as a nameless channel.
            }
        } catch (IngestionException e) {
            throw e;
        } catch (IOException e) {
            throw IngestionException.from(e);
        }

        if (emitted == 0) {
            throw new IngestionException(IngestionErrorCode.SOURCE_EMPTY,
                    "The playlist parsed correctly but holds no channel");
        }
        return emitted;
    }

    private static String nextMeaningfulLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return null;
    }

    private static ExtInf parseExtInf(String line) {
        int comma = line.lastIndexOf(',');
        String displayName = comma >= 0 && comma < line.length() - 1
                ? line.substring(comma + 1).trim()
                : null;
        String attributesPart = comma >= 0 ? line.substring(0, comma) : line;

        String tvgId = null;
        String tvgName = null;
        String logo = null;
        String group = null;

        Matcher matcher = ATTRIBUTE.matcher(attributesPart);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            String value = firstNonBlank(matcher.group(3), matcher.group(4), matcher.group(5));
            switch (key) {
                case "tvg-id" -> tvgId = value;
                case "tvg-name" -> tvgName = value;
                case "tvg-logo" -> logo = value;
                case "group-title" -> group = value;
                default -> { /* playlists carry many other attributes; none are ours */ }
            }
        }
        // The text after the comma is what the user sees in their own player, so
        // it wins over tvg-name when both are present.
        return new ExtInf(blankToNull(tvgId), firstNonBlank(displayName, tvgName, "Unnamed"),
                blankToNull(logo), blankToNull(group));
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record ExtInf(String tvgId, String name, String logo, String group) {
    }

    /**
     * One channel, as read from the playlist.
     *
     * @param categoryName never null; entries with no group land in "Unclassified"
     *                     rather than being dropped (US-07)
     * @param streamUrl    sensitive: never logged, never returned by a listing
     */
    public record ParsedChannel(String tvgId, String name, String logoUrl,
                                String categoryName, String streamUrl) {
    }
}
