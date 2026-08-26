package tv.lumo.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.CreateSourceRequest;
import tv.lumo.api.generated.model.Entitlement;
import tv.lumo.api.generated.model.EntitlementStatus;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.Plan;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.source.SourceService;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Plan limits, which are the reason {@code SOURCE_LIMIT_REACHED} and
 * {@code DEVICE_LIMIT_REACHED} exist.
 *
 * <p>What is being pinned here is not arithmetic. It is that the numbers live on
 * the server and that hitting one is reported as a quota rather than as a bare
 * {@code CONFLICT} — a client that cannot tell "you already have this" from "your
 * plan stops here" cannot offer the one thing that resolves the second.
 *
 * <p>The device rule has a second half that is easy to get wrong and expensive to
 * discover in production: a slot is held by a <em>live session</em>, not by a
 * device row. Signing out frees it. Without that, a two-device plan would refuse
 * its third sign-in forever, including from the same browser the user signed out
 * of last week.
 */
@Import(PostgresContainerInitializer.class)
class PlanLimitsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private EntitlementService entitlements;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private SessionService sessions;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;

    @BeforeEach
    void createAccount() {
        user = users.insert(UUID.randomUUID(),
                "limits-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Limits Test", "en");
    }

    @Test
    @DisplayName("an account with no entitlement row is FREE, and says what FREE allows")
    void freeAccountsCarryTheirLimits() {
        Entitlement entitlement = entitlements.forUser(user.id(), user.createdAt());

        assertThat(entitlement.getPlan()).isEqualTo(Plan.FREE);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        // The whole point of G1: the client reads the ceiling instead of carrying
        // its own copy of it.
        assertThat(entitlement.getMaxSources()).isEqualTo(1);
        assertThat(entitlement.getMaxDevices()).isEqualTo(2);
        // Not "now": these rights have held since the account existed, and a
        // timestamp that moved on every poll would claim otherwise.
        assertThat(entitlement.getUpdatedAt()).isEqualTo(user.createdAt());
    }

    @Test
    @DisplayName("a premium account has no ceiling, and null means unlimited")
    void premiumAccountsHaveNoCeiling() {
        grantPremium("ACTIVE");

        Entitlement entitlement = entitlements.forUser(user.id(), user.createdAt());

        assertThat(entitlement.getPlan()).isEqualTo(Plan.PREMIUM);
        // Null is "unlimited", not "unknown". The contract is explicit about it
        // because the two would render very differently.
        assertThat(entitlement.getMaxSources()).isNull();
        assertThat(entitlement.getMaxDevices()).isNull();
        assertThatCode(() -> entitlements.requireSourceSlot(user.id(), 900)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a trial grants premium; a lapsed subscription does not")
    void statusDecidesWhatIsGranted() {
        grantPremium("TRIALING");
        assertThat(entitlements.isPremium(user.id())).isTrue();
        assertThat(entitlements.forUser(user.id(), user.createdAt()).getMaxSources()).isNull();

        grantPremium("PAST_DUE");
        // The plan still reads PREMIUM — "your payment failed" needs both halves
        // — but the ceiling comes back. Reading the plan alone would leave a
        // lapsed subscriber unlimited forever.
        Entitlement lapsed = entitlements.forUser(user.id(), user.createdAt());
        assertThat(lapsed.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(lapsed.getStatus()).isEqualTo(EntitlementStatus.PAST_DUE);
        assertThat(lapsed.getMaxSources()).isEqualTo(1);
        assertThat(entitlements.isPremium(user.id())).isFalse();
    }

    @Test
    @DisplayName("a second source on a FREE plan is refused with SOURCE_LIMIT_REACHED")
    void secondSourceIsRefused() {
        sources.insert(UUID.randomUUID(), user.id(), "First", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/one.m3u", null, null, null);

        CreateSourceRequest request = new CreateSourceRequest("Second", SourceKind.M3U_URL)
                .m3uUrl("https://playlist.example/two.m3u");

        assertThatThrownBy(() -> sourceService.create(user.id(), request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                // Not CONFLICT. A generic conflict offers the user nothing to do.
                .isEqualTo(ErrorCode.SOURCE_LIMIT_REACHED);

        // And nothing was created, nor was the playlist ever fetched: the quota is
        // checked before anything reaches the network.
        assertThat(sources.countOwnedBy(user.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("a third live session on a FREE plan is refused, and signing out frees the slot")
    void deviceSlotsAreHeldByLiveSessionsOnly() {
        String firstRefresh = openSessionOnANewDevice();
        openSessionOnANewDevice();

        assertThat(devices.countLinked(user.id())).isEqualTo(2);
        assertThatThrownBy(() -> entitlements.requireDeviceSlot(user.id(), devices.countLinked(user.id())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.DEVICE_LIMIT_REACHED);

        sessions.signOut(firstRefresh, user.id());

        // The row is still there; the session is not. What counts against the plan
        // is the session — otherwise a user would exhaust their plan by signing in
        // twice and could never recover.
        assertThat(devices.countLinked(user.id())).isEqualTo(1);
        assertThatCode(() -> entitlements.requireDeviceSlot(user.id(), devices.countLinked(user.id())))
                .doesNotThrowAnyException();
    }

    // ---- helpers ------------------------------------------------------------

    private String openSessionOnANewDevice() {
        UUID deviceId = devices.insert(user.id(), Platform.WEB.getValue(), "lumo.tv", null, "0.1.0");
        return sessions.openSession(user, deviceId).getRefreshToken();
    }

    /**
     * Writes the entitlement directly.
     *
     * <p>There is no endpoint that grants premium, and there must not be: an
     * entitlement moves because a payment provider said so (ADR 0003). This test
     * plays the part of the webhook that does not exist yet.
     */
    private void grantPremium(String status) {
        jdbc.sql("""
                INSERT INTO entitlement (id, user_id, plan, status, provider, updated_at)
                VALUES (:id, :userId, 'PREMIUM', :status, 'STRIPE', now())
                ON CONFLICT (user_id) DO UPDATE SET status = EXCLUDED.status, updated_at = now()
                """)
                .param("id", UUID.randomUUID())
                .param("userId", user.id())
                .param("status", status)
                .update();
    }
}
