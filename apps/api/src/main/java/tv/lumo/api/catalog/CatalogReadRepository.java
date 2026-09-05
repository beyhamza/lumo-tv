package tv.lumo.api.catalog;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.Category;
import tv.lumo.api.generated.model.Channel;
import tv.lumo.api.generated.model.ContentType;
import tv.lumo.api.generated.model.EpgProgramme;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.Episode;
import tv.lumo.api.generated.model.Season;
import tv.lumo.api.generated.model.Series;
import tv.lumo.api.generated.model.VodItem;

/**
 * User-scoped catalogue reads.
 *
 * <p><b>Every query joins to {@code source} and filters on {@code source.user_id}.</b>
 * A channel or category id from a URL is never enough on its own: ownership is
 * part of the WHERE clause, so asking for someone else's channel returns nothing
 * rather than returning their data (docs/architecture.md §2).
 *
 * <p><b>No listing query selects {@code stream_url}.</b> The column is named in
 * exactly one method here, {@link #findStreamUrlOwnedBy}, which serves
 * {@code GET /channels/{id}/playback}. That is the contract's rule made
 * mechanical: a list of a thousand channels cannot leak a thousand stream URLs if
 * the SQL never asks for them.
 */
@Repository
public class CatalogReadRepository {

    private final JdbcClient jdbc;

    public CatalogReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Category> findCategories(UUID sourceId, UUID userId, ContentType contentType) {
        return jdbc.sql("""
                SELECT c.id, c.source_id, c.external_id, c.name, c.content_type, c.position,
                       -- What a category holds depends on what kind it is. Counting
                       -- the channel table alone gave every film and series category
                       -- a "(0)" next to a name that had thousands of items behind it.
                       CASE c.content_type
                           WHEN 'VOD'    THEN (SELECT count(*) FROM vod_item v  WHERE v.category_id = c.id)
                           WHEN 'SERIES' THEN (SELECT count(*) FROM series se   WHERE se.category_id = c.id)
                           ELSE               (SELECT count(*) FROM channel ch  WHERE ch.category_id = c.id)
                       END AS channel_count
                  FROM category c
                  JOIN source s ON s.id = c.source_id
                 WHERE c.source_id = :sourceId
                   AND s.user_id = :userId
                   AND (:contentType::text IS NULL OR c.content_type = :contentType)
                 ORDER BY c.position, c.name
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("contentType", contentType == null ? null : contentType.getValue())
                .query((rs, n) -> {
                    Category category = new Category(
                            rs.getObject("id", UUID.class),
                            rs.getObject("source_id", UUID.class),
                            rs.getString("name"),
                            ContentType.fromValue(rs.getString("content_type")),
                            rs.getInt("position"));
                    category.setExternalId(rs.getString("external_id"));
                    category.setChannelCount(rs.getInt("channel_count"));
                    return category;
                })
                .list();
    }

    /** One page of channels. Note the absence of {@code stream_url} in the projection. */
    public List<Channel> findChannels(UUID sourceId, UUID userId, UUID categoryId,
                                      String search, List<UUID> ids, int page, int size) {
        return jdbc.sql("""
                SELECT ch.id, ch.source_id, ch.category_id, ch.external_id, ch.name,
                       ch.logo_url, ch.tvg_id, ch.number, ch.quality, ch.position, ch.is_adult
                  FROM channel ch
                  JOIN source s ON s.id = ch.source_id
                 WHERE ch.source_id = :sourceId
                   AND s.user_id = :userId
                   AND (:categoryId::uuid IS NULL OR ch.category_id = :categoryId)
                   AND (:search::text IS NULL OR ch.name ILIKE '%' || :search || '%')
                   AND (:ids::text IS NULL OR ch.id = ANY(string_to_array(:ids, ',')::uuid[]))
                 ORDER BY ch.category_id NULLS LAST, ch.position, ch.name
                 LIMIT :size OFFSET :offset
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("search", search)
                .param("ids", idArray(ids))
                .param("size", size)
                .param("offset", (long) page * size)
                .query(CatalogReadRepository::mapChannel)
                .list();
    }

    public long countChannels(UUID sourceId, UUID userId, UUID categoryId, String search,
                              List<UUID> ids) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM channel ch
                  JOIN source s ON s.id = ch.source_id
                 WHERE ch.source_id = :sourceId
                   AND s.user_id = :userId
                   AND (:categoryId::uuid IS NULL OR ch.category_id = :categoryId)
                   AND (:search::text IS NULL OR ch.name ILIKE '%' || :search || '%')
                   AND (:ids::text IS NULL OR ch.id = ANY(string_to_array(:ids, ',')::uuid[]))
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("search", search)
                .param("ids", idArray(ids))
                .query(Long.class)
                .single();
    }

    /**
     * The {@code ids} filter, as one bound value.
     *
     * <p>Joined into a single string and split back into a {@code uuid[]} by
     * PostgreSQL, rather than expanded into an {@code IN (...)} list. Two reasons,
     * and neither is style:
     *
     * <ul>
     *   <li>the SQL above stays one static statement with one plan, instead of a
     *       different statement — and a different prepared-statement cache entry —
     *       for every number of identifiers a client happens to send;
     *   <li>an absent filter needs no branch. {@code NULL} disables the clause the
     *       same way it does for {@code categoryId} and {@code search} just above,
     *       whereas an expanded {@code IN} list has no legal empty form.
     * </ul>
     *
     * <p>The values are {@link UUID} instances, so their {@code toString} cannot
     * contain a comma or a quote; and they are bound, not concatenated, so this is
     * a parameter in every sense that matters.
     */
    private static String idArray(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return null;

        StringBuilder joined = new StringBuilder();
        for (UUID id : ids) {
            if (!joined.isEmpty()) joined.append(',');
            joined.append(id);
        }
        return joined.toString();
    }

    /**
     * The one query in this application that reads {@code stream_url}.
     *
     * <p>Scoped to the owner in the same statement rather than by a separate
     * ownership check, so there is no window in which the URL is loaded for a
     * caller who turns out not to own it.
     */
    public Optional<PlaybackRow> findStreamUrlOwnedBy(UUID channelId, UUID userId) {
        return jdbc.sql("""
                SELECT ch.stream_url, s.max_connections, s.status, s.expires_at
                  FROM channel ch
                  JOIN source s ON s.id = ch.source_id
                 WHERE ch.id = :channelId
                   AND s.user_id = :userId
                """)
                .param("channelId", channelId)
                .param("userId", userId)
                .query((rs, n) -> new PlaybackRow(
                        rs.getString("stream_url"),
                        rs.getObject("max_connections", Integer.class),
                        rs.getString("status"),
                        rs.getObject("expires_at", OffsetDateTime.class)))
                .optional();
    }

    /**
     * Resolves a channel to its source, for the caller only.
     *
     * <p>This is how {@code source_id} gets onto a favourite or a recently watched
     * entry without either request accepting one. The contract refuses that
     * property on both bodies for the same reason: derived from the channel, the
     * two cannot disagree, and a client cannot file another user's channel under a
     * source of its own.
     *
     * @return empty when no such channel exists on a source this user owns —
     *         indistinguishable, on purpose, from a channel id that does not exist
     */
    public Optional<UUID> findOwnedChannelSourceId(UUID channelId, UUID userId) {
        return jdbc.sql("""
                SELECT ch.source_id
                  FROM channel ch
                  JOIN source s ON s.id = ch.source_id
                 WHERE ch.id = :channelId AND s.user_id = :userId
                """)
                .param("channelId", channelId)
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    /** Confirms a channel belongs to the caller, without loading its stream URL. */
    public Optional<String> findChannelTvgId(UUID channelId, UUID userId) {
        return jdbc.sql("""
                SELECT COALESCE(ch.tvg_id, '') AS tvg_id
                  FROM channel ch
                  JOIN source s ON s.id = ch.source_id
                 WHERE ch.id = :channelId AND s.user_id = :userId
                """)
                .param("channelId", channelId)
                .param("userId", userId)
                .query(String.class)
                .optional();
    }

    /**
     * Programmes for one channel over a range.
     *
     * <p>Overlap rather than containment: a programme that started before
     * {@code from} and is still running is what "what is on now" means.
     */
    public List<EpgProgramme> findProgrammes(UUID channelId, UUID userId,
                                             OffsetDateTime from, OffsetDateTime to) {
        return jdbc.sql("""
                SELECT p.id, p.source_id, p.tvg_id, p.starts_at, p.ends_at,
                       p.title, p.description, p.category
                  FROM epg_programme p
                  JOIN channel ch ON ch.source_id = p.source_id AND ch.tvg_id = p.tvg_id
                  JOIN source s   ON s.id = ch.source_id
                 WHERE ch.id = :channelId
                   AND s.user_id = :userId
                   AND p.ends_at > :from
                   AND p.starts_at < :to
                 ORDER BY p.starts_at
                """)
                .param("channelId", channelId)
                .param("userId", userId)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> {
                    EpgProgramme programme = new EpgProgramme(
                            rs.getObject("id", UUID.class),
                            rs.getObject("source_id", UUID.class),
                            rs.getString("tvg_id"),
                            rs.getObject("starts_at", OffsetDateTime.class),
                            rs.getObject("ends_at", OffsetDateTime.class),
                            rs.getString("title"));
                    programme.setDescription(rs.getString("description"));
                    programme.setCategory(rs.getString("category"));
                    return programme;
                })
                .list();
    }

    // ---- films --------------------------------------------------------------
    //
    // Deliberately the channel queries again rather than one parameterised over a
    // table name: the two projections differ (a film has a poster and a year, a
    // channel has a tvg_id and a number), and a shared query would take a table
    // name from Java, which is the one kind of value that cannot be bound.

    /** One page of films. Note the absence of {@code stream_url} and of {@code plot}. */
    public List<VodItem> findVod(UUID sourceId, UUID userId, UUID categoryId,
                                 String search, List<UUID> ids, int page, int size) {
        return jdbc.sql("""
                SELECT v.id, v.source_id, v.category_id, v.external_id, v.name,
                       v.poster_url, v.year, v.duration_seconds, v.rating,
                       v.position, v.is_adult
                  FROM vod_item v
                  JOIN source s ON s.id = v.source_id
                 WHERE v.source_id = :sourceId
                   AND s.user_id = :userId
                   AND (:categoryId::uuid IS NULL OR v.category_id = :categoryId)
                   AND (:search::text IS NULL OR v.name ILIKE '%' || :search || '%')
                   AND (:ids::text IS NULL OR v.id = ANY(string_to_array(:ids, ',')::uuid[]))
                 ORDER BY v.category_id NULLS LAST, v.position, v.name
                 LIMIT :size OFFSET :offset
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("search", search)
                .param("ids", idArray(ids))
                .param("size", size)
                .param("offset", (long) page * size)
                .query(CatalogReadRepository::mapVodItem)
                .list();
    }

    public long countVod(UUID sourceId, UUID userId, UUID categoryId, String search,
                         List<UUID> ids) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM vod_item v
                  JOIN source s ON s.id = v.source_id
                 WHERE v.source_id = :sourceId
                   AND s.user_id = :userId
                   AND (:categoryId::uuid IS NULL OR v.category_id = :categoryId)
                   AND (:search::text IS NULL OR v.name ILIKE '%' || :search || '%')
                   AND (:ids::text IS NULL OR v.id = ANY(string_to_array(:ids, ',')::uuid[]))
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("search", search)
                .param("ids", idArray(ids))
                .query(Long.class)
                .single();
    }

    /**
     * One film, with its synopsis and everything needed to decide whether to ask
     * the panel for one.
     *
     * <p>The only projection that selects {@code plot}. It also selects
     * {@code plot_fetched_at}, which is not on the contract and is not meant to
     * be: whether a synopsis has been asked for is this server's bookkeeping, and
     * a client that could see it would start deciding when to refresh — which is
     * exactly the decision that has to stay on this side of the wire, because it
     * spends the user's own panel's capacity.
     */
    public Optional<VodDetail> findVodOwnedBy(UUID vodItemId, UUID userId) {
        return jdbc.sql("""
                SELECT v.id, v.source_id, v.category_id, v.external_id, v.name,
                       v.poster_url, v.year, v.duration_seconds, v.rating,
                       v.position, v.is_adult, v.plot, v.plot_fetched_at,
                       v.container_extension, s.kind AS source_kind
                  FROM vod_item v
                  JOIN source s ON s.id = v.source_id
                 WHERE v.id = :vodItemId
                   AND s.user_id = :userId
                """)
                .param("vodItemId", vodItemId)
                .param("userId", userId)
                .query((rs, n) -> new VodDetail(
                        mapVodItem(rs, n),
                        rs.getString("plot"),
                        rs.getObject("plot_fetched_at", OffsetDateTime.class),
                        rs.getObject("source_id", UUID.class),
                        rs.getString("external_id"),
                        SourceKind.fromValue(rs.getString("source_kind"))))
                .optional();
    }

    /**
     * A film plus what the server needs in order to decide whether to call the
     * panel, and how.
     *
     * @param plotFetchedAt null means never asked. Non-null with a null
     *                      {@code plot} means asked, and the provider had nothing
     * @param sourceKind    an M3U film has no panel to ask: its playlist is the
     *                      whole of what is known about it
     */
    public record VodDetail(VodItem item, String plot, OffsetDateTime plotFetchedAt,
                            UUID sourceId, String externalId, SourceKind sourceKind) {
    }

    /**
     * The second query in this application that reads a stream URL, and the last.
     *
     * <p>Scoped to the owner in the same statement, for the reason written on
     * {@link #findStreamUrlOwnedBy}: there is no window in which the URL is loaded
     * for a caller who turns out not to own it.
     */
    public Optional<PlaybackRow> findVodStreamUrlOwnedBy(UUID vodItemId, UUID userId) {
        return jdbc.sql("""
                SELECT v.stream_url, s.max_connections, s.status, s.expires_at
                  FROM vod_item v
                  JOIN source s ON s.id = v.source_id
                 WHERE v.id = :vodItemId
                   AND s.user_id = :userId
                """)
                .param("vodItemId", vodItemId)
                .param("userId", userId)
                .query((rs, n) -> new PlaybackRow(
                        rs.getString("stream_url"),
                        rs.getObject("max_connections", Integer.class),
                        rs.getString("status"),
                        rs.getObject("expires_at", OffsetDateTime.class)))
                .optional();
    }

    /** @param streamUrl sensitive; must not be logged or cached anywhere shared */
    public record PlaybackRow(String streamUrl, Integer maxConnections,
                              String sourceStatus, OffsetDateTime sourceExpiresAt) {
    }

    // ---- series -------------------------------------------------------------
    //
    // The film queries again, one level deeper. Same reasoning as above for not
    // parameterising them over a table name, and one more that is particular to
    // this level: a series carries a tree, and the tree is read by a second query
    // rather than joined into the first — see {@link #findTree}.

    /** One page of series. No {@code plot}, and no tree. */
    public List<Series> findSeries(UUID sourceId, UUID userId, UUID categoryId,
                                   String search, List<UUID> ids, int page, int size) {
        return jdbc.sql("""
                SELECT sr.id, sr.source_id, sr.category_id, sr.external_id, sr.name,
                       sr.poster_url, sr.year, sr.episode_run_time, sr.rating,
                       sr.position, sr.is_adult
                  FROM series sr
                  JOIN source s ON s.id = sr.source_id
                 WHERE sr.source_id = :sourceId
                   AND s.user_id = :userId
                   AND (:categoryId::uuid IS NULL OR sr.category_id = :categoryId)
                   AND (:search::text IS NULL OR sr.name ILIKE '%' || :search || '%')
                   AND (:ids::text IS NULL OR sr.id = ANY(string_to_array(:ids, ',')::uuid[]))
                 ORDER BY sr.category_id NULLS LAST, sr.position, sr.name
                 LIMIT :size OFFSET :offset
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("search", search)
                .param("ids", idArray(ids))
                .param("size", size)
                .param("offset", (long) page * size)
                .query(CatalogReadRepository::mapSeries)
                .list();
    }

    public long countSeries(UUID sourceId, UUID userId, UUID categoryId, String search,
                            List<UUID> ids) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM series sr
                  JOIN source s ON s.id = sr.source_id
                 WHERE sr.source_id = :sourceId
                   AND s.user_id = :userId
                   AND (:categoryId::uuid IS NULL OR sr.category_id = :categoryId)
                   AND (:search::text IS NULL OR sr.name ILIKE '%' || :search || '%')
                   AND (:ids::text IS NULL OR sr.id = ANY(string_to_array(:ids, ',')::uuid[]))
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("search", search)
                .param("ids", idArray(ids))
                .query(Long.class)
                .single();
    }

    /**
     * One series, with what the cache stamp says about its tree.
     *
     * <p>The stamp comes back with the row rather than being read separately,
     * because the caller's next decision depends on it: an absent tree and a stale
     * tree both mean "call the provider", and a fresh one means "do not". One
     * query answers all three.
     */
    public Optional<SeriesDetailRow> findSeriesOwnedBy(UUID seriesId, UUID userId) {
        return jdbc.sql("""
                SELECT sr.id, sr.source_id, sr.category_id, sr.external_id, sr.name,
                       sr.poster_url, sr.year, sr.episode_run_time, sr.rating,
                       sr.position, sr.is_adult,
                       sr.plot, sr.tree_fetched_at, s.kind AS source_kind
                  FROM series sr
                  JOIN source s ON s.id = sr.source_id
                 WHERE sr.id = :seriesId
                   AND s.user_id = :userId
                """)
                .param("seriesId", seriesId)
                .param("userId", userId)
                .query((rs, n) -> new SeriesDetailRow(
                        mapSeries(rs, n),
                        rs.getString("plot"),
                        rs.getObject("tree_fetched_at", OffsetDateTime.class),
                        rs.getObject("source_id", UUID.class),
                        rs.getString("external_id"),
                        SourceKind.fromValue(rs.getString("source_kind"))))
                .optional();
    }

    public record SeriesDetailRow(Series series, String plot, OffsetDateTime treeFetchedAt,
                                  UUID sourceId, String externalId, SourceKind sourceKind) {
    }

    /**
     * The tree of one series, in one pass.
     *
     * <p>Seasons and episodes come back as a single ordered result set and are
     * grouped in Java rather than in two queries: the tree of a series is tens of
     * rows, and a second round trip to fetch the seasons of a series whose
     * episodes are already in hand buys nothing.
     *
     * <p><b>A season with no episodes is still a season.</b> The left join keeps
     * it, because a panel that lists a season and returns nothing for it is
     * describing something a viewer should see as empty rather than as absent.
     */
    public List<Season> findTree(UUID seriesId) {
        List<Season> seasons = new ArrayList<>();
        Map<Integer, Season> bySeasonNumber = new LinkedHashMap<>();

        jdbc.sql("""
                SELECT se.season_number, se.episode_count, se.poster_url,
                       e.id AS episode_id, e.series_id, e.source_id, e.external_id,
                       e.season_number AS episode_season_number, e.episode_number,
                       e.name AS episode_name, e.duration_seconds, e.plot AS episode_plot,
                       e.audio_codec, e.audio_channels
                  FROM season se
                  LEFT JOIN episode e ON e.season_id = se.id
                 WHERE se.series_id = :seriesId
                 ORDER BY se.season_number, e.episode_number
                """)
                .param("seriesId", seriesId)
                .query((rs, n) -> {
                    int seasonNumber = rs.getInt("season_number");
                    Season season = bySeasonNumber.get(seasonNumber);
                    if (season == null) {
                        season = new Season(seasonNumber, new ArrayList<>());
                        season.setEpisodeCount(rs.getObject("episode_count", Integer.class));
                        season.setPosterUrl(rs.getString("poster_url"));
                        bySeasonNumber.put(seasonNumber, season);
                        seasons.add(season);
                    }
                    UUID episodeId = rs.getObject("episode_id", UUID.class);
                    // Null on the left join's empty side: the season exists and
                    // holds nothing, which is a state a panel really produces.
                    if (episodeId != null) {
                        season.getEpisodes().add(mapEpisode(rs));
                    }
                    return season;
                })
                .list();

        return seasons;
    }

    /** Episodes by identifier, scoped to a source the caller owns. */
    public List<Episode> findEpisodes(UUID sourceId, UUID userId, List<UUID> ids,
                                      int page, int size) {
        return jdbc.sql("""
                SELECT e.id AS episode_id, e.series_id, e.source_id, e.external_id,
                       e.season_number AS episode_season_number, e.episode_number,
                       e.name AS episode_name, e.duration_seconds, e.plot AS episode_plot,
                       e.audio_codec, e.audio_channels
                  FROM episode e
                  JOIN source s ON s.id = e.source_id
                 WHERE e.source_id = :sourceId
                   AND s.user_id = :userId
                   AND e.id = ANY(string_to_array(:ids, ',')::uuid[])
                 ORDER BY e.series_id, e.season_number, e.episode_number
                 LIMIT :size OFFSET :offset
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("ids", idArray(ids))
                .param("size", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> mapEpisode(rs))
                .list();
    }

    public long countEpisodes(UUID sourceId, UUID userId, List<UUID> ids) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM episode e
                  JOIN source s ON s.id = e.source_id
                 WHERE e.source_id = :sourceId
                   AND s.user_id = :userId
                   AND e.id = ANY(string_to_array(:ids, ',')::uuid[])
                """)
                .param("sourceId", sourceId)
                .param("userId", userId)
                .param("ids", idArray(ids))
                .query(Long.class)
                .single();
    }

    /**
     * The third and last query in this application that reads a stream URL.
     *
     * <p>Scoped to the owner in the same statement, for the reason written on
     * {@link #findStreamUrlOwnedBy}.
     */
    public Optional<PlaybackRow> findEpisodeStreamUrlOwnedBy(UUID episodeId, UUID userId) {
        return jdbc.sql("""
                SELECT e.stream_url, s.max_connections, s.status, s.expires_at
                  FROM episode e
                  JOIN source s ON s.id = e.source_id
                 WHERE e.id = :episodeId
                   AND s.user_id = :userId
                """)
                .param("episodeId", episodeId)
                .param("userId", userId)
                .query((rs, n) -> new PlaybackRow(
                        rs.getString("stream_url"),
                        rs.getObject("max_connections", Integer.class),
                        rs.getString("status"),
                        rs.getObject("expires_at", OffsetDateTime.class)))
                .optional();
    }

    static Series mapSeries(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Series series = new Series(
                rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getString("name"),
                rs.getInt("position"),
                rs.getBoolean("is_adult"));
        series.setCategoryId(rs.getObject("category_id", UUID.class));
        series.setExternalId(rs.getString("external_id"));
        series.setPosterUrl(rs.getString("poster_url"));
        series.setYear(rs.getObject("year", Integer.class));
        series.setEpisodeRunTime(rs.getObject("episode_run_time", Integer.class));
        series.setRating(rs.getString("rating"));
        return series;
    }

    /**
     * Maps an episode row.
     *
     * <p>The column aliases are the tree query's, so one mapper serves both it and
     * the resolver. {@code stream_url} is in neither projection.
     */
    static Episode mapEpisode(java.sql.ResultSet rs) throws java.sql.SQLException {
        Episode episode = new Episode(
                rs.getObject("episode_id", UUID.class),
                rs.getObject("series_id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getInt("episode_season_number"),
                rs.getInt("episode_number"));
        episode.setExternalId(rs.getString("external_id"));
        episode.setName(rs.getString("episode_name"));
        episode.setDurationSeconds(rs.getObject("duration_seconds", Long.class));
        episode.setPlot(rs.getString("episode_plot"));
        // Verbatim, both of them. What a browser can decode is the browser's
        // business — see the contract's note on this field.
        episode.setAudioCodec(rs.getString("audio_codec"));
        episode.setAudioChannels(rs.getObject("audio_channels", Integer.class));
        return episode;
    }

    /**
     * Maps a film row.
     *
     * <p>{@code plot} is absent from every projection that feeds this, and that is
     * not an omission: it is fetched when somebody opens a film rather than when
     * they scroll past a thousand, because on an Xtream panel it costs one HTTP
     * call to the user's own server per film.
     */
    static VodItem mapVodItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        VodItem item = new VodItem(
                rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getString("name"),
                rs.getInt("position"),
                rs.getBoolean("is_adult"));
        item.setCategoryId(rs.getObject("category_id", UUID.class));
        item.setExternalId(rs.getString("external_id"));
        item.setPosterUrl(rs.getString("poster_url"));
        item.setYear(rs.getObject("year", Integer.class));
        item.setDurationSeconds(rs.getObject("duration_seconds", Integer.class));
        item.setRating(rs.getString("rating"));
        return item;
    }

    static Channel mapChannel(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Channel channel = new Channel(
                rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getString("name"),
                rs.getInt("position"),
                rs.getBoolean("is_adult"));
        channel.setCategoryId(rs.getObject("category_id", UUID.class));
        channel.setExternalId(rs.getString("external_id"));
        channel.setLogoUrl(rs.getString("logo_url"));
        channel.setTvgId(rs.getString("tvg_id"));
        channel.setNumber(rs.getObject("number", Integer.class));
        channel.setQuality(rs.getString("quality"));
        return channel;
    }
}
