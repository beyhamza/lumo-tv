package tv.lumo.api.shared.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tv.lumo.api.generated.model.CreateSourceRequest;
import tv.lumo.api.generated.model.DeviceTokenRequest;
import tv.lumo.api.generated.model.GoogleSignInRequest;
import tv.lumo.api.generated.model.LoginRequest;
import tv.lumo.api.generated.model.RefreshRequest;
import tv.lumo.api.generated.model.RegisterRequest;
import tv.lumo.api.generated.model.ResetPasswordRequest;
import tv.lumo.api.generated.model.UpdateSourceRequest;

/**
 * Makes the contract's {@code writeOnly: true} actually true at runtime.
 *
 * <p><b>The gap this closes.</b> {@code openapi.yaml} marks every secret-bearing
 * request property {@code writeOnly}, but openapi-generator's Spring generator
 * does not translate that: it emits a plain {@code @JsonProperty("password")}
 * with no {@code access} attribute. The generated request models will therefore
 * serialise a password, an ID token or a refresh token straight back out if
 * anything ever writes one to a response.
 *
 * <p>Nothing does today — request models are deserialised inbound and never
 * returned. But that is a property of the current controllers, not of the types,
 * and it is one careless echo-back away from being false. These mix-ins move the
 * guarantee into the type system, where it holds regardless of what a future
 * endpoint does.
 *
 * <p>Deserialisation is unaffected: {@code WRITE_ONLY} means "accept it on the
 * way in, never emit it on the way out", which is exactly what the contract
 * says.
 *
 * <p>One mix-in per accessor name rather than per class, so each secret is
 * declared once and applied everywhere it appears.
 */
@Configuration
public class SecretSerializationConfig {

    @Bean
    JacksonModule lumoSecretMaskingModule() {
        SimpleModule module = new SimpleModule("lumo-secret-masking");

        module.setMixInAnnotation(RegisterRequest.class, PasswordMixin.class);
        module.setMixInAnnotation(LoginRequest.class, PasswordMixin.class);
        module.setMixInAnnotation(CreateSourceRequest.class, PasswordMixin.class);
        module.setMixInAnnotation(UpdateSourceRequest.class, PasswordMixin.class);
        module.setMixInAnnotation(ResetPasswordRequest.class, ResetPasswordMixin.class);
        module.setMixInAnnotation(RefreshRequest.class, RefreshTokenMixin.class);
        module.setMixInAnnotation(GoogleSignInRequest.class, IdTokenMixin.class);
        module.setMixInAnnotation(DeviceTokenRequest.class, DeviceCodeMixin.class);

        return module;
    }

    // Each mix-in is @JsonIgnore on the getter plus @JsonProperty on the setter,
    // rather than a single Access.WRITE_ONLY on the getter. The latter suppressed
    // serialisation correctly but also stopped the property DESERIALISING, which
    // broke every request carrying a secret — caught by
    // WireFormatSerializationTest's round-trip assertion. The explicit split
    // leaves no room for that ambiguity: ignored on the way out, named on the way in.

    /** Xtream and account passwords. */
    abstract static class PasswordMixin {
        @JsonIgnore
        abstract String getPassword();

        @JsonProperty("password")
        abstract void setPassword(String password);
    }

    /** The reset token is a credential in its own right: it sets a new password. */
    abstract static class ResetPasswordMixin {
        @JsonIgnore
        abstract String getPassword();

        @JsonProperty("password")
        abstract void setPassword(String password);

        @JsonIgnore
        abstract String getToken();

        @JsonProperty("token")
        abstract void setToken(String token);
    }

    abstract static class RefreshTokenMixin {
        @JsonIgnore
        abstract String getRefreshToken();

        @JsonProperty("refresh_token")
        abstract void setRefreshToken(String refreshToken);
    }

    abstract static class IdTokenMixin {
        @JsonIgnore
        abstract String getIdToken();

        @JsonProperty("id_token")
        abstract void setIdToken(String idToken);
    }

    /** The television's private half of the RFC 8628 exchange. */
    abstract static class DeviceCodeMixin {
        @JsonIgnore
        abstract String getDeviceCode();

        @JsonProperty("device_code")
        abstract void setDeviceCode(String deviceCode);
    }
}
