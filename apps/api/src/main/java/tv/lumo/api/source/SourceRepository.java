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
import tv.lumo.api.generated.model.SyncStep;

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
            sync_step, error_code, last_error_at, last_synced_at, expires_at,
            max_connections, auto_sync
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
                       -- The previous run's diagnostics go back with it. A step is
                       -- only meaningful while SYNCING — the database enforces
                       -- that — and an error_code without the date it happened is
                       -- half a message.
                       sync_step  = CASE WHEN :reset THEN NULL ELSE sync_step END,
                       error_code = CASE WHEN :reset THEN NULL ELSE error_code END,
                       last_error_at = CASE WHEN :reset THEN NULL ELSE last_error_at END,
                       -- The guide's import record goes back too, in the same
                       -- statement and for a sharper reason than tidiness (C1, D3).
                       -- A success date measured against the previous URL would
                       -- vouch for programmes the new one has never produced; and
                       -- clearing epg_attempt_id is what stops an attempt still
                       -- running on the old configuration from publishing: every
                       -- write of an outcome is `WHERE epg_attempt_id = <its own>`,
                       -- and after this there is no such row (C1-10).
                       epg_attempt_status      = CASE WHEN :reset THEN 'UNKNOWN' ELSE epg_attempt_status END,
                       epg_last_success_at     = CASE WHEN :reset THEN NULL ELSE epg_last_success_at END,
                       epg_attempt_started_at  = CASE WHEN :reset THEN NULL ELSE epg_attempt_started_at END,
                       epg_attempt_finished_at = CASE WHEN :reset THEN NULL ELSE epg_attempt_finished_at END,
                       epg_attempt_id          = CASE WHEN :reset THEN NULL ELSE epg_attempt_id END,
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
                   SET status = 'SYNCING', sync_step = 'CONNECTING',
                       error_code = NULL, last_error_at = NULL, updated_at = now()
                 WHERE id = :id AND status <> 'SYNCING'
                """)
                .param("id", sourceId)
                .update() == 1;
    }

    /**
     * Advances the step of a running ingestion.
     *
     * <p>Guarded on {@code status = 'SYNCING'} so a step can never be written onto
     * a source that has already finished. A worker whose claim was taken away by
     * the stale-sync sweep simply stops being able to talk about it, rather than
     * resurrecting "fetching the guide" on a row that failed twenty minutes ago.
     *
     * <p>Unscoped by {@code user_id} like the other two ingestion methods, and
     * named for it.
     */
    public void markSyncStep(UUID sourceId, SyncStep step) {
        jdbc.sql("""
                UPDATE source
                   SET sync_step = :step, updated_at = now()
                 WHERE id = :id AND status = 'SYNCING'
                """)
                .param("step", step.getValue())
                .param("id", sourceId)
                .update();
    }

    public void markReady(UUID sourceId, OffsetDateTime expiresAt, Integer maxConnections) {
        jdbc.sql("""
                UPDATE source
                   SET status = 'READY', sync_step = NULL, error_code = NULL,
                       last_error_at = NULL, last_synced_at = now(),
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
                   SET status = 'ERROR', sync_step = NULL, error_code = :errorCode,
                       last_error_at = now(), updated_at = now()
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
     *
     * <p>A guide import that was {@code RUNNING} on such a row becomes
     * {@code INTERRUPTED} in the same statement (C1, D3). The worker that would
     * have written its outcome is gone with the process, and a {@code RUNNING}
     * that nothing will ever finish is exactly the false "import in progress"
     * the contract forbids. It is not {@code FAILED}: nothing about the guide
     * failed, and the client's wording for the two differs.
     */
    public int releaseStaleSyncs(int olderThanMinutes) {
        return jdbc.sql("""
                UPDATE source
                   SET status = 'ERROR', sync_step = NULL, error_code = 'SOURCE_UNREACHABLE',
                       last_error_at = now(),
                       epg_attempt_status = CASE WHEN epg_attempt_status = 'RUNNING'
                                                 THEN 'INTERRUPTED' ELSE epg_attempt_status END,
                       epg_attempt_finished_at = CASE WHEN epg_attempt_status = 'RUNNING'
                                                      THEN now() ELSE epg_attempt_finished_at END,
                       updated_at = now()
                 WHERE status = 'SYNCING'
                   AND updated_at < now() - make_interval(mins => :minutes)
                """)
                .param("minutes", olderThanMinutes)
                .update();
    }

    // ---- guide import record (US-16, lot C1, D3) ---------------------------
    //
    // Three writes and one read, all about the five epg_* columns of
    // 0020-epg-import.sql. The writes are unscoped by user_id like the other
    // ingestion methods and named for it; the read is scoped, because it serves
    // an HTTP request.

    /**
     * Opens a guide import attempt: {@code RUNNING}, dated now, under a fresh id.
     *
     * <p>Called <b>before the first batch</b> is written, never after: from the
     * moment the first upsert lands, the stored guide may be a mix of old and
     * new rows, and a reader must be able to see that it is.
     *
     * <p>Guarded on {@code status = 'SYNCING'} like {@link #markSyncStep}, so a
     * worker whose claim was taken away by the stale-sync sweep cannot reopen an
     * attempt on a row that has already been released. The id is returned
     * regardless: every later write carries it in its WHERE clause, so an id
     * that was never stored simply matches nothing.
     *
     * @return the id of this attempt, to be handed to {@link #markEpgSucceeded}
     *         or {@link #markEpgFailed} and to nothing else
     */
    public UUID beginEpgAttempt(UUID sourceId) {
        UUID attemptId = UUID.randomUUID();
        jdbc.sql("""
                UPDATE source
                   SET epg_attempt_id = :attempt, epg_attempt_status = 'RUNNING',
                       epg_attempt_started_at = now(), epg_attempt_finished_at = NULL,
                       updated_at = now()
                 WHERE id = :id AND status = 'SYNCING'
                """)
                .param("attempt", attemptId)
                .param("id", sourceId)
                .update();
        return attemptId;
    }

    /**
     * Publishes a success: {@code SUCCEEDED}, finished now, and the success date.
     *
     * <p><b>{@code WHERE epg_attempt_id = :attempt} is the whole mechanism.</b>
     * A PATCH that changed the guide's configuration cleared the id
     * ({@link #update}), and an attempt that started before it therefore finds
     * no row to write on. Its success was real, but it was a success against a
     * URL the source no longer has, and publishing it would date the wrong guide
     * (C1-10). The same clause makes a second attempt's outcome unable to
     * overwrite a third's.
     *
     * @return whether the outcome was recorded — false means the attempt was
     *         superseded, which the caller logs and does not retry
     */
    public boolean markEpgSucceeded(UUID sourceId, UUID attemptId) {
        return jdbc.sql("""
                UPDATE source
                   SET epg_attempt_status = 'SUCCEEDED', epg_attempt_finished_at = now(),
                       epg_last_success_at = now(), updated_at = now()
                 WHERE id = :id AND epg_attempt_id = :attempt
                """)
                .param("id", sourceId)
                .param("attempt", attemptId)
                .update() == 1;
    }

    /**
     * Records a failure: {@code FAILED}, finished now. The success date is left
     * exactly as it was — it dates the guide that is still stored, and a failed
     * attempt has not replaced it (C1-07).
     *
     * <p>Same guard as {@link #markEpgSucceeded}, for the same reason.
     */
    public boolean markEpgFailed(UUID sourceId, UUID attemptId) {
        return jdbc.sql("""
                UPDATE source
                   SET epg_attempt_status = 'FAILED', epg_attempt_finished_at = now(),
                       updated_at = now()
                 WHERE id = :id AND epg_attempt_id = :attempt
                """)
                .param("id", sourceId)
                .param("attempt", attemptId)
                .update() == 1;
    }

    /**
     * The guide's import record, for its owner.
     *
     * <p>Doubles as the ownership check of the grouped read: an empty result is
     * "no such source on this account", absent and someone else's alike.
     *
     * <p>{@code configured} is derived from {@code epg_url} in SQL rather than
     * by returning the URL and testing it in Java, so this projection never
     * carries the URL at all: the contract promises {@code EpgImportStatus}
     * holds no URL and no secret, and a projection that cannot select one
     * cannot leak one.
     */
    public Optional<EpgImportRow> findEpgImport(UUID sourceId, UUID userId) {
        return jdbc.sql("""
                SELECT (epg_url IS NOT NULL AND btrim(epg_url) <> '') AS configured,
                       epg_last_success_at, epg_attempt_started_at, epg_attempt_finished_at,
                       epg_attempt_status
                  FROM source
                 WHERE id = :id AND user_id = :userId
                """)
                .param("id", sourceId)
                .param("userId", userId)
                .query((rs, n) -> new EpgImportRow(
                        rs.getBoolean("configured"),
                        rs.getObject("epg_last_success_at", OffsetDateTime.class),
                        rs.getObject("epg_attempt_started_at", OffsetDateTime.class),
                        rs.getObject("epg_attempt_finished_at", OffsetDateTime.class),
                        rs.getString("epg_attempt_status")))
                .optional();
    }

    /**
     * The five columns of {@code 0020-epg-import.sql}, plus {@code configured}.
     *
     * @param attemptStatus one of the contract's {@code EpgAttemptStatus} values,
     *                      as the CHECK constraint guarantees
     */
    public record EpgImportRow(boolean configured, OffsetDateTime lastSuccessAt,
                               OffsetDateTime attemptStartedAt, OffsetDateTime attemptFinishedAt,
                               String attemptStatus) {
    }

    /** How many sources this account holds. Measured against the plan's ceiling. */
    public int countOwnedBy(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM source WHERE user_id = :userId")
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }

    /** How many channels this source has ingested. Rendered as "we found N channels". */
    public int countChannels(UUID sourceId) {
        return jdbc.sql("SELECT count(*) FROM channel WHERE source_id = :id")
                .param("id", sourceId)
                .query(Integer.class)
                .single();
    }

    /**
     * How many categories this source has ingested.
     *
     * <p>The other half of "1 248 chaînes · 96 catégories". Derived on read like
     * {@link #countChannels}, never stored, so it cannot drift from the rows it
     * counts.
     */
    public int countCategories(UUID sourceId) {
        return jdbc.sql("SELECT count(*) FROM category WHERE source_id = :id")
                .param("id", sourceId)
                .query(Integer.class)
                .single();
    }

    /**
     * Sources the server may re-synchronise on its own, right now.
     *
     * <p>Without this query {@code auto_sync} is a decoration: the property tells
     * the user the server refreshes the source by itself, and until something asks
     * which ones are due, nothing ever does.
     *
     * <p>Only {@code READY} sources are returned. A source in {@code ERROR} is one
     * whose credentials were refused or whose host is gone, and retrying that on a
     * timer forever is how a user's own provider locks their account; they retry
     * it themselves, which is also how they find out. {@code PENDING} and
     * {@code SYNCING} are already moving.
     */
    public List<UUID> findDueForAutoSync(int olderThanHours, int limit) {
        return jdbc.sql("""
                SELECT id
                  FROM source
                 WHERE auto_sync
                   AND status = 'READY'
                   AND (last_synced_at IS NULL
                        OR last_synced_at < now() - make_interval(hours => :hours))
                 ORDER BY last_synced_at NULLS FIRST
                 LIMIT :limit
                """)
                .param("hours", olderThanHours)
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    public record SourceRow(
            UUID id, UUID userId, String label, SourceKind kind, String host, String username,
            String m3uUrl, String epgUrl, SourceStatus status, SyncStep syncStep,
            IngestionErrorCode errorCode, OffsetDateTime lastErrorAt,
            OffsetDateTime lastSyncedAt, OffsetDateTime expiresAt, Integer maxConnections,
            boolean autoSync
    ) {
    }

    static SourceRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String errorCode = rs.getString("error_code");
        String syncStep = rs.getString("sync_step");
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
                syncStep == null ? null : SyncStep.fromValue(syncStep),
                errorCode == null ? null : IngestionErrorCode.fromValue(errorCode),
                rs.getObject("last_error_at", OffsetDateTime.class),
                rs.getObject("last_synced_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("max_connections", Integer.class),
                rs.getBoolean("auto_sync"));
    }
}
