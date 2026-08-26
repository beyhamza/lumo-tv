package tv.lumo.api.billing;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.auth.CurrentUser;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.api.BillingApi;
import tv.lumo.api.generated.model.BillingSession;
import tv.lumo.api.generated.model.CreateCheckoutSessionRequest;
import tv.lumo.api.generated.model.CreatePortalSessionRequest;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.FieldError;
import tv.lumo.api.generated.model.Locale;
import tv.lumo.api.generated.model.Plan;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.shared.error.RateLimitedException;
import tv.lumo.api.shared.ratelimit.RateLimiter;
import tv.lumo.api.shared.web.ClientIp;

/**
 * Implements the generated {@code BillingApi}: opening a checkout, and opening the
 * customer portal.
 *
 * <h2>Nothing here grants anything</h2>
 *
 * <p>Both operations return a URL to redirect to. The entitlement moves when the
 * provider's webhook writes it, and clients keep reading
 * {@code GET /me/entitlement} afterwards (ADR 0003). A client that assumed premium
 * because the redirect succeeded would be wrong for every abandoned checkout.
 *
 * <h2>The webhook does not exist yet</h2>
 *
 * <p><b>Which means a completed payment currently changes nothing.</b> The
 * endpoint that would receive it is not in {@code openapi.yaml}, and AGENTS.md §3
 * is explicit that a need the contract does not cover is escalated rather than
 * invented. So this half is built and the other half is a decision to take:
 * everything up to the redirect works, and {@code plan} stays {@code FREE} until
 * something writes it. Recorded in docs/design/api-gaps.md.
 *
 * <h2>Return URLs are not accepted from the caller</h2>
 *
 * <p>They are built from {@code lumo.web.base-url}. A caller-supplied return URL
 * is an open redirect wearing a billing costume, and this project already guards
 * the same hole on the sign-in {@code next} parameter.
 */
@RestController
public class BillingController implements BillingApi {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    /**
     * Per account, not per IP: these operations require authentication, and the
     * thing worth bounding is one account opening checkout sessions in a loop.
     */
    private static final int SESSIONS_PER_MINUTE = 5;

    private final EntitlementService entitlements;
    private final EntitlementRepository entitlementRows;
    private final StripeClient stripe;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final LumoProperties properties;
    /** Request-scoped proxy, as in {@code AuthController}. */
    private final HttpServletRequest request;

    public BillingController(EntitlementService entitlements,
                             EntitlementRepository entitlementRows,
                             StripeClient stripe,
                             UserRepository users,
                             RateLimiter rateLimiter,
                             LumoProperties properties,
                             HttpServletRequest request) {
        this.entitlements = entitlements;
        this.entitlementRows = entitlementRows;
        this.stripe = stripe;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.request = request;
    }

    @Override
    public ResponseEntity<BillingSession> createCheckoutSession(CreateCheckoutSessionRequest request) {
        requireConfigured();
        UserRow account = requireAccount();
        limit("checkout", account.id());

        if (request.getPlan() != Plan.PREMIUM) {
            // FREE is not something anyone buys. The property exists so that a
            // second paid tier does not change the shape of this operation, not so
            // that the free one can be purchased.
            throw ApiException.validation("Only PREMIUM can be purchased",
                    java.util.List.of(new FieldError("/plan", "UNSUPPORTED")));
        }
        if (entitlements.isPremium(account.id())) {
            // A distinct code, because the client's next move differs: send the
            // user to the portal rather than opening a second subscription.
            throw ApiException.conflict(ErrorCode.ALREADY_SUBSCRIBED,
                    "This account already holds an active paid entitlement");
        }

        String customerRef = resolveOrCreateCustomer(account);
        String url = stripe.createCheckoutSession(customerRef, localeOf(request.getLocale(), account),
                webUrl(properties.billing().successPath()),
                webUrl(properties.billing().cancelPath()));

        // The account id, never the URL: a session URL opens somebody's payment
        // page (AGENTS.md §5).
        log.info("Checkout session opened for account {}", account.id());
        return ResponseEntity.ok(new BillingSession(url));
    }

    @Override
    public ResponseEntity<BillingSession> createPortalSession(CreatePortalSessionRequest request) {
        requireConfigured();
        UserRow account = requireAccount();
        limit("portal", account.id());

        String customerRef = entitlementRows.findByUser(account.id())
                .map(EntitlementRepository.EntitlementRow::billingCustomerRef)
                .filter(ref -> ref != null && !ref.isBlank())
                // A FREE user who never subscribed is the ordinary case, not an
                // error worth a stack trace: the client offers checkout instead.
                .orElseThrow(() -> ApiException.notFound(ErrorCode.BILLING_CUSTOMER_NOT_FOUND,
                        "This account has never been billed"));

        String url = stripe.createPortalSession(customerRef,
                localeOf(request == null ? null : request.getLocale(), account),
                webUrl(properties.billing().returnPath()));

        log.info("Portal session opened for account {}", account.id());
        return ResponseEntity.ok(new BillingSession(url));
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Creates the billing customer on first checkout, and remembers it.
     *
     * <p>Written before the redirect rather than after the payment: the customer
     * is what {@code POST /billing/portal-session} needs, and a user who reaches
     * the payment page and abandons it still has a billing identity. The
     * entitlement is untouched — this records who they are, not what they bought.
     */
    private String resolveOrCreateCustomer(UserRow account) {
        return entitlementRows.findByUser(account.id())
                .map(EntitlementRepository.EntitlementRow::billingCustomerRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .orElseGet(() -> {
                    String created = stripe.createCustomer(account.email(), account.id().toString());
                    entitlementRows.saveBillingCustomer(account.id(), created);
                    return created;
                });
    }

    /**
     * Refuses politely when this deployment has no payment provider.
     *
     * <p>503 rather than 500: nothing is broken, the capability is absent, and the
     * distinction is what tells an operator to set a key rather than read a stack
     * trace. The contract does not enumerate this status on either operation — it
     * describes an API that has a provider — and the body is the same
     * {@code Problem} every other failure returns, so no client needs a new branch.
     */
    private void requireConfigured() {
        if (!properties.billing().configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR,
                    "No payment provider is configured on this deployment");
        }
    }

    private UserRow requireAccount() {
        UUID userId = CurrentUser.requireUserId();
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthenticated(ErrorCode.UNAUTHENTICATED,
                        "The authenticated account no longer exists"));
    }

    /**
     * Keyed on the account and on the address it came from.
     *
     * <p>The account alone would let one compromised session hammer the provider
     * from anywhere; the address alone would throttle a household behind one NAT.
     */
    private void limit(String action, UUID userId) {
        RateLimiter.Decision decision = rateLimiter.attempt(
                "billing-" + action + ":" + userId + ":" + ClientIp.of(request), SESSIONS_PER_MINUTE);
        if (!decision.allowed()) {
            throw new RateLimitedException(ErrorCode.RATE_LIMITED, decision.retryAfterSeconds(),
                    "Too many billing sessions opened; retry shortly");
        }
    }

    /** The caller may pick the language; it may not pick anything else. */
    private static String localeOf(Locale requested, UserRow account) {
        return requested == null ? account.locale() : requested.getValue();
    }

    private String webUrl(String path) {
        return properties.web().baseUrl() + path;
    }
}
