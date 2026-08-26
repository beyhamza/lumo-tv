package tv.lumo.api.auth;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.billing.EntitlementService;
import tv.lumo.api.generated.api.AccountApi;
import tv.lumo.api.generated.model.Device;
import tv.lumo.api.generated.model.DeviceList;
import tv.lumo.api.generated.model.Entitlement;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.UpdateUserRequest;
import tv.lumo.api.generated.model.User;
import tv.lumo.api.shared.error.ApiException;

/**
 * Implements the generated {@code AccountApi}: the account, its devices and what
 * it is entitled to.
 *
 * <p>Every operation here is about the <b>caller</b>. There is no user id in any
 * path, and none of these methods accepts one: the subject is always
 * {@link CurrentUser}, so there is no shape of request that reads another
 * account.
 *
 * <p>The entitlement is delegated to {@code billing}, which owns it. This
 * controller does not decide what a plan allows — no controller does (ADR 0003).
 */
@RestController
public class AccountController implements AccountApi {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final UserRepository users;
    private final UserMapper userMapper;
    private final DeviceRepository devices;
    private final EntitlementService entitlements;

    public AccountController(UserRepository users,
                             UserMapper userMapper,
                             DeviceRepository devices,
                             EntitlementService entitlements) {
        this.users = users;
        this.userMapper = userMapper;
        this.devices = devices;
        this.entitlements = entitlements;
    }

    @Override
    public ResponseEntity<User> getCurrentUser() {
        return ResponseEntity.ok(userMapper.toApi(requireAccount(CurrentUser.requireUserId())));
    }

    @Override
    @Transactional
    public ResponseEntity<User> updateCurrentUser(UpdateUserRequest request) {
        UUID userId = CurrentUser.requireUserId();
        users.updateProfile(userId, request.getDisplayName(),
                request.getLocale() == null ? null : request.getLocale().getValue());
        return ResponseEntity.ok(userMapper.toApi(requireAccount(userId)));
    }

    /**
     * Never 404, by the contract: an account with no subscription is
     * {@code FREE} / {@code ACTIVE}, which is an answer rather than an absence.
     */
    @Override
    public ResponseEntity<Entitlement> getEntitlement() {
        UserRow account = requireAccount(CurrentUser.requireUserId());
        return ResponseEntity.ok(entitlements.forUser(account.id(), account.createdAt()));
    }

    @Override
    public ResponseEntity<DeviceList> listDevices() {
        AuthPrincipal caller = CurrentUser.require();

        List<Device> items = devices.findLinked(caller.userId(), caller.deviceId()).stream()
                .map(row -> toApi(row, caller.deviceId()))
                .toList();
        return ResponseEntity.ok(new DeviceList(items));
    }

    /**
     * Unlinking your own device is allowed and signs you out — the contract says
     * so, and a stolen laptop is exactly when someone needs it.
     */
    @Override
    @Transactional
    public ResponseEntity<Void> revokeDevice(UUID id) {
        UUID userId = CurrentUser.requireUserId();

        if (devices.delete(id, userId) == 0) {
            // Scoped to the caller in the same statement, so someone else's device
            // id is indistinguishable from one that does not exist. This endpoint
            // cannot be used to discover device ids.
            throw ApiException.notFound(ErrorCode.DEVICE_NOT_FOUND, "No such device on this account");
        }
        // The refresh-token chain goes with the row: refresh_token.device_id
        // cascades. A revoked device's next refresh finds nothing at all rather
        // than finding something marked revoked.
        log.info("Device {} unlinked", id);
        return ResponseEntity.noContent().build();
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * The caller's own row.
     *
     * <p>A valid token whose user has since been deleted is not a client error to
     * report as 404 on {@code /me}: the token is no longer usable, which is what
     * {@code UNAUTHENTICATED} means.
     */
    private UserRow requireAccount(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthenticated(ErrorCode.UNAUTHENTICATED,
                        "The authenticated account no longer exists"));
    }

    /**
     * @param currentDeviceId the device whose token made this call; the one row
     *                        the user must not revoke by accident
     */
    private static Device toApi(DeviceRepository.DeviceRow row, UUID currentDeviceId) {
        Device device = new Device(row.id(), Platform.fromValue(row.platform()),
                row.id().equals(currentDeviceId), row.createdAt());
        device.setName(row.name());
        device.setModel(row.model());
        device.setAppVersion(row.appVersion());
        // A "last seen", never a presence: nothing announces a disconnection, and
        // the client decides what threshold it calls "active".
        device.setLastSeenAt(row.lastSeenAt());
        return device;
    }
}
