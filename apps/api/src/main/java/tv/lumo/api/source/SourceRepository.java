package tv.lumo.api.source;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SourceStatus;

/**
 * Access to {@code source}.
 *
 * <p><b>Every method that reads or writes a user's source takes a
 * {@code userId} and filters on it.</b> That is the multi-tenant rule of
 * docs/architecture.md §2, and it is expressed as a mandatory parameter rather
 * than as a convention so it cannot be forgotten: there is no
 * {@code findById(UUID)} to reach for.
 *
 * <p>The two exceptions are named for what they are — {@link #findForIngestion}
 * and {@link #markSyncing} — and are reachable only from the ingestion worker,
 * which has already resolved ownership.
 */
@Repository
public class SourceRepository {

    private static final String COLUMNS = """
            id, user_id, label, kind, host, username, m3u_url, epg_url, status,
            error_code, last_synced_at, expires_at, max_connections, auto_sync
            """;

    private final JdbcClient jdbc;

    public SourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<SourceRow> findAllOwnedBy(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM source
                 WHERE user_id = :userId
                 ORDER BY created_at
                """)
                .param("userId", userId)
                .query(SourceRepository::map)
                .list();
    }

    public Optional<SourceRow> findOwned(UUID sourceId, UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM source
                 WHERE id = :id AND user_id = :userId
                """)
                .param("id", sourceId)
                .param("userId", userId)
                .query(SourceRepository::map)
                .optional();
    }

    /**
     * Reads a source for the background ingestion worker.
     *
     * <p>No {@code user_id} filter, because the worker was handed an id by a
     * caller that already proved ownership. Kept as its own explicitly named
     * method so that an unscoped read is always a deliberate act, never an
     * accident.
     */
    public Optional<SourceRow> findForIngestion(UUID sourceId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM source WHERE id = :id")
                .param("id", sourceId)
                .query(SourceRepository::map)
                .optional();
    }

    /** Reads the encrypted credential. Returns the sealed envelope, never a plaintext. */
    public Optional<byte[]> findSealedPassword(UUID sourceId) {
        return jdbc.sql("SELECT password_encrypted FROM source WHERE id = :id")
                .param("id", sourceId)
                .query(byte[].class)
                .optional();
    }

    public void insert(UUID id, UUID userId, String label, SourceKind kind, String host,
                       String username, byte[] sealedPassword, String m3uUrl, String epgUrl,
                       OffsetDateTime expiresAt, Integer maxConnections) {
        jdbc.sql("""
                INSERT INTO source (id, user_id, label, kind, host, username, password_encrypted,
                                    m3u_url, epg_url, status, expires_at, max_connections)
                VALUES (:id, :userId, :label, :kind, :host, :username, :password,
                        :m3uUrl, :epgUrl, 'PENDING', :expiresAt, :maxConnections)
                """)
                .param("id", id)
                .param("userId", userId)
                .param("label", label)
                .param("kind", kind.getValue())
                .param("host", host)
                .param("username", username)
                .param("password", sealedPassword)
                .param("m3uUrl", m3uUrl)
                .param("epgUrl", epgUrl)
                .param("expiresAt", expiresAt)
                .param("maxConnections", maxConnections)
                .update();
    }

    public void update(UUID sourceId, UUID userId, String label, String host, String username,
                       byte[] sealedPassword, String m3uUrl, String epgUrl, Boolean autoSync,
                       boolean resetToPending) {
        jdbc.sql("""
                UPDATE source
                   SET label      = COALESCE(:label, label),
                       host       = COALESCE(:host, host),
                       username   = COALESCE(:username, username),
                       password_encrypted = COALESCE(:password, password_encrypted),
                       m3u_url    = COALESCE(:m3uUrl, m3u_url),
                       epg_url    = COALESCE(:epgUrl, epg_url),
                       -- Omitted means unchanged, like every other property here.
                       -- Unlike them, changing it leaves the catalogue alone: it
                       -- only decides whether the server starts an ingestion later.
                       auto_sync  = COALESCE(:autoSync, auto_sync),
                       status     = CASE WHEN :reset THEN 'PENDING' ELSE status END,
                       error_code = CASE WHEN :reset THEN NULL ELSE error_code END,
                       updated_at = now()
                 WHERE id = :id AND user_id = :userId
                """)
                .param("label", label)
                .param("host", host)
                .param("username", username)
                .param("password", sealedPassword)
                .param("m3uUrl", m3uUrl)
                .param("epgUrl", epgUrl)
                .param("autoSync", autoSync)
                .param("reset", resetToPending)
                .param("id", sourceId)
                .param("userId", userId)
                .update();
    }

    public int delete(UUID sourceId, UUID userId) {
        return jdbc.sql("DELETE FROM source WHERE id = :id AND user_id = :userId")
                .param("id", sourceId)
                .param("userId", userId)
                .update();
    }

    /**
     * Claims a source for synchronisation.
     *
     * <p>The {@code status <> 'SYNCING'} guard makes this the concurrency control
     * for the whole ingestion pipeline: whichever caller updates the row wins, and
     * everyone else is told a sync is already running. Two workers cannot ingest
     * one source at the same time and interleave their upserts.
     *
     * @return true if this caller claimed it
     */
    public boolean markSyncing(UUID sourceId) {
        return jdbc.sql("""
                UPDATE source
                   SET status = 'SYNCING', error_code = NULL, updated_at = now()
                 WHERE id = :id AND status <> 'SYNCING'
                """)
                .param("id", sourceId)
                .update() == 1;
    }

    public void markReady(UUID sourceId, OffsetDateTime expiresAt, Integer maxConnections) {
        jdbc.sql("""
                UPDATE source
                   SET status = 'READY', error_code = NULL, last_synced_at = now(),
                       expires_at = COALESCE(:expiresAt, expires_at),
                       max_connections = COALESCE(:maxConnections, max_connections),
                       updated_at = now()
                 WHERE id = :id
                """)
                .param("expiresAt", expiresAt)
                .param("maxConnections", maxConnections)
                .param("id", sourceId)
                .update();
    }

    public void markError(UUID sourceId, IngestionErrorCode errorCode) {
        jdbc.sql("""
                UPDATE source
                   SET status = 'ERROR', error_code = :errorCode, updated_at = now()
                 WHERE id = :id
                """)
                .param("errorCode", errorCode.getValue())
                .param("id", sourceId)
                .update();
    }

    /**
     * Releases sources left mid-sync by a crash or a restart.
     *
     * <p>Without this a SYNCING row is stuck forever: {@link #markSyncing} refuses
     * to reclaim it, so the user can never retry.
     */
    public int releaseStaleSyncs(int olderThanMinutes) {
        return jdbc.sql("""
                UPDATE source
                   SET status = 'ERROR', error_code = 'SOURCE_UNREACHABLE', updated_at = now()
                 WHERE status = 'SYNCING'
                   AND updated_at < now() - make_interval(mins => :minutes)
                """)
                .param("minutes", olderThanMinutes)
                .update();
    }

    /** How many channels this source has ingested. Rendered as "we found N channels". */
    public int countChannels(UUID sourceId) {
        return jdbc.sql("SELECT count(*) FROM channel WHERE source_id = :id")
                .param("id", sourceId)
                .query(Integer.class)
                .single();
    }

    public record SourceRow(
            UUID id, UUID userId, String label, SourceKind kind, String host, String username,
            String m3uUrl, String epgUrl, SourceStatus status, IngestionErrorCode errorCode,
            OffsetDateTime lastSyncedAt, OffsetDateTime expiresAt, Integer maxConnections,
            boolean autoSync
    ) {
    }

    static SourceRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String errorCode = rs.getString("error_code");
        return new SourceRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("label"),
                SourceKind.fromValue(rs.getString("kind")),
                rs.getString("host"),
                rs.getString("username"),
                rs.getString("m3u_url"),
                rs.getString("epg_url"),
                SourceStatus.fromValue(rs.getString("status")),
                errorCode == null ? null : IngestionErrorCode.fromValue(errorCode),
                rs.getObject("last_synced_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("max_connections", Integer.class),
                rs.getBoolean("auto_sync"));
    }
}
