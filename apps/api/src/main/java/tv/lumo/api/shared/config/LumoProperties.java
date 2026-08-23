package tv.lumo.api.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Everything under the {@code lumo.*} prefix, bound and validated at start-up.
 *
 * <p>Records, so configuration is immutable once bound. {@code @Validated} makes
 * a missing secret a start-up failure with a readable message rather than a
 * {@code NullPointerException} on the first request that needs it.
 *
 * @param jwt        access-token signing and lifetime
 * @param refresh    refresh-token lifetime
 * @param encryption master key for source credentials
 * @param deviceCode RFC 8628 TV activation timings
 * @param web        public web surface, for links sent by email or encoded in a QR code
 * @param cors       browser origins allowed to call this API
 * @param ingest     explicit backpressure for outbound calls (ADR 0005 §1)
 * @param rateLimit  abuse limits on the authentication surface
 */
@ConfigurationProperties(prefix = "lumo")
@Validated
public record LumoProperties(
        @NotNull Jwt jwt,
        @NotNull Refresh refresh,
        @NotNull Encryption encryption,
        @NotNull DeviceCode deviceCode,
        @NotNull Web web,
        @NotNull Cors cors,
        @NotNull Ingest ingest,
        @NotNull RateLimit rateLimit
) {

    public record Jwt(
            /** HMAC signing key, at least 32 bytes. No default: see application.yml. */
            @NotBlank String secret,
            @NotNull Duration accessTtl,
            @NotBlank String issuer
    ) {}

    public record Refresh(@NotNull Duration ttl) {}

    public record Encryption(
            /** Base64, 32 bytes. Wraps the per-credential data keys. */
            @NotBlank String masterKey
    ) {}

    public record DeviceCode(@NotNull Duration ttl, @NotNull Duration pollInterval) {}

    public record Web(@NotBlank String baseUrl) {}

    public record Cors(@DefaultValue("") List<String> allowedOrigins) {}

    public record Ingest(
            /**
             * Concurrent outbound requests allowed to a SINGLE destination host.
             * A product safeguard, not a performance knob: virtual threads removed
             * the ceiling a bounded pool used to give for free, and without this a
             * burst of syncs gets the USER's own IPTV account throttled or banned.
             */
            @Positive int maxConcurrentPerHost,
            @Positive int maxConcurrentSyncs,
            @NotNull Duration httpTimeout,
            @Positive int maxPayloadMb
    ) {
        public long maxPayloadBytes() {
            return (long) maxPayloadMb * 1024L * 1024L;
        }
    }

    public record RateLimit(
            @Positive int authAttemptsPerMinute,
            @Positive int deviceApproveAttemptsPerMinute
    ) {}
}
