package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.DeviceApproval;
import tv.lumo.api.generated.model.DeviceCodeRequest;
import tv.lumo.api.generated.model.DeviceCodeResponse;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Television activation end to end (US-05, RFC 8628).
 *
 * <p>This exists because the flow was completely broken and nothing noticed.
 * The rate-limit check read {@code last_polled_at} out of a {@code RETURNING}
 * clause, which sees the row as the UPDATE left it — the timestamp the same
 * statement had just written. Compared against {@code now()}, the transaction
 * timestamp, the difference was always zero and the answer was always
 * {@code SLOW_DOWN}: on every poll, of every authorization, including the very
 * first one of a code issued a millisecond earlier. No television could ever
 * have been activated. It was an end-to-end run against the compose stack that
 * found it, not the suite, so the suite gets this.
 *
 * <p>Runs against a real PostgreSQL: the behaviour under test <i>is</i> the
 * database's — snapshot visibility inside an UPDATE, and the transaction clock.
 * No substitute reproduces the bug, and one that did would prove nothing.
 */
@Import(PostgresContainerInitializer.class)
class DeviceActivationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private DeviceActivationService activation;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;

    @BeforeEach
    void createAccount() {
        user = users.insert(UUID.randomUUID(),
                "activation-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Activation Test", "en");
    }

    @Test
    @DisplayName("the television polls, the phone approves, the television gets a session")
    void theHappyPath() {
        DeviceCodeResponse issued = requestCode();

        // The nominal answer before approval. Anything else here — SLOW_DOWN in
        // particular — and the television is stuck on its activation screen.
        assertThatThrownBy(() -> activation.poll(issued.getDeviceCode()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTHORIZATION_PENDING);

        // Typed off the screen on a phone: separators and case are the user's
        // problem, not theirs.
        DeviceApproval approved = activation.approve(issued.getUserCode().toLowerCase(), user.id());

        // What the confirmation screen renders. Without it the phone can only
        // say "done", and the user standing in front of two televisions has no
        // way to tell which one they just linked.
        assertThat(approved.getName()).isEqualTo("Test TV");
        assertThat(approved.getPlatform()).isEqualTo(Platform.ANDROID_TV);

        // No device row yet, on purpose: it is provisioned by the poll below. An
        // approval whose television is never switched on must not leave an
        // installation behind, counted against the account's quota.
        assertThat(deviceCount()).isZero();

        allowTheNextPoll(issued);
        AuthSession session = activation.poll(issued.getDeviceCode());

        assertThat(session.getAccessToken()).isNotBlank();
        assertThat(session.getRefreshToken()).isNotBlank();
        assertThat(session.getUser().getId()).isEqualTo(user.id());

        // Provisioned here and nowhere earlier.
        assertThat(deviceCount()).isOne();
    }

    @Test
    @DisplayName("the first poll of a fresh code is never SLOW_DOWN")
    void theFirstPollIsNeverTooFast() {
        DeviceCodeResponse issued = requestCode();

        // The regression, stated on its own. `last_polled_at` is NULL on a code
        // that has never been polled, so there is no previous poll to be too
        // close to, however fast the television is.
        assertThat(codeOf(() -> activation.poll(issued.getDeviceCode())))
                .isEqualTo(ErrorCode.AUTHORIZATION_PENDING);
    }

    @Test
    @DisplayName("a second poll inside the interval is SLOW_DOWN, and a later one is not")
    void pollingTooFastIsRefusedAndRecovers() {
        DeviceCodeResponse issued = requestCode();

        assertThat(codeOf(() -> activation.poll(issued.getDeviceCode())))
                .isEqualTo(ErrorCode.AUTHORIZATION_PENDING);

        // Immediately again: this is the case the check is for.
        assertThat(codeOf(() -> activation.poll(issued.getDeviceCode())))
                .isEqualTo(ErrorCode.SLOW_DOWN);

        // And it must not be permanent. A television that adds its five seconds
        // and comes back has to be served, or SLOW_DOWN is just a dead end with
        // a friendlier name.
        allowTheNextPoll(issued);
        assertThat(codeOf(() -> activation.poll(issued.getDeviceCode())))
                .isEqualTo(ErrorCode.AUTHORIZATION_PENDING);
    }

    @Test
    @DisplayName("an approved code mints exactly one session")
    void theCodeIsSingleUse() {
        DeviceCodeResponse issued = requestCode();
        activation.approve(issued.getUserCode(), user.id());

        allowTheNextPoll(issued);
        assertThat(activation.poll(issued.getDeviceCode()).getAccessToken()).isNotBlank();

        // A second success would hand a second session to whoever replayed the
        // device code.
        allowTheNextPoll(issued);
        assertThat(codeOf(() -> activation.poll(issued.getDeviceCode())))
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("an unknown device code is not an authorization")
    void unknownCodesAreRejected() {
        assertThat(codeOf(() -> activation.poll("not-a-device-code")))
                .isEqualTo(ErrorCode.DEVICE_CODE_NOT_FOUND);
    }

    /** Devices provisioned for the account under test. */
    private long deviceCount() {
        return jdbc.sql("SELECT count(*) FROM device WHERE user_id = :userId")
                .param("userId", user.id())
                .query(Long.class)
                .single();
    }

    private DeviceCodeResponse requestCode() {
        DeviceCodeRequest request = new DeviceCodeRequest(Platform.ANDROID_TV);
        request.setName("Test TV");
        request.setAppVersion("1.0.0");
        return activation.requestCode(request);
    }

    /**
     * Moves the last poll back beyond the advertised interval.
     *
     * <p>The alternative is sleeping for the real interval in four tests, which
     * buys nothing: the production clock is the database's, and this moves the
     * database's idea of the last poll. Nothing about the check is faked — the
     * comparison it makes afterwards is the real one.
     */
    private void allowTheNextPoll(DeviceCodeResponse issued) {
        jdbc.sql("""
                UPDATE device_authorization
                   SET last_polled_at = now() - make_interval(secs => :interval + 60)
                 WHERE user_code = :userCode
                """)
                .param("interval", issued.getInterval())
                .param("userCode", issued.getUserCode())
                .update();
    }

    private ErrorCode codeOf(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e.code();
        }
        return null;
    }
}
