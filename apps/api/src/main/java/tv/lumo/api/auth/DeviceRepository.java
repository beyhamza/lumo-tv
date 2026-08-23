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

    public Optional<UUID> findOwnedId(UUID deviceId, UUID userId) {
        return jdbc.sql("SELECT id FROM device WHERE id = :id AND user_id = :userId")
                .param("id", deviceId)
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    public void touch(UUID deviceId) {
        jdbc.sql("UPDATE device SET last_seen_at = now() WHERE id = :id")
                .param("id", deviceId)
                .update();
    }

    /** @param lastSeenAt null until the device makes its first authenticated call */
    public record DeviceRow(UUID id, String platform, String name, String model,
                            String appVersion, OffsetDateTime lastSeenAt, OffsetDateTime createdAt) {
    }
}
