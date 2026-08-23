package tv.lumo.api.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.web.ApiPathConfig;

/**
 * Security for the whole application.
 *
 * <p><b>Why no hand-written JWT filter.</b> Spring Security's resource-server
 * support already provides one, and it is the version that has had the
 * algorithm-confusion, {@code alg: none} and clock-skew bugs found and fixed in
 * it. Re-implementing that filter would be re-implementing those bugs. What is
 * custom here is only what is genuinely ours: the claim-to-principal conversion,
 * and an entry point that answers in {@code application/problem+json} with a
 * stable {@code code} rather than an empty 401.
 *
 * <p>Stateless: no HTTP session, no CSRF token. Every call carries a bearer
 * token, and there is no cookie for a cross-site request to ride on. The web
 * application keeps its refresh token in an httpOnly cookie on ITS OWN origin,
 * not on this one.
 */
@Configuration
public class SecurityConfig {

    /** Endpoints reachable without a token, exactly as the contract declares them. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/v1/auth/register",
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/oauth/google",
            "/v1/auth/verify-email",
            "/v1/auth/password/forgot",
            "/v1/auth/password/reset",
            "/v1/auth/device/code",
            "/v1/auth/device/token"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // withDefaults() rather than an injected CorsConfigurationSource:
                // Spring Security looks the bean up BY NAME (corsConfigurationSource),
                // whereas injecting by type is ambiguous — MvcHandlerMappingIntrospector
                // also implements CorsConfigurationSource.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // /v1/auth/device/approve is NOT here: approving a
                        // television requires a signed-in web session.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(SecurityConfig::toPrincipal))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint));
        return http.build();
    }

    /**
     * Turns verified claims into an {@link AuthPrincipal}.
     *
     * <p>A token whose {@code did} claim is missing or malformed is rejected
     * rather than defaulted: a session with no device cannot be revoked
     * per-device, which would quietly defeat {@code DELETE /me/devices/{id}}.
     */
    private static AbstractAuthenticationToken toPrincipal(Jwt jwt) {
        UUID userId = parseUuid(jwt.getSubject(), "sub");
        UUID deviceId = parseUuid(jwt.getClaimAsString(AccessTokenService.DEVICE_CLAIM),
                AccessTokenService.DEVICE_CLAIM);
        return new LumoAuthenticationToken(jwt, new AuthPrincipal(userId, deviceId));
    }

    private static UUID parseUuid(String value, String claim) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Malformed '" + claim + "' claim");
        }
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(LumoProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        // No credentials: the browser sends a bearer token in a header, never a
        // cookie to this origin. Allowing credentials here would widen the attack
        // surface for nothing.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiPathConfig.API_PREFIX + "/**", configuration);
        return source;
    }

    /** Authentication carrying the verified {@link AuthPrincipal}. */
    static final class LumoAuthenticationToken extends AbstractAuthenticationToken {

        private final transient Jwt jwt;
        private final transient AuthPrincipal principal;

        LumoAuthenticationToken(Jwt jwt, AuthPrincipal principal) {
            super(List.of());
            this.jwt = jwt;
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            // The raw token is deliberately not exposed: anything that can reach
            // it can log it.
            return null;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        Jwt jwt() {
            return jwt;
        }
    }

}
