package tv.lumo.api.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.generated.api.SettingsApi;
import tv.lumo.api.generated.model.AppSettings;
import tv.lumo.api.generated.model.PublicConfig;
import tv.lumo.api.generated.model.UpdateAppSettingsRequest;
import tv.lumo.api.settings.AppSettingsRepository.AppSettingsRow;

/**
 * Implements the generated {@code SettingsApi} (ADR 0015).
 *
 * <p>{@code GET /config} is public and narrow by type: {@code PublicConfig} has
 * no property for what an administrator alone should read. {@code /admin/settings}
 * is closed by {@code SecurityConfig} before these methods can run.
 */
@RestController
public class AppSettingsController implements SettingsApi {

    private final AppSettingsRepository settings;

    public AppSettingsController(AppSettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public ResponseEntity<PublicConfig> getPublicConfig() {
        return ResponseEntity.ok(new PublicConfig(settings.get().supportEmail()));
    }

    @Override
    public ResponseEntity<AppSettings> getAppSettings() {
        return ResponseEntity.ok(toApi(settings.get()));
    }

    @Override
    public ResponseEntity<AppSettings> updateAppSettings(UpdateAppSettingsRequest request) {
        settings.update(request.getSupportEmail(), request.getRegistrationOpen());
        return ResponseEntity.ok(toApi(settings.get()));
    }

    private static AppSettings toApi(AppSettingsRow row) {
        return new AppSettings(row.supportEmail(), row.registrationOpen(), row.updatedAt());
    }
}
