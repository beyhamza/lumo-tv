package tv.lumo.api.billing;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Access to {@code entitlement}, the single source of truth for access rights
 * (ADR 0003).
 *
 * <p><b>Reads are user-scoped; writes are billing-only.</b> Nothing outside this
 * package writes here, and no endpoint in the contract writes here either: an
 * entitlement moves because a payment provider said so, never because a client
 * asked. That is the whole of rule 3 in CLAUDE.md — a client never tells the
 * server it is premium.
 *
 * <p>Most accounts have no row at all. A user who never subscribed is
 * {@code FREE}, and a table that stored a row saying so for every registration
 * would be a table whose size tracks sign-ups rather than subscriptions. The
 * absence is read as the default by {@link EntitlementService}; a row appears the
 * first time a billing customer exists for the account.
 */
@Repository
public class EntitlementRepository {

    private static final String COLUMNS = """
            user_id, plan, status, provider, provider_ref, billing_customer_ref,
            current_period_end, trial_ends_at, updated_at
            """;

    private final JdbcClient jdbc;

    public EntitlementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EntitlementRow> findByUser(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM entitlement WHERE user_id = :userId")
                .param("userId", userId)
                .query(EntitlementRepository::map)
                .optional();
    }

    /**
     * Records the billing customer created for an account, without touching what
     * that account is entitled to.
     *
     * <p>The two are deliberately separate writes. Opening a checkout creates a
     * customer and grants nothing — the row it writes here says "this account has
     * a billing identity", and {@code plan} stays {@code FREE} until the provider
     * says otherwise. A method that did both would be the shortcut that hands
     * premium to everyone who reached the payment page and abandoned it.
     */
    public void saveBillingCustomer(UUID userId, String customerRef) {
        jdbc.sql("""
                INSERT INTO entitlement (id, user_id, plan, status, provider,
                                         billing_customer_ref, updated_at)
                VALUES (:id, :userId, 'FREE', 'ACTIVE', 'STRIPE', :customerRef, now())
                ON CONFLICT (user_id)
                DO UPDATE SET billing_customer_ref = EXCLUDED.billing_customer_ref,
                              updated_at = now()
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("customerRef", customerRef)
                .update();
    }

    /**
     * @param plan               never null on a stored row
     * @param billingCustomerRef the provider's customer identifier, distinct from
     *                           {@code providerRef}, which names the subscription
     */
    public record EntitlementRow(
            UUID userId, String plan, String status, String provider, String providerRef,
            String billingCustomerRef, OffsetDateTime currentPeriodEnd,
            OffsetDateTime trialEndsAt, OffsetDateTime updatedAt
    ) {
    }

    static EntitlementRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new EntitlementRow(
                rs.getObject("user_id", UUID.class),
                rs.getString("plan"),
                rs.getString("status"),
                rs.getString("provider"),
                rs.getString("provider_ref"),
                rs.getString("billing_customer_ref"),
                rs.getObject("current_period_end", OffsetDateTime.class),
                rs.getObject("trial_ends_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
