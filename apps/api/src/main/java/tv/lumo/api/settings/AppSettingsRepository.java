package tv.lumo.api.settings;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Access to {@code app_settings}, one row created by the migration (ADR 0015). */
@Repository
public class AppSettingsRepository {

    private final JdbcClient jdbc;

    public AppSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public AppSettingsRow get() {
        return jdbc.sql("SELECT support_email, registration_open, updated_at FROM app_settings")
                .query((rs, rowNum) -> new AppSettingsRow(
                        rs.getString("support_email"),
                        rs.getBoolean("registration_open"),
                        rs.getObject("updated_at", OffsetDateTime.class)))
                .single();
    }

    public void update(String supportEmail, boolean registrationOpen) {
        jdbc.sql("""
                UPDATE app_settings
                   SET support_email = :supportEmail,
                       registration_open = :registrationOpen,
                       updated_at = now()
                """)
                .param("supportEmail", supportEmail)
                .param("registrationOpen", registrationOpen)
                .update();
    }

    /** The row of {@code app_settings}. */
    public record AppSettingsRow(String supportEmail, boolean registrationOpen, OffsetDateTime updatedAt) {
    }
}
