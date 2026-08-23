package tv.lumo.api.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.error.ApiException;

/**
 * Reads the authenticated caller off the security context.
 *
 * <p>Every controller that touches user-owned data starts here, and the
 * {@code userId} it returns is threaded into the SQL rather than trusted from a
 * path or a body. That is the whole of the multi-tenant rule in
 * docs/architecture.md §2: no query on a table carrying {@code user_id} runs
 * without filtering on it.
 *
 * <p>Reading the context statically rather than via {@code ScopedValue} because
 * Spring Security already propagates it and duplicating that would create two
 * sources of truth for who is calling. ADR 0005 §3 forbids {@code ThreadLocal}
 * for caching expensive objects across virtual threads; a per-request
 * authentication that is cleared by the filter chain is a different thing.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw ApiException.unauthenticated(ErrorCode.UNAUTHENTICATED, "No authenticated caller");
        }
        return principal;
    }

    public static java.util.UUID requireUserId() {
        return require().userId();
    }
}
