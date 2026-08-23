package tv.lumo.api.auth;

import java.util.UUID;

/**
 * The authenticated caller, as carried on the security context.
 *
 * <p>Holds the device as well as the user because a session is bound to one
 * installation: revoking a device must not sign the account out everywhere, and
 * a refresh chain belongs to a device rather than to an account.
 */
public record AuthPrincipal(UUID userId, UUID deviceId) {
}
