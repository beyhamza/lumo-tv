package tv.lumo.api.catalog;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bulk writes performed by ingestion.
 *
 * <p>Separate from the read side because the shapes have nothing in common: reads
 * are user-scoped single queries, writes are batched upserts of a whole
 * catalogue.
 *
 * <p><b>Upsert, not delete-and-reinsert.</b> Replacing a catalogue by truncating
 * it would give every channel a new id on every sync, and every favourite would
 * point at a row that no longer exists. Conflicting on
 * {@code (source_id, external_id)} keeps identity stable across syncs.
 *
 * <p>Batched in fixed-size chunks so a fifteen-thousand channel panel does not
 * become one statement with fifteen thousand parameter sets.
 */
@Repository
public class CatalogWriteRepository {

    /** Large enough to amortise round-trips, small enough to bound statement memory. */
    public static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public CatalogWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upserts one category and returns the id that ended up in the table.
     *
     * <p>{@code RETURNING id} is the point: on conflict the EXISTING id is kept,
     * and the caller needs that id rather than the one it proposed. Without it,
     * a re-sync would attach every channel to a category id that was never
     * inserted, and the whole catalogue would look uncategorised.
     *
     * <p>One statement per category is fine — a panel has tens of them, not
     * thousands. Channels are the ones that get batched.
     */
    public UUID upsertCategoryReturningId(UUID sourceId, String externalId, String name,
                                          String contentType, int position) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO category (id, source_id, external_id, name, content_type, position)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, content_type, external_id) WHERE external_id IS NOT NULL
                DO UPDATE SET name = EXCLUDED.name, position = EXCLUDED.position
                RETURNING id
                """, UUID.class, UUID.randomUUID(), sourceId, externalId, name, contentType, position);
    }

    /**
     * Upserts one batch of channels.
     *
     * <p>{@code id} is only used when the row is new; on conflict the existing id
     * is kept, which is what preserves favourites across a re-sync.
     */
    public void upsertChannels(UUID sourceId, List<ChannelUpsert> channels) {
        if (channels.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO channel (id, source_id, category_id, external_id, name,
                                     logo_url, tvg_id, stream_url, position, is_adult,
                                     number, quality)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, external_id) WHERE external_id IS NOT NULL
                DO UPDATE SET category_id = EXCLUDED.category_id,
                              name        = EXCLUDED.name,
                              logo_url    = EXCLUDED.logo_url,
                              tvg_id      = EXCLUDED.tvg_id,
                              stream_url  = EXCLUDED.stream_url,
                              position    = EXCLUDED.position,
                              is_adult    = EXCLUDED.is_adult,
                              -- Overwritten, not COALESCEd: a provider that drops
                              -- a channel's number has dropped it, and keeping the
                              -- old one would leave a remote control dialling a
                              -- number that no longer exists.
                              number      = EXCLUDED.number,
                              quality     = EXCLUDED.quality
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ChannelUpsert channel = channels.get(i);
                ps.setObject(1, channel.id());
                ps.setObject(2, sourceId);
                ps.setObject(3, channel.categoryId());
                ps.setString(4, channel.externalId());
                ps.setString(5, channel.name());
                ps.setString(6, channel.logoUrl());
                ps.setString(7, channel.tvgId());
                ps.setString(8, channel.streamUrl());
                ps.setInt(9, channel.position());
                ps.setBoolean(10, channel.adult());
                ps.setObject(11, channel.number());
                ps.setString(12, channel.quality());
            }

            @Override
            public int getBatchSize() {
                return channels.size();
            }
        });
    }

    public void upsertProgrammes(UUID sourceId, List<ProgrammeUpsert> programmes) {
        if (programmes.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO epg_programme (id, source_id, tvg_id, starts_at, ends_at,
                                           title, description, category)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, tvg_id, starts_at)
                DO UPDATE SET ends_at     = EXCLUDED.ends_at,
                              title       = EXCLUDED.title,
                              description = EXCLUDED.description,
                              category    = EXCLUDED.category
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ProgrammeUpsert programme = programmes.get(i);
                ps.setObject(1, programme.id());
                ps.setObject(2, sourceId);
                ps.setString(3, programme.tvgId());
                ps.setObject(4, programme.startsAt());
                ps.setObject(5, programme.endsAt());
                ps.setString(6, programme.title());
                ps.setString(7, programme.description());
                ps.setString(8, programme.category());
            }

            @Override
            public int getBatchSize() {
                return programmes.size();
            }
        });
    }

    /**
     * Removes channels this sync did not see.
     *
     * <p>Run after a successful full ingestion only. Running it after a partial
     * one would delete channels the panel still serves, because "not seen" would
     * mean "the sync stopped early" rather than "removed upstream".
     */
    public int deleteChannelsNotIn(UUID sourceId, List<String> seenExternalIds) {
        if (seenExternalIds.isEmpty()) {
            return 0;
        }
        return jdbcTemplate.update(
                "DELETE FROM channel WHERE source_id = ? AND external_id IS NOT NULL "
                        + "AND NOT (external_id = ANY (?))",
                sourceId, seenExternalIds.toArray(String[]::new));
    }

    /** Purges programmes outside the D-1 / D+3 retention window. */
    public int purgeExpiredProgrammes() {
        return jdbcTemplate.update(
                "DELETE FROM epg_programme WHERE ends_at < now() - interval '1 day'");
    }

    /** Buffers items and flushes every {@link #BATCH_SIZE}, so nothing accumulates unboundedly. */
    public static <T> Batcher<T> batcher(java.util.function.Consumer<List<T>> flush) {
        return new Batcher<>(flush);
    }

    /**
     * Accumulator between a streaming parser and a batched write.
     *
     * <p>Not thread-safe, and does not need to be: one ingestion owns one
     * instance for the length of one parse.
     */
    public static final class Batcher<T> {

        private final java.util.function.Consumer<List<T>> flush;
        private final List<T> buffer = new ArrayList<>(BATCH_SIZE);

        private Batcher(java.util.function.Consumer<List<T>> flush) {
            this.flush = flush;
        }

        public void add(T item) {
            buffer.add(item);
            if (buffer.size() >= BATCH_SIZE) {
                flushNow();
            }
        }

        public void flushNow() {
            if (!buffer.isEmpty()) {
                flush.accept(List.copyOf(buffer));
                buffer.clear();
            }
        }
    }

    /**
     * @param position display index, reassigned at every ingestion
     * @param number   the provider's own channel number, and a different thing
     *                 entirely: it survives across syncs and it is what a remote
     *                 control dials. Null for the many sources that carry none
     * @param quality  definition badge as advertised, echoed verbatim, null when
     *                 nothing advertises one
     */
    public record ChannelUpsert(UUID id, UUID categoryId, String externalId, String name,
                                String logoUrl, String tvgId, String streamUrl,
                                int position, boolean adult, Integer number, String quality) {
    }

    public record ProgrammeUpsert(UUID id, String tvgId, java.time.OffsetDateTime startsAt,
                                  java.time.OffsetDateTime endsAt, String title,
                                  String description, String category) {
    }
}
