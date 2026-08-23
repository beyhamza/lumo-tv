package tv.lumo.api.auth;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a device's refresh-token chain in its <b>own</b> transaction.
 *
 * <p>This exists for one specific reason, and removing it silently reopens a
 * security hole.
 *
 * <p>Reuse detection has to do two things at once: revoke the chain, and fail
 * the request. Doing both inside {@link SessionService#rotate}'s transaction
 * means the exception rolls the revocation back with it — the caller gets a
 * correct-looking {@code REFRESH_TOKEN_REUSED} error while every stolen token on
 * that device stays valid. The attacker simply retries with the newest one.
 *
 * <p>{@code REQUIRES_NEW} suspends the caller's transaction and commits this one
 * independently, so the revocation survives the throw.
 *
 * <p>It is a separate bean rather than a method on {@link SessionService}
 * because Spring's transaction advice is a proxy: a self-invoked
 * {@code @Transactional} method runs with no new transaction at all, which would
 * look correct and do nothing.
 */
@Component
public class TokenChainRevoker {

    private static final Logger log = LoggerFactory.getLogger(TokenChainRevoker.class);

    private final RefreshTokenRepository refreshTokens;

    public TokenChainRevoker(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /**
     * Commits the revocation immediately, independently of the caller.
     *
     * @return how many live tokens were revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeDeviceChainNow(UUID deviceId) {
        int revoked = refreshTokens.revokeDeviceChain(deviceId);
        log.warn("Refresh token reuse detected for device {}; revoked {} live token(s)", deviceId, revoked);
        return revoked;
    }
}
