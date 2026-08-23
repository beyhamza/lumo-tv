package tv.lumo.api.auth;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.DeviceCodeRequest;
import tv.lumo.api.generated.model.DeviceCodeResponse;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.error.ApiException;

/**
 * TV activation, modelled on RFC 8628 (US-05).
 *
 * <p>Typing an email and a password on a remote control is a punitive experience
 * and the first abandonment point of a television application. This flow moves
 * the credential entry to a phone and leaves the television with an eight
 * character code.
 *
 * <p>The polling states are the fiddly part and are worth stating plainly:
 * {@code AUTHORIZATION_PENDING} is the <b>nominal</b> answer, not a failure, and
 * a client that surfaces it as an error has misread the protocol.
 * {@code SLOW_DOWN} means the television polled faster than the interval it was
 * given and must add five seconds.
 */
@Service
public class DeviceActivationService {

    private static final Logger log = LoggerFactory.getLogger(DeviceActivationService.class);
    private static final int MAX_USER_CODE_ATTEMPTS = 5;

    private final DeviceAuthorizationRepository authorizations;
    private final DeviceRepository devices;
    private final UserRepository users;
    private final SessionService sessions;
    private final LumoProperties properties;

    public DeviceActivationService(DeviceAuthorizationRepository authorizations,
                                   DeviceRepository devices,
                                   UserRepository users,
                                   SessionService sessions,
                                   LumoProperties properties) {
        this.authorizations = authorizations;
        this.devices = devices;
        this.users = users;
        this.sessions = sessions;
        this.properties = properties;
    }

    /** Called by the television. Returns the code to display and the secret to poll with. */
    @Transactional
    public DeviceCodeResponse requestCode(DeviceCodeRequest request) {
        String deviceCode = SecretTokens.generate();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(properties.deviceCode().ttl());
        int interval = (int) properties.deviceCode().pollInterval().toSeconds();

        String userCode = allocateUserCode(deviceCode, request, expiresAt, interval);

        String baseUrl = properties.web().baseUrl() + "/activate";
        return new DeviceCodeResponse(
                deviceCode,
                userCode,
                URI.create(baseUrl),
                // ?code=, matching what the TV encodes in its QR code (US-05).
                // RFC 8628 names this parameter user_code; the sprint specifies
                // ?code=, and the web activation page is the only consumer.
                URI.create(baseUrl + "?code=" + userCode),
                (int) properties.deviceCode().ttl().toSeconds(),
                interval);
    }

    /**
     * Called from lumo.tv/activate by a signed-in user.
     *
     * <p>Rate limiting happens in the controller: {@code user_code} is short and
     * guessable by design, so this is the endpoint an attacker brute-forces.
     */
    @Transactional
    public void approve(String typedUserCode, UUID approvingUserId) {
        String userCode = SecretTokens.normaliseUserCode(typedUserCode);

        DeviceAuthorizationRepository.DeviceAuthorizationRow row =
                authorizations.lockPendingByUserCode(userCode)
                        .orElseThrow(() -> ApiException.notFound(ErrorCode.DEVICE_CODE_NOT_FOUND,
                                "No pending authorization carries this code"));

        if (row.isExpired(OffsetDateTime.now())) {
            authorizations.updateStatus(row.id(), DeviceAuthorizationRepository.Status.EXPIRED);
            throw new ApiException(HttpStatus.GONE, ErrorCode.DEVICE_CODE_EXPIRED,
                    "This code has expired; the television will show a new one");
        }

        authorizations.approve(row.id(), approvingUserId);
        log.info("Device authorization {} approved", row.id());
    }

    /**
     * Called by the television on a timer.
     *
     * <p>Every terminal state is reported as 400 with a code, per RFC 8628.
     */
    @Transactional
    public AuthSession poll(String deviceCode) {
        DeviceAuthorizationRepository.DeviceAuthorizationRow row =
                authorizations.lockByDeviceCodeHash(SecretTokens.hash(deviceCode))
                        .orElseThrow(() -> pollingState(ErrorCode.DEVICE_CODE_NOT_FOUND,
                                "Unknown device code"));

        if (authorizations.registerPollAndCheckTooFast(row.id(), row.intervalSeconds())) {
            throw pollingState(ErrorCode.SLOW_DOWN, "Polling faster than the advertised interval");
        }

        if (row.isExpired(OffsetDateTime.now())) {
            authorizations.updateStatus(row.id(), DeviceAuthorizationRepository.Status.EXPIRED);
            throw pollingState(ErrorCode.EXPIRED_TOKEN, "Device code has expired");
        }

        return switch (row.status()) {
            case PENDING -> throw pollingState(ErrorCode.AUTHORIZATION_PENDING,
                    "Not approved yet; keep polling at the advertised interval");
            case DENIED -> throw pollingState(ErrorCode.ACCESS_DENIED, "The user refused this device");
            case EXPIRED -> throw pollingState(ErrorCode.EXPIRED_TOKEN, "Device code has expired");
            // Single use: a second successful poll must not mint a second session.
            case CONSUMED -> throw pollingState(ErrorCode.EXPIRED_TOKEN, "Device code was already used");
            case APPROVED -> consume(row);
        };
    }

    private AuthSession consume(DeviceAuthorizationRepository.DeviceAuthorizationRow row) {
        UserRow user = users.findById(row.userId())
                .orElseThrow(() -> pollingState(ErrorCode.ACCESS_DENIED, "Approving account no longer exists"));

        authorizations.updateStatus(row.id(), DeviceAuthorizationRepository.Status.CONSUMED);
        UUID deviceId = devices.insert(user.id(), row.platform(), row.name(), row.model(), row.appVersion());
        log.info("Device authorization {} consumed; device {} provisioned", row.id(), deviceId);
        return sessions.openSession(user, deviceId);
    }

    /**
     * Allocates a user code, retrying on the unique-index collision.
     *
     * <p>The alphabet is 31 characters over 8 positions, so a collision among
     * codes that are simultaneously pending is vanishingly unlikely — but
     * "unlikely" is not "impossible", and the failure mode without a retry is a
     * 500 on a television that can do nothing about it.
     */
    private String allocateUserCode(String deviceCode, DeviceCodeRequest request,
                                    OffsetDateTime expiresAt, int interval) {
        for (int attempt = 0; attempt < MAX_USER_CODE_ATTEMPTS; attempt++) {
            String candidate = SecretTokens.generateUserCode();
            try {
                authorizations.insert(UUID.randomUUID(), SecretTokens.hash(deviceCode), candidate,
                        request.getPlatform().getValue(), request.getName(), request.getModel(),
                        request.getAppVersion(), expiresAt, interval);
                return candidate;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                log.debug("user_code collision on attempt {}", attempt + 1);
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR,
                "Could not allocate a device code");
    }

    private static ApiException pollingState(ErrorCode code, String detail) {
        // RFC 8628 carries every polling state as a 400.
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    /**
     * Retires timed-out authorizations.
     *
     * <p>Not merely housekeeping: the unique index on {@code user_code} is partial
     * on {@code status = 'PENDING'}, so codes are only returned to circulation
     * once their rows leave that state.
     */
    @Scheduled(fixedDelay = 60_000)
    public void expireStaleAuthorizations() {
        int expired = authorizations.expireStale();
        if (expired > 0) {
            log.debug("Expired {} stale device authorization(s)", expired);
        }
    }
}
