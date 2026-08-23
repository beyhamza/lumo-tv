package tv.lumo.api.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.error.ApiException;

/**
 * Verifies a Google ID token and extracts the identity from it.
 *
 * <p><b>The server verifies the token itself and never trusts an email sent by a
 * client</b> (US-03). Signature is checked against Google's published JWKS,
 * then {@code iss}, {@code aud} and {@code exp}. An unverified {@code aud} check
 * in particular would let an ID token minted for ANY other Google application be
 * replayed here — which is the classic way this integration is got wrong.
 */
@Service
public class GoogleIdentityService {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> VALID_ISSUERS =
            List.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private volatile JwtDecoder decoder;

    public GoogleIdentityService(@Value("${LUMO_GOOGLE_CLIENT_ID:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public GoogleIdentity verify(String idToken) {
        if (clientId.isEmpty()) {
            // Configuration is missing. Failing loudly beats accepting a token we
            // cannot bind to an audience.
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.INTERNAL_ERROR, "Google sign-in is not configured on this deployment");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            // The token itself is never logged or echoed.
            throw ApiException.unauthenticated(ErrorCode.OAUTH_TOKEN_INVALID,
                    "Google ID token failed verification");
        }

        if (!VALID_ISSUERS.contains(jwt.getClaimAsString("iss"))) {
            throw ApiException.unauthenticated(ErrorCode.OAUTH_TOKEN_INVALID, "Unexpected issuer");
        }
        if (jwt.getAudience() == null || !jwt.getAudience().contains(clientId)) {
            throw ApiException.unauthenticated(ErrorCode.OAUTH_TOKEN_INVALID, "Token audience mismatch");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw ApiException.unauthenticated(ErrorCode.OAUTH_TOKEN_INVALID, "Token carries no email");
        }
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            // An unverified Google email could belong to someone else; attaching
            // it to an existing Lumo account would be an account takeover.
            throw ApiException.unauthenticated(ErrorCode.OAUTH_TOKEN_INVALID, "Google email is not verified");
        }
        return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    /**
     * Built lazily so a deployment with no Google configuration still starts, and
     * so start-up does not depend on reaching Google's JWKS endpoint.
     */
    private JwtDecoder decoder() {
        JwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                local = decoder;
                if (local == null) {
                    local = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
                    decoder = local;
                }
            }
        }
        return local;
    }

    /** @param subject Google's stable user id, which is what we key the identity on */
    public record GoogleIdentity(String subject, String email, String displayName) {
    }
}
