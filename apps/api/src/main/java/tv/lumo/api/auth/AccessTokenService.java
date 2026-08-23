package tv.lumo.api.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * Issues and describes the short-lived access token.
 *
 * <p>HS256 over a shared secret, through Spring Security's own Nimbus support
 * rather than a third-party JWT library. Fifteen minutes of validity is what
 * makes a leaked access token a bounded problem; the refresh rotation is what
 * makes that invisible to the user.
 *
 * <p>The token carries the user and the device and nothing else. No email, no
 * display name, no entitlement: a JWT is readable by anyone holding it, and an
 * entitlement claim would also be a stale copy of a value the client is
 * supposed to read from {@code GET /me/entitlement} (ADR 0003).
 */
@Service
public class AccessTokenService {

    public static final String DEVICE_CLAIM = "did";

    private final JwtEncoder encoder;
    private final LumoProperties properties;

    public AccessTokenService(JwtEncoder encoder, LumoProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedAccessToken issue(UUID userId, UUID deviceId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().accessTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(userId.toString())
                .claim(DEVICE_CLAIM, deviceId.toString())
                .build();

        String value = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new IssuedAccessToken(value, properties.jwt().accessTtl().toSeconds());
    }

    public record IssuedAccessToken(String value, long expiresInSeconds) {
    }

    /**
     * Encoder and decoder over one symmetric key.
     *
     * <p>Declared here, next to the code that uses them, rather than in a global
     * security configuration: they are an implementation detail of how this
     * service signs tokens.
     */
    @Configuration
    static class JwtCodecConfiguration {

        private static SecretKeySpec key(LumoProperties properties) {
            byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalStateException(
                        "LUMO_JWT_SECRET must be at least 32 bytes for HS256. "
                                + "Generate one with: openssl rand -base64 48");
            }
            return new SecretKeySpec(secret, "HmacSHA256");
        }

        @Bean
        JwtEncoder jwtEncoder(LumoProperties properties) {
            return new NimbusJwtEncoder(new ImmutableSecret<>(key(properties)));
        }

        @Bean
        JwtDecoder jwtDecoder(LumoProperties properties) {
            return NimbusJwtDecoder.withSecretKey(key(properties))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        }
    }
}
