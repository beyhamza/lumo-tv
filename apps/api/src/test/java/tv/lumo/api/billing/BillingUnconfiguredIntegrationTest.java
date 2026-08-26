package tv.lumo.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * How the billing endpoints behave on a deployment with no payment provider.
 *
 * <p>That is a <b>supported state</b>, not a broken one: no developer has a
 * Stripe account, and refusing to boot without one would make the whole
 * application undevelopable. So the two endpoints answer 503 with the same
 * {@code Problem} body as everything else, and every other endpoint works.
 *
 * <p>What this pins is that they do not answer 500, and do not answer 200 with an
 * invented URL. Both would be worse: the first tells an operator to read a stack
 * trace instead of setting a key, and the second sends a user to a page that does
 * not exist.
 */
@Import(PostgresContainerInitializer.class)
class BillingUnconfiguredIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    @Test
    @DisplayName("checkout and portal answer 503 when no provider is configured")
    void billingIsUnavailableWithoutAProvider() {
        String token = accessToken();

        Response checkout = post("/v1/billing/checkout-session", "{\"plan\":\"PREMIUM\"}", token);
        Response portal = post("/v1/billing/portal-session", "{}", token);

        assertThat(checkout.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(portal.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // Not a bare framework error page: errors leave this application through
        // GlobalExceptionHandler and nowhere else.
        assertThat(checkout.body()).contains("\"code\":\"INTERNAL_ERROR\"");
        // And nothing that looks like a session URL was invented.
        assertThat(checkout.body()).doesNotContain("\"url\"");
    }

    @Test
    @DisplayName("the endpoints exist, which is what makes 503 the right answer")
    void theEndpointsAreRouted() {
        // A 404 with the generic code would mean "no controller", which is what
        // apps/web renders as "not built yet". These are built; the provider is
        // absent. The distinction is the whole point of NotFoundContractTest.
        assertThat(post("/v1/billing/portal-session", "{}", accessToken()).body())
                .doesNotContain("\"code\":\"NOT_FOUND\"");
    }

    private String accessToken() {
        UserRow user = users.insert(UUID.randomUUID(),
                "billing-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Billing Test", "en");
        UUID deviceId = devices.insert(user.id(), Platform.WEB.getValue(), "lumo.tv", null, "0.1.0");
        return sessions.openSession(user, deviceId).getAccessToken();
    }

    private Response post(String path, String body, String token) {
        return RestClient.create("http://localhost:" + port)
                .post()
                .uri(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> new Response(
                        HttpStatus.valueOf(response.getStatusCode().value()),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)),
                        false);
    }

    private record Response(HttpStatus status, String body) {
    }
}
