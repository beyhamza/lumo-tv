package tv.lumo.api.userdata;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.PlaybackProgress;
import tv.lumo.api.generated.model.ProgressItemType;

/**
 * Access to {@code playback_progress}: where the user stopped watching.
 *
 * <p>VOD items and episodes only. A live channel has no position, which is why
 * "recently watched" lives in its own table rather than as a third
 * {@code item_type} — see {@link RecentChannelRepository}.
 *
 * <p>{@code item_ref} is an opaque identifier minted by the user's own panel.
 * Nothing here parses it, and nothing here puts it in a path segment: it may
 * contain a slash or a percent sign, which is exactly why the contract filters on
 * it through a query parameter.
 */
@Repository
public class ProgressRepository {

    private final JdbcClient jdbc;

    public ProgressRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One page, most recently updated first — which is the order a "Continue
     * watching" rail wants, so no client has to re-sort it.
     *
     * @param itemType null for every kind
     * @param itemRef  null for every item; with {@code itemType} it yields at most one
     */
    public List<PlaybackProgress> findPage(UUID userId, ProgressItemType itemType, String itemRef,
                                           int page, int size) {
        return jdbc.sql("""
                SELECT id, item_type, item_ref, position_ms, duration_ms, updated_at
                  FROM playback_progress
                 WHERE user_id = :userId
                   AND (:itemType::text IS NULL OR item_type = :itemType)
                   AND (:itemRef::text  IS NULL OR item_ref  = :itemRef)
                 ORDER BY updated_at DESC
                 LIMIT :size OFFSET :offset
                """)
                .param("userId", userId)
                .param("itemType", itemType == null ? null : itemType.getValue())
                .param("itemRef", itemRef)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(ProgressRepository::map)
                .list();
    }

    public long count(UUID userId, ProgressItemType itemType, String itemRef) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM playback_progress
                 WHERE user_id = :userId
                   AND (:itemType::text IS NULL OR item_type = :itemType)
                   AND (:itemRef::text  IS NULL OR item_ref  = :itemRef)
                """)
                .param("userId", userId)
                .param("itemType", itemType == null ? null : itemType.getValue())
                .param("itemRef", itemRef)
                .query(Long.class)
                .single();
    }

    /**
     * Idempotent upsert on {@code (user_id, item_type, item_ref)}.
     *
     * <p>{@code RETURNING} rather than a second SELECT: a player saves a position
     * every few seconds, and on a conflict the row that matters is the one already
     * in the table, whose id the client keeps.
     *
     * <p>{@code duration_ms} is overwritten even when null. A source that stops
     * reporting a duration has stopped reporting it, and a remembered one would
     * quietly outlive the item it described.
     */
    public PlaybackProgress upsert(UUID userId, ProgressItemType itemType, String itemRef,
                                   long positionMs, Long durationMs) {
        return jdbc.sql("""
                INSERT INTO playback_progress (id, user_id, item_type, item_ref,
                                               position_ms, duration_ms, updated_at)
                VALUES (:id, :userId, :itemType, :itemRef, :positionMs, :durationMs, now())
                ON CONFLICT (user_id, item_type, item_ref)
                DO UPDATE SET position_ms = EXCLUDED.position_ms,
                              duration_ms = EXCLUDED.duration_ms,
                              updated_at  = now()
                RETURNING id, item_type, item_ref, position_ms, duration_ms, updated_at
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("itemType", itemType.getValue())
                .param("itemRef", itemRef)
                .param("positionMs", positionMs)
                .param("durationMs", durationMs)
                .query(ProgressRepository::map)
                .single();
    }

    static PlaybackProgress map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        PlaybackProgress progress = new PlaybackProgress(
                rs.getObject("id", UUID.class),
                ProgressItemType.fromValue(rs.getString("item_type")),
                rs.getString("item_ref"),
                rs.getLong("position_ms"),
                rs.getObject("updated_at", OffsetDateTime.class));
        progress.setDurationMs(rs.getObject("duration_ms", Long.class));
        return progress;
    }
}
