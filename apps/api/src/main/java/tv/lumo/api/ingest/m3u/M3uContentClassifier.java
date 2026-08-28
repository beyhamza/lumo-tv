package tv.lumo.api.ingest.m3u;

import java.util.Locale;
import java.util.Set;
import tv.lumo.api.generated.model.ContentType;

/**
 * Decides whether an M3U entry is a channel or a film (ADR 0009).
 *
 * <h2>One function, in one place, testable without a network</h2>
 *
 * That is rule 2 of the ADR, and the reason for it: a heuristic spread through a
 * parser is a heuristic nobody can show to be wrong. This takes a URL and returns
 * a {@link ContentType}, and its test is a table of the cases where it fails
 * rather than a list of the ones where it works.
 *
 * <h2>Only the URL classifies</h2>
 *
 * Not {@code group-title}, not a missing {@code tvg-id}, not any number of them
 * combined. The two signals below describe <em>what the thing is</em>; a group
 * title describes <em>what somebody called it</em>, and people call live channels
 * {@code CINE+}, {@code Film4} and {@code VOD Sports News}.
 *
 * <p>It is also the only signal whose meaning does not depend on a language. A
 * French playlist writes {@code FILMS}, a Spanish one {@code PELÍCULAS}, and a
 * keyword list per language is a list permanently one language behind.
 *
 * <h2>Doubt falls towards LIVE</h2>
 *
 * The two errors do not cost the same. A film among the channels plays and is
 * searchable — untidy. A channel among the films lands in a poster grid with no
 * poster, beside a "resume at 20 min" that means nothing on a continuous stream,
 * and starts accumulating progress rows for something that has no position.
 *
 * <p>An Xtream source never reaches this class: its endpoints are authoritative.
 */
public final class M3uContentClassifier {

    /**
     * Container extensions a live stream is not served as.
     *
     * <p>A channel arrives as {@code .m3u8}, {@code .ts}, or with no extension at
     * all. These are files, and a file is something somebody downloads and seeks
     * through.
     */
    private static final Set<String> FILM_EXTENSIONS = Set.of("mkv", "mp4", "avi", "m4v", "mov");

    /** Xtream's own path segment for a film, in a playlist exported from a panel. */
    private static final String FILM_PATH_SEGMENT = "/movie/";

    private M3uContentClassifier() {
    }

    /**
     * @param streamUrl the entry's URL, exactly as the playlist wrote it
     * @return {@link ContentType#VOD} on a structural match, {@link ContentType#LIVE}
     *         otherwise — never null, and never {@code SERIES}: an M3U playlist
     *         declares no tree and this product does not invent one from titles
     *         (see `sprint-06.md`, S6-00)
     */
    public static ContentType classify(String streamUrl) {
        if (streamUrl == null || streamUrl.isBlank()) {
            return ContentType.LIVE;
        }

        String path = pathOf(streamUrl).toLowerCase(Locale.ROOT);

        if (path.contains(FILM_PATH_SEGMENT)) {
            return ContentType.VOD;
        }

        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        // A dot before the last slash belongs to a host name or a directory, not
        // to the file: `http://vod.example/live/1` is not a film.
        if (dot > slash && dot >= 0) {
            return FILM_EXTENSIONS.contains(path.substring(dot + 1))
                    ? ContentType.VOD
                    : ContentType.LIVE;
        }

        return ContentType.LIVE;
    }

    /**
     * The URL without its query string or fragment.
     *
     * <p>Both are common on real playlists — a token, a session id — and both can
     * end in something that looks like an extension. Parsing with {@code URI}
     * would be stricter and would also throw on the malformed URLs playlists
     * genuinely contain, so this cuts rather than parses.
     */
    private static String pathOf(String streamUrl) {
        int cut = streamUrl.length();
        int query = streamUrl.indexOf('?');
        if (query >= 0) {
            cut = query;
        }
        int fragment = streamUrl.indexOf('#');
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        return streamUrl.substring(0, cut);
    }
}
