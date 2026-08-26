package tv.lumo.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.generated.api.AuthApi;
import tv.lumo.api.generated.model.ApproveDeviceRequest;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.DeviceApproval;
import tv.lumo.api.generated.model.DeviceCodeRequest;
import tv.lumo.api.generated.model.DeviceCodeResponse;
import tv.lumo.api.generated.model.DeviceTokenRequest;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.ForgotPasswordRequest;
import tv.lumo.api.generated.model.GoogleSignInRequest;
import tv.lumo.api.generated.model.LoginRequest;
import tv.lumo.api.generated.model.RefreshRequest;
import tv.lumo.api.generated.model.RegisterRequest;
import tv.lumo.api.generated.model.ResetPasswordRequest;
import tv.lumo.api.generated.model.TokenPair;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.error.RateLimitedException;
import tv.lumo.api.shared.ratelimit.RateLimiter;
import tv.lumo.api.shared.web.ClientIp;

/**
 * Implements the generated {@code AuthApi}.
 *
 * <p>The interface has no default methods
 * ({@code interfaceOnly} + {@code skipDefaultInterface}, ADR 0001), so every
 * operation of the {@code auth} tag is implemented here or this class does not
 * compile. That is the contract-drift guarantee in practice.
 *
 * <p>The controller does routing, rate limiting and status codes. Everything
 * else lives in {@link AccountService}, {@link SessionService} and
 * {@link DeviceActivationService}.
 */
@RestController
public class AuthController implements AuthApi {

    private final AccountService accounts;
    private final SessionService sessions;
    private final DeviceActivationService activation;
    private final GoogleIdentityService google;
    private final RateLimiter rateLimiter;
    private final LumoProperties properties;
    private final HttpServletRequest request;

    public AuthController(AccountService accounts,
                          SessionService sessions,
                          DeviceActivationService activation,
                          GoogleIdentityService google,
                          RateLimiter rateLimiter,
                          LumoProperties properties,
                          HttpServletRequest request) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.activation = activation;
        this.google = google;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.request = request;
    }

    // ---- password accounts --------------------------------------------------

    @Override
    public ResponseEntity<AuthSession> register(RegisterRequest registerRequest) {
        limitByIp("register");
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.register(registerRequest));
    }

    @Override
    public ResponseEntity<AuthSession> login(LoginRequest loginRequest) {
        // Keyed on IP and email together. On IP alone, one office behind a single
        // NAT locks itself out; on email alone, an attacker sprays one password
        // across many accounts unthrottled.
        String key = "login:" + ClientIp.of(request) + ":" + safeEmailKey(loginRequest.getEmail());
        limit(key, properties.rateLimit().authAttemptsPerMinute());

        AuthSession session = accounts.login(loginRequest);
        // A user who mistyped four times and then got it right should not stay
        // throttled.
        rateLimiter.reset(key);
        return ResponseEntity.ok(session);
    }

    @Override
    public ResponseEntity<AuthSession> signInWithGoogle(GoogleSignInRequest googleSignInRequest) {
        limitByIp("oauth-google");

        GoogleIdentityService.GoogleIdentity identity = google.verify(googleSignInRequest.getIdToken());
        return ResponseEntity.ok(accounts.signInWithGoogle(identity, googleSignInRequest.getDevice()));
    }

    @Override
    public ResponseEntity<Void> verifyEmail(String token) {
        accounts.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> requestPasswordReset(ForgotPasswordRequest forgotPasswordRequest) {
        limitByIp("password-forgot");
        accounts.requestPasswordReset(forgotPasswordRequest.getEmail());
        // 202 whether or not the email exists.
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        limitByIp("password-reset");
        accounts.resetPassword(resetPasswordRequest.getToken(), resetPasswordRequest.getPassword());
        return ResponseEntity.noContent().build();
    }

    // ---- sessions -----------------------------------------------------------

    @Override
    public ResponseEntity<TokenPair> refreshSession(RefreshRequest refreshRequest) {
        return ResponseEntity.ok(sessions.rotate(refreshRequest.getRefreshToken()));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshRequest refreshRequest) {
        sessions.signOut(refreshRequest.getRefreshToken(), CurrentUser.requireUserId());
        return ResponseEntity.noContent().build();
    }

    // ---- TV activation, RFC 8628 -------------------------------------------

    @Override
    public ResponseEntity<DeviceCodeResponse> requestDeviceCode(DeviceCodeRequest deviceCodeRequest) {
        limitByIp("device-code");
        return ResponseEntity.status(HttpStatus.CREATED).body(activation.requestCode(deviceCodeRequest));
    }

    @Override
    public ResponseEntity<DeviceApproval> approveDeviceCode(ApproveDeviceRequest approveDeviceRequest) {
        // The tightest limit in the application. user_code is 8 characters from a
        // 31-character alphabet and lives for ten minutes; without this, guessing
        // one is a matter of volume.
        limit("device-approve:" + ClientIp.of(request),
                properties.rateLimit().deviceApproveAttemptsPerMinute());
        return ResponseEntity.ok(
                activation.approve(approveDeviceRequest.getUserCode(), CurrentUser.requireUserId()));
    }

    @Override
    public ResponseEntity<AuthSession> pollDeviceToken(DeviceTokenRequest deviceTokenRequest) {
        // Not IP-limited: polling is the protocol. SLOW_DOWN, enforced per
        // authorization against the database clock, is the throttle here.
        return ResponseEntity.ok(activation.poll(deviceTokenRequest.getDeviceCode()));
    }

    // ---- helpers ------------------------------------------------------------

    private void limitByIp(String action) {
        limit(action + ":" + ClientIp.of(request), properties.rateLimit().authAttemptsPerMinute());
    }

    private void limit(String key, int perMinute) {
        RateLimiter.Decision decision = rateLimiter.attempt(key, perMinute);
        if (!decision.allowed()) {
            throw new RateLimitedException(ErrorCode.RATE_LIMITED, decision.retryAfterSeconds(),
                    "Too many attempts");
        }
    }

    /**
     * Bounded, lower-cased email for use as a rate-limit key. Never logged: the
     * key identifies who is trying to sign in.
     */
    private static String safeEmailKey(String email) {
        if (email == null) {
            return "";
        }
        String normalised = email.trim().toLowerCase(java.util.Locale.ROOT);
        return normalised.length() > 254 ? normalised.substring(0, 254) : normalised;
    }
}
