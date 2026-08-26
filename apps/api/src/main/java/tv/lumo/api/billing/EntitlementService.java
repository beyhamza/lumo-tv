package tv.lumo.api.billing;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tv.lumo.api.generated.model.Entitlement;
import tv.lumo.api.generated.model.EntitlementProvider;
import tv.lumo.api.generated.model.EntitlementStatus;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.Plan;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.error.ApiException;

/**
 * What an account is allowed to do, decided here and nowhere else.
 *
 * <p>Three things live in this class, and they belong together:
 *
 * <ol>
 *   <li><b>Reading the entitlement.</b> Most accounts have no row: absence means
 *       {@code FREE}, which is a default rather than an unknown.</li>
 *   <li><b>Turning it into limits.</b> The numbers come from configuration
 *       ({@code lumo.plans.*}) so that the free plan's ceiling exists once in the
 *       product instead of once per application.</li>
 *   <li><b>Enforcing them.</b> {@link #requireSourceSlot} and
 *       {@link #requireDeviceSlot} raise the two codes the contract added for
 *       exactly this, so a client can tell "you already have this" from "your
 *       plan stops here" — and only the second has a way out to offer.</li>
 * </ol>
 *
 * <h2>Status and plan are not the same question</h2>
 *
 * <p>A subscription that failed to renew keeps {@code plan = PREMIUM} and moves to
 * {@code PAST_DUE}. The response says so, because "your payment failed" is a
 * screen that needs both halves. The <em>limits</em>, on the other hand, follow
 * the granting statuses only: {@code ACTIVE} and {@code TRIALING} grant, the other
 * three do not. Reading the plan alone would leave a lapsed subscriber with
 * unlimited sources forever.
 *
 * <p>Counts are passed in rather than fetched. The rule belongs to billing; the
 * tables belong to their own domains, and this class has no business querying
 * {@code source} or {@code device}.
 */
@Service
public class EntitlementService {

    private final EntitlementRepository entitlements;
    private final LumoProperties.Plans plans;

    public EntitlementService(EntitlementRepository entitlements, LumoProperties properties) {
        this.entitlements = entitlements;
        this.plans = properties.plans();
    }

    /**
     * The caller's access rights, as the contract renders them.
     *
     * @param accountCreatedAt used as {@code updated_at} for an account that has
     *                         no entitlement row. It is the truthful answer —
     *                         these rights have held since the account existed —
     *                         where {@code now()} would claim something changed
     *                         on every poll
     */
    public Entitlement forUser(UUID userId, OffsetDateTime accountCreatedAt) {
        Optional<EntitlementRepository.EntitlementRow> row = entitlements.findByUser(userId);

        Plan plan = row.map(r -> Plan.fromValue(r.plan())).orElse(Plan.FREE);
        EntitlementStatus status = row.map(r -> EntitlementStatus.fromValue(r.status()))
                .orElse(EntitlementStatus.ACTIVE);
        EntitlementProvider provider = row.map(r -> EntitlementProvider.fromValue(r.provider()))
                .orElse(EntitlementProvider.MANUAL);

        Entitlement entitlement = new Entitlement(plan, status, provider,
                row.map(EntitlementRepository.EntitlementRow::updatedAt).orElse(accountCreatedAt));
        entitlement.setCurrentPeriodEnd(
                row.map(EntitlementRepository.EntitlementRow::currentPeriodEnd).orElse(null));
        entitlement.setTrialEndsAt(
                row.map(EntitlementRepository.EntitlementRow::trialEndsAt).orElse(null));

        // provider_ref and billing_customer_ref are billing-internal and stay
        // here: the contract has no property for either, deliberately.
        LumoProperties.Limits limits = limitsFor(plan, status);
        entitlement.setMaxSources(limits.maxSources());
        entitlement.setMaxDevices(limits.maxDevices());
        return entitlement;
    }

    /** True while the account may use paid features. */
    public boolean isPremium(UUID userId) {
        return entitlements.findByUser(userId)
                .map(row -> grants(Plan.fromValue(row.plan()),
                        EntitlementStatus.fromValue(row.status())))
                .orElse(false);
    }

    /**
     * @throws ApiException 409 {@code SOURCE_LIMIT_REACHED} when the plan stops here
     */
    public void requireSourceSlot(UUID userId, int currentSources) {
        Integer max = limitsFor(userId).maxSources();
        if (max != null && currentSources >= max) {
            throw ApiException.conflict(ErrorCode.SOURCE_LIMIT_REACHED,
                    "This plan allows " + max + " source(s)");
        }
    }

    /**
     * @throws ApiException 409 {@code DEVICE_LIMIT_REACHED} when the plan stops here
     */
    public void requireDeviceSlot(UUID userId, int currentDevices) {
        Integer max = limitsFor(userId).maxDevices();
        if (max != null && currentDevices >= max) {
            throw ApiException.conflict(ErrorCode.DEVICE_LIMIT_REACHED,
                    "This plan allows " + max + " device(s)");
        }
    }

    // ---- helpers ------------------------------------------------------------

    private LumoProperties.Limits limitsFor(UUID userId) {
        return entitlements.findByUser(userId)
                .map(row -> limitsFor(Plan.fromValue(row.plan()),
                        EntitlementStatus.fromValue(row.status())))
                .orElse(plans.free());
    }

    private LumoProperties.Limits limitsFor(Plan plan, EntitlementStatus status) {
        return grants(plan, status) ? plans.premium() : plans.free();
    }

    /**
     * Whether this pair grants paid features.
     *
     * <p>Both halves are required. A {@code PREMIUM} plan in {@code CANCELED} does
     * not grant, and a {@code FREE} plan in {@code ACTIVE} — the ordinary state of
     * most accounts — never did.
     */
    private static boolean grants(Plan plan, EntitlementStatus status) {
        return plan == Plan.PREMIUM
                && (status == EntitlementStatus.ACTIVE || status == EntitlementStatus.TRIALING);
    }
}
