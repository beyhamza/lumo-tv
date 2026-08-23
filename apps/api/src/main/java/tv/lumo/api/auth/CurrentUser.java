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
 * <h2>Why this is not a {@code ScopedValue}</h2>
 *
 * <p>ADR 0005 §3 says request-scoped context uses {@code ScopedValue} and that
 * {@code ThreadLocal} is a leak by construction with virtual threads. This class
 * reads Spring Security's {@code SecurityContextHolder}, whose default strategy
 * IS a {@code ThreadLocal}. That is a deliberate exception, on two grounds.
 *
 * <p>First, the hazard the ADR describes is <em>per-thread caching of expensive
 * objects</em> across millions of short-lived threads. {@code SecurityContextHolder}
 * caches nothing: the filter chain sets it at the start of a request and clears
 * it in a {@code finally} at the end, so nothing outlives the request that
 * created it.
 *
 * <p>Second, Spring Security has no {@code ScopedValue} strategy, and a
 * {@code ScopedValue} maintained in parallel would be a SECOND source of truth
 * for who is calling — with method security, {@code @AuthenticationPrincipal} and
 * the filter chain still reading the first. Two answers to "who is this" is a
 * worse failure mode than a framework-managed {@code ThreadLocal}.
 *
 * <p>What this project does honour without exception: <b>no {@code ThreadLocal}
 * of our own anywhere</b>, and nothing carried implicitly into the ingestion
 * workers. Background work receives what it needs as explicit parameters, which
 * is why {@code IngestionService} takes a {@code sourceId} rather than reading
 * an ambient caller.
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
