package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.TokenPair;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.error.ApiException;

/**
 * Issues sessions and rotates refresh tokens.
 *
 * <p>This is the security-critical core of the authentication domain. Two rules
 * it exists to enforce:
 *
 * <ol>
 *   <li><b>Rotation.</b> Every use of a refresh token issues a new one and
 *       revokes the presented one. A refresh token is single-use.</li>
 *   <li><b>Reuse detection.</b> Presenting an already-consumed token revokes the
 *       device's entire chain. That is the theft signal: the legitimate device
 *       and the thief cannot both hold a valid token, so whichever one loses the
 *       race exposes the other, and the account owner has to sign in again
 *       instead of sharing their session with an attacker
 *       (docs/domain-model.md §2, US-04).</li>
 * </ol>
 *
 * <p>Nothing here logs a token value, hashed or otherwise.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final AccessTokenService accessTokens;
    private final RefreshTokenRepository refreshTokens;
    private final DeviceRepository devices;
    private final TokenChainRevoker revoker;
    private final UserMapper userMapper;
    private final LumoProperties properties;

    public SessionService(AccessTokenService accessTokens,
                          RefreshTokenRepository refreshTokens,
                          DeviceRepository devices,
                          TokenChainRevoker revoker,
                          UserMapper userMapper,
                          LumoProperties properties) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.devices = devices;
        this.revoker = revoker;
        this.userMapper = userMapper;
        this.properties = properties;
    }

    /** Opens a session on a newly provisioned device. */
    @Transactional
    public AuthSession openSession(UserRow user, UUID deviceId) {
        TokenPair pair = issuePair(user.id(), deviceId);
        return new AuthSession(
                pair.getAccessToken(),
                AuthSession.TokenTypeEnum.BEARER,
                pair.getExpiresIn(),
                pair.getRefreshToken(),
                userMapper.toApi(user),
                deviceId);
    }

    /**
     * Rotates a refresh token.
     *
     * <p>Runs in one transaction with the row locked ({@code FOR UPDATE}) so two
     * concurrent refreshes from the same client cannot both succeed. Clients must
     * still serialise their own refresh calls — two parallel ones at start-up is
     * the classic way this pattern signs a user out (US-04) — but the server does
     * not depend on them doing so.
     */
    @Transactional
    public TokenPair rotate(String presentedToken) {
        String hash = SecretTokens.hash(presentedToken);
        OffsetDateTime now = OffsetDateTime.now();

        RefreshTokenRepository.RefreshTokenRow row = refreshTokens.lockByHash(hash)
                .orElseThrow(() -> ApiException.unauthenticated(
                        ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token is unknown"));

        if (row.isRevoked()) {
            // Someone is holding a token that was already spent. Either the
            // legitimate device replayed it, or it leaked. The two are
            // indistinguishable from here, so assume the worse case.
            //
            // The revocation MUST commit in its own transaction. Doing it inline
            // would put it in the transaction this method is about to fail, and
            // the rollback would undo it: the caller would get a correct-looking
            // REFRESH_TOKEN_REUSED while every stolen token on that device stayed
            // valid. See TokenChainRevoker.
            revoker.revokeDeviceChainNow(row.deviceId());
            throw ApiException.unauthenticated(ErrorCode.REFRESH_TOKEN_REUSED,
                    "Refresh token was already used; the device token chain has been revoked");
        }

        if (row.isExpired(now)) {
            throw ApiException.unauthenticated(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token has expired");
        }

        UUID newTokenId = UUID.randomUUID();
        String newToken = SecretTokens.generate();
        refreshTokens.insert(newTokenId, row.userId(), row.deviceId(), SecretTokens.hash(newToken),
                now.plus(properties.refresh().ttl()));
        refreshTokens.markRotated(row.id(), newTokenId);
        devices.touch(row.deviceId(), row.userId());

        AccessTokenService.IssuedAccessToken access = accessTokens.issue(row.userId(), row.deviceId());
        return pair(access, newToken);
    }

    /** Revokes the whole chain of the device that presented this token. */
    @Transactional
    public void signOut(String presentedToken, UUID callerUserId) {
        refreshTokens.lockByHash(SecretTokens.hash(presentedToken)).ifPresent(row -> {
            // A caller may only sign out their own device. Without this check the
            // endpoint would let any authenticated user revoke any session whose
            // token they could guess or replay.
            if (!row.userId().equals(callerUserId)) {
                throw ApiException.unauthenticated(ErrorCode.REFRESH_TOKEN_INVALID,
                        "Refresh token does not belong to the authenticated user");
            }
            refreshTokens.revokeDeviceChain(row.deviceId());
        });
        // A token that is already gone yields 204: sign-out is idempotent, and
        // telling a caller which tokens exist is an oracle we do not need.
    }

    private TokenPair issuePair(UUID userId, UUID deviceId) {
        String refreshToken = SecretTokens.generate();
        refreshTokens.insert(UUID.randomUUID(), userId, deviceId, SecretTokens.hash(refreshToken),
                OffsetDateTime.now().plus(properties.refresh().ttl()));
        return pair(accessTokens.issue(userId, deviceId), refreshToken);
    }

    private static TokenPair pair(AccessTokenService.IssuedAccessToken access, String refreshToken) {
        return new TokenPair(access.value(), TokenPair.TokenTypeEnum.BEARER,
                (int) access.expiresInSeconds(), refreshToken);
    }
}
