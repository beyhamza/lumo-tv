package tv.lumo.api.userdata;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.PlaybackProgress;
import tv.lumo.api.generated.model.ProgressItemType;

/**
 * Access to {@code playback_progress}: where the user stopped watching, how long
 * they actually watched, and whether the item belongs in "Continue watching"
 * (ADR 0012).
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

    /** Actual playback that puts an item in the rail (CW-03). */
    static final long CONTINUE_THRESHOLD_MS = 30_000;

    /**
     * The row as a client sees it. {@code in_continue} is computed here, once, so
     * that three clients never copy the 30-second and 95 % rules (ADR 0012,
     * ruling 4). Finished means strictly past 95 % of a known duration (CW-06).
     */
    private static final String SELECT = """
            SELECT id, source_id, item_type, item_ref, position_ms, duration_ms, updated_at,
                   watched_ms, hidden_at IS NOT NULL AS hidden,
                   (hidden_at IS NULL
                    AND NOT (duration_ms IS NOT NULL AND duration_ms > 0
                             AND position_ms * 100 > duration_ms * 95)
                    AND (watched_ms >= %d OR restarted_after_hide)) AS in_continue
              FROM playback_progress
            """.formatted(CONTINUE_THRESHOLD_MS);

    private final JdbcClient jdbc;

    public ProgressRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One page, most recently updated first — which is the order a "Continue
     * watching" rail wants, so no client has to re-sort it.
     *
     * @param sourceId null for every source
     * @param itemType null for every kind
     * @param itemRef  null for every item; with the other two it yields at most one
     */
    public List<PlaybackProgress> findPage(UUID userId, UUID sourceId, ProgressItemType itemType,
                                           String itemRef, int page, int size) {
        return jdbc.sql(SELECT + """
                 WHERE user_id = :userId
                   AND (:sourceId::uuid IS NULL OR source_id = :sourceId)
                   AND (:itemType::text IS NULL OR item_type = :itemType)
                   AND (:itemRef::text  IS NULL OR item_ref  = :itemRef)
                 ORDER BY updated_at DESC, id
                 LIMIT :size OFFSET :offset
                """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("itemType", itemType == null ? null : itemType.getValue())
                .param("itemRef", itemRef)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(ProgressRepository::map)
                .list();
    }

    public long count(UUID userId, UUID sourceId, ProgressItemType itemType, String itemRef) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM playback_progress
                 WHERE user_id = :userId
                   AND (:sourceId::uuid IS NULL OR source_id = :sourceId)
                   AND (:itemType::text IS NULL OR item_type = :itemType)
                   AND (:itemRef::text  IS NULL OR item_ref  = :itemRef)
                """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("itemType", itemType == null ? null : itemType.getValue())
                .param("itemRef", itemRef)
                .query(Long.class)
                .single();
    }

    /**
     * Upsert on {@code (user_id, source_id, item_type, item_ref)}.
     *
     * <p><b>The source is part of the key, not a passenger.</b> {@code item_ref} is
     * minted by the user's own panel: two subscriptions can perfectly well both
     * call a film {@code 1042}, and without the source in the key the position of
     * one would be served for the other.
     *
     * <p><b>The latest observation wins the position</b>, not the latest request
     * (CW-30, CW-32): a device reconnecting with an old save does not overwrite a
     * more recent playback. {@code updated_at} — the rail's order — moves only
     * when the position does.
     *
     * <p><b>A hidden item comes back on a new real start</b>: a save whose session
     * started after the hide (CW-18, CW-22). A session already running when the
     * card was hidden keeps saving and keeps it hidden (CW-31).
     *
     * @param observedAt       when the device read the position; never null here
     * @param sessionStartedAt when playback really started; null if unknown
     * @return the row's id
     */
    public UUID upsert(UUID userId, UUID sourceId, ProgressItemType itemType, String itemRef,
                       long positionMs, Long durationMs, OffsetDateTime observedAt,
                       OffsetDateTime sessionStartedAt) {
        return jdbc.sql("""
                INSERT INTO playback_progress AS p
                       (id, user_id, source_id, item_type, item_ref, position_ms, duration_ms,
                        updated_at, observed_at, last_session_started_at)
                VALUES (:id, :userId, :sourceId, :itemType, :itemRef, :positionMs, :durationMs,
                        now(), :observedAt, :sessionStartedAt)
                ON CONFLICT (user_id, source_id, item_type, item_ref)
                DO UPDATE SET
                    position_ms = CASE WHEN EXCLUDED.observed_at >= coalesce(p.observed_at, '-infinity')
                                       THEN EXCLUDED.position_ms ELSE p.position_ms END,
                    duration_ms = CASE WHEN EXCLUDED.observed_at >= coalesce(p.observed_at, '-infinity')
                                       THEN EXCLUDED.duration_ms ELSE p.duration_ms END,
                    updated_at  = CASE WHEN EXCLUDED.observed_at >= coalesce(p.observed_at, '-infinity')
                                       THEN now() ELSE p.updated_at END,
                    observed_at = greatest(p.observed_at, EXCLUDED.observed_at),
                    last_session_started_at =
                        greatest(p.last_session_started_at, EXCLUDED.last_session_started_at),
                    restarted_after_hide = CASE
                        WHEN p.hidden_at IS NOT NULL
                             AND EXCLUDED.last_session_started_at > p.hidden_at THEN true
                        ELSE p.restarted_after_hide END,
                    hidden_at = CASE
                        WHEN p.hidden_at IS NOT NULL
                             AND EXCLUDED.last_session_started_at > p.hidden_at THEN NULL
                        ELSE p.hidden_at END
                RETURNING id
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("itemType", itemType.getValue())
                .param("itemRef", itemRef)
                .param("positionMs", positionMs)
                .param("durationMs", durationMs)
                .param("observedAt", observedAt)
                .param("sessionStartedAt", sessionStartedAt)
                .query(UUID.class)
                .single();
    }

    /** One wall-clock interval of actual playback. */
    public record Interval(OffsetDateTime startedAt, OffsetDateTime endedAt) {}

    /**
     * Adds intervals to an item's watched time, as a union (ADR 0012, ruling 1).
     *
     * <p>The stored intervals and the new ones are merged and written back, so the
     * table holds a few disjoint rows per item however many saves arrive. Two
     * devices playing during the same seconds count them once (CW-29); a resent
     * save adds nothing (CW-33).
     */
    public void addWatched(UUID progressId, List<Interval> added) {
        if (added.isEmpty()) {
            return;
        }
        List<Interval> all = new ArrayList<>(jdbc.sql("""
                SELECT started_at, ended_at FROM playback_watched_interval
                 WHERE progress_id = :id
                """)
                .param("id", progressId)
                .query((rs, n) -> new Interval(rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("ended_at", OffsetDateTime.class)))
                .list());
        all.addAll(added);
        List<Interval> merged = union(all);

        jdbc.sql("DELETE FROM playback_watched_interval WHERE progress_id = :id")
                .param("id", progressId).update();
        long watchedMs = 0;
        for (Interval interval : merged) {
            jdbc.sql("""
                    INSERT INTO playback_watched_interval (progress_id, started_at, ended_at)
                    VALUES (:id, :startedAt, :endedAt)
                    """)
                    .param("id", progressId)
                    .param("startedAt", interval.startedAt())
                    .param("endedAt", interval.endedAt())
                    .update();
            watchedMs += java.time.Duration.between(interval.startedAt(), interval.endedAt()).toMillis();
        }
        jdbc.sql("UPDATE playback_progress SET watched_ms = legacy_watched_ms + :watched WHERE id = :id")
                .param("watched", watchedMs)
                .param("id", progressId)
                .update();
    }

    /** Disjoint, sorted union of {@code intervals}; touching intervals merge. */
    static List<Interval> union(List<Interval> intervals) {
        List<Interval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(Interval::startedAt));
        List<Interval> out = new ArrayList<>();
        for (Interval next : sorted) {
            if (!out.isEmpty() && !next.startedAt().isAfter(out.getLast().endedAt())) {
                Interval last = out.removeLast();
                out.add(new Interval(last.startedAt(),
                        next.endedAt().isAfter(last.endedAt()) ? next.endedAt() : last.endedAt()));
            } else {
                out.add(next);
            }
        }
        return out;
    }

    /**
     * Hides an item from the rail, progress kept (CW-10). For an episode, every
     * episode of its series that has progress: the rail shows one card per series,
     * and hiding it must not let the next episode take its place.
     */
    public void hide(UUID userId, UUID sourceId, ProgressItemType itemType, String itemRef) {
        jdbc.sql("""
                UPDATE playback_progress
                   SET hidden_at = now(), restarted_after_hide = false
                 WHERE user_id = :userId AND source_id = :sourceId AND item_type = :itemType
                   AND hidden_at IS NULL
                   AND (item_ref = :itemRef
                        OR (item_type = 'EPISODE' AND item_ref IN (
                              SELECT sibling.id::text
                                FROM episode e
                                JOIN episode sibling ON sibling.series_id = e.series_id
                               WHERE e.id::text = :itemRef AND e.source_id = :sourceId)))
                """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("itemType", itemType.getValue())
                .param("itemRef", itemRef)
                .update();
    }

    public PlaybackProgress findById(UUID progressId) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", progressId)
                .query(ProgressRepository::map)
                .single();
    }

    static PlaybackProgress map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        PlaybackProgress progress = new PlaybackProgress(
                rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class),
                ProgressItemType.fromValue(rs.getString("item_type")),
                rs.getString("item_ref"),
                rs.getLong("position_ms"),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getLong("watched_ms"),
                rs.getBoolean("hidden"),
                rs.getBoolean("in_continue"));
        progress.setDurationMs(rs.getObject("duration_ms", Long.class));
        return progress;
    }
}
