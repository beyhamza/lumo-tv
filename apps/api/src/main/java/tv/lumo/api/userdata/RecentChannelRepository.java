package tv.lumo.api.userdata;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.RecentChannel;

/**
 * Access to {@code recent_channel}: the television's first rail.
 *
 * <p>Identifiers and a timestamp, nothing else. The name, the logo and what is on
 * now are reachable from the catalogue and the guide; copied in here they would
 * be a rail showing a channel name that the last ingestion has since changed.
 *
 * <p>A rolling window, not a history. {@link #prune} keeps it bounded per account
 * and drops the oldest silently — nobody asked to be able to page through what
 * they watched last month, and storing it would be keeping a viewing record of
 * someone's television for no feature at all.
 */
@Repository
public class RecentChannelRepository {

    private final JdbcClient jdbc;

    public RecentChannelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RecentChannel> findRecent(UUID userId, int limit) {
        return jdbc.sql("""
                SELECT channel_id, source_id, watched_at
                  FROM recent_channel
                 WHERE user_id = :userId
                 ORDER BY watched_at DESC
                 LIMIT :limit
                """)
                .param("userId", userId)
                .param("limit", limit)
                .query((rs, n) -> new RecentChannel(
                        rs.getObject("channel_id", UUID.class),
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("watched_at", OffsetDateTime.class)))
                .list();
    }

    /**
     * Idempotent upsert on {@code (user_id, channel_id)}.
     *
     * <p>Watching the same channel again moves it to the top; it does not add a
     * second row. Without the conflict clause the rail would fill with one channel
     * on an evening spent on one channel, which is most evenings.
     */
    public RecentChannel record(UUID userId, UUID sourceId, UUID channelId) {
        return jdbc.sql("""
                INSERT INTO recent_channel (id, user_id, source_id, channel_id, watched_at)
                VALUES (:id, :userId, :sourceId, :channelId, now())
                ON CONFLICT (user_id, channel_id)
                DO UPDATE SET watched_at = now(), source_id = EXCLUDED.source_id
                RETURNING channel_id, source_id, watched_at
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("channelId", channelId)
                .query((rs, n) -> new RecentChannel(
                        rs.getObject("channel_id", UUID.class),
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("watched_at", OffsetDateTime.class)))
                .single();
    }

    /**
     * Keeps only the newest {@code keep} entries for one account.
     *
     * @return how many were dropped
     */
    public int prune(UUID userId, int keep) {
        return jdbc.sql("""
                DELETE FROM recent_channel
                 WHERE user_id = :userId
                   AND id NOT IN (SELECT id
                                    FROM recent_channel
                                   WHERE user_id = :userId
                                   ORDER BY watched_at DESC
                                   LIMIT :keep)
                """)
                .param("userId", userId)
                .param("keep", keep)
                .update();
    }
}
