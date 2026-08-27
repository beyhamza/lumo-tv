package tv.lumo.api.catalog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.Category;
import tv.lumo.api.generated.model.Channel;
import tv.lumo.api.generated.model.ContentType;
import tv.lumo.api.generated.model.EpgProgramme;

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
                       (SELECT count(*) FROM channel ch WHERE ch.category_id = c.id) AS channel_count
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

    /** @param streamUrl sensitive; must not be logged or cached anywhere shared */
    public record PlaybackRow(String streamUrl, Integer maxConnections,
                              String sourceStatus, OffsetDateTime sourceExpiresAt) {
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
