package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Access to {@code device_authorization}: the RFC 8628 TV activation flow. */
@Repository
public class DeviceAuthorizationRepository {

    public enum Status {
        PENDING, APPROVED, DENIED, EXPIRED, CONSUMED
    }

    private static final String COLUMNS = """
            id, user_code, platform, name, model, app_version, status,
            user_id, expires_at, interval_seconds, last_polled_at
            """;

    private final JdbcClient jdbc;

    public DeviceAuthorizationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String deviceCodeHash, String userCode, String platform,
                       String name, String model, String appVersion,
                       OffsetDateTime expiresAt, int intervalSeconds) {
        jdbc.sql("""
                INSERT INTO device_authorization
                       (id, device_code_hash, user_code, platform, name, model, app_version,
                        status, expires_at, interval_seconds)
                VALUES (:id, :hash, :userCode, :platform, :name, :model, :appVersion,
                        'PENDING', :expiresAt, :interval)
                """)
                .param("id", id)
                .param("hash", deviceCodeHash)
                .param("userCode", userCode)
                .param("platform", platform)
                .param("name", name)
                .param("model", model)
                .param("appVersion", appVersion)
                .param("expiresAt", expiresAt)
                .param("interval", intervalSeconds)
                .update();
    }

    /** Locked lookup by the code the television types nowhere and polls with. */
    public Optional<DeviceAuthorizationRow> lockByDeviceCodeHash(String deviceCodeHash) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM device_authorization
                 WHERE device_code_hash = :hash
                   FOR UPDATE
                """)
                .param("hash", deviceCodeHash)
                .query(DeviceAuthorizationRepository::map)
                .optional();
    }

    /**
     * Locked lookup by the code a human typed on the web.
     *
     * <p>Restricted to {@code PENDING}, which is also what the partial unique
     * index covers: an expired or already-consumed row keeps its user_code
     * forever, and matching those would make an old code appear approvable.
     */
    public Optional<DeviceAuthorizationRow> lockPendingByUserCode(String userCode) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM device_authorization
                 WHERE user_code = :userCode AND status = 'PENDING'
                   FOR UPDATE
                """)
                .param("userCode", userCode)
                .query(DeviceAuthorizationRepository::map)
                .optional();
    }

    public void approve(UUID id, UUID userId) {
        jdbc.sql("""
                UPDATE device_authorization
                   SET status = 'APPROVED', user_id = :userId
                 WHERE id = :id AND status = 'PENDING'
                """)
                .param("userId", userId)
                .param("id", id)
                .update();
    }

    public void updateStatus(UUID id, Status status) {
        jdbc.sql("UPDATE device_authorization SET status = :status WHERE id = :id")
                .param("status", status.name())
                .param("id", id)
                .update();
    }

    /**
     * Records a poll and reports whether it arrived too early.
     *
     * <p>The comparison happens in SQL against {@code last_polled_at} so the
     * decision uses the database clock, the same one that stamps the row. A
     * television polling faster than its {@code interval} gets {@code SLOW_DOWN}
     * (RFC 8628) rather than free reign to hammer this endpoint.
     *
     * <p>The self-join is the point of this statement, and it is not
     * decoration. {@code RETURNING} sees the row as the UPDATE left it, so
     * reading {@code last_polled_at} there yields the timestamp this very
     * statement just wrote; {@code now()} is the transaction timestamp and
     * therefore identical to it, the difference is always zero, and the answer
     * was always "too fast" - on the first poll of a brand-new authorization
     * included, when the column was still NULL. Every television polling this
     * endpoint got SLOW_DOWN forever and no set-top box could ever have been
     * activated. The {@code FROM} clause reads the table at the statement
     * snapshot, which is the pre-UPDATE value, and is what makes the comparison
     * mean what the RFC says. (PostgreSQL 18 would allow {@code RETURNING
     * OLD.last_polled_at}; the target is 16, per ADR 0002.)
     *
     * <p>{@code REQUIRES_NEW} is the second half of the same defect, and is
     * equally load-bearing. {@code AUTHORIZATION_PENDING} is the nominal answer
     * to a poll and it leaves the service as an exception, so the caller's
     * transaction rolls back - taking this write with it. The rate limit would
     * have recorded nothing on any poll that was not the single successful one,
     * which is every poll a television ever makes. In its own transaction, the
     * record of the poll survives the rollback of the answer.
     *
     * <p>Consequences of that, both deliberate: this borrows a second pooled
     * connection while the caller holds one (ADR 0005 §2 - the pool is sized for
     * the database, and this endpoint is polled once per five seconds per
     * activating television), and it MUST be called before the caller takes its
     * {@code FOR UPDATE} lock on the same row. Called after, the new transaction
     * would wait on a lock held by the transaction it suspended: a deadlock with
     * itself, broken only by a timeout.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerPollAndCheckTooFast(String deviceCodeHash) {
        Boolean tooFast = jdbc.sql("""
                UPDATE device_authorization AS d
                   SET last_polled_at = now()
                  FROM device_authorization AS prev
                 WHERE d.device_code_hash = :hash AND prev.id = d.id
             RETURNING (prev.last_polled_at IS NOT NULL
                        AND now() - prev.last_polled_at
                            < make_interval(secs => prev.interval_seconds)) AS too_fast
                """)
                .param("hash", deviceCodeHash)
                .query(Boolean.class)
                .optional()
                .orElse(Boolean.FALSE);
        return Boolean.TRUE.equals(tooFast);
    }

    /** Marks timed-out authorizations. Keeps the pending partial index small. */
    public int expireStale() {
        return jdbc.sql("""
                UPDATE device_authorization
                   SET status = 'EXPIRED'
                 WHERE status = 'PENDING' AND expires_at < now()
                """)
                .update();
    }

    public record DeviceAuthorizationRow(
            UUID id,
            String userCode,
            String platform,
            String name,
            String model,
            String appVersion,
            Status status,
            UUID userId,
            OffsetDateTime expiresAt,
            int intervalSeconds,
            OffsetDateTime lastPolledAt
    ) {
        public boolean isExpired(OffsetDateTime now) {
            return expiresAt.isBefore(now);
        }
    }

    static DeviceAuthorizationRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DeviceAuthorizationRow(
                rs.getObject("id", UUID.class),
                rs.getString("user_code"),
                rs.getString("platform"),
                rs.getString("name"),
                rs.getString("model"),
                rs.getString("app_version"),
                Status.valueOf(rs.getString("status")),
                rs.getObject("user_id", UUID.class),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getInt("interval_seconds"),
                rs.getObject("last_polled_at", OffsetDateTime.class));
    }
}
