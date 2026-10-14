package tv.lumo.api.userdata;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.WatchlistContentType;
import tv.lumo.api.generated.model.WatchlistItem;

/**
 * Access to {@code watchlist_item} and {@code watchlist_intention} (ADR 0011).
 *
 * <p>Every statement filters on {@code user_id}. An item is identified by
 * {@code (source, content_type, external_id)} — the source's own identifier —
 * and joined to the catalogue on every read, so availability is whatever the
 * last completed synchronisation left, never a flag that could drift from it.
 */
@Repository
public class WatchlistRepository {

    /**
     * The item as a client sees it. Catalogue values win while the content
     * exists; the snapshot taken at add time stands in once it does not.
     */
    private static final String SELECT_ITEM = """
            SELECT w.id, w.source_id, w.content_type, w.added_at,
                   COALESCE(v.id, se.id) AS content_id,
                   CASE WHEN v.id IS NULL AND se.id IS NULL THEN w.title
                        ELSE COALESCE(v.name, se.name) END AS title,
                   CASE WHEN v.id IS NULL AND se.id IS NULL THEN w.poster_url
                        ELSE COALESCE(v.poster_url, se.poster_url) END AS poster_url
              FROM watchlist_item w
              LEFT JOIN vod_item v
                     ON w.content_type = 'VOD'
                    AND v.source_id = w.source_id AND v.external_id = w.external_id
              LEFT JOIN series se
                     ON w.content_type = 'SERIES'
                    AND se.source_id = w.source_id AND se.external_id = w.external_id
            """;

    private final JdbcClient jdbc;

    public WatchlistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** What an add needs from the catalogue: whose it is, its identity, its snapshot. */
    public record CatalogueEntry(UUID sourceId, String externalId, String title, String posterUrl) {}

    /**
     * The film or series, if it is on a source the caller owns. {@code Optional.empty()}
     * for somebody else's as for a missing one, so the answer reveals nothing.
     */
    public Optional<CatalogueEntry> findOwnedContent(WatchlistContentType type, UUID contentId,
                                                     UUID userId) {
        String table = switch (type) {
            case VOD -> "vod_item";
            case SERIES -> "series";
        };
        return jdbc.sql("""
                SELECT c.source_id, c.external_id, c.name, c.poster_url
                  FROM %s c
                  JOIN source s ON s.id = c.source_id
                 WHERE c.id = :contentId AND s.user_id = :userId AND c.external_id IS NOT NULL
                """.formatted(table))
                .param("contentId", contentId)
                .param("userId", userId)
                .query((rs, n) -> new CatalogueEntry(
                        rs.getObject("source_id", UUID.class), rs.getString("external_id"),
                        rs.getString("name"), rs.getString("poster_url")))
                .optional();
    }

    /**
     * Inserts the item unless the account already has it.
     *
     * @return the new item's id, or empty when it was already there — in which
     *         case nothing changed, its {@code added_at} included
     */
    public Optional<UUID> insertIfAbsent(UUID userId, WatchlistContentType type, CatalogueEntry entry) {
        return jdbc.sql("""
                INSERT INTO watchlist_item (id, user_id, source_id, content_type, external_id,
                                            title, poster_url)
                VALUES (:id, :userId, :sourceId, :type, :externalId, :title, :posterUrl)
                ON CONFLICT (user_id, source_id, content_type, external_id) DO NOTHING
                RETURNING id
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("sourceId", entry.sourceId())
                .param("type", type.getValue())
                .param("externalId", entry.externalId())
                .param("title", entry.title())
                .param("posterUrl", entry.posterUrl())
                .query(UUID.class)
                .optional();
    }

    public Optional<WatchlistItem> findItemByIdentity(UUID userId, WatchlistContentType type,
                                                      CatalogueEntry entry) {
        return jdbc.sql(SELECT_ITEM + """
                 WHERE w.user_id = :userId AND w.source_id = :sourceId
                   AND w.content_type = :type AND w.external_id = :externalId
                """)
                .param("userId", userId)
                .param("sourceId", entry.sourceId())
                .param("type", type.getValue())
                .param("externalId", entry.externalId())
                .query(WatchlistRepository::map)
                .optional();
    }

    public Optional<WatchlistItem> findItem(UUID userId, UUID itemId) {
        return jdbc.sql(SELECT_ITEM + " WHERE w.user_id = :userId AND w.id = :id")
                .param("userId", userId)
                .param("id", itemId)
                .query(WatchlistRepository::map)
                .optional();
    }

    /**
     * One page, most recently added first, then by id so two adds accepted in
     * the same microsecond still have one order.
     *
     * @param sourceId null for every source
     * @param type     null for films and series
     */
    public List<WatchlistItem> findPage(UUID userId, UUID sourceId, WatchlistContentType type,
                                        int page, int size) {
        return jdbc.sql(SELECT_ITEM + """
                 WHERE w.user_id = :userId
                   AND (:sourceId::uuid IS NULL OR w.source_id = :sourceId)
                   AND (:type::text IS NULL OR w.content_type = :type)
                 ORDER BY w.added_at DESC, w.id DESC
                 LIMIT :size OFFSET :offset
                """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("type", type == null ? null : type.getValue())
                .param("size", size)
                .param("offset", (long) page * size)
                .query(WatchlistRepository::map)
                .list();
    }

    public long count(UUID userId, UUID sourceId, WatchlistContentType type) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM watchlist_item
                 WHERE user_id = :userId
                   AND (:sourceId::uuid IS NULL OR source_id = :sourceId)
                   AND (:type::text IS NULL OR content_type = :type)
                """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("type", type == null ? null : type.getValue())
                .query(Long.class)
                .single();
    }

    /** The types present for this source filter, in enum order. */
    public List<WatchlistContentType> findContentTypes(UUID userId, UUID sourceId) {
        List<String> present = jdbc.sql("""
                SELECT DISTINCT content_type
                  FROM watchlist_item
                 WHERE user_id = :userId
                   AND (:sourceId::uuid IS NULL OR source_id = :sourceId)
                """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(String.class)
                .list();
        return java.util.Arrays.stream(WatchlistContentType.values())
                .filter(t -> present.contains(t.getValue()))
                .toList();
    }

    public int delete(UUID userId, UUID itemId) {
        return jdbc.sql("DELETE FROM watchlist_item WHERE id = :id AND user_id = :userId")
                .param("id", itemId)
                .param("userId", userId)
                .update();
    }

    // ---- intentions ---------------------------------------------------------

    /** What was recorded for a key: the request it was for, and the answer given. */
    public record RecordedIntention(String fingerprint, int status, String body) {}

    /** Drops this account's keys older than the retention window. */
    public void purgeExpiredIntentions(UUID userId, java.time.Duration retention) {
        jdbc.sql("""
                DELETE FROM watchlist_intention
                 WHERE user_id = :userId AND accepted_at < now() - make_interval(secs => :seconds)
                """)
                .param("userId", userId)
                .param("seconds", retention.toSeconds())
                .update();
    }

    /**
     * Claims a key for this request.
     *
     * <p>Waits when another transaction holds the same key uncommitted — a retry
     * racing its original — and then reports it as taken.
     *
     * @return true when the key is new and this request now owns it
     */
    public boolean claim(UUID userId, UUID key, String fingerprint) {
        return jdbc.sql("""
                INSERT INTO watchlist_intention (user_id, idempotency_key, request_fingerprint,
                                                 response_status)
                VALUES (:userId, :key, :fingerprint, 0)
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """)
                .param("userId", userId)
                .param("key", key)
                .param("fingerprint", fingerprint)
                .update() == 1;
    }

    public Optional<RecordedIntention> findIntention(UUID userId, UUID key) {
        return jdbc.sql("""
                SELECT request_fingerprint, response_status, response_body::text AS body
                  FROM watchlist_intention
                 WHERE user_id = :userId AND idempotency_key = :key
                """)
                .param("userId", userId)
                .param("key", key)
                .query((rs, n) -> new RecordedIntention(rs.getString("request_fingerprint"),
                        rs.getInt("response_status"), rs.getString("body")))
                .optional();
    }

    public void recordOutcome(UUID userId, UUID key, int status, String body) {
        jdbc.sql("""
                UPDATE watchlist_intention
                   SET response_status = :status, response_body = :body::jsonb
                 WHERE user_id = :userId AND idempotency_key = :key
                """)
                .param("userId", userId)
                .param("key", key)
                .param("status", status)
                .param("body", body)
                .update();
    }

    static WatchlistItem map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        UUID contentId = rs.getObject("content_id", UUID.class);
        WatchlistItem item = new WatchlistItem(
                rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class),
                WatchlistContentType.fromValue(rs.getString("content_type")),
                contentId != null,
                rs.getString("title"),
                rs.getObject("added_at", OffsetDateTime.class));
        item.setContentId(contentId);
        item.setPosterUrl(rs.getString("poster_url"));
        return item;
    }
}
