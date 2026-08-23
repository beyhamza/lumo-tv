package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.DeviceRegistration;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.LoginRequest;
import tv.lumo.api.generated.model.RegisterRequest;
import tv.lumo.api.shared.error.ApiException;

/**
 * Registration, sign-in, email verification and password reset.
 *
 * <p>Two anti-enumeration rules run through this class:
 *
 * <ul>
 *   <li>Sign-in failures are always {@code INVALID_CREDENTIALS}, and an unknown
 *       email still pays the full Argon2 cost, so existence cannot be read off
 *       the status code or the response time (US-02).</li>
 *   <li>{@code POST /auth/password/forgot} always answers 202, whether or not
 *       the email is registered.</li>
 * </ul>
 *
 * <p>Registration is the one place that deliberately does distinguish: US-01
 * requires telling a returning user to sign in or reset their password. The
 * timing of that response is still equalised.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int MIN_PASSWORD_LENGTH = 10;

    private static final String PROVIDER_GOOGLE = "GOOGLE";

    private final UserRepository users;
    private final OAuthIdentityRepository oauthIdentities;
    private final DeviceRepository devices;
    private final RefreshTokenRepository refreshTokens;
    private final UserTokenRepository userTokens;
    private final PasswordHasher passwords;
    private final SessionService sessions;
    private final AccountMailer mail;

    public AccountService(UserRepository users,
                          OAuthIdentityRepository oauthIdentities,
                          DeviceRepository devices,
                          RefreshTokenRepository refreshTokens,
                          UserTokenRepository userTokens,
                          PasswordHasher passwords,
                          SessionService sessions,
                          AccountMailer mail) {
        this.users = users;
        this.oauthIdentities = oauthIdentities;
        this.devices = devices;
        this.refreshTokens = refreshTokens;
        this.userTokens = userTokens;
        this.passwords = passwords;
        this.sessions = sessions;
        this.mail = mail;
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = normaliseEmail(request.getEmail());
        requireStrongPassword(request.getPassword());

        if (users.findByEmail(email).isPresent()) {
            // Spend the hashing cost anyway. Returning 409 in a millisecond while
            // a successful registration takes ~100 ms would let an attacker
            // enumerate accounts by timing alone, even though this branch is
            // openly distinguishable by status code.
            passwords.burn(request.getPassword());
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account already exists for this email");
        }

        UserRow user = users.insert(
                UUID.randomUUID(),
                email,
                passwords.hash(request.getPassword()),
                request.getDisplayName(),
                request.getLocale() == null ? "en" : request.getLocale().getValue());

        UUID deviceId = devices.insert(user.id(), request.getDevice());
        sendVerificationEmail(user);
        return sessions.openSession(user, deviceId);
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        String email = normaliseEmail(request.getEmail());
        Optional<UserRow> found = users.findByEmail(email);

        if (found.isEmpty()) {
            passwords.burn(request.getPassword());
            throw invalidCredentials();
        }
        UserRow user = found.get();
        if (!passwords.matches(request.getPassword(), user.passwordHash())) {
            throw invalidCredentials();
        }

        UUID deviceId = devices.insert(user.id(), request.getDevice());
        return sessions.openSession(user, deviceId);
    }

    /**
     * Signs in a Google identity that {@link GoogleIdentityService} has already
     * verified. Nothing from the client is trusted here.
     *
     * <p>Resolution order matters. An existing link wins, because that is the
     * account this Google user signed in with last time. Failing that, the
     * verified email attaches to whatever account already holds it — creating a
     * second account for one email is exactly what US-03 forbids.
     */
    @Transactional
    public AuthSession signInWithGoogle(GoogleIdentityService.GoogleIdentity identity,
                                        DeviceRegistration registration) {
        String email = normaliseEmail(identity.email());

        UserRow user = oauthIdentities.findUserId(PROVIDER_GOOGLE, identity.subject())
                .flatMap(users::findById)
                .orElseGet(() -> users.findByEmail(email)
                        // SSO-only account: password_hash stays null, and
                        // PasswordHasher treats null as "never matches" rather
                        // than as "no password required".
                        .orElseGet(() -> users.insert(UUID.randomUUID(), email, null,
                                identity.displayName(), "en")));

        oauthIdentities.link(user.id(), PROVIDER_GOOGLE, identity.subject());

        // An account reached through a verified provider has a verified email by
        // construction; making the user click a link as well would be theatre.
        if (user.emailVerifiedAt() == null) {
            users.markEmailVerified(user.id());
            user = users.findById(user.id()).orElseThrow();
        }

        UUID deviceId = devices.insert(user.id(), registration);
        return sessions.openSession(user, deviceId);
    }

    @Transactional
    public void verifyEmail(String token) {
        UserTokenRepository.UserTokenRow row = userTokens
                .lockByHash(SecretTokens.hash(token), UserTokenRepository.Purpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        ErrorCode.VERIFICATION_TOKEN_INVALID, "Verification token is unknown"));

        if (!row.isUsable(OffsetDateTime.now())) {
            throw new ApiException(org.springframework.http.HttpStatus.GONE,
                    ErrorCode.VERIFICATION_TOKEN_EXPIRED, "Verification token has expired or was already used");
        }
        userTokens.consume(row.id());
        users.markEmailVerified(row.userId());
    }

    /** Always succeeds from the caller's point of view. Enumeration is not possible here. */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        users.findByEmail(normaliseEmail(rawEmail)).ifPresent(user -> {
            userTokens.consumeAllFor(user.id(), UserTokenRepository.Purpose.PASSWORD_RESET);
            String token = SecretTokens.generate();
            userTokens.insert(user.id(), UserTokenRepository.Purpose.PASSWORD_RESET,
                    SecretTokens.hash(token), OffsetDateTime.now().plusHours(1));
            mail.sendPasswordReset(user, token);
        });
        log.debug("Password reset requested");
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        requireStrongPassword(newPassword);

        UserTokenRepository.UserTokenRow row = userTokens
                .lockByHash(SecretTokens.hash(token), UserTokenRepository.Purpose.PASSWORD_RESET)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        ErrorCode.RESET_TOKEN_INVALID, "Reset token is unknown"));

        if (!row.isUsable(OffsetDateTime.now())) {
            throw new ApiException(org.springframework.http.HttpStatus.GONE,
                    ErrorCode.RESET_TOKEN_EXPIRED, "Reset token has expired or was already used");
        }

        userTokens.consume(row.id());
        users.updatePasswordHash(row.userId(), passwords.hash(newPassword));

        // Whoever prompted the reset may be holding a stolen session. Changing
        // the password without revoking sessions leaves them signed in.
        int revoked = refreshTokens.revokeAllForUser(row.userId());
        log.info("Password reset completed; revoked {} live session(s) for the account", revoked);
    }

    private void sendVerificationEmail(UserRow user) {
        String token = SecretTokens.generate();
        userTokens.insert(user.id(), UserTokenRepository.Purpose.EMAIL_VERIFICATION,
                SecretTokens.hash(token), OffsetDateTime.now().plusDays(3));
        mail.sendEmailVerification(user, token);
    }

    private static ApiException invalidCredentials() {
        // Deliberately identical for "no such email" and "wrong password".
        return ApiException.unauthenticated(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    private static void requireStrongPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.unprocessable(ErrorCode.PASSWORD_TOO_WEAK,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
