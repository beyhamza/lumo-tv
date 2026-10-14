package tv.lumo.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.Entitlement;
import tv.lumo.api.generated.model.Plan;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * What FREE allows as shipped, with no override: everything (R020-15,
 * BUG-R020-15-01).
 *
 * <p>0.1.0 enforced no ceiling. A default of one source and two devices made
 * the update itself take something away: an account from 0.1.0 with two
 * sources could no longer add one, and its third device could no longer sign
 * in. Nobody had decided that, and the {@code dev} profile, which raised both
 * numbers to 1000, hid it from every local run. This test reads the shipped
 * configuration, so a ceiling can only come back as a deliberate edit to it.
 */
@Import(PostgresContainerInitializer.class)
class FreePlanDefaultsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private EntitlementService entitlements;

    @Autowired
    private UserRepository users;

    private UserRow user;

    @BeforeEach
    void createAccount() {
        user = users.insert(UUID.randomUUID(),
                "free-defaults-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Free Defaults Test", "en");
    }

    @Test
    @DisplayName("FREE ships without a ceiling, and says so with nulls")
    void freeIsUnlimitedAsShipped() {
        Entitlement entitlement = entitlements.forUser(user.id(), user.createdAt());

        assertThat(entitlement.getPlan()).isEqualTo(Plan.FREE);
        // Null is "unlimited" in the contract, not "unknown": both clients read
        // it that way already.
        assertThat(entitlement.getMaxSources()).isNull();
        assertThat(entitlement.getMaxDevices()).isNull();
    }

    @Test
    @DisplayName("a FREE account past the old ceilings is still let in")
    void freeAccountsPastTheOldCeilingsAreNotRefused() {
        // Two sources and three devices: what a 0.1.0 account could hold, and
        // what the old defaults refused after the update.
        assertThatCode(() -> entitlements.requireSourceSlot(user.id(), 2)).doesNotThrowAnyException();
        assertThatCode(() -> entitlements.requireDeviceSlot(user.id(), 3)).doesNotThrowAnyException();
    }
}
