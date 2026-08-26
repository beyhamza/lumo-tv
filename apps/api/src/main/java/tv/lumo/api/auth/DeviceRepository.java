package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.DeviceRegistration;

/** Access to {@code device}. Every read filters on {@code user_id}. */
@Repository
public class DeviceRepository {

    private final JdbcClient jdbc;

    public DeviceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID userId, String platform, String name, String model, String appVersion) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO device (id, user_id, platform, name, model, app_version, last_seen_at)
                VALUES (:id, :userId, :platform, :name, :model, :appVersion, now())
                """)
                .param("id", id)
                .param("userId", userId)
                .param("platform", platform)
                .param("name", name)
                .param("model", model)
                .param("appVersion", appVersion)
                .update();
        return id;
    }

    public UUID insert(UUID userId, DeviceRegistration registration) {
        return insert(userId,
                registration.getPlatform().getValue(),
                registration.getName(),
                registration.getModel(),
                registration.getAppVersion());
    }

    /**
     * The installations currently linked to an account.
     *
     * <p><b>Linked means holding a live refresh token</b>, not merely having a row.
     * Every sign-in inserts a {@code device}, so a list of rows would grow by one
     * every time the user opened the site in a private window and would show four
     * "lumo.tv" entries for one browser. Worse, that count is what the plan limit
     * is measured against: counting dead rows would lock a two-device plan after
     * two sign-ins and never let go.
     *
     * <p>Signing out therefore frees the slot, and so does letting the refresh
     * token expire. Both are what a user means by "that device is not mine any
     * more".
     *
     * <p>The caller's own device is always included, even in the window where its
     * refresh chain was revoked while its access token is still valid — a device
     * list that cannot show you which one you are on is the one thing it must
     * never do.
     */
    public java.util.List<DeviceRow> findLinked(UUID userId, UUID currentDeviceId) {
        return jdbc.sql("""
                SELECT d.id, d.platform, d.name, d.model, d.app_version,
                       d.last_seen_at, d.created_at
                  FROM device d
                 WHERE d.user_id = :userId
                   AND (d.id = :currentDeviceId OR EXISTS (
                           SELECT 1 FROM refresh_token t
                            WHERE t.device_id = d.id
                              AND t.revoked_at IS NULL
                              AND t.expires_at > now()))
                 ORDER BY d.last_seen_at DESC NULLS LAST, d.created_at DESC
                """)
                .param("userId", userId)
                .param("currentDeviceId", currentDeviceId)
                .query(DeviceRepository::map)
                .list();
    }

    /**
     * How many installations count against the plan's device limit.
     *
     * <p>Same definition as {@link #findLinked}, minus the caller's own device —
     * this is asked <em>before</em> a device row exists, on the sign-in path, so
     * there is no current device to exempt.
     */
    public int countLinked(UUID userId) {
        return jdbc.sql("""
                SELECT count(*)
                  FROM device d
                 WHERE d.user_id = :userId
                   AND EXISTS (SELECT 1 FROM refresh_token t
                                WHERE t.device_id = d.id
                                  AND t.revoked_at IS NULL
                                  AND t.expires_at > now())
                """)
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }

    /**
     * Unlinks a device and, with it, every session it holds.
     *
     * <p>The row goes rather than being marked: {@code refresh_token.device_id}
     * cascades, so the tokens disappear with it and the next refresh attempt finds
     * nothing rather than finding something revoked. Revoking your own device is
     * allowed and signs you out, which is what the contract says and what a user
     * on a stolen laptop needs.
     *
     * @return 0 when no such device belongs to the caller
     */
    public int delete(UUID deviceId, UUID userId) {
        return jdbc.sql("DELETE FROM device WHERE id = :id AND user_id = :userId")
                .param("id", deviceId)
                .param("userId", userId)
                .update();
    }

    public Optional<UUID> findOwnedId(UUID deviceId, UUID userId) {
        return jdbc.sql("SELECT id FROM device WHERE id = :id AND user_id = :userId")
                .param("id", deviceId)
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    /**
     * Records that a device was seen.
     *
     * <p>Scoped on {@code user_id} even though the caller could not plausibly
     * pass a device that is not theirs — the id comes from their own refresh
     * token row. The rule in docs/architecture.md §2 is written without
     * exceptions on purpose: an unscoped write on a table carrying
     * {@code user_id} is a hole the day somebody reuses this method with an id
     * from a request parameter, and nothing in its signature would warn them.
     */
    public void touch(UUID deviceId, UUID userId) {
        jdbc.sql("UPDATE device SET last_seen_at = now() WHERE id = :id AND user_id = :userId")
                .param("id", deviceId)
                .param("userId", userId)
                .update();
    }

    /** @param lastSeenAt null until the device makes its first authenticated call */
    public record DeviceRow(UUID id, String platform, String name, String model,
                            String appVersion, OffsetDateTime lastSeenAt, OffsetDateTime createdAt) {
    }

    static DeviceRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DeviceRow(
                rs.getObject("id", UUID.class),
                rs.getString("platform"),
                rs.getString("name"),
                rs.getString("model"),
                rs.getString("app_version"),
                rs.getObject("last_seen_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
