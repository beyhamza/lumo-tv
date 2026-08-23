package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
     */
    public boolean registerPollAndCheckTooFast(UUID id, int intervalSeconds) {
        Boolean tooFast = jdbc.sql("""
                UPDATE device_authorization
                   SET last_polled_at = now()
                 WHERE id = :id
             RETURNING (last_polled_at IS NOT NULL
                        AND now() - last_polled_at < make_interval(secs => :interval)) AS too_fast
                """)
                .param("id", id)
                .param("interval", intervalSeconds)
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
